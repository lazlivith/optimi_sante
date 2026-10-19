package com.optimisante.backend.domain.exercerenfrance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une demande d'accompagnement déposée depuis la page publique.
 *
 * <p><b>Le tenant est posé par le serveur.</b> La route qui crée ces lignes est ouverte aux
 * visiteurs non connectés : lire le tenant dans un en-tête ou dans le corps laisserait
 * l'appelant choisir chez qui sa demande atterrit. Voir {@code TenantParDefaut}.</p>
 *
 * <p><b>Deux dates, deux usages.</b> {@code creeLe} ne bouge plus ; {@code derniereActiviteLe}
 * suit chaque événement du fil. La conservation se compte sur la seconde : calculée sur la
 * première, elle effacerait un dossier encore en cours de traitement.</p>
 */
@Entity
@Table(name = "accompaniment_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccompanimentRequest {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 160)
    private String nom;

    @Column(nullable = false)
    private String email;

    @Column(name = "telephone_whatsapp", length = 40)
    private String telephoneWhatsapp;

    @Column(length = 80)
    private String pays;

    @Column(length = 80)
    private String profession;

    @Column(length = 160)
    private String specialite;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String etape = Enums.Etape.AUTRE.code();

    /**
     * Clé de l'offre, et non clé étrangère : retirer une offre du catalogue ne doit pas
     * emporter les demandes qu'elle a suscitées, ni empêcher de les relire.
     */
    @Column(name = "offre_cle", length = 60)
    private String offreCle;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "consentement_rgpd", nullable = false)
    @Builder.Default
    private Boolean consentementRgpd = false;

    /** La case cochée ne prouve rien sans la date : la contrainte V72 exige les deux. */
    @Column(name = "consentement_le")
    private OffsetDateTime consentementLe;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = Enums.Statut.NOUVEAU.code();

    @Column(name = "assigne_a")
    private UUID assigneA;

    @Column(name = "notes_internes", columnDefinition = "TEXT")
    private String notesInternes;

    @Column(name = "cree_le", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime creeLe = OffsetDateTime.now();

    @Column(name = "derniere_activite_le", nullable = false)
    @Builder.Default
    private OffsetDateTime derniereActiviteLe = OffsetDateTime.now();

    /** Fin de prestation : ouvre le délai de conservation des demandes réglées. */
    @Column(name = "prestation_fin_le")
    private OffsetDateTime prestationFinLe;

    /** Renseignée par la tâche d'anonymisation : la ligne reste, les données nominatives non. */
    @Column(name = "anonymise_le")
    private OffsetDateTime anonymiseLe;

    public boolean estAnonymisee() {
        return anonymiseLe != null;
    }
}
