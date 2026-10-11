package com.optimisante.backend.domain.exercerenfrance.service;

import com.optimisante.backend.domain.exercerenfrance.repository.*;
import com.optimisante.backend.config.tenant.TenantParDefaut;
import com.optimisante.backend.domain.audit.service.AuditService;
import com.optimisante.backend.domain.exercerenfrance.dto.ExercerEnFranceDtos.*;
import com.optimisante.backend.domain.exercerenfrance.entity.EvcContenu;
import com.optimisante.backend.domain.exercerenfrance.entity.EvcReglage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * L'écriture du brouillon : sections, périodes, liens, spécialités, offres.
 *
 * <p><b>Rien de ce qui est écrit ici n'atteint un visiteur.</b> Ces tables sont le brouillon ;
 * le public ne lit que l'instantané de la dernière publication. C'est ce qui permet de
 * corriger un texte à midi et de ne le mettre en ligne qu'une fois relu.</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceContenuEvc {

    /** Au-delà, un lien n'a plus été vu fonctionner depuis assez longtemps pour s'en méfier. */
    static final int JOURS_AVANT_REVERIFICATION = 90;

    private final DepotSections sections;
    private final DepotPeriodes periodes;
    private final DepotLiens liens;
    private final DepotSpecialites specialites;
    private final DepotOffres offres;
    private final DepotReglages reglages;
    private final TenantParDefaut tenantParDefaut;
    private final AuditService audit;

    // ── Sections ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EvcContenu.Section> sections() {
        return sections.findAllByOrderByOrdreAsc();
    }

    @Transactional
    public EvcContenu.Section enregistrerSection(UUID id, SectionRequest dto) {
        EvcContenu.Section s = id == null ? new EvcContenu.Section() : charger(id);
        s.setCle(dto.cle());
        s.setSurTitre(dto.surTitre());
        s.setTitre(dto.titre());
        s.setContenu(dto.contenu());
        if (dto.ordre() != null) s.setOrdre(dto.ordre());
        if (dto.visible() != null) s.setVisible(dto.visible());
        s.setModifieLe(OffsetDateTime.now());
        sections.save(s);
        audit.record("EVC_SECTION", "EvcSection", s.getCle(),
                id == null ? "Section créée" : "Section modifiée", null);
        return s;
    }

    /**
     * Déplace une section d'un rang.
     *
     * <p>Deux flèches plutôt qu'un glisser-déposer : la bibliothèque nécessaire pèse une
     * trentaine de kilo-octets, et le garde-fou de poids du projet refuse le build au-delà
     * d'un plafond déjà approché. Pour sept sections réordonnées deux fois par an, le gain
     * d'ergonomie ne vaut pas ce prix.</p>
     */
    @Transactional
    public void deplacerSection(UUID id, boolean versLeHaut) {
        List<EvcContenu.Section> toutes = sections.findAllByOrderByOrdreAsc();
        int index = -1;
        for (int i = 0; i < toutes.size(); i++) {
            if (toutes.get(i).getId().equals(id)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Section introuvable.");
        }
        int cible = versLeHaut ? index - 1 : index + 1;
        if (cible < 0 || cible >= toutes.size()) {
            return; // Déjà en bout de liste : ne rien faire vaut mieux qu'une erreur.
        }
        // Les rangs sont RÉÉCRITS de 0 à n, et non échangés : des rangs en double ou troués
        // — que rien n'interdit en base — rendraient l'ordre dépendant du hasard des lignes.
        EvcContenu.Section deplacee = toutes.remove(index);
        toutes.add(cible, deplacee);
        for (int i = 0; i < toutes.size(); i++) {
            toutes.get(i).setOrdre(i);
        }
        sections.saveAll(toutes);
        audit.record("EVC_SECTION_ORDRE", "EvcSection", id.toString(),
                "Section déplacée vers " + (versLeHaut ? "le haut" : "le bas"), null);
    }

    @Transactional
    public void supprimerSection(UUID id) {
        sections.deleteById(id);
        audit.record("EVC_SECTION", "EvcSection", id.toString(), "Section supprimée", null);
    }

    // ── Périodes ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EvcContenu.Periode> periodes() {
        return periodes.findAllByOrderByOrdreAsc();
    }

    @Transactional
    public EvcContenu.Periode enregistrerPeriode(UUID id, PeriodeRequest dto) {
        if (dto.dateDebut() != null && dto.dateFin() != null && dto.dateFin().isBefore(dto.dateDebut())) {
            throw new IllegalArgumentException("La date de fin précède la date de début.");
        }
        EvcContenu.Periode p = id == null ? new EvcContenu.Periode()
                : periodes.findById(id).orElseThrow(() -> new IllegalArgumentException("Période introuvable."));
        p.setLibelle(dto.libelle());
        p.setDateDebut(dto.dateDebut());
        p.setDateFin(dto.dateFin());
        p.setSpecialitesNonOuvertes(dto.specialitesNonOuvertes());
        p.setSourceUrl(dto.sourceUrl());
        if (dto.visible() != null) p.setVisible(dto.visible());
        if (dto.ordre() != null) p.setOrdre(dto.ordre());
        periodes.save(p);
        audit.record("EVC_PERIODE", "EvcPeriode", p.getLibelle(),
                id == null ? "Période créée" : "Période modifiée", null);
        return p;
    }

    @Transactional
    public void supprimerPeriode(UUID id) {
        periodes.deleteById(id);
        audit.record("EVC_PERIODE", "EvcPeriode", id.toString(), "Période supprimée", null);
    }

    // ── Liens ────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EvcContenu.Lien> liens() {
        return liens.findAllByOrderByCleAsc();
    }

    @Transactional
    public EvcContenu.Lien enregistrerLien(UUID id, LienRequest dto) {
        EvcContenu.Lien l = id == null ? new EvcContenu.Lien()
                : liens.findById(id).orElseThrow(() -> new IllegalArgumentException("Lien introuvable."));
        l.setCle(dto.cle());
        l.setLibelle(dto.libelle());
        if (dto.url() != null) {
            // Changer l'adresse périme la vérification : ce qui a été vu fonctionner, c'est
            // l'ancienne. La garder marquerait comme contrôlé un lien que personne n'a ouvert.
            if (!dto.url().equals(l.getUrl())) {
                l.setVerifieLe(null);
            }
            l.setUrl(dto.url());
        }
        liens.save(l);
        audit.record("EVC_LIEN", "EvcLien", l.getCle(),
                id == null ? "Lien créé" : "Lien modifié", null);
        return l;
    }

    @Transactional
    public void marquerLienVerifie(UUID id) {
        EvcContenu.Lien l = liens.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lien introuvable."));
        l.setVerifieLe(OffsetDateTime.now());
        liens.save(l);
        audit.record("EVC_LIEN_VERIFIE", "EvcLien", l.getCle(), "Lien marqué comme vérifié", null);
    }

    @Transactional(readOnly = true)
    public long liensNonVerifies() {
        return liens.compterNonVerifiesDepuis(
                OffsetDateTime.now().minusDays(JOURS_AVANT_REVERIFICATION));
    }

    @Transactional(readOnly = true)
    public long periodesSansDates() {
        return periodes.countByDateDebutIsNullAndDateFinIsNull();
    }

    // ── Spécialités ──────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EvcContenu.Specialite> specialites() {
        return specialites.findAllByOrderByGroupeAscOrdreAsc();
    }

    @Transactional
    public EvcContenu.Specialite enregistrerSpecialite(UUID id, SpecialiteRequest dto) {
        EvcContenu.Specialite s = id == null ? new EvcContenu.Specialite()
                : specialites.findById(id).orElseThrow(() -> new IllegalArgumentException("Spécialité introuvable."));
        s.setGroupe(dto.groupe());
        s.setLibelle(dto.libelle());
        if (dto.ordre() != null) s.setOrdre(dto.ordre());
        specialites.save(s);
        audit.record("EVC_SPECIALITE", "EvcSpecialite", s.getLibelle(),
                id == null ? "Spécialité créée" : "Spécialité modifiée", null);
        return s;
    }

    @Transactional
    public void supprimerSpecialite(UUID id) {
        specialites.deleteById(id);
        audit.record("EVC_SPECIALITE", "EvcSpecialite", id.toString(), "Spécialité supprimée", null);
    }

    // ── Offres ───────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EvcContenu.Offre> offres() {
        return offres.findByTenantIdOrderByOrdreAsc(tenantParDefaut.identifiant());
    }

    @Transactional
    public EvcContenu.Offre enregistrerOffre(UUID id, OffreRequest dto) {
        EvcContenu.Offre o = id == null ? new EvcContenu.Offre()
                : offres.findById(id).orElseThrow(() -> new IllegalArgumentException("Offre introuvable."));
        o.setTenantId(tenantParDefaut.identifiant());
        o.setCle(dto.cle());
        o.setSurTitre(dto.surTitre());
        o.setTitre(dto.titre());
        o.setPoints(dto.points());
        // Un prix absent reste absent : la page affiche « Sur devis ». Le forcer à zéro
        // annoncerait « gratuit », ce qui n'est pas la même chose.
        o.setPrix(dto.prix());
        if (dto.devise() != null) o.setDevise(dto.devise());
        o.setLibelleBouton(dto.libelleBouton());
        if (dto.actif() != null) o.setActif(dto.actif());
        if (dto.ordre() != null) o.setOrdre(dto.ordre());
        offres.save(o);
        audit.record("EVC_OFFRE", "EvcOffre", o.getCle(),
                (id == null ? "Offre créée" : "Offre modifiée")
                        + (dto.prix() == null ? " (sur devis)" : " — prix " + dto.prix()), null);
        return o;
    }

    // ── Réglages éditoriaux ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public java.time.LocalDate calendrierActualiseLe() {
        return reglages.findById(EvcReglage.CALENDRIER_ACTUALISE_LE)
                .map(EvcReglage::enDate).orElse(null);
    }

    @Transactional
    public void definirCalendrierActualiseLe(java.time.LocalDate date) {
        EvcReglage r = reglages.findById(EvcReglage.CALENDRIER_ACTUALISE_LE)
                .orElseGet(() -> EvcReglage.builder().cle(EvcReglage.CALENDRIER_ACTUALISE_LE).build());
        r.setValeur(date == null ? null : date.toString());
        r.setModifieLe(OffsetDateTime.now());
        reglages.save(r);
        audit.record("EVC_REGLAGE", "EvcReglage", EvcReglage.CALENDRIER_ACTUALISE_LE,
                "Calendrier des commissions actualisé au " + date, null);
    }

    /**
     * Le calendrier des commissions a-t-il vieilli ?
     *
     * <p>Une date absente compte comme vieille : ne rien savoir de la dernière publication
     * n'est pas plus rassurant que de la savoir ancienne.</p>
     */
    @Transactional(readOnly = true)
    public boolean calendrierAVerifier() {
        java.time.LocalDate date = calendrierActualiseLe();
        return date == null
                || date.isBefore(java.time.LocalDate.now().minusDays(JOURS_AVANT_REVERIFICATION));
    }

    private EvcContenu.Section charger(UUID id) {
        return sections.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Section introuvable."));
    }
}
