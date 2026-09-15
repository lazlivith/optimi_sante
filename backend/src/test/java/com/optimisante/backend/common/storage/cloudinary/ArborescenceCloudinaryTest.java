package com.optimisante.backend.common.storage.cloudinary;

import com.optimisante.backend.common.storage.DossierStockage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Arborescence Cloudinary : tout sous racine/environnement, rien hors de la racine. */
class ArborescenceCloudinaryTest {

    @Test
    void chaqueDossierEstRangeSousLaRacineEtLEnvironnement() {
        ArborescenceCloudinary arbo = new ArborescenceCloudinary("optimisante", "dev");
        assertEquals("optimisante/dev/dossiers-candidats/pieces", arbo.dossier(DossierStockage.DOSSIERS_PIECES));
        for (DossierStockage dossier : DossierStockage.values()) {
            assertTrue(arbo.dossier(dossier).startsWith("optimisante/dev/"), dossier.name());
        }
    }

    @Test
    void laProductionAUnDossierDistinct() {
        assertEquals("optimisante/prod/documents-emis/recus",
                new ArborescenceCloudinary("optimisante", "PROD").dossier(DossierStockage.DOCUMENTS_RECUS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "../TowerCore", "optimisante/autre", "dev prod", "é"})
    void uneRacineOuUnEnvironnementQuiSortiraitDeLaRacineEstRefuse(String valeur) {
        assertThrows(IllegalStateException.class, () -> new ArborescenceCloudinary(valeur, "dev"));
        assertThrows(IllegalStateException.class, () -> new ArborescenceCloudinary("optimisante", valeur));
    }

    @Test
    void deuxDossiersNePartagentJamaisLeMemeChemin() {
        Set<String> chemins = new HashSet<>();
        Arrays.stream(DossierStockage.values())
                .forEach(d -> assertTrue(chemins.add(d.chemin()), "chemin en double : " + d.chemin()));
    }

    @Test
    void lesCheminsSontRelatifsEtSansSegmentVide() {
        for (DossierStockage d : DossierStockage.values()) {
            assertFalse(d.chemin().startsWith("/") || d.chemin().endsWith("/") || d.chemin().contains("//")
                    || d.chemin().contains(".."), d.name());
        }
    }
}
