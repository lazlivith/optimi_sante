package com.optimisante.backend.domain.ai;

import com.optimisante.backend.config.security.PlatformAdmin;
import com.optimisante.backend.domain.ai.web.AiResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le périmètre d'accès des routes d'IA, vérifié par la structure et non par le déroulé.
 *
 * <p><b>Pourquoi un test de structure.</b> Le défaut corrigé par ce lot n'était pas un
 * comportement faux : c'était une <b>annotation absente</b>. {@code /extract} envoyait un
 * fichier au modèle pour tout compte connecté, alors que {@code /draft} et {@code /summarize},
 * écrits dix lignes plus bas dans le même fichier, étaient réservés. Rien ne cassait, rien
 * n'échouait, et aucun test de comportement ne pouvait le voir. Un test qui lit les annotations
 * le voit — et le reverra le jour où quelqu'un ajoutera une route au même endroit sans y
 * penser.</p>
 */
@DisplayName("Périmètre d'accès des routes d'IA")
class RoutesIaProtegeesTest {

    /**
     * Les méthodes qui doivent porter {@link PlatformAdmin}.
     *
     * <p>Les trois routes d'extraction y figurent depuis {@code SEC‑1a} : elles transmettent un
     * fichier à un service tiers et chaque appel est facturé.</p>
     */
    private static final List<String> RESERVEES_A_L_ADMINISTRATION = List.of(
            "status",
            "extractionTypes",
            "extract",
            "markApplied",
            "draftTypes",
            "draft",
            "summarize");

    /**
     * Les méthodes délibérément ouvertes. Elles sont plafonnées par
     * {@code FiltrePlafondDebit} — c'est leur seule protection, et c'est assumé : l'assistant
     * doit répondre à un visiteur qui n'a pas de compte.
     */
    private static final List<String> OUVERTES_ET_PLAFONNEES = List.of(
            "chat",
            "chatStream");

    @Test
    @DisplayName("les trois routes d'extraction sont réservées à l'administration")
    void lesRoutesDExtractionSontReservees() {
        for (String nom : List.of("extract", "extractionTypes", "markApplied")) {
            assertThat(porteLAnnotationAdmin(nom))
                    .as("%s transmet un fichier au modèle et chaque appel est facturé : "
                            + "elle ne peut pas être ouverte à tout compte connecté", nom)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("toutes les routes coûteuses sont réservées, aucune n'a été oubliée")
    void aucuneRouteCouteuseOubliee() {
        for (String nom : RESERVEES_A_L_ADMINISTRATION) {
            assertThat(porteLAnnotationAdmin(nom))
                    .as("la route %s doit porter @PlatformAdmin", nom)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("seules les deux routes de conversation restent ouvertes")
    void seulesLesRoutesDeConversationRestentOuvertes() {
        for (String nom : OUVERTES_ET_PLAFONNEES) {
            assertThat(porteLAnnotationAdmin(nom))
                    .as("%s est ouverte par choix, protégée par le plafond de débit", nom)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("aucune route n'échappe à l'inventaire de ce test")
    void aucuneRouteHorsInventaire() {
        List<String> connues = Arrays.asList(
                RESERVEES_A_L_ADMINISTRATION.toArray(new String[0]));

        List<String> sansVerdict = Arrays.stream(AiResource.class.getDeclaredMethods())
                .filter(methode -> java.lang.reflect.Modifier.isPublic(methode.getModifiers()))
                .map(Method::getName)
                .distinct()
                .filter(nom -> !connues.contains(nom))
                .filter(nom -> !OUVERTES_ET_PLAFONNEES.contains(nom))
                // Routes de lecture d'une conversation : elles ne coûtent rien et ne sortent
                // aucune donnée d'un autre visiteur, la conversation étant retrouvée par un
                // identifiant qu'il faut déjà posséder.
                .filter(nom -> !List.of("conversations", "messages").contains(nom))
                .toList();

        assertThat(sansVerdict)
                .as("une route d'IA ajoutée sans décision d'accès : c'est exactement ainsi que "
                        + "/extract est resté ouvert. Classe-la dans l'une des deux listes.")
                .isEmpty();
    }

    private boolean porteLAnnotationAdmin(String nomDeMethode) {
        return Arrays.stream(AiResource.class.getDeclaredMethods())
                .filter(methode -> methode.getName().equals(nomDeMethode))
                .anyMatch(methode -> methode.isAnnotationPresent(PlatformAdmin.class));
    }
}
