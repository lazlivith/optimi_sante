package com.optimisante.backend.domain.training.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entretien de selection : le CHU propose des creneaux, OptimiSante transmet, le medecin choisit.
 *
 * <p>Le passage par l'administration reprend la regle deja portee par
 * {@link EnrollmentTransitions} — le partenaire ne s'adresse jamais directement au medecin.
 * OptimiSante reste l'intermediaire du parcours, et garde la trace de ce qui a ete promis.</p>
 *
 * <p><b>Aucun statut de dossier n'est touche.</b> Un entretien accompagne la decision du
 * partenaire sans la remplacer : un dossier peut etre accepte sans entretien, et un entretien
 * confirme ne fait pas avancer la candidature. Meme choix qu'en V37 pour les demandes de
 * pieces — greffer chaque etape sur l'automate finirait par le rendre illisible.</p>
 */
@Entity
@Table(name = "interview_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSchedule {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InterviewScheduleStatus status = InterviewScheduleStatus.PROPOSED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewMode mode;

    /** Adresse pour un entretien sur place, lien pour une visio. Facultatif au telephone. */
    @Column(name = "location_or_link", length = 512)
    private String locationOrLink;

    /** Consignes du CHU : jury, duree, pieces a preparer. Le medecin les lit telles quelles. */
    @Column(name = "partner_note", columnDefinition = "TEXT")
    private String partnerNote;

    /** Identifiants bruts, sans association : un compte desactive ne doit pas rendre
     *  l'historique illisible (meme raison qu'en V25 pour les emails). */
    @Column(name = "proposed_by")
    private UUID proposedBy;

    @Column(name = "proposed_at", nullable = false, updatable = false)
    private OffsetDateTime proposedAt;

    @Column(name = "transmitted_by")
    private UUID transmittedBy;

    @Column(name = "transmitted_at")
    private OffsetDateTime transmittedAt;

    /** Ce qu'OptimiSante ajoute au message du CHU. Distinct de {@code partnerNote} :
     *  le medecin doit pouvoir distinguer qui lui dit quoi. */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    /**
     * Creneau retenu par le medecin.
     *
     * <p>Association simple plutot que par identifiant : contrairement aux auteurs, un creneau
     * supprime doit bien faire disparaitre le choix — c'est le sens du {@code ON DELETE SET
     * NULL} de la V41, et la contrainte de coherence empeche alors l'entretien de rester
     * « confirme » sans rendez-vous.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_slot_id")
    private InterviewSlot confirmedSlot;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "cancelled_reason", columnDefinition = "TEXT")
    private String cancelledReason;

    /** Convocation deposee au coffre du dossier a la confirmation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convocation_document_id")
    private EnrollmentDocument convocationDocument;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startsAt ASC")
    @Builder.Default
    private List<InterviewSlot> slots = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (proposedAt == null) {
            proposedAt = OffsetDateTime.now();
        }
    }
}
