package com.optimisante.backend.domain.exercerenfrance.web;

import com.optimisante.backend.config.security.MobilityAdmin;
import com.optimisante.backend.config.security.SuperAdmin;
import com.optimisante.backend.domain.exercerenfrance.dto.ExercerEnFranceDtos.*;
import com.optimisante.backend.domain.exercerenfrance.entity.EvcContenu;
import com.optimisante.backend.domain.exercerenfrance.service.ServiceContenuEvc;
import com.optimisante.backend.domain.exercerenfrance.service.ServiceDemandesAccompagnement;
import com.optimisante.backend.domain.exercerenfrance.service.ServicePublicationEvc;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administration du contenu « Exercer en France ».
 *
 * <p><b>Deux niveaux de droit, et la frontière n'est pas arbitraire.</b> L'admin Mobilité
 * tient le contenu : textes, dates, liens, spécialités, et la mise en ligne. Restent au seul
 * super administrateur les deux actes qui n'appartiennent pas à l'éditorial — fixer un
 * <b>prix</b>, qui engage le chiffre d'affaires, et <b>restaurer une version</b>, qui réécrit
 * ce que le public lit sans passer par la relecture.</p>
 *
 * <p>Les annotations sont posées sur les METHODES et non sur la classe : une garde de classe
 * se serait appliquée aussi aux prix, et il aurait fallu l'y contredire — un endroit de plus
 * où se tromper.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/exercer-en-france")
@RequiredArgsConstructor
public class AdminExercerEnFranceResource {

    private final ServiceContenuEvc contenu;
    private final ServicePublicationEvc publication;
    private final ServiceDemandesAccompagnement demandes;

    // ── Sections ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/sections")
    @MobilityAdmin
    public ResponseEntity<List<EvcContenu.Section>> sections() {
        return ResponseEntity.ok(contenu.sections());
    }

    @PostMapping("/sections")
    @MobilityAdmin
    public ResponseEntity<EvcContenu.Section> creerSection(@Valid @RequestBody SectionRequest dto) {
        return ResponseEntity.ok(contenu.enregistrerSection(null, dto));
    }

    @PutMapping("/sections/{id}")
    @MobilityAdmin
    public ResponseEntity<EvcContenu.Section> modifierSection(@PathVariable UUID id,
                                                              @Valid @RequestBody SectionRequest dto) {
        return ResponseEntity.ok(contenu.enregistrerSection(id, dto));
    }

