package com.optimisante.backend.domain.exercerenfrance.service;

import com.optimisante.backend.domain.exercerenfrance.repository.*;
import com.optimisante.backend.domain.audit.service.AuditService;
import com.optimisante.backend.domain.exercerenfrance.entity.AccompanimentRequest;
import com.optimisante.backend.config.tenant.TenantParDefaut;
import com.optimisante.backend.domain.parametres.ParametrePlateformeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Efface les données personnelles des demandes dont la conservation est échue.
 *
 * <p><b>Deux régimes, parce que deux natures.</b> Une demande non convertie est de la
 * prospection : elle s'efface deux ans après la dernière activité. Une demande passée au
 * statut « payé » est devenue une pièce contractuelle, soumise à la prescription commerciale,
 * et se conserve cinq ans après la fin de la prestation. Les appliquer la même durée
 * reviendrait soit à garder trop longtemps des prospects, soit à détruire une pièce qu'un
 * litige pourrait réclamer.</p>
 *
 * <p><b>Anonymiser, et non supprimer.</b> La ligne reste, vidée de ce qui désigne une
 * personne. La supprimer ferait disparaître des statistiques déjà publiées et romprait les
 * références du journal d'audit, qui désigne les demandes par leur identifiant.</p>
 *
 * <p><b>Le journal d'audit n'est jamais touché.</b> Il est une preuve : s'il se réécrivait,
 * il ne prouverait rien. C'est précisément pourquoi il ne contient aucune donnée personnelle
 * — ni nom, ni adresse, ni téléphone, ni texte de note. Ce qui doit disparaître est dans la
 * demande et dans son fil ; ce qui doit rester est dans l'audit.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceAnonymisation {

    /** Clés des deux durées, tenues en base pour être changeables après avis juridique. */
    static final String CLE_PROSPECTS = "rgpd.accompagnement.mois_avant_anonymisation";
    static final String CLE_CONTRATS = "rgpd.accompagnement.mois_conservation_apres_paiement";

    /**
     * Cent au plus par passage.
     *
     * <p>L'instance est petite — 516 Mo au repos — et charger d'un coup toutes les demandes
     * échues ferait de cette tâche de ménage la cause d'un redémarrage. Ce qui dépasse le lot
     * attend le lendemain : la conservation se compte en mois, un jour de décalage n'est pas
     * un manquement.</p>
     */
    static final int TAILLE_LOT = 100;

    private final DepotDemandes demandes;
    private final DepotEvenements evenements;
    private final ParametrePlateformeRepository parametres;
    private final TenantParDefaut tenantParDefaut;
    private final AuditService audit;

    /** Une fois par jour, à 3 h 15 : l'heure creuse, et jamais pile pour ne pas croiser d'autres tâches. */
    @Scheduled(cron = "0 15 3 * * *")
    public void passageQuotidien() {
        int traitees = executer();
        // Journalisé MÊME À ZÉRO, et c'est le point : une tâche silencieuse ne se distingue
        // pas d'une tâche qui ne s'exécute plus. Sans cette ligne, le jour où le
        // planificateur s'arrête, personne ne l'apprend — jusqu'à un contrôle.
        log.info("Conservation des demandes d'accompagnement : {} anonymisée(s){}.",
                traitees, traitees == TAILLE_LOT ? " — lot plein, suite au prochain passage" : "");
    }

    @Transactional
    public int executer() {
        OffsetDateTime limiteProspects = OffsetDateTime.now().minusMonths(mois(CLE_PROSPECTS, 24));
        OffsetDateTime limiteContrats = OffsetDateTime.now().minusMonths(mois(CLE_CONTRATS, 60));

        List<AccompanimentRequest> lot = demandes.aAnonymiser(limiteProspects, limiteContrats,
                PageRequest.of(0, TAILLE_LOT));
        for (AccompanimentRequest d : lot) {
            anonymiser(d);
        }
        return lot.size();
    }

    private void anonymiser(AccompanimentRequest d) {
        d.setNom("[anonymisé]");
        // L'adresse est remplacée par une valeur unique, et non vidée : la colonne est
        // obligatoire, et deux demandes anonymisées ne doivent pas devenir indiscernables.
        d.setEmail("anonyme+" + d.getId() + "@invalide.local");
        d.setTelephoneWhatsapp(null);
        d.setMessage(null);
        d.setNotesInternes(null);
        // Pays, profession, spécialité et statut restent : ils ne désignent personne, et
        // ce sont eux qui font les statistiques d'activité.
        d.setAnonymiseLe(OffsetDateTime.now());
        demandes.save(d);

        // Le fil garde ses lignes et leurs auteurs — un administrateur n'est pas le candidat —
        // mais perd son contenu, qui parle du candidat.
        evenements.effacerContenu(d.getId());

        // L'audit enregistre l'acte, par identifiant. Aucune donnée personnelle n'y entre,
        // et aucune ligne existante n'en sort.
        audit.record("ACCOMPAGNEMENT_ANONYMISATION", "AccompanimentRequest",
                d.getId().toString(), "Conservation échue : données personnelles effacées", null);
    }

    /**
     * Durée de conservation, en mois.
     *
     * <p><b>Lue par le dépôt, et non par {@code ServiceParametres}.</b> Celui-ci résout le
     * tenant depuis le contexte de la requête — or cette tâche s'exécute sur un fil du
     * planificateur, où ce contexte n'existe pas. Elle aurait échoué chaque nuit à 3 h 15,
     * sans requête à qui rendre l'erreur et sans personne pour la voir.</p>
     */
    private int mois(String cle, int defaut) {
        try {
            String valeur = parametres.findByTenantIdAndCle(tenantParDefaut.identifiant(), cle)
                    .map(p -> p.getValeur()).orElse(null);
            return valeur == null || valeur.isBlank() ? defaut : Integer.parseInt(valeur.trim());
        } catch (Exception e) {
            // Un paramètre illisible ne doit pas faire effacer trop tôt : on retombe sur la
            // durée la plus longue connue, c'est-à-dire celle qui conserve.
            log.warn("Paramètre {} illisible, durée par défaut de {} mois retenue.", cle, defaut);
            return defaut;
        }
    }
}
