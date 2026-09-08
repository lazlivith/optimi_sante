package com.optimisante.backend.domain.notification.repository;

import com.optimisante.backend.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Toutes les requêtes sont bornées au destinataire courant : jamais de lecture croisée entre
 * utilisateurs. Les paramètres liés sont toujours non nuls (id de l'utilisateur, horodatage) :
 * on évite le piège JPQL «&nbsp;:param IS NULL OR ...&nbsp;» rencontré ailleurs sur PostgreSQL.
 * Les conditions {@code colonne IS NULL} (snooze) sont sûres — ce sont des colonnes, pas des paramètres.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    String NOT_SNOOZED = "(n.snoozedUntil IS NULL OR n.snoozedUntil <= :now)";

    @Query("SELECT n FROM Notification n WHERE n.recipientUserId = :uid AND " + NOT_SNOOZED
            + " ORDER BY n.createdAt DESC")
    Page<Notification> findVisible(@Param("uid") UUID recipientUserId,
                                  @Param("now") OffsetDateTime now, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.recipientUserId = :uid AND n.readAt IS NULL AND "
            + NOT_SNOOZED + " ORDER BY n.createdAt DESC")
    Page<Notification> findVisibleUnread(@Param("uid") UUID recipientUserId,
                                         @Param("now") OffsetDateTime now, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientUserId = :uid AND n.readAt IS NULL AND "
            + NOT_SNOOZED)
    long countVisibleUnread(@Param("uid") UUID recipientUserId, @Param("now") OffsetDateTime now);

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    /** Doublon candidat pour la déduplication : même destinataire, même clé, non acquitté. */
    Optional<Notification> findFirstByRecipientUserIdAndDedupeKeyAndAcknowledgedAtIsNullOrderByCreatedAtDesc(
            UUID recipientUserId, String dedupeKey);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :ts WHERE n.recipientUserId = :uid AND n.readAt IS NULL")
    int markAllRead(@Param("uid") UUID recipientUserId, @Param("ts") OffsetDateTime ts);
}
