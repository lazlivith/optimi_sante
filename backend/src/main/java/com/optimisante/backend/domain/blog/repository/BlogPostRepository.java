package com.optimisante.backend.domain.blog.repository;

import com.optimisante.backend.domain.blog.entity.BlogPost;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    /** Le dos de l'écran d'administration : brouillons compris, les plus récents d'abord. */
    List<BlogPost> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** Ce que voit le public : uniquement ce qui a été mis en ligne. */
    List<BlogPost> findByTenantIdAndIsPublishedTrueOrderByPublishedAtDesc(UUID tenantId);

    /**
     * Les événements encore d'actualité, le plus proche d'abord — la bannière d'accueil
     * n'en montre qu'un.
     *
     * <p>Un congrès de quatre jours reste d'actualité pendant qu'il se tient : la borne
     * porte donc sur la date de fin, et sur la date de début pour un événement d'une seule
     * journée, qui n'en a pas.</p>
     */
    @Query("""
           SELECT b FROM BlogPost b
            WHERE b.tenant.id = :tenantId
              AND b.isPublished = true
              AND b.eventStartsOn IS NOT NULL
              AND COALESCE(b.eventEndsOn, b.eventStartsOn) >= :aPartirDe
            ORDER BY b.eventStartsOn ASC
           """)
    List<BlogPost> evenementsAVenir(@Param("tenantId") UUID tenantId,
                                    @Param("aPartirDe") LocalDate aPartirDe,
                                    Limit combien);

    Optional<BlogPost> findByTenantIdAndSlug(UUID tenantId, String slug);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);
}
