package com.optimisante.backend.domain.exercerenfrance.service;

import com.optimisante.backend.domain.exercerenfrance.repository.*;
import com.optimisante.backend.config.tenant.TenantParDefaut;
import com.optimisante.backend.domain.audit.service.AuditService;
import com.optimisante.backend.domain.exercerenfrance.dto.ExercerEnFranceDtos.*;
import com.optimisante.backend.domain.exercerenfrance.entity.EvcContenu;
import com.optimisante.backend.domain.exercerenfrance.entity.EvcPublication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Le brouillon, la publication, et ce que lit le public.
 *
 * <p><b>Le partage est net.</b> Les tables de contenu sont le brouillon : l'administration y
 * écrit librement, et rien de ce qu'elle y fait n'atteint un visiteur. Publier fige l'état
 * courant en un document, qui devient la seule chose que la page publique lit. Une faute de
 * frappe corrigée à 14 h ne part en ligne qu'au moment choisi.</p>
 *
 * <p><b>L'instantané tient lieu de cache.</b> Servir la page, c'est rendre une colonne déjà
 * sérialisée — une ligne, aucune jointure, aucune recomposition. C'est pourquoi aucun cache
 * mémoire n'a été ajouté : il n'aurait rien à économiser, et il pèserait sur une instance
 * déjà juste.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicePublicationEvc {

    /**
     * Le projet n'expose pas de {@code ObjectMapper} injectable : chaque service qui en a
     * besoin tient le sien.
     *
     * <p><b>Celui de Jackson 3, et non le {@code com.fasterxml} des autres services.</b>
     * C'est celui que Spring Boot 4 embarque et qui sérialise déjà la réponse HTTP :
     * l'instantané figé et la réponse servie ont donc exactement la même forme. Le
     * {@code com.fasterxml} utilisé ailleurs n'arrive ici que par transitivité, sans son
     * module de dates — il ne saurait pas écrire un {@code OffsetDateTime}.</p>
     */
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final DepotSections sections;
    private final DepotPeriodes periodes;
    private final DepotLiens liens;
    private final DepotSpecialites specialites;
    private final DepotOffres offres;
    private final DepotPublications publications;
    private final DepotReglages reglages;
    private final TenantParDefaut tenantParDefaut;
    private final AuditService auditService;

    // ── Lecture ──────────────────────────────────────────────────────────────────────────

    /**
     * La page publiée. Vide tant que rien n'a été publié — la page s'affiche alors sans
     * contenu plutôt que de montrer un brouillon que personne n'a validé.
     */
    @Transactional(readOnly = true)
    public PagePublique publiee() {
        return publications.findFirstByOrderByVersionDesc()
                .map(this::relire)
                .orElseGet(() -> new PagePublique(List.of(), List.of(), List.of(), List.of(),
                        List.of(), null, null, null));
    }

    /** L'état courant du brouillon, tel qu'il serait publié. Sert l'aperçu de l'administration. */
    @Transactional(readOnly = true)
    public PagePublique brouillon() {
        return composer(null, null);
    }

    // ── Publication ──────────────────────────────────────────────────────────────────────

    @Transactional
    public VersionVue publier(UUID auteur) {
        int version = publications.findFirstByOrderByVersionDesc()
                .map(p -> p.getVersion() + 1).orElse(1);
        return enregistrer(composer(version, OffsetDateTime.now()), version, auteur, null);
    }

    /**
     * Remet en ligne une version passée.
     *
     * <p><b>Elle n'efface pas l'historique, elle l'allonge.</b> L'ancien contenu est recopié
     * dans une version NEUVE, qui dit de laquelle elle vient. Écraser la ligne existante
     * rendrait indémontrable qui a remis quoi en ligne, et quand — exactement ce qu'un
     * historique sert à établir.</p>
     *
     * <p>Noter que cela ne touche pas le brouillon : l'administration retrouve ses tables
     * telles qu'elle les avait laissées, et peut republier le brouillon courant ensuite.</p>
     */
    @Transactional
    public VersionVue restaurer(int versionSource, UUID auteur) {
        EvcPublication source = publications.findByVersion(versionSource)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucune version « " + versionSource + " » n'a été publiée."));
        int version = publications.findFirstByOrderByVersionDesc()
                .map(p -> p.getVersion() + 1).orElse(1);

        EvcPublication neuve = EvcPublication.builder()
                .version(version).instantane(source.getInstantane())
                .publieLe(OffsetDateTime.now()).publiePar(auteur).restaureDe(versionSource)
                .build();
        publications.save(neuve);
        auditService.record("EVC_RESTAURATION", "EvcPublication", String.valueOf(version),
                "Version " + versionSource + " remise en ligne sous le numéro " + version, null);
        return new VersionVue(version, neuve.getPublieLe(), null, versionSource);
    }

    @Transactional(readOnly = true)
    public List<VersionVue> historique() {
        return publications.findAllByOrderByVersionDesc().stream()
                .map(p -> new VersionVue(p.getVersion(), p.getPublieLe(),
                        p.getPubliePar() == null ? null : p.getPubliePar().toString(),
                        p.getRestaureDe()))
                .toList();
    }

    // ── Composition ──────────────────────────────────────────────────────────────────────

    private VersionVue enregistrer(PagePublique page, int version, UUID auteur, Integer restaureDe) {
        String document;
        try {
            document = JSON.writeValueAsString(page);
        } catch (Exception e) {
            throw new IllegalStateException("La page n'a pas pu être figée : " + e.getMessage(), e);
        }
        EvcPublication publication = EvcPublication.builder()
                .version(version).instantane(document)
                .publieLe(OffsetDateTime.now()).publiePar(auteur).restaureDe(restaureDe)
                .build();
        publications.save(publication);
        auditService.record("EVC_PUBLICATION", "EvcPublication", String.valueOf(version),
                "Page « Exercer en France » publiée en version " + version, null);
        log.info("« Exercer en France » publiée en version {}", version);
        return new VersionVue(version, publication.getPublieLe(), null, restaureDe);
    }

    private PagePublique relire(EvcPublication publication) {
        try {
            return JSON.readValue(publication.getInstantane(), PagePublique.class);
        } catch (Exception e) {
            // Un instantané illisible est une anomalie, mais pas une raison de rendre la page
            // inaccessible : mieux vaut une page vide qu'une erreur 500 pour un visiteur.
            log.error("Instantané de la version {} illisible : {}",
                    publication.getVersion(), e.getMessage());
            return new PagePublique(List.of(), List.of(), List.of(), List.of(), List.of(),
                    null, null, null);
        }
    }

    private PagePublique composer(Integer version, OffsetDateTime publieLe) {
        UUID tenant = tenantParDefaut.identifiant();

        List<SectionVue> s = sections.findAllByOrderByOrdreAsc().stream()
                .filter(EvcContenu.Section::getVisible)
                .map(x -> new SectionVue(x.getCle(), x.getSurTitre(), x.getTitre(), x.getContenu()))
                .toList();

        // Le statut est calculé À LA COMPOSITION, mais recalculé à chaque lecture publique :
        // voir publieeAvecStatutsFrais(). Figer un statut dans l'instantané le périmerait.
        List<PeriodeVue> p = periodes.findAllByOrderByOrdreAsc().stream()
                .filter(EvcContenu.Periode::getVisible)
                .map(x -> new PeriodeVue(x.getLibelle(), x.getDateDebut(), x.getDateFin(),
                        x.getSpecialitesNonOuvertes(), x.getSourceUrl(),
                        StatutPeriode.calculer(x.getDateDebut(), x.getDateFin()).name().toLowerCase()))
                .toList();

        // Un lien dont l'adresse n'est pas renseignée n'est pas publié : un bouton qui ne mène
        // nulle part est pire que pas de bouton.
        List<LienVue> l = liens.findAllByOrderByCleAsc().stream()
                .filter(EvcContenu.Lien::estRenseigne)
                .map(x -> new LienVue(x.getCle(), x.getLibelle(), x.getUrl()))
                .toList();

        List<SpecialiteVue> sp = specialites.findAllByOrderByGroupeAscOrdreAsc().stream()
                .map(x -> new SpecialiteVue(x.getGroupe(), x.getLibelle()))
                .toList();

        List<OffreVue> o = offres.findByTenantIdAndActifTrueAndTechniqueFalseOrderByOrdreAsc(tenant).stream()
                .map(x -> new OffreVue(x.getCle(), x.getSurTitre(), x.getTitre(),
                        decouper(x.getPoints()), x.getPrix(), x.getPrixBarre(), x.getBadge(),
                        x.getDevise(), x.getLibelleBouton()))
                .toList();

        return new PagePublique(s, p, l, sp, o, publieLe, version,
                reglages.findById(com.optimisante.backend.domain.exercerenfrance.entity.EvcReglage
                        .CALENDRIER_ACTUALISE_LE)
                        .map(com.optimisante.backend.domain.exercerenfrance.entity.EvcReglage::enDate)
                        .orElse(null));
    }

    /**
     * La page publiée, dont les statuts de période sont recalculés maintenant.
     *
     * <p><b>Le seul champ que l'instantané ne peut pas figer.</b> Tout le reste ne change que
     * si quelqu'un le modifie ; un statut, lui, change tout seul à minuit. Figé le 1er août,
     * il annoncerait encore « Ouvert » le 2 octobre — et des praticiens déposeraient un
     * dossier irrecevable sans que personne ait rien fait de mal.</p>
     */
    @Transactional(readOnly = true)
    public PagePublique publieeAvecStatutsFrais() {
        PagePublique page = publiee();
        List<PeriodeVue> fraiches = page.periodes().stream()
                .map(x -> new PeriodeVue(x.libelle(), x.dateDebut(), x.dateFin(),
                        x.specialitesNonOuvertes(), x.sourceUrl(),
                        StatutPeriode.calculer(x.dateDebut(), x.dateFin()).name().toLowerCase()))
                .toList();
        return new PagePublique(page.sections(), fraiches, page.liens(), page.specialites(),
                page.offres(), page.misAJourLe(), page.version(), page.calendrierActualiseLe());
    }

    /** Les puces d'une offre, une par ligne. */
    private static List<String> decouper(String points) {
        if (points == null || points.isBlank()) {
            return List.of();
        }
        return Arrays.stream(points.split("\\r?\\n")).map(String::trim)
                .filter(t -> !t.isEmpty()).toList();
    }
}
