package com.optimisante.backend.domain.exercerenfrance.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Charges utiles de « Exercer en France », réunies comme {@code SupplierDtos}.
 */
public final class ExercerEnFranceDtos {

    private ExercerEnFranceDtos() {
    }

    // ── Ce que lit la page publique ──────────────────────────────────────────────────────

    /**
     * La page entière, en un seul objet.
     *
     * <p>Un seul appel plutôt que six : la page n'a pas de sens par morceaux, et six requêtes
     * depuis un téléphone en connexion lente font six occasions d'afficher un écran à moitié
     * construit.</p>
     */
    /**
     * @param calendrierActualiseLe dernière publication du calendrier des commissions que NOUS
     *                              avons constatée. La page l'annonce comme un constat, et non
     *                              comme une publication d'Optimi Santé : le calendrier est
     *                              celui du CNG, et le dater nous-mêmes laisserait croire que
     *                              nous l'éditons.
     */
    public record PagePublique(List<SectionVue> sections, List<PeriodeVue> periodes,
                               List<LienVue> liens, List<SpecialiteVue> specialites,
                               List<OffreVue> offres, OffsetDateTime misAJourLe, Integer version,
                               LocalDate calendrierActualiseLe) {
    }

    public record SectionVue(String cle, String surTitre, String titre, String contenu) {
    }

    /** @param statut calculé à la lecture : « ouvert », « a_venir » ou « ferme » */
    public record PeriodeVue(String libelle, LocalDate dateDebut, LocalDate dateFin,
                             String specialitesNonOuvertes, String sourceUrl, String statut) {
    }

    public record LienVue(String cle, String libelle, String url) {
    }

    public record SpecialiteVue(String groupe, String libelle) {
    }

    /** @param prix nul : la page affiche « Sur devis » */
    /**
     * @param prixBarre prix de référence affiché barré ; nul = aucune comparaison
     * @param badge     mention mise en avant sur la carte ; nulle = aucun badge
     */
    public record OffreVue(String cle, String surTitre, String titre, List<String> points,
                           BigDecimal prix, BigDecimal prixBarre, String badge,
                           String devise, String libelleBouton) {
    }

    // ── Ce qu'envoie un visiteur ─────────────────────────────────────────────────────────

