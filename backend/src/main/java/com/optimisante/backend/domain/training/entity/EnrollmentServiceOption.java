package com.optimisante.backend.domain.training.entity;

import com.optimisante.backend.domain.training.finance.EnrollmentPayment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service retenu par le medecin sur son dossier.
 *
 * <p><b>Le type, le libelle et le prix sont copies au moment du choix</b>, jamais relus depuis
 * le catalogue. Meme regle qu'en V27 pour le taux de commission : renegocier un tarif ne doit
 * pas reecrire ce qui a deja ete propose et accepte. Sans cette copie, changer le prix d'un
 * hebergement modifierait retroactivement le montant d'une souscription deja reglee.</p>
 *
 * <p>Souscrire ne fait pas avancer la candidature : aucun statut de dossier n'est touche. Une
 * inscription reste parfaitement valable sans aucune option.</p>
 */
@Entity
@Table(name = "enrollment_service_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentServiceOption {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    /**
     * L'offre du catalogue dont provient cette souscription.
     *
     * <p>{@code ON DELETE SET NULL} : si l'offre disparait, la souscription subsiste. Elle
     * porte deja tout ce qu'il faut pour etre lue — c'est l'objet des trois champs copies.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_service_option_id")
    private TrainingServiceOption catalogOption;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false, length = 20)
    private ServiceOptionType optionType;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ServiceOptionStatus status = ServiceOptionStatus.SELECTED;

    /** L'encaissement qui a couvert ce service. Plusieurs options partagent la meme ligne :
     *  le medecin regle son panier en une fois. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private EnrollmentPayment payment;

    @Column(name = "selected_at", nullable = false, updatable = false)
    private OffsetDateTime selectedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        if (selectedAt == null) {
            selectedAt = OffsetDateTime.now();
        }
    }
}
