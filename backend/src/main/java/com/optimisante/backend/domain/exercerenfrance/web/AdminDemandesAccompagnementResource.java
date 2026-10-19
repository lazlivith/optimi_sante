package com.optimisante.backend.domain.exercerenfrance.web;

import com.optimisante.backend.config.security.MobilityAdmin;
import com.optimisante.backend.domain.exercerenfrance.dto.ExercerEnFranceDtos.*;
import com.optimisante.backend.domain.exercerenfrance.entity.AccompanimentRequest;
import com.optimisante.backend.domain.exercerenfrance.service.CelluleCsv;
import com.optimisante.backend.domain.exercerenfrance.service.ServiceDemandesAccompagnement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Suivi des demandes d'accompagnement.
 *
 * <p>Entièrement sous {@code @MobilityAdmin} : traiter une demande est un acte métier, pas un
 * acte de gouvernance. Les prix, eux, restent ailleurs et sous une autre garde.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/demandes-accompagnement")
@RequiredArgsConstructor
public class AdminDemandesAccompagnementResource {

    private final ServiceDemandesAccompagnement service;

    @GetMapping
    @MobilityAdmin
    public ResponseEntity<Page<DemandeResume>> lister(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String offreCle,
            @RequestParam(required = false) String profession,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqua,
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int taille) {
        var trouvees = service.lister(filtres(statut, offreCle, profession, depuis, jusqua,
                        recherche),
                PageRequest.of(page, Math.min(taille, 100), Sort.by(Sort.Direction.DESC, "creeLe")));
        return ResponseEntity.ok(trouvees.map(AdminDemandesAccompagnementResource::resume));
    }

    /**
     * Assemble les critères.
     *
     * <p>Les dates arrivent en JOURS et désignent des journées entières : « jusqu'au 12 »
     * inclut le 12 au soir. Les prendre à minuit amputerait le dernier jour, et c'est
     * toujours celui qu'on cherche.</p>
     */
    private static ServiceDemandesAccompagnement.Filtres filtres(
            String statut, String offreCle, String profession,
            LocalDate depuis, LocalDate jusqua, String recherche) {
        ZoneId paris = ZoneId.of("Europe/Paris");
        return new ServiceDemandesAccompagnement.Filtres(statut, offreCle, profession,
                depuis == null ? null : depuis.atStartOfDay(paris).toOffsetDateTime(),
                jusqua == null ? null : jusqua.plusDays(1).atStartOfDay(paris).toOffsetDateTime(),
                recherche);
    }

