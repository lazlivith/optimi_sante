package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.ConfigurationBaseDeTest;
import com.optimisante.backend.domain.exercerenfrance.entity.EvcReglage;
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
    @Autowired private DepotReglages reglages;

    @Test
    @DisplayName("le premier passage a posé tout le contenu")
    void premierPassage() {
        assertThat(sections.count()).isEqualTo(7);
        assertThat(periodes.count()).isEqualTo(3);
        assertThat(liens.count()).isEqualTo(6);
        assertThat(specialites.count()).isEqualTo(33);
        // Trois offres affichables, plus « alerte_aep », technique et jamais publiée.
        // Quatre offres commerciales, plus l'offre technique « alerte_aep ».
        assertThat(offres.count()).isEqualTo(5);
        assertThat(publications.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("un second passage ne crée rien et ne publie rien")
    void secondPassageSansEffet() {
        long avantSections = sections.count();
        long avantSpecialites = specialites.count();
        long avantVersions = publications.count();

        seed.run(null);
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
    @DisplayName("une suppression faite par l'administration est DÉFINITIVE")
    void unElementSupprimeNeRevientPas() {
        // Le seed ne repasse qu'une fois : le marqueur « seed_applique_le » est posé au
        // premier passage réussi. Sans lui, ce qui manque était recréé à chaque démarrage —
        // une section retirée revenait au déploiement suivant, et il aurait fallu la
        // supprimer après chaque mise en ligne.
        assertThat(seed.dejaApplique())
                .as("le contexte de test a déjà exécuté le seed une fois").isTrue();

        var lien = liens.findByCle("fellowship").orElseThrow();
        String libelle = lien.getLibelle();
        String url = lien.getUrl();
        liens.delete(lien);

        // run() est ce que fait réellement un démarrage : il consulte le marqueur.
        seed.run(null);

        assertThat(liens.findByCle("fellowship"))
                .as("le lien supprimé ne revient pas").isEmpty();

        // Reposé pour les autres tests, qui comptent six liens. Reconstruit plutôt que
        // ré-enregistré : l'entité supprimée est détachée, et la sauver produirait une mise
        // à jour d'une ligne qui n'existe plus.
        liens.save(com.optimisante.backend.domain.exercerenfrance.entity.EvcContenu.Lien.builder()
                .cle("fellowship").libelle(libelle).url(url)
                .verifieLe(java.time.OffsetDateTime.now()).build());
    }

    @Test
    @DisplayName("sur une base déjà garnie mais sans marqueur, le seed ne pose QUE le marqueur")
    void baseGarnieAvantLeMarqueur() {
        // Le cas de la montée de version : une base remplie avant que le marqueur n'existe.
        // Sans ce garde-fou, le premier démarrage du nouveau code reposerait ce que
        // l'administration avait supprimé entre-temps — précisément ce que le marqueur sert à
        // empêcher, et la seule occasion où il ne le pourrait pas.
        reglages.deleteById(EvcReglage.SEED_APPLIQUE_LE);
        assertThat(seed.dejaApplique()).isFalse();
        assertThat(seed.dejaGarnie()).as("la base porte déjà le contenu").isTrue();

        var lien = liens.findByCle("fellowship").orElseThrow();
        String libelle = lien.getLibelle();
        String url = lien.getUrl();
        liens.delete(lien);
        long versionsAvant = publications.count();

        seed.run(null);

        assertThat(liens.findByCle("fellowship"))
                .as("rien n'est reposé").isEmpty();
        assertThat(publications.count())
                .as("et rien n'est republié").isEqualTo(versionsAvant);
        assertThat(seed.dejaApplique())
                .as("le marqueur est posé, pour que la question ne se repose plus").isTrue();

        liens.save(com.optimisante.backend.domain.exercerenfrance.entity.EvcContenu.Lien.builder()
                .cle("fellowship").libelle(libelle).url(url)
                .verifieLe(java.time.OffsetDateTime.now()).build());
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
    @DisplayName("les offres partent avec la grille validée, et ne promettent rien d'autre")
    void grilleDePrix() {
        // Les prix ne sont plus « sur devis » : une grille a été arrêtée, et elle est affichée.
        // Les montants sont TTC, en euros.
        assertThat(prix("preparation_evc")).isEqualByComparingTo("89");
        assertThat(prix("dossier_demarches")).isEqualByComparingTo("450");
        assertThat(prix("mobilite_installation")).isEqualByComparingTo("650");
        assertThat(prix("pack_dossier_mobilite")).isEqualByComparingTo("990");

        // Seul le pack annonce une comparaison, et elle doit être supérieure à son prix :
        // sinon la page afficherait une économie qui n'existe pas.
        var pack = offre("pack_dossier_mobilite");
        assertThat(pack.getPrixBarre()).isEqualByComparingTo("1100");
        assertThat(pack.getPrixBarre()).isGreaterThan(pack.getPrix());
        assertThat(pack.getBadge()).isEqualTo("Le plus complet");

        assertThat(offres.findAll()).filteredOn(o -> !Boolean.TRUE.equals(o.getTechnique()))
                .filteredOn(o -> !"pack_dossier_mobilite".equals(o.getCle()))
                .allSatisfy(o -> assertThat(o.getPrixBarre())
                        .as("seul le pack affiche un prix barré (%s)", o.getCle()).isNull());

        // L'offre technique n'est pas vendue : lui poser un prix la ferait apparaître comme
        // une prestation.
        assertThat(offre("alerte_aep").getPrix()).isNull();
    }

    @Test
    @DisplayName("aucune offre ne promet ce qu'Optimi Santé ne fournit pas")
    void aucunePromesseHorsPerimetre() {
        // Optimi Santé informe, oriente, vérifie et suit. Elle n'assure ni préparation
        // pédagogique, ni traduction, ni assurance, ni logement. Les premiers textes
        // promettaient les quatre — un praticien qui paie pour une banque de QCM et reçoit
        // une orientation a été trompé, et il l'a payé.
        var interdits = java.util.List.of("qcm", "examen blanc", "examens blancs",
                "classe virtuelle", "classes virtuelles", "traduction", "traductions");
        assertThat(offres.findAll()).allSatisfy(o -> {
            String texte = ((o.getTitre() == null ? "" : o.getTitre()) + " "
                    + (o.getPoints() == null ? "" : o.getPoints())).toLowerCase();
            interdits.forEach(mot -> assertThat(texte)
                    .as("l'offre %s ne doit pas promettre « %s »", o.getCle(), mot)
                    .doesNotContain(mot));
        });
    }

    private com.optimisante.backend.domain.exercerenfrance.entity.EvcContenu.Offre offre(String cle) {
        return offres.findAll().stream().filter(o -> cle.equals(o.getCle())).findFirst()
                .orElseThrow(() -> new AssertionError("Offre « " + cle + " » absente du seed."));
    }

    private java.math.BigDecimal prix(String cle) {
        return offre(cle).getPrix();
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
