package com.optimisante.backend.domain.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le relais du flux SSE de l'assistant. Un {@code trim()} sur la ligne {@code data:} rognait
 * l'espace de tete de chaque fragment : la reponse arrivait dans le navigateur sous la forme
 * « Poureffectuer un achatsurlaplateforme ». Ces cas verrouillent la correction.
 */
class AiClientFluxSseTest {

    private static String relayer(String flux) throws IOException {
        StringBuilder recu = new StringBuilder();
        AiClient.relayerFlux(new BufferedReader(new StringReader(flux)), (event, data) -> {
            if ("delta".equals(event)) {
                recu.append(data);
            }
        });
        return recu.toString();
    }

    @Test
    @DisplayName("les espaces de tete des fragments sont conserves")
    void conserveLesEspacesEntreLesMots() throws IOException {
        String flux = """
                event: delta
                data: Pour

                event: delta
                data:  effectuer

                event: delta
                data:  un achat

                """;

        assertThat(relayer(flux)).isEqualTo("Pour effectuer un achat");
    }

    @Test
    @DisplayName("un seul espace est retire apres le deux-points, comme le veut SSE")
    void retireUnSeulEspaceDeSeparation() throws IOException {
        assertThat(relayer("event: delta\ndata:   deux espaces\n\n")).isEqualTo("  deux espaces");
    }

    @Test
    @DisplayName("l'echappement des sauts de ligne est transmis tel quel au navigateur")
    void laisseLEchappementIntact() throws IOException {
        assertThat(relayer("event: delta\ndata: ligne 1\\nligne 2\n\n"))
                .isEqualTo("ligne 1\\nligne 2");
    }

    @Test
    @DisplayName("un fragment survit a l'aller-retour relais puis reemission")
    void surviveALAllerRetour() throws IOException {
        // Le contrat entre ce relais et AiResource : Spring ecrit « data: » SANS separateur, donc
        // AiResource en pose un explicitement. Sans lui, « ␣effectuer » repartait en
        // « data:␣effectuer » et le client le relisait « effectuer » — les mots se collaient.
        String fragment = " effectuer un achat";

        String reemis = "event: delta\ndata: " + fragment + "\n\n";

        assertThat(relayer(reemis)).isEqualTo(fragment);
    }

    @Test
    @DisplayName("les evenements autres que delta sont bien distingues")
    void distingueLesEvenements() throws IOException {
        List<String> evenements = new ArrayList<>();
        AiClient.relayerFlux(
                new BufferedReader(new StringReader(
                        "event: delta\ndata: texte\n\nevent: error\ndata: panne\n\n")),
                (event, data) -> evenements.add(event + "=" + data));

        assertThat(evenements).containsExactly("delta=texte", "error=panne");
    }
}
