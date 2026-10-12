package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.domain.exercerenfrance.service.CelluleCsv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qu'une cellule d'export a le droit de contenir.
 *
 * <p><b>L'attaque éprouvée ici.</b> Un tableur n'ouvre pas un CSV, il l'INTERPRÈTE. Une
 * cellule commençant par {@code =} est une formule, et son contenu s'exécute sur le poste de
 * l'administrateur qui ouvre le fichier. Le nom saisi dans un formulaire PUBLIC arrive
 * jusque-là sans qu'aucune faille technique ne soit nécessaire — seulement un champ de texte
 * et un export.</p>
 */
class CelluleCsvTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "=HYPERLINK(\"http://malveillant.example\",\"Cliquez\")",
            "=1+1",
            "+1+1",
            "-1+1",
            "@SUM(A1:A9)",
            // La commande DDE : la plus dangereuse des formules, elle lance un programme.
            "=cmd|'/c calc'!A1",
    })
    @DisplayName("une formule est préfixée d'une apostrophe, donc traitée comme du texte")
    void formulesNeutralisees(String valeur) {
        String cellule = CelluleCsv.neutraliser(valeur);
        // Les guillemets protègent le DÉCOUPAGE, pas l'interprétation : « "=1+1" » reste une
        // formule pour Excel. Seule l'apostrophe en tête force le texte.
        assertThat(cellule).startsWith("\"'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\t=1+1", "\r=1+1", "\n=1+1"})
    @DisplayName("une formule précédée d'un blanc invisible est neutralisée elle aussi")
    void formulesPrecedeesDUnBlanc(String valeur) {
        // Certains tableurs ignorent ces caractères avant d'analyser ce qui suit : sans ce
        // traitement, « \t=1+1 » redevient une formule.
        assertThat(CelluleCsv.neutraliser(valeur)).startsWith("\"'");
    }

    @Test
    @DisplayName("un nom ordinaire traverse sans apostrophe parasite")
    void nomsOrdinaires() {
        assertThat(CelluleCsv.neutraliser("Dr Samba Nkounkou")).isEqualTo("\"Dr Samba Nkounkou\"");
        assertThat(CelluleCsv.neutraliser("Néphrologie")).isEqualTo("\"Néphrologie\"");
        // Le « + » de l'indicatif est en tête : il est préfixé, et c'est voulu — on ne peut
        // pas distinguer un numéro d'une formule sur ce seul caractère.
        assertThat(CelluleCsv.neutraliser("+242 06 123 45 67")).startsWith("\"'+242");
    }

    @Test
    @DisplayName("un guillemet est doublé, un retour à la ligne devient une espace")
    void decoupagePreserve() {
        assertThat(CelluleCsv.neutraliser("Le \"CHU\" de Brazzaville"))
                .isEqualTo("\"Le \"\"CHU\"\" de Brazzaville\"");
        assertThat(CelluleCsv.neutraliser("Ligne 1\nLigne 2"))
                .isEqualTo("\"Ligne 1 Ligne 2\"");
    }

    @Test
    @DisplayName("une valeur absente devient une cellule vide, pas le mot « null »")
    void valeurAbsente() {
        assertThat(CelluleCsv.neutraliser(null)).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("le fichier s'annonce en UTF-8 et se découpe au point-virgule")
    void conventionsExcelFrancais() {
        // Sans le BOM, Excel en configuration française lit l'UTF-8 comme du Windows-1252 et
        // les accents deviennent illisibles. Avec la virgule, le fichier s'ouvre en une
        // seule colonne.
        assertThat(CelluleCsv.BOM).isEqualTo("﻿");
        assertThat(CelluleCsv.SEPARATEUR).isEqualTo(';');
    }

    // ── La colonne WhatsApp ────────────────────────────────────────────────────────────
    //
    // Ce fichier-là existe pour qu'on COLLE les numéros dans WhatsApp. Mesuré sur Excel :
    // l'apostrophe de protection, invisible quand on la TAPE, s'AFFICHE dans la cellule
    // quand elle vient d'un CSV — « '+242 06 123 45 67 ». Un numéro qu'il faut nettoyer à
    // la main avant de s'en servir est un numéro inutilisable.

    @ParameterizedTest
    @ValueSource(strings = {
            "+242 06 123 45 67",
            "+223 70 11 22 33",
            "06 123 45 67",
    })
    @DisplayName("un numéro avec espaces sort nu : collable, et Excel le laisse en texte")
    void numeroCollable(String valeur) {
        // Mesuré : Excel n'évalue pas « +242 06 123 45 67 » — ce n'est pas une formule
        // valide, il retombe sur du texte et affiche le numéro tel quel.
        assertThat(CelluleCsv.numeroTelephone(valeur))
                .isEqualTo("\"" + valeur + "\"")
                .doesNotContain("'");
    }

    @Test
    @DisplayName("un numéro sans espace garde l'apostrophe : sinon Excel en fait un nombre")
    void numeroSansEspaceProtege() {
        // Mesuré : « +24206123456 » s'affiche « 2,4206E+10 ». Le « + » est pris pour un
        // signe et les chiffres pour une valeur. Une apostrophe gênante vaut mieux qu'un
        // numéro détruit.
        assertThat(CelluleCsv.numeroTelephone("+24206123456")).startsWith("\"'");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "=1+1",
            "=cmd|'/c calc'!A1",
            "+1+1",
            "\t=1+1",
            "\r=1+1",
            // Une formule maquillée en numéro : des chiffres, des espaces... et un « = ».
            "=1+1 2 3",
            // Un numéro suivi d'une formule : la dispense ne porte que sur la forme ENTIÈRE.
            "+242 06 123 45 67 =1+1",
            "+242-06-123",
    })
    @DisplayName("la dispense ne s'étend à rien d'autre : tout le reste est neutralisé")
    void dispenseStrictementLimitee(String valeur) {
        // Cette fonction ACCORDE une dispense, elle ne la présume pas : ce qui ne ressemble
        // pas exactement à un numéro repasse par neutraliser().
        assertThat(CelluleCsv.numeroTelephone(valeur))
                .isEqualTo(CelluleCsv.neutraliser(valeur));
    }

    @Test
    @DisplayName("un numéro absent ou vide devient une cellule vide")
    void numeroAbsent() {
        assertThat(CelluleCsv.numeroTelephone(null)).isEqualTo("\"\"");
        assertThat(CelluleCsv.numeroTelephone("   ")).isEqualTo("\"\"");
    }
}
