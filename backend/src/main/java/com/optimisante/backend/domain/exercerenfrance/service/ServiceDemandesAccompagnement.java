package com.optimisante.backend.domain.exercerenfrance.service;

import com.optimisante.backend.domain.exercerenfrance.repository.*;
import com.optimisante.backend.config.tenant.TenantParDefaut;
import com.optimisante.backend.domain.audit.service.AuditService;
import com.optimisante.backend.domain.exercerenfrance.dto.ExercerEnFranceDtos.*;
import com.optimisante.backend.domain.exercerenfrance.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Les demandes d'accompagnement : dépôt public, suivi par l'administration.
 *
 * <p><b>Ce qui est écrit où.</b> Le fil ({@code accompaniment_request_events}) porte le
 * contenu — une note parle du candidat, et doit s'anonymiser avec lui. Le journal d'audit ne
 * porte que l'acte : qui, quoi, quand, sur quelle demande. Jamais de nom, d'adresse, de
 * téléphone ni de texte de note. L'audit est une preuve, il ne se modifie pas ; y inscrire
 * des données personnelles les y figerait au-delà de leur durée de conservation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceDemandesAccompagnement {

    /** Au-delà, une même adresse n'est plus un praticien pressé mais un automate. */
    static final int DEMANDES_MAX_PAR_JOUR = 3;

    /** Un formulaire rempli plus vite que cela n'a pas été lu. */
    static final long DELAI_MINIMUM_MS = 3_000L;

    private final DepotDemandes demandes;
    private final DepotEvenements evenements;
    private final TenantParDefaut tenantParDefaut;
    private final AuditService auditService;

    /** Levée quand un dépôt est écarté : le visiteur voit un message neutre, pas la raison. */
    public static class DepotRefuse extends RuntimeException {
        public DepotRefuse(String message) {
            super(message);
        }
    }

    // ── Dépôt public ─────────────────────────────────────────────────────────────────────

    /**
     * Enregistre une demande déposée par un visiteur non connecté.
     *
     * <p><b>Le tenant vient du serveur.</b> Rien de ce que l'appelant envoie n'entre dans ce
     * choix — ni en-tête, ni champ du corps. Le DTO ne porte d'ailleurs aucun champ de
     * tenant, pour qu'aucune distraction future ne puisse en lire un.</p>
     */
    @Transactional
    public AccompanimentRequest deposer(DemandeRequest dto) {
        verifierAbsenceDAbus(dto);

        OffsetDateTime maintenant = OffsetDateTime.now();
        AccompanimentRequest demande = demandes.save(AccompanimentRequest.builder()
                .tenantId(tenantParDefaut.identifiant())
                .nom(dto.nom().trim())
                .email(dto.email().trim().toLowerCase())
                .telephoneWhatsapp(dto.telephoneWhatsapp())
                .pays(dto.pays())
                .profession(dto.profession())
                .specialite(dto.specialite())
                .etape(Enums.Etape.depuis(dto.etape()).code())
                .offreCle(dto.offreCle())
                .message(dto.message())
                .consentementRgpd(true)
                .consentementLe(maintenant)
                .statut(Enums.Statut.NOUVEAU.code())
                .creeLe(maintenant)
                .derniereActiviteLe(maintenant)
                .build());

        inscrireAuFil(demande, Enums.TypeEvenement.CREATION, null, Enums.Statut.NOUVEAU.code(),
                null, null);

        // L'audit dit QU'UNE demande est arrivée, et rien de qui l'a déposée.
        auditService.record("ACCOMPAGNEMENT_DEPOT", "AccompanimentRequest",
                demande.getId().toString(), "Demande déposée depuis la page publique", null);
        return demande;
    }

    /**
     * Écarte ce qui ne vient pas d'un praticien.
     *
     * <p>Trois filtres, aucun n'étant une forteresse : le champ piège arrête les robots qui
     * remplissent tout, le délai minimum ceux qui envoient instantanément, et le plafond
     * quotidien ce qui passe quand même. Le délai est transmis par le navigateur, donc
     * falsifiable en une ligne — il n'est compté ni comme une sécurité, ni comme suffisant.</p>
     *
     * <p>Le plafond se lit <b>en base</b> et non dans un compteur en mémoire : l'instance est
     * petite et redémarre, un compteur mémoire repartirait à zéro et grossirait d'une entrée
     * par adresse croisée.</p>
     */
    private void verifierAbsenceDAbus(DemandeRequest dto) {
        if (dto.piege() != null && !dto.piege().isBlank()) {
            log.debug("Dépôt écarté : champ piège rempli.");
            throw new DepotRefuse("Votre demande n'a pas pu être enregistrée.");
        }
        if (dto.ouvertDepuisMs() != null && dto.ouvertDepuisMs() < DELAI_MINIMUM_MS) {
            log.debug("Dépôt écarté : formulaire envoyé en {} ms.", dto.ouvertDepuisMs());
            throw new DepotRefuse("Votre demande n'a pas pu être enregistrée.");
        }
        long dejaDeposees = demandes.countByEmailIgnoreCaseAndCreeLeAfter(
                dto.email().trim().toLowerCase(), OffsetDateTime.now().minusDays(1));
        if (dejaDeposees >= DEMANDES_MAX_PAR_JOUR) {
            throw new DepotRefuse("Vous avez déjà déposé plusieurs demandes aujourd'hui. "
                    + "Nous vous répondons au plus vite ; inutile de renvoyer le formulaire.");
        }
    }

    // ── Suivi par l'administration ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AccompanimentRequest> lister(Pageable pageable) {
        return demandes.findByTenantId(tenantParDefaut.identifiant(), pageable);
    }

    @Transactional(readOnly = true)
    public DemandeVue detail(UUID id) {
        AccompanimentRequest d = demandes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
        List<EvenementVue> fil = evenements.findByRequestIdOrderByCreeLeAsc(id).stream()
                .map(e -> new EvenementVue(e.getType(), e.getAncienStatut(), e.getNouveauStatut(),
                        e.getContenu(), e.getAuteurId(), e.getCreeLe()))
                .toList();
        return new DemandeVue(d.getId(), d.getNom(), d.getEmail(), d.getTelephoneWhatsapp(),
                d.getPays(), d.getProfession(), d.getSpecialite(), d.getEtape(), d.getOffreCle(),
                d.getMessage(), d.getStatut(), d.getAssigneA(), d.getNotesInternes(),
                d.getCreeLe(), d.getDerniereActiviteLe(), d.getPrestationFinLe(),
                d.estAnonymisee(), fil);
    }

    @Transactional
    public void changerStatut(UUID id, ChangementStatutRequest dto, UUID auteur) {
        AccompanimentRequest d = charger(id);
        String ancien = d.getStatut();
        Enums.Statut nouveau = Enums.Statut.depuis(dto.statut());

        d.setStatut(nouveau.code());
        // Le passage à « payé » ouvre le régime de conservation long : la demande devient une
        // pièce contractuelle. Sans date de fin de prestation, elle ne sera jamais anonymisée
        // — c'est voulu : mieux vaut conserver que d'effacer une pièce encore utile.
        if (dto.prestationFinLe() != null) {
            d.setPrestationFinLe(dto.prestationFinLe());
        }
        toucher(d);

        inscrireAuFil(d, Enums.TypeEvenement.STATUT, ancien, nouveau.code(), dto.note(), auteur);
        auditService.record("ACCOMPAGNEMENT_STATUT", "AccompanimentRequest", id.toString(),
                "Statut : " + ancien + " → " + nouveau.code(), null);
    }

    @Transactional
    public void assigner(UUID id, AssignationRequest dto, UUID auteur) {
        AccompanimentRequest d = charger(id);
        d.setAssigneA(dto.assigneA());
        toucher(d);
        inscrireAuFil(d, Enums.TypeEvenement.ASSIGNATION, null, null, dto.note(), auteur);
        auditService.record("ACCOMPAGNEMENT_ASSIGNATION", "AccompanimentRequest", id.toString(),
                dto.assigneA() == null ? "Assignation retirée" : "Demande assignée", null);
    }

    @Transactional
    public void ajouterNote(UUID id, NoteRequest dto, UUID auteur) {
        AccompanimentRequest d = charger(id);
        toucher(d);
        inscrireAuFil(d, Enums.TypeEvenement.NOTE, null, null, dto.contenu(), auteur);
        // Le texte de la note reste dans le fil : l'audit n'en consigne que l'existence.
        auditService.record("ACCOMPAGNEMENT_NOTE", "AccompanimentRequest", id.toString(),
                "Note interne ajoutée", null);
    }

    @Transactional(readOnly = true)
    public Indicateurs indicateurs(long liensNonVerifies, long periodesSansDates,
                                   OffsetDateTime dernierePublication, Integer version) {
        UUID tenant = tenantParDefaut.identifiant();
        return new Indicateurs(
                demandes.countByTenantIdAndStatut(tenant, Enums.Statut.NOUVEAU.code()),
                demandes.countByTenantIdAndCreeLeAfter(tenant,
                        OffsetDateTime.now().withDayOfMonth(1).toLocalDate()
                                .atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime()),
                demandes.countByTenantIdAndStatut(tenant, Enums.Statut.PAYE.code()),
                dernierePublication, version, liensNonVerifies, periodesSansDates);
    }

    // ── Rouages ──────────────────────────────────────────────────────────────────────────

    private AccompanimentRequest charger(UUID id) {
        return demandes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
    }

    /**
     * Repousse la date de dernière activité.
     *
     * <p>C'est elle, et non la date de création, qui déclenche l'anonymisation : un dossier
     * ouvert il y a deux ans et suivi la semaine dernière ne doit pas disparaître en plein
     * traitement.</p>
     */
    private void toucher(AccompanimentRequest d) {
        d.setDerniereActiviteLe(OffsetDateTime.now());
        demandes.save(d);
    }

    /** Inscrit au fil dans la MÊME transaction que la modification qu'il relate. */
    private void inscrireAuFil(AccompanimentRequest d, Enums.TypeEvenement type, String ancien,
                               String nouveau, String contenu, UUID auteur) {
        evenements.save(AccompanimentRequestEvent.builder()
                .requestId(d.getId()).tenantId(d.getTenantId()).type(type.code())
                .ancienStatut(ancien).nouveauStatut(nouveau).contenu(contenu).auteurId(auteur)
                .build());
    }
}
