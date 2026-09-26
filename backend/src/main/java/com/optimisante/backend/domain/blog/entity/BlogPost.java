package com.optimisante.backend.domain.blog.entity;

import com.optimisante.backend.domain.identity.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une publication du blog : article d'actualité ou annonce d'événement.
 *
 * <p>La distinction ne tient pas à un type mais à une date : {@link #eventStartsOn}
 * renseigné fait de la publication un événement, qui porte alors un lieu et des dates.
 * Voir la migration V61 pour le détail du choix.</p>
 */
@Entity
@Table(name = "blog_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlogPost {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 200)
    private String title;

    /** Adresse publique de la publication : fixée à la création, jamais recalculée ensuite. */
    @Column(nullable = false, length = 220)
    private String slug;

    @Column(length = 400)
    private String excerpt;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "cover_image_url", columnDefinition = "text")
    private String coverImageUrl;

    @Column(name = "event_location", length = 160)
    private String eventLocation;

    @Column(name = "event_starts_on")
    private LocalDate eventStartsOn;

    @Column(name = "event_ends_on")
    private LocalDate eventEndsOn;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Un événement se reconnaît à sa date : sans elle, la publication est un article. */
    public boolean estUnEvenement() {
        return eventStartsOn != null;
    }
}
