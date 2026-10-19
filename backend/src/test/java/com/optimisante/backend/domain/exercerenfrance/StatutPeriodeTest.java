package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.domain.exercerenfrance.service.StatutPeriode;
import com.optimisante.backend.domain.exercerenfrance.service.StatutPeriode.Etat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'état public d'une période de dépôt.
 *
 * <p>Ce qui se joue ici n'est pas une formule mais une conséquence : un praticien qui lit
 * « Ouvert » dépose son dossier. Une période annoncée ouverte un jour de trop fait perdre
 * une année à quelqu'un, et le calcul est la seule chose qui s'interpose.</p>
 */
class StatutPeriodeTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 8, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 10, 1);

    /** Le jour dit, à midi heure de Paris. */
    private static Clock le(int annee, int mois, int jour) {
        return Clock.fixed(LocalDate.of(annee, mois, jour)
                .atTime(12, 0).atZone(StatutPeriode.PARIS).toInstant(), StatutPeriode.PARIS);
    }

    @Test
    @DisplayName("la veille de l'ouverture, la période est à venir")
    void laVeille() {
        assertThat(StatutPeriode.calculer(DEBUT, FIN, le(2026, 7, 31))).isEqualTo(Etat.A_VENIR);
    }

    @Test
    @DisplayName("le jour de début, la période est ouverte")
    void leJourDeDebut() {
        // Borne incluse : un dossier déposé le premier jour est recevable.
        assertThat(StatutPeriode.calculer(DEBUT, FIN, le(2026, 8, 1))).isEqualTo(Etat.OUVERT);
    }

    @Test
    @DisplayName("le jour de fin, la période est ENCORE ouverte")
    void leJourDeFin() {
        // Le défaut que ce test empêche : « fermé dès le jour de clôture » retirerait au
        // praticien sa dernière journée, celle où les dépôts sont les plus nombreux.
        assertThat(StatutPeriode.calculer(DEBUT, FIN, le(2026, 10, 1))).isEqualTo(Etat.OUVERT);
    }

    @Test
    @DisplayName("le lendemain de la fin, la période est fermée")
    void leLendemain() {
        assertThat(StatutPeriode.calculer(DEBUT, FIN, le(2026, 10, 2))).isEqualTo(Etat.FERME);
    }

    @Test
    @DisplayName("sans aucune date, la période est à venir")
    void sansDates() {
        // « Périodes 2027 » existe avant que le CNG n'annonce son calendrier.
        assertThat(StatutPeriode.calculer(null, null, le(2026, 10, 2))).isEqualTo(Etat.A_VENIR);
    }

    @Test
    @DisplayName("sans date de fin, la période reste ouverte une fois commencée")
    void sansDateDeFin() {
        assertThat(StatutPeriode.calculer(DEBUT, null, le(2026, 7, 31))).isEqualTo(Etat.A_VENIR);
        assertThat(StatutPeriode.calculer(DEBUT, null, le(2027, 5, 1))).isEqualTo(Etat.OUVERT);
    }

    @Test
    @DisplayName("sans date de début, seule la fin tranche")
    void sansDateDeDebut() {
        assertThat(StatutPeriode.calculer(null, FIN, le(2026, 10, 1))).isEqualTo(Etat.OUVERT);
        assertThat(StatutPeriode.calculer(null, FIN, le(2026, 10, 2))).isEqualTo(Etat.FERME);
    }

    @Test
    @DisplayName("le dernier jour, à 23 h 30 à Paris, la période est encore ouverte")
    void laDerniereHeure() {
        // Calculé en UTC, ce même instant est déjà le 2 octobre : la période se fermerait
        // deux heures trop tôt, et toute une soirée de dépôts serait refusée.
        Clock veilleDeMinuit = Clock.fixed(
                LocalDate.of(2026, 10, 1).atTime(23, 30).atZone(StatutPeriode.PARIS).toInstant(),
                java.time.ZoneOffset.UTC);
        assertThat(StatutPeriode.calculer(DEBUT, FIN, veilleDeMinuit)).isEqualTo(Etat.OUVERT);
    }

    @Test
    @DisplayName("une période d'un seul jour est ouverte ce jour-là, et lui seul")
    void periodeDUnJour() {
        LocalDate jour = LocalDate.of(2026, 3, 15);
        assertThat(StatutPeriode.calculer(jour, jour, le(2026, 3, 14))).isEqualTo(Etat.A_VENIR);
        assertThat(StatutPeriode.calculer(jour, jour, le(2026, 3, 15))).isEqualTo(Etat.OUVERT);
        assertThat(StatutPeriode.calculer(jour, jour, le(2026, 3, 16))).isEqualTo(Etat.FERME);
    }

    @Test
    @DisplayName("c'est la date à Paris qui tranche, pas celle du serveur")
    void cEstParisQuiTranche() {
        // Le serveur tourne en Oregon (Render). À cet instant précis, il est encore le
        // 1er octobre là-bas — mais déjà le 2 à Paris, et la période est close.
        //
        // Lu à l'heure du serveur, le site afficherait « Ouvert » toute une journée après
        // la clôture, et des praticiens déposeraient un dossier irrecevable.
        Clock oregon = Clock.fixed(Instant.parse("2026-10-01T22:00:00Z"),
                java.time.ZoneId.of("America/Los_Angeles"));

        assertThat(LocalDate.ofInstant(oregon.instant(), oregon.getZone()))
                .as("il est encore le 1er octobre pour le serveur")
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(StatutPeriode.calculer(DEBUT, FIN, oregon)).isEqualTo(Etat.FERME);
    }
}
