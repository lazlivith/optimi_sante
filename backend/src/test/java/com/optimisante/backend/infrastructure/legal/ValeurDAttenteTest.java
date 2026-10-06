package com.optimisante.backend.infrastructure.legal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La reconnaissance d'une valeur posée « en attendant ».
 *
 * <p>Deux propriétés, et la seconde compte plus que la première : les formules d'attente sont
 * rejetées, <b>et les valeurs réelles passent</b>. Un garde trop zélé refuserait le démarrage
 * d'une plateforme correctement configurée — une panne que personne ne comprendrait, causée
 * par le dispositif censé prévenir les pannes.</p>
 */
class ValeurDAttenteTest {

    @Test
    @DisplayName("les vraies mentions légales d'Optimi Santé passent")
    void valeursReelles() {
        // Ce sont les valeurs effectivement posées en production. Elles doivent passer, sinon
        // ce garde met la plateforme a l'arret. « Villenave » contient « na » : c'est
        // precisement le piege qu'un contrôle en sous-chaîne ferait tomber.
        for (String reelle : new String[]{
                "HOLDING GUIDON",
                "1 rue Larc Gauthier, 33140 Villenave-d'Ornon",
                "937 848 869 00023",
                "93784886900023",
                "FR24937848869",
                "RCS Bordeaux 937 848 869",
                "250 000 €",
                "Société par Actions Simplifiée (SAS)",
                "Andoche Guide Varin Tchiloemba"}) {
            assertThat(ValeurDAttente.enEst(reelle))
                    .as("valeur réelle refusée : %s", reelle)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("les formules réellement rencontrées en production sont rejetées")
    void formulesRencontrees() {
        // Celles-ci ont ete lues sur un document de production le 3 octobre 2026.
        for (String attente : new String[]{
                "A renseigner",
                "Siege social a renseigner",
                "IMMATRICULATION EN COURS",
                "EN COURS D'ATTRIBUTION"}) {
            assertThat(ValeurDAttente.enEst(attente))
                    .as("formule d'attente acceptée : %s", attente)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("la casse et les accents ne changent rien")
    void casseEtAccents() {
        assertThat(ValeurDAttente.enEst("À RENSEIGNER")).isTrue();
        assertThat(ValeurDAttente.enEst("a renseigner")).isTrue();
        assertThat(ValeurDAttente.enEst("À compléter")).isTrue();
        assertThat(ValeurDAttente.enEst("a completer")).isTrue();
    }

    @Test
    @DisplayName("les abréviations sont cherchées comme des mots, jamais en sous-chaîne")
    void motsEntiers() {
        assertThat(ValeurDAttente.enEst("TODO")).isTrue();
        assertThat(ValeurDAttente.enEst("N/A")).isTrue();
        assertThat(ValeurDAttente.enEst("XXX")).isTrue();
        assertThat(ValeurDAttente.enEst("-")).isTrue();

        // Et les memes suites de lettres au milieu d'un vrai mot ne declenchent rien.
        assertThat(ValeurDAttente.enEst("Villenave-d'Ornon")).isFalse();
        assertThat(ValeurDAttente.enEst("Nantes")).isFalse();
        assertThat(ValeurDAttente.enEst("Naturopathie SARL")).isFalse();
        assertThat(ValeurDAttente.enEst("Maxxximum Medical")).isFalse();
    }

    @Test
    @DisplayName("une valeur absente n'est pas une valeur d'attente")
    void absenceDistincteDeLAttente() {
        // L'absence se traite ailleurs, avec son propre message : « non renseignée » et
        // « contient une valeur d'attente » ne se corrigent pas de la meme facon.
        assertThat(ValeurDAttente.enEst(null)).isFalse();
        assertThat(ValeurDAttente.enEst("")).isFalse();
        assertThat(ValeurDAttente.enEst("   ")).isFalse();
    }

    @Test
    @DisplayName("un identifiant faux mais plausible passe, et c'est assumé")
    void nePretendPasValider() {
        // Ce garde attrape l'oubli, pas la fraude : seul le repertoire officiel dirait qu'un
        // SIRET a quatorze chiffres n'existe pas.
        assertThat(ValeurDAttente.enEst("12345678900012")).isFalse();
    }
}