    @GetMapping("/{id}")
    @MobilityAdmin
    public ResponseEntity<DemandeVue> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(service.detail(id));
    }

    @PostMapping("/{id}/statut")
    @MobilityAdmin
    public ResponseEntity<Void> changerStatut(@PathVariable UUID id,
                                              @Valid @RequestBody ChangementStatutRequest dto,
                                              Authentication auth) {
        service.changerStatut(id, dto, utilisateur(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assignation")
    @MobilityAdmin
    public ResponseEntity<Void> assigner(@PathVariable UUID id,
                                         @RequestBody AssignationRequest dto,
                                         Authentication auth) {
        service.assigner(id, dto, utilisateur(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/notes")
    @MobilityAdmin
    public ResponseEntity<Void> ajouterNote(@PathVariable UUID id,
                                            @Valid @RequestBody NoteRequest dto,
                                            Authentication auth) {
        service.ajouterNote(id, dto, utilisateur(auth));
        return ResponseEntity.noContent().build();
    }

    /**
     * Export complet des demandes retenues par les filtres.
     *
     * <p><b>Point-virgule et BOM.</b> Excel en configuration française découpe sur le
     * point-virgule et ne lit l'UTF-8 que s'il est annoncé : sans ces deux détails, le
     * fichier s'ouvre en une seule colonne, accents cassés.</p>
     *
     * <p><b>Chaque export est inscrit au journal d'audit.</b> Il fait sortir des données
     * personnelles vers un fichier que plus rien ne protège : si l'une d'elles réapparaît
     * ailleurs, la seule question qui compte est de savoir qui l'a sortie, et quand.</p>
     */
    @GetMapping(value = "/export.csv", produces = "text/csv")
    @MobilityAdmin
    public ResponseEntity<byte[]> exporter(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String offreCle,
            @RequestParam(required = false) String profession,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqua,
            @RequestParam(required = false) String recherche) {

        var criteres = filtres(statut, offreCle, profession, depuis, jusqua, recherche);
        var lignes = service.lister(criteres,
                PageRequest.of(0, EXPORT_MAX, Sort.by(Sort.Direction.DESC, "creeLe")));

        StringBuilder csv = new StringBuilder(CelluleCsv.BOM);
        ligne(csv, "Date", "Nom", "E-mail", "Téléphone", "Pays", "Profession",
                "Spécialité", "Étape", "Offre", "Statut");
        lignes.forEach(d -> ligne(csv,
                String.valueOf(d.getCreeLe()), d.getNom(), d.getEmail(),
                d.getTelephoneWhatsapp(), d.getPays(), d.getProfession(), d.getSpecialite(),
                d.getEtape(), d.getOffreCle(), d.getStatut()));

        service.tracerExport("demandes", criteres, (int) lignes.getNumberOfElements());
        return fichier(csv.toString(), "demandes-accompagnement.csv");
    }

    /**
     * Les contacts à prévenir quand le CNG publiera ses dates.
     *
     * <p><b>Volontairement plus pauvre que l'export complet.</b> Ce fichier sert à écrire à
     * des gens, pas à les étudier : il porte de quoi les joindre et les orienter, rien de
     * plus. Y verser leur message ou leur statut commercial ferait sortir des données dont
     * cet usage n'a pas besoin — et un export minimal est aussi un export qu'on peut
     * partager sans y réfléchir à deux fois.</p>
     */
    @GetMapping(value = "/export-alertes.csv", produces = "text/csv")
    @MobilityAdmin
    public ResponseEntity<byte[]> exporterAlertes() {
        var criteres = new ServiceDemandesAccompagnement.Filtres(
                null, OFFRE_ALERTE_AEP, null, null, null, null);
        var lignes = service.lister(criteres,
                PageRequest.of(0, EXPORT_MAX, Sort.by(Sort.Direction.DESC, "creeLe")));

        StringBuilder csv = new StringBuilder(CelluleCsv.BOM);
        ligne(csv, "Nom", "E-mail", "WhatsApp", "Profession", "Spécialité", "Demandé le");
        // Le numéro passe par numeroTelephone() : ce fichier existe pour qu'on COLLE ces
        // numéros dans WhatsApp, et l'apostrophe de protection s'y afficherait.
        lignes.forEach(d -> csv
                .append(CelluleCsv.neutraliser(d.getNom())).append(CelluleCsv.SEPARATEUR)
                .append(CelluleCsv.neutraliser(d.getEmail())).append(CelluleCsv.SEPARATEUR)
                .append(CelluleCsv.numeroTelephone(d.getTelephoneWhatsapp())).append(CelluleCsv.SEPARATEUR)
                .append(CelluleCsv.neutraliser(d.getProfession())).append(CelluleCsv.SEPARATEUR)
                .append(CelluleCsv.neutraliser(d.getSpecialite())).append(CelluleCsv.SEPARATEUR)
                .append(CelluleCsv.neutraliser(String.valueOf(d.getCreeLe()))).append('\n'));

        service.tracerExport("alertes-aep", criteres, (int) lignes.getNumberOfElements());
        return fichier(csv.toString(), "contacts-a-prevenir.csv");
    }

    /** Clé de l'offre technique rattachée aux demandes d'alerte d'ouverture. */
    private static final String OFFRE_ALERTE_AEP = "alerte_aep";

    /**
     * Plafond d'un export.
     *
     * <p>Assez large pour n'amputer aucun usage réel, assez borné pour qu'une erreur de
     * filtre ne compose pas un fichier de plusieurs dizaines de milliers de lignes en
     * mémoire — sur une instance qui en a peu.</p>
     */
    private static final int EXPORT_MAX = 2000;

    /** Ce qu'une ligne de liste a le droit d'emporter : ni le message, ni les notes. */
    private static DemandeResume resume(AccompanimentRequest d) {
        return new DemandeResume(d.getId(), d.getNom(), d.getEmail(), d.getTelephoneWhatsapp(),
                d.getPays(), d.getProfession(), d.getSpecialite(), d.getEtape(), d.getOffreCle(),
                d.getStatut(), d.getAssigneA(), d.getCreeLe(), d.getDerniereActiviteLe(),
                d.estAnonymisee());
    }

    private static void ligne(StringBuilder csv, String... valeurs) {
        for (int i = 0; i < valeurs.length; i++) {
            if (i > 0) {
                csv.append(CelluleCsv.SEPARATEUR);
            }
            csv.append(CelluleCsv.neutraliser(valeurs[i]));
        }
        csv.append('\n');
    }

    private static ResponseEntity<byte[]> fichier(String csv, String nom) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + nom + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
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
