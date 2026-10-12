package com.optimisante.backend.domain.exercerenfrance.service;

import com.optimisante.backend.domain.exercerenfrance.repository.*;
import com.optimisante.backend.config.tenant.TenantParDefaut;
import com.optimisante.backend.domain.audit.service.AuditService;
import com.optimisante.backend.domain.exercerenfrance.dto.ExercerEnFranceDtos.*;
import com.optimisante.backend.domain.exercerenfrance.entity.*;
import com.optimisante.backend.domain.identity.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private final com.optimisante.backend.domain.parametres.ParametrePlateformeRepository parametres;
    private final com.optimisante.backend.common.email.EmailService emailService;
    private final com.optimisante.backend.domain.identity.repository.UserRepository userRepository;

    /** Adresse publique du site, pour composer le lien vers la fiche d'une demande. */
    @org.springframework.beans.factory.annotation.Value("${app.mail.frontend-base-url}")
    private String baseUrl;

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

        prevenir(demande);
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

    /**
     * Accuse réception au praticien, et alerte l'administration.
     *
     * <p><b>Rien de tout cela ne peut faire échouer le dépôt.</b> La demande est déjà
     * enregistrée ; perdre un courriel est ennuyeux, perdre la demande d'un praticien qui a
     * rempli le formulaire ne se rattrape pas. {@code EmailService} journalise ses échecs
     * sans les propager, et ce qui l'entoure est tout de même protégé.</p>
     *
     * <p>L'alerte part vers les comptes ACTIFS qui peuvent réellement traiter la demande.
     * Prévenir un compte désactivé reviendrait à ne prévenir personne, en donnant
     * l'impression du contraire.</p>
     */
    private void prevenir(AccompanimentRequest demande) {
        String offre = demande.getOffreCle() == null ? "un accompagnement" : demande.getOffreCle();
        try {
            emailService.sendAccompagnementRecu(demande.getEmail(), demande.getNom(), offre,
                    reglage("accompagnement.delai_reponse", "sous 48 h ouvrées"),
                    reglage("accompagnement.adresse_reponse", "infos@optimisante.com"));
        } catch (Exception e) {
            log.warn("Accusé de réception non envoyé pour la demande {} : {}",
                    demande.getId(), e.getMessage());
        }
        try {
            List<String> destinataires = userRepository.emailsActifsParRoles(
                    List.of(Role.SUPER_ADMIN, Role.ADMIN_MOBILITE));
            String lien = baseUrl + "/admin/demandes-accompagnement/" + demande.getId();
            for (String adresse : destinataires) {
                emailService.sendAccompagnementAlerteAdmin(adresse, demande.getNom(),
                        demande.getProfession(), demande.getPays(), offre, lien);
            }
            if (destinataires.isEmpty()) {
                // Signalé plutôt que tu : une demande arrive, et personne n'en est averti.
                log.warn("Demande {} déposée, mais aucun administrateur actif à prévenir.",
                        demande.getId());
            }
        } catch (Exception e) {
            log.warn("Alerte d'administration non envoyée pour la demande {} : {}",
                    demande.getId(), e.getMessage());
        }
    }

    // ── Suivi par l'administration ───────────────────────────────────────────────────────

    /** Les critères que l'écran propose. Tous facultatifs. */
    public record Filtres(String statut, String offreCle, String profession,
                          OffsetDateTime depuis, OffsetDateTime jusqua, String recherche) {

        /** Un filtre vide vaut « pas de filtre » : une chaîne blanche ne doit rien exclure. */
        static String vide(String v) {
            return v == null || v.isBlank() ? null : v.trim();
        }

        Filtres nettoyes() {
            return new Filtres(vide(statut), vide(offreCle), vide(profession),
                    depuis, jusqua, vide(recherche));
        }

        /** Ce que le journal d'audit retient : les critères, jamais les résultats. */
        String resume() {
            StringBuilder b = new StringBuilder();
            if (statut != null) b.append("statut=").append(statut).append(' ');
            if (offreCle != null) b.append("offre=").append(offreCle).append(' ');
            if (profession != null) b.append("profession=").append(profession).append(' ');
            if (depuis != null) b.append("depuis=").append(depuis.toLocalDate()).append(' ');
            if (jusqua != null) b.append("jusqua=").append(jusqua.toLocalDate()).append(' ');
            // La RECHERCHE est volontairement omise : on y tape un nom ou une adresse, et
            // l'audit ne doit porter aucune donnée personnelle.
            if (recherche != null) b.append("recherche=(texte) ");
            return b.isEmpty() ? "aucun filtre" : b.toString().trim();
        }
    }

    /**
     * Les demandes, filtrées comme l'écran le propose.
     *
     * <p><b>Construite critère par critère, et non en une requête à trous.</b> La forme
     * habituelle — {@code (:statut IS NULL OR d.statut = :statut)} — paraît économique :
     * une requête pour seize combinaisons. Mais PostgreSQL ne sait pas typer un paramètre
     * qui n'apparaît que dans un {@code IS NULL}, et la requête échoue dès qu'un filtre est
     * vide — c'est-à-dire dans le cas le plus courant, celui où l'on ouvre simplement la
     * liste. Ici, un critère absent n'ajoute rien à la requête, donc rien à typer.</p>
     */
    @Transactional(readOnly = true)
    public Page<AccompanimentRequest> lister(Filtres filtres, Pageable pageable) {
        Filtres f = filtres.nettoyes();
        UUID tenant = tenantParDefaut.identifiant();

        Specification<AccompanimentRequest> criteres = (racine, requete, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicats = new ArrayList<>();
            predicats.add(cb.equal(racine.get("tenantId"), tenant));
            if (f.statut() != null) predicats.add(cb.equal(racine.get("statut"), f.statut()));
            if (f.offreCle() != null) predicats.add(cb.equal(racine.get("offreCle"), f.offreCle()));
            if (f.profession() != null) predicats.add(cb.equal(racine.get("profession"), f.profession()));
            if (f.depuis() != null) {
                predicats.add(cb.greaterThanOrEqualTo(racine.get("creeLe"), f.depuis()));
            }
            if (f.jusqua() != null) {
                predicats.add(cb.lessThan(racine.get("creeLe"), f.jusqua()));
            }
            if (f.recherche() != null) {
                // Le nom OU l'adresse : c'est l'un des deux qu'on a sous les yeux quand on
                // cherche quelqu'un. La casse est ignorée.
                String motif = "%" + f.recherche().toLowerCase() + "%";
                predicats.add(cb.or(
                        cb.like(cb.lower(racine.get("nom")), motif),
                        cb.like(cb.lower(racine.get("email")), motif)));
            }
            return cb.and(predicats.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return demandes.findAll(criteres, pageable);
    }

    /**
     * Inscrit un export au journal d'audit.
     *
     * <p><b>Pourquoi tracer un export.</b> Il fait SORTIR de la plateforme des données
     * personnelles — nom, adresse, téléphone, situation administrative — vers un fichier que
     * plus rien ne protège ni n'efface. Si l'un d'eux se retrouve ailleurs, la seule question
     * qui compte est : qui l'a sorti, et quand. Sans trace, elle reste sans réponse.</p>
     *
     * <p>Les critères sont consignés, <b>jamais les données</b> : le journal d'audit ne
     * s'anonymise pas, et y verser ce que l'export contient reviendrait à les conserver
     * indéfiniment — précisément ce que la durée de conservation interdit.</p>
     */
    @Transactional
    public void tracerExport(String genre, Filtres filtres, int lignes) {
        auditService.record("ACCOMPAGNEMENT_EXPORT", "AccompanimentRequest", genre,
                lignes + " ligne(s) exportée(s) — " + filtres.nettoyes().resume(), null);
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
        if (dto.prestationFinLe() != null) {
            d.setPrestationFinLe(dto.prestationFinLe());
        } else if (nouveau == Enums.Statut.TERMINE && d.getPrestationFinLe() == null) {
            // Renseignée d'office à la clôture, et c'est ce qui FAIT COURIR le délai de
            // conservation : sans elle, la demande n'est jamais anonymisée. « Conserver par
            // précaution » est aussi un manquement que d'effacer trop tôt, et compter sur un
            // administrateur pour y penser garantissait l'oubli.
            //
            // La valeur reste modifiable : une prestation peut s'être achevée avant qu'on ne
            // pense à fermer le dossier.
            d.setPrestationFinLe(OffsetDateTime.now());
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
                                   boolean calendrierAVerifier,
                                   OffsetDateTime dernierePublication, Integer version) {
        UUID tenant = tenantParDefaut.identifiant();
        OffsetDateTime debutDuMois = OffsetDateTime.now().withDayOfMonth(1).toLocalDate()
                .atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        return new Indicateurs(
                demandes.countByTenantIdAndStatut(tenant, Enums.Statut.NOUVEAU.code()),
                demandes.countByTenantIdAndCreeLeAfter(tenant, debutDuMois),
                demandes.countByTenantIdAndStatut(tenant, Enums.Statut.PAYE.code()),
                dernierePublication, version, liensNonVerifies, periodesSansDates,
                calendrierAVerifier,
                demandes.compterPrestationsSansFin(tenant,
                        OffsetDateTime.now().minusMonths(moisAlertePrestationSansFin())));
    }

    /**
     * Un réglage d'exploitation, ou sa valeur de repli.
     *
     * <p>Le délai de réponse et l'adresse de contact sont des engagements commerciaux, pas
     * des constantes techniques : ils se révisent pendant les congés ou quand l'équipe
     * grandit, et le changer ne doit pas demander un déploiement à quelqu'un qui n'a pas les
     * accès.</p>
     *
     * <p>Un paramètre absent n'empêche pas l'envoi : il serait absurde de perdre un accusé
     * de réception parce qu'une phrase n'est pas configurée.</p>
     */
    private String reglage(String cle, String defaut) {
        try {
            return parametres.findByTenantIdAndCle(tenantParDefaut.identifiant(), cle)
                    .map(p -> p.getValeur())
                    .filter(v -> !v.isBlank())
                    .orElse(defaut);
        } catch (Exception e) {
            return defaut;
        }
    }

    /** Seuil du rappel « prestation sans date de fin », paramétrable comme les durées RGPD. */
    private int moisAlertePrestationSansFin() {
        try {
            return parametres.findByTenantIdAndCle(tenantParDefaut.identifiant(),
                            "rgpd.accompagnement.mois_alerte_prestation_sans_fin")
                    .map(p -> Integer.parseInt(p.getValeur().trim())).orElse(12);
        } catch (Exception e) {
            return 12;
        }
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