    @PostMapping("/sections/{id}/deplacer")
    @MobilityAdmin
    public ResponseEntity<Void> deplacerSection(@PathVariable UUID id,
                                                @RequestParam(defaultValue = "true") boolean versLeHaut) {
        contenu.deplacerSection(id, versLeHaut);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sections/{id}")
    @MobilityAdmin
    public ResponseEntity<Void> supprimerSection(@PathVariable UUID id) {
        contenu.supprimerSection(id);
        return ResponseEntity.noContent().build();
    }

    // ── Périodes ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/periodes")
    @MobilityAdmin
    public ResponseEntity<List<EvcContenu.Periode>> periodes() {
        return ResponseEntity.ok(contenu.periodes());
    }

    @PostMapping("/periodes")
    @MobilityAdmin
    public ResponseEntity<EvcContenu.Periode> creerPeriode(@Valid @RequestBody PeriodeRequest dto) {
        return ResponseEntity.ok(contenu.enregistrerPeriode(null, dto));
    }

    @PutMapping("/periodes/{id}")
    @MobilityAdmin
    public ResponseEntity<EvcContenu.Periode> modifierPeriode(@PathVariable UUID id,
                                                               @Valid @RequestBody PeriodeRequest dto) {
        return ResponseEntity.ok(contenu.enregistrerPeriode(id, dto));
    }

    @DeleteMapping("/periodes/{id}")
    @MobilityAdmin
    public ResponseEntity<Void> supprimerPeriode(@PathVariable UUID id) {
        contenu.supprimerPeriode(id);
        return ResponseEntity.noContent().build();
    }

    // ── Liens ────────────────────────────────────────────────────────────────────────────

    @GetMapping("/liens")
    @MobilityAdmin
    public ResponseEntity<List<EvcContenu.Lien>> liens() {
        return ResponseEntity.ok(contenu.liens());
    }

    @PostMapping("/liens")
    @MobilityAdmin
    public ResponseEntity<EvcContenu.Lien> creerLien(@Valid @RequestBody LienRequest dto) {
        return ResponseEntity.ok(contenu.enregistrerLien(null, dto));
    }

    @PutMapping("/liens/{id}")
    @MobilityAdmin
    public ResponseEntity<EvcContenu.Lien> modifierLien(@PathVariable UUID id,
                                                        @Valid @RequestBody LienRequest dto) {
        return ResponseEntity.ok(contenu.enregistrerLien(id, dto));
    }

    @PostMapping("/liens/{id}/verifier")
    @MobilityAdmin
    public ResponseEntity<Void> verifierLien(@PathVariable UUID id) {
        contenu.marquerLienVerifie(id);
        return ResponseEntity.noContent().build();
    }

    // ── Spécialités ──────────────────────────────────────────────────────────────────────

    @GetMapping("/specialites")
    @MobilityAdmin
    public ResponseEntity<List<EvcContenu.Specialite>> specialites() {
        return ResponseEntity.ok(contenu.specialites());
    }

    @PostMapping("/specialites")
    @MobilityAdmin
    public ResponseEntity<EvcContenu.Specialite> creerSpecialite(@Valid @RequestBody SpecialiteRequest dto) {
        return ResponseEntity.ok(contenu.enregistrerSpecialite(null, dto));
    }

    @PutMapping("/specialites/{id}")
    @MobilityAdmin
    public ResponseEntity<EvcContenu.Specialite> modifierSpecialite(@PathVariable UUID id,
                                                                     @Valid @RequestBody SpecialiteRequest dto) {
        return ResponseEntity.ok(contenu.enregistrerSpecialite(id, dto));
    }

    @DeleteMapping("/specialites/{id}")
    @MobilityAdmin
    public ResponseEntity<Void> supprimerSpecialite(@PathVariable UUID id) {
        contenu.supprimerSpecialite(id);
        return ResponseEntity.noContent().build();
    }

    // ── Offres : LECTURE pour la Mobilité, ÉCRITURE pour le super administrateur seul ────

    @GetMapping("/offres")
    @MobilityAdmin
    public ResponseEntity<List<EvcContenu.Offre>> offres() {
        return ResponseEntity.ok(contenu.offres());
    }

    @PostMapping("/offres")
    @SuperAdmin
    public ResponseEntity<EvcContenu.Offre> creerOffre(@Valid @RequestBody OffreRequest dto) {
        return ResponseEntity.ok(contenu.enregistrerOffre(null, dto));
    }

    @PutMapping("/offres/{id}")
    @SuperAdmin
    public ResponseEntity<EvcContenu.Offre> modifierOffre(@PathVariable UUID id,
                                                          @Valid @RequestBody OffreRequest dto) {
        return ResponseEntity.ok(contenu.enregistrerOffre(id, dto));
    }

    // ── Aperçu, publication, historique ──────────────────────────────────────────────────

    /** Le brouillon tel qu'il serait publié : l'aperçu montre ce que l'on s'apprête à diffuser. */
    @GetMapping("/apercu")
    @MobilityAdmin
    public ResponseEntity<PagePublique> apercu() {
        return ResponseEntity.ok(publication.brouillon());
    }

    @PostMapping("/publier")
    @MobilityAdmin
    public ResponseEntity<VersionVue> publier(Authentication auth) {
        return ResponseEntity.ok(publication.publier(utilisateur(auth)));
    }

    @GetMapping("/versions")
    @MobilityAdmin
    public ResponseEntity<List<VersionVue>> versions() {
        return ResponseEntity.ok(publication.historique());
    }

    @PostMapping("/versions/{version}/restaurer")
    @SuperAdmin
    public ResponseEntity<RestaurationVue> restaurer(@PathVariable int version,
                                                     Authentication auth) {
        return ResponseEntity.ok(publication.restaurer(version, utilisateur(auth)));
    }

    /** Date de la dernière publication connue du calendrier des commissions. */
    @PutMapping("/reglages/calendrier-actualise-le")
    @MobilityAdmin
    public ResponseEntity<Void> definirCalendrier(@RequestParam java.time.LocalDate date) {
        contenu.definirCalendrierActualiseLe(date);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/indicateurs")
    @MobilityAdmin
    public ResponseEntity<Indicateurs> indicateurs() {
        List<VersionVue> versions = publication.historique();
        VersionVue derniere = versions.isEmpty() ? null : versions.get(0);
        return ResponseEntity.ok(demandes.indicateurs(
                contenu.liensNonVerifies(), contenu.periodesSansDates(),
                contenu.calendrierAVerifier(),
                derniere == null ? null : derniere.publieLe(),
                derniere == null ? null : derniere.version()));
    }

    /**
     * L'identifiant de l'auteur, ou rien.
     *
     * <p><b>Un principal illisible ne doit pas faire échouer l'acte.</b> Cette valeur sert à
     * TRACER qui a publié ; elle ne conditionne aucun droit — ceux-là sont vérifiés par les
     * annotations, avant d'arriver ici. La version précédente appelait {@code
     * UUID.fromString} directement : toute authentification dont le principal n'est pas un
     * UUID rendait « UUID string too large » et un 400, c'est-à-dire une publication refusée
     * pour une raison qui n'a rien à voir avec la publication.</p>
     */
    private static UUID utilisateur(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getPrincipal().toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
