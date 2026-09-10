package com.optimisante.backend.domain.training.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service propose autour d'une formation : assurance, hebergement, transport.
 *
 * <p><b>Tarife par l'administration de la mobilite, jamais par le partenaire.</b> Meme regle
 * que pour les frais de dossier (V40) : le CHU ne fixe pas la remuneration d'une prestation
 * qu'il ne fournit pas. Le prix depend de la ville, de la duree et du prestataire retenu, il se
 * decide donc formation par formation.</p>
 */
@Entity
@Table(name = "training_service_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingServiceOption {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id", nullable = false)
    private Training training;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false, length = 20)
    private ServiceOptionType optionType;

    /**
     * Ce que le medecin lira. Le type ne suffit pas : {@code HOUSING} ne dit pas s'il s'agit
     * d'un studio meuble ou d'une chambre en internat.
     */
    @Column(nullable = false, length = 255)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Retirer une offre du catalogue la desactive au lieu de la supprimer : les souscriptions
     * deja passees continuent de pointer vers elle, et l'historique reste lisible.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Identifiant brut, sans association : un administrateur desactive ne doit pas rendre
     *  l'historique du catalogue illisible (meme raison qu'en V25 pour les emails). */
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
