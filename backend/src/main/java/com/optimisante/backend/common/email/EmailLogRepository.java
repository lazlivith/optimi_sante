package com.optimisante.backend.common.email;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {

    /**
     * Filtres optionnels : chaque critère est ignoré quand son paramètre vaut null.
     *
     * `CAST(:search AS string)` est indispensable : PostgreSQL ne peut pas déduire le type
     * d'un paramètre lié valant NULL et le suppose `bytea`, ce qui fait échouer l'appel à
     * LOWER() avec « function lower(bytea) does not exist » dès que le filtre est vide.
     */
    @Query("""
            SELECT l FROM EmailLog l
            WHERE (:status IS NULL OR l.status = :status)
              AND (:emailType IS NULL OR l.emailType = :emailType)
              AND (:search IS NULL OR LOWER(l.recipient) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            ORDER BY l.sentAt DESC
            """)
    Page<EmailLog> search(EmailStatus status, EmailType emailType, String search, Pageable pageable);

    long countByStatus(EmailStatus status);
}
