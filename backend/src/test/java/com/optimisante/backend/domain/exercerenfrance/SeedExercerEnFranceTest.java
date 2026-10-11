package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.ConfigurationBaseDeTest;
import com.optimisante.backend.domain.exercerenfrance.repository.*;
import com.optimisante.backend.domain.exercerenfrance.service.SeedExercerEnFrance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contenu initial, et ce qu'il advient quand on le repose.
 *
 * <p><b>Le seed s'exécute à chaque démarrage.</b> Ce n'est donc pas une précaution théorique :
 * en production, il repassera après chaque déploiement, sur un contenu que l'administration
 * aura modifié entre-temps. S'il écrasait, le travail d'éditorial disparaîtrait à la mise en
 * ligne suivante — sans trace, et sans que personne ne fasse le lien entre les deux.</p>
 *
 * <p>Le contexte de test l'a déjà exécuté une fois au démarrage. Ces tests constatent cet
 * état, puis le relancent : c'est exactement le deuxième passage que l'on veut éprouver.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class SeedExercerEnFranceTest {

    @Autowired private SeedExercerEnFrance seed;
    @Autowired private DepotSections sections;
    @Autowired private DepotPeriodes periodes;
    @Autowired private DepotLiens liens;
    @Autowired private DepotSpecialites specialites;
    @Autowired private DepotOffres offres;
    @Autowired private DepotPublications publications;

    @Test
    @DisplayName("le premier passage a posé tout le contenu")
    void premierPassage() {
        assertThat(sections.count()).isEqualTo(7);
        assertThat(periodes.count()).isEqualTo(3);
        assertThat(liens.count()).isEqualTo(6);
        assertThat(specialites.count()).isEqualTo(33);
        assertThat(offres.count()).isEqualTo(3);
        assertThat(publications.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("un second passage ne crée rien et ne publie rien")
    void secondPassageSansEffet() {
        long avantSections = sections.count();
        long avantSpecialites = specialites.count();
        long avantVersions = publications.count();

        assertThat(seed.garnir()).as("aucun élément posé").isZero();
        assertThat(seed.publierSiAucuneVersion()).as("aucune version ajoutée").isFalse();

        assertThat(sections.count()).isEqualTo(avantSections);
        assertThat(specialites.count()).isEqualTo(avantSpecialites);
        assertThat(publications.count()).isEqualTo(avantVersions);
    }

    @Test
    @DisplayName("un contenu modifié par l'administration survit au seed")
    void leTravailDeLAdministrationSurvit() {
        // Le cas réel : quelqu'un corrige un texte, puis on déploie.
        var evc = sections.findByCle("evc").orElseThrow();
        evc.setTitre("Titre corrigé par l'administration");
        evc.setContenu("Texte réécrit après l'avis du CNG.");
        evc.setVisible(false);
        sections.save(evc);

        seed.garnir();

        var apres = sections.findByCle("evc").orElseThrow();
        assertThat(apres.getTitre()).isEqualTo("Titre corrigé par l'administration");
        assertThat(apres.getContenu()).isEqualTo("Texte réécrit après l'avis du CNG.");
        assertThat(apres.getVisible()).isFalse();

        // Remis en état : les autres tests de cette classe lisent le même contenu.
        apres.setTitre("Les épreuves de vérification des connaissances (EVC)");
        apres.setVisible(true);
        sections.save(apres);
    }

    @Test
    @DisplayName("un prix fixé par le super administrateur survit au seed")
    void lePrixSurvit() {
        var offre = offres.findAll().stream()
                .filter(o -> "preparation_evc".equals(o.getCle())).findFirst().orElseThrow();
        offre.setPrix(new java.math.BigDecimal("490.00"));
        offres.save(offre);

        seed.garnir();

        assertThat(offres.findAll().stream()
                .filter(o -> "preparation_evc".equals(o.getCle())).findFirst().orElseThrow()
                .getPrix()).isEqualByComparingTo("490.00");
    }

    @Test
    @DisplayName("un élément supprimé est reposé : le seed comble, il n'écrase pas")
    void unElementSupprimeRevient() {
        // La contrepartie de « ne jamais modifier » : ce qui manque est recréé. C'est voulu —
        // une section supprimée par erreur revient au déploiement suivant, et l'administration
        // dispose d'un interrupteur « visible » pour la masquer sans la détruire.
        var lien = liens.findByCle("fellowship").orElseThrow();
        liens.delete(lien);

        assertThat(seed.garnir()).isEqualTo(1);
        assertThat(liens.findByCle("fellowship")).isPresent();
    }

    @Test
    @DisplayName("les cinq liens officiels sont posés vérifiés, et aucun n'est à compléter")
    void liensRenseignes() {
        var tous = liens.findAllByOrderByCleAsc();
        assertThat(tous).allSatisfy(l -> {
            assertThat(l.estRenseigne()).as("le lien %s porte une adresse", l.getCle()).isTrue();
            assertThat(l.getVerifieLe()).as("le lien %s est daté", l.getCle()).isNotNull();
        });
        // refugies pointe sciemment vers la page EVC : le CNG n'a pas de page dédiée.
        assertThat(liens.findByCle("refugies").orElseThrow().getUrl())
                .isEqualTo(liens.findByCle("session_evc").orElseThrow().getUrl());
    }

    @Test
    @DisplayName("les trois offres partent sans prix, donc « Sur devis »")
    void offresSansPrix() {
        // lePrixSurvit() en fixe un sur preparation_evc ; les deux autres doivent rester nus.
        assertThat(offres.findAll()).filteredOn(o -> !"preparation_evc".equals(o.getCle()))
                .allSatisfy(o -> assertThat(o.getPrix()).isNull());
    }

    @Test
    @DisplayName("« Périodes 2027 » est posée sans dates, donc « à venir »")
    void periodeSansDates() {
        var p = periodes.findAllByOrderByOrdreAsc().stream()
                .filter(x -> x.getLibelle().equals("Périodes 2027")).findFirst().orElseThrow();
        assertThat(p.getDateDebut()).isNull();
        assertThat(p.getDateFin()).isNull();
        assertThat(p.getSourceUrl()).as("la source reste obligatoire, même sans dates").isNotBlank();
    }
}
