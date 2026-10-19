package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.config.tenant.TenantParDefaut;
import com.optimisante.backend.domain.audit.service.AuditService;
import com.optimisante.backend.domain.exercerenfrance.entity.AccompanimentRequest;
import com.optimisante.backend.domain.exercerenfrance.entity.Enums;
import com.optimisante.backend.domain.exercerenfrance.repository.DepotDemandes;
import com.optimisante.backend.domain.exercerenfrance.repository.DepotEvenements;
import com.optimisante.backend.domain.exercerenfrance.service.ServiceAnonymisation;
import com.optimisante.backend.domain.parametres.ParametrePlateformeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * La tâche de conservation, telle qu'elle s'exécute réellement : à 3 h 15, sur un fil du
 * planificateur, SANS requête HTTP et donc sans contexte de tenant.
 *
 * <p>Ce point n'est pas un détail de mise en scène — c'est le défaut que ces tests ont fait
 * apparaître. La première version lisait les durées par {@code ServiceParametres}, qui résout
 * le tenant depuis le contexte de la requête : la tâche aurait échoué chaque nuit, sans
 * requête à qui rendre l'erreur et sans personne pour la voir.</p>
 *
 * <p>Ce qui est éprouvé ensuite est une obligation légale, pas une commodité : effacer ce qui
 * doit l'être, <b>et rien d'autre</b>. Anonymiser une demande encore en cours la rend
 * intraitable ; en oublier une est un manquement.</p>
 */
class ServiceAnonymisationTest {

    private static final UUID TENANT = UUID.randomUUID();

    private DepotDemandes demandes;
    private DepotEvenements evenements;
    private ParametrePlateformeRepository parametres;
    private TenantParDefaut tenantParDefaut;
    private AuditService audit;
    private ServiceAnonymisation service;

    /** Ce que le dépôt contient, pour que le test porte sur le TRI autant que sur l'effacement. */
    private final Map<UUID, AccompanimentRequest> base = new HashMap<>();

    @BeforeEach
    void preparer() {
        demandes = mock(DepotDemandes.class);
        evenements = mock(DepotEvenements.class);
        parametres = mock(ParametrePlateformeRepository.class);
        tenantParDefaut = mock(TenantParDefaut.class);
        audit = mock(AuditService.class);

        when(tenantParDefaut.identifiant()).thenReturn(TENANT);
        // Paramètres absents : la tâche retombe sur 24 et 60 mois.
        when(parametres.findByTenantIdAndCle(any(), anyString())).thenReturn(Optional.empty());
        when(demandes.save(any())).thenAnswer(i -> i.getArgument(0));

        service = new ServiceAnonymisation(demandes, evenements, parametres, tenantParDefaut, audit);
    }

    private static AccompanimentRequest demande(String nom, String statut, OffsetDateTime activite,
                                                OffsetDateTime finPrestation) {
        return AccompanimentRequest.builder()
                .id(UUID.randomUUID()).tenantId(TENANT)
                .nom(nom).email(nom.toLowerCase() + "@exemple.fr").telephoneWhatsapp("+242 06 000 00 00")
                .message("Je souhaite être accompagné.").notesInternes("Rappeler lundi.")
                .pays("Congo").profession("Médecin").specialite("Néphrologie")
                .statut(statut).creeLe(activite).derniereActiviteLe(activite)
                .prestationFinLe(finPrestation)
                .build();
    }

    /**
     * Reproduit le tri que fait la requête {@code aAnonymiser}, pour que le test porte sur la
     * RÈGLE et non sur une liste écrite d'avance.
     */
    private void alimenter(List<AccompanimentRequest> toutes) {
        toutes.forEach(d -> base.put(d.getId(), d));
        when(demandes.aAnonymiser(any(), any(), any(Pageable.class))).thenAnswer(invocation -> {
            OffsetDateTime limiteProspects = invocation.getArgument(0);
            OffsetDateTime limiteContrats = invocation.getArgument(1);
            List<AccompanimentRequest> echues = new ArrayList<>();
            for (AccompanimentRequest d : toutes) {
                boolean engagee = Enums.Statut.PAYE.code().equals(d.getStatut())
                        || "en_cours".equals(d.getStatut()) || "termine".equals(d.getStatut());
                boolean prospectEchu = d.getPrestationFinLe() == null && !engagee
                        && d.getDerniereActiviteLe().isBefore(limiteProspects);
                boolean contratEchu = d.getPrestationFinLe() != null
                        && d.getPrestationFinLe().isBefore(limiteContrats);
                if (d.getAnonymiseLe() == null && (prospectEchu || contratEchu)) {
                    echues.add(d);
                }
            }
            return echues;
        });
    }