    /**
     * Une demande d'accompagnement.
     *
     * <p><b>Aucun champ de tenant, et c'est délibéré.</b> Le rattachement est décidé par le
     * serveur. Un champ ici, même ignoré, finirait par être lu un jour par distraction.</p>
     *
     * @param piege  champ invisible : rempli, c'est un robot. Les humains ne le voient pas.
     * @param ouvertDepuisMs millisecondes entre l'ouverture du formulaire et l'envoi. Fourni
     *                       par le navigateur, donc falsifiable : il écarte les robots
     *                       simples, il ne protège de rien de déterminé.
     */
    public record DemandeRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 160) String nom,
            @NotBlank(message = "L'adresse e-mail est obligatoire.")
            @Email(message = "L'adresse e-mail est mal formée.") @Size(max = 255) String email,
            @Size(max = 40) String telephoneWhatsapp,
            /*
              Le pays de résidence décide du RÉGIME DE TVA (CGV, article 3.9) : France et
              Union européenne en TTC, hors Union européenne hors taxe. Il n'est donc plus
              facultatif — une demande sans pays ne peut pas être facturée correctement, et
              le réclamer après coup par e-mail fait perdre le client.
            */
            @NotBlank(message = "Le pays de résidence est nécessaire : il détermine le régime de TVA applicable.")
            @Size(max = 80) String pays,
            @Size(max = 80) String profession,
            @Size(max = 160) String specialite,
            @Size(max = 20) String etape,
            @Size(max = 60) String offreCle,
            @Size(max = 4000) String message,
            @AssertTrue(message = "Le consentement est obligatoire pour enregistrer votre demande.")
            Boolean consentementRgpd,
            @Size(max = 200) String piege,
            Long ouvertDepuisMs) {
    }

    public record DemandeConfirmation(UUID id, String message) {
    }

    // ── Administration ───────────────────────────────────────────────────────────────────

    public record SectionRequest(@NotBlank @Size(max = 60) String cle, @Size(max = 120) String surTitre,
                                 @NotBlank @Size(max = 255) String titre, String contenu,
                                 Integer ordre, Boolean visible) {
    }

    public record PeriodeRequest(@NotBlank @Size(max = 120) String libelle, LocalDate dateDebut,
                                 LocalDate dateFin, String specialitesNonOuvertes,
                                 @NotBlank(message = "La source officielle est obligatoire.")
                                 @Size(max = 1000) String sourceUrl,
                                 Boolean visible, Integer ordre) {
    }

    public record LienRequest(@NotBlank @Size(max = 60) String cle, @NotBlank @Size(max = 255) String libelle,
                              @Size(max = 1000) String url) {
    }

    public record SpecialiteRequest(@NotBlank @Size(max = 20) String groupe,
                                    @NotBlank @Size(max = 160) String libelle, Integer ordre) {
    }

    public record OffreRequest(@NotBlank @Size(max = 60) String cle, @Size(max = 120) String surTitre,
                               @NotBlank @Size(max = 255) String titre, String points,
                               @DecimalMin(value = "0.0", message = "Un prix ne peut pas être négatif.")
                               BigDecimal prix, @Size(max = 3) String devise,
                               @DecimalMin(value = "0.0", message = "Un prix de référence ne peut pas être négatif.")
                               BigDecimal prixBarre, @Size(max = 40) String badge,
                               @NotBlank @Size(max = 80) String libelleBouton,
                               Boolean actif, Boolean technique, Integer ordre) {
    }

    /** Les quatre chiffres du tableau de bord, et les trois rappels. */
    /**
     * Les quatre chiffres du tableau de bord, et les quatre rappels.
     *
     * @param calendrierAVerifier    le calendrier des commissions n'a pas été actualisé depuis
     *                               plus de 90 jours, ou sa date est inconnue
     * @param prestationsSansFin     demandes engagées depuis plus d'un an dont la fin de
     *                               prestation n'est pas renseignée : leur délai de
     *                               conservation n'a jamais commencé
     */
    public record Indicateurs(long demandesATraiter, long demandesCeMois, long demandesPayees,
                              OffsetDateTime dernierePublication, Integer version,
                              long liensNonVerifies, long periodesSansDates,
                              boolean calendrierAVerifier, long prestationsSansFin) {
    }

    public record VersionVue(Integer version, OffsetDateTime publieLe, String publiePar,
                             Integer restaureDe) {
    }

    /**
     * Ce qu'une restauration a repris, et rien de plus.
     *
     * <p>Elle ne crée aucune version : elle recopie dans le brouillon. Les nombres disent
     * combien d'entrées ont été réécrites — une entrée supprimée depuis n'est pas
     * ressuscitée, et l'écart se lit ici.</p>
     */
    public record RestaurationVue(int versionSource, int sections, int periodes,
                                  int liens, int offres) {
    }

    public record DemandeVue(UUID id, String nom, String email, String telephoneWhatsapp, String pays,
                             String profession, String specialite, String etape, String offreCle,
                             String message, String statut, UUID assigneA, String notesInternes,
                             OffsetDateTime creeLe, OffsetDateTime derniereActiviteLe,
                             OffsetDateTime prestationFinLe, boolean anonymisee,
                             List<EvenementVue> fil) {
    }

    /**
     * Une demande telle qu'elle apparaît dans la LISTE.
     *
     * <p><b>Pourquoi la liste ne renvoie pas l'entité.</b> Elle porte le message du praticien
     * et les notes internes — ce que l'équipe écrit entre elle sur un dossier. Un écran qui
     * affiche vingt-cinq lignes n'a besoin ni de l'un ni de l'autre, et tout ce qui part vers
     * le navigateur est lisible par qui ouvre les outils de développement. Le détail, lui, est
     * demandé dossier par dossier, et c'est à ce moment-là que ces champs sont justifiés.</p>
     */
    public record DemandeResume(UUID id, String nom, String email, String telephoneWhatsapp,
                                String pays, String profession, String specialite, String etape,
                                String offreCle, String statut, UUID assigneA,
                                OffsetDateTime creeLe, OffsetDateTime derniereActiviteLe,
                                boolean anonymisee) {
    }

    public record EvenementVue(String type, String ancienStatut, String nouveauStatut,
                               String contenu, UUID auteurId, OffsetDateTime creeLe) {
    }

    public record ChangementStatutRequest(@NotBlank String statut, String note,
                                          OffsetDateTime prestationFinLe) {
    }

    public record AssignationRequest(UUID assigneA, String note) {
    }

    public record NoteRequest(@NotBlank(message = "Une note vide n'a rien à dire.") String contenu) {
    }
}