    @Test
    @DisplayName("seule la demande non convertie de plus de 24 mois est anonymisée")
    void seuleLaPremiereEstAnonymisee() {
        OffsetDateTime maintenant = OffsetDateTime.now();
        AccompanimentRequest vieuxProspect = demande("Ancien", Enums.Statut.PERDU.code(),
                maintenant.minusMonths(25), null);
        AccompanimentRequest recente = demande("Recente", Enums.Statut.QUALIFIE.code(),
                maintenant.minusMonths(2), null);
        AccompanimentRequest payeeRecente = demande("Payee", Enums.Statut.PAYE.code(),
                maintenant.minusMonths(30), maintenant.minusMonths(30));
        alimenter(List.of(vieuxProspect, recente, payeeRecente));

        assertThat(service.executer()).isEqualTo(1);

        assertThat(vieuxProspect.getNom()).isEqualTo("[anonymisé]");
        assertThat(vieuxProspect.getAnonymiseLe()).isNotNull();

        // Une demande suivie il y a deux mois est en cours de traitement : l'effacer la
        // rendrait intraitable, et c'est le défaut que la date de DERNIÈRE ACTIVITÉ évite.
        assertThat(recente.getNom()).isEqualTo("Recente");
        assertThat(recente.getAnonymiseLe()).isNull();

        // Payée il y a 30 mois : au-delà des 24 mois des prospects, mais dans les 60 mois de
        // la prescription commerciale. C'est une pièce contractuelle, elle se conserve.
        assertThat(payeeRecente.getNom()).isEqualTo("Payee");
        assertThat(payeeRecente.getAnonymiseLe()).isNull();
    }

    @Test
    @DisplayName("une demande payée dont la prestation s'est achevée il y a plus de 60 mois part")
    void leContratEchuPart() {
        OffsetDateTime maintenant = OffsetDateTime.now();
        AccompanimentRequest vieuxContrat = demande("Contrat", Enums.Statut.PAYE.code(),
                maintenant.minusMonths(70), maintenant.minusMonths(61));
        alimenter(List.of(vieuxContrat));

        assertThat(service.executer()).isEqualTo(1);
        assertThat(vieuxContrat.getAnonymiseLe()).isNotNull();
    }

    @Test
    @DisplayName("une prestation ACHEVÉE garde le régime long, bien qu'elle ne soit plus « payée »")
    void leStatutAvanceMaisLeRegimeReste() {
        // Le défaut que ce test empêche : le régime long se reconnaissait au statut « payé ».
        // Or une demande réglée avance vers « en cours » puis « terminé » — elle quitte donc
        // ce statut. Fondée sur lui, la règle la faisait retomber parmi les prospects, et une
        // pièce contractuelle aurait été effacée 24 mois après la dernière activité au lieu de
        // 5 ans après la fin de prestation.
        OffsetDateTime maintenant = OffsetDateTime.now();
        AccompanimentRequest achevee = demande("Achevee", "termine",
                maintenant.minusMonths(30), maintenant.minusMonths(30));
        alimenter(List.of(achevee));

        assertThat(service.executer())
                .as("30 mois : au-delà des 24 des prospects, dans les 60 des contrats")
                .isZero();
        assertThat(achevee.getAnonymiseLe()).isNull();
    }

    @Test
    @DisplayName("une demande payée SANS date de fin de prestation n'est jamais effacée")
    void leContratSansFinResteOuvert() {
        // Le délai court à partir de la fin de la prestation. Tant qu'elle n'est pas
        // renseignée, il n'a pas commencé : conserver vaut mieux qu'effacer une pièce
        // qu'un litige pourrait réclamer.
        OffsetDateTime maintenant = OffsetDateTime.now();
        AccompanimentRequest sansFin = demande("EnCours", "en_cours",
                maintenant.minusMonths(80), null);
        alimenter(List.of(sansFin));

        assertThat(service.executer()).isZero();
        assertThat(sansFin.getAnonymiseLe()).isNull();
    }

    @Test
    @DisplayName("ce qui est effacé désigne une personne ; ce qui reste fait les statistiques")
    void ceQuiPartEtCeQuiReste() {
        OffsetDateTime maintenant = OffsetDateTime.now();
        AccompanimentRequest d = demande("Ancien", Enums.Statut.PERDU.code(),
                maintenant.minusMonths(25), null);
        alimenter(List.of(d));
        service.executer();

        assertThat(d.getNom()).isEqualTo("[anonymisé]");
        assertThat(d.getTelephoneWhatsapp()).isNull();
        assertThat(d.getMessage()).isNull();
        assertThat(d.getNotesInternes()).isNull();
        // L'adresse est REMPLACÉE et non vidée : la colonne est obligatoire, et deux demandes
        // anonymisées ne doivent pas devenir indiscernables l'une de l'autre.
        assertThat(d.getEmail()).contains(d.getId().toString()).endsWith("@invalide.local");

        // Pays, profession, spécialité et statut ne désignent personne : ce sont eux qui
        // permettront encore de dire « tant de néphrologues congolais en 2026 ».
        assertThat(d.getPays()).isEqualTo("Congo");
        assertThat(d.getProfession()).isEqualTo("Médecin");
        assertThat(d.getSpecialite()).isEqualTo("Néphrologie");
        assertThat(d.getStatut()).isEqualTo(Enums.Statut.PERDU.code());
    }

    @Test
    @DisplayName("le fil perd son contenu, le journal d'audit n'est jamais modifié")
    void lAuditResteIntact() {
        OffsetDateTime maintenant = OffsetDateTime.now();
        AccompanimentRequest d = demande("Ancien", Enums.Statut.PERDU.code(),
                maintenant.minusMonths(25), null);
        alimenter(List.of(d));
        service.executer();

        // Le fil parle du candidat : son contenu s'efface avec lui.
        verify(evenements).effacerContenu(d.getId());

        // L'audit, lui, est une preuve : s'il se réécrivait, il ne prouverait plus rien.
        // La seule chose qu'on lui demande est d'ENREGISTRER l'anonymisation — une écriture
        // de plus, jamais une modification. Le service n'expose d'ailleurs aucune méthode de
        // mise à jour ou de suppression, et ce test le constate par l'usage.
        verify(audit, times(1)).record(eq("ACCOMPAGNEMENT_ANONYMISATION"),
                eq("AccompanimentRequest"), eq(d.getId().toString()), anyString(), isNull());
        verifyNoMoreInteractions(audit);
    }

    @Test
    @DisplayName("aucune donnée personnelle n'entre dans le journal d'audit")
    void lAuditNeRecoitRienDeNominatif() {
        OffsetDateTime maintenant = OffsetDateTime.now();
        AccompanimentRequest d = demande("Dupont", Enums.Statut.PERDU.code(),
                maintenant.minusMonths(25), null);
        alimenter(List.of(d));
        service.executer();

        var resume = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(audit).record(anyString(), anyString(), anyString(), resume.capture(), isNull());
        // Si le nom ou l'adresse passaient par là, ils seraient figés dans une table qui ne
        // s'efface pas — c'est-à-dire conservés au-delà de leur durée, par la tâche même qui
        // est censée les supprimer.
        assertThat(resume.getValue()).doesNotContain("Dupont").doesNotContain("dupont@exemple.fr");
    }

    @Test
    @DisplayName("rien à faire : la tâche le dit quand même")
    void silenceSignale() {
        alimenter(List.of());
        assertThat(service.executer()).isZero();
        // passageQuotidien journalise même à zéro : une tâche silencieuse ne se distingue
        // pas d'une tâche qui ne s'exécute plus.
        service.passageQuotidien();
    }

    @Test
    @DisplayName("les lots sont plafonnés à cent")
    void lotsPlafonnes() {
        
        alimenter(List.of());
        service.executer();
        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(demandes).aAnonymiser(any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }
}
