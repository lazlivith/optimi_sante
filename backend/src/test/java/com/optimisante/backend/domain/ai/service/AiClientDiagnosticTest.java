package com.optimisante.backend.domain.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le message rendu à l'exploitant nomme la bonne cause.
 *
 * <p><b>Ce que ce test empêche.</b> Les trois pannes du service IA appellent trois gestes
 * différents : renseigner une clé, vérifier une facturation, ou simplement réessayer. Les
 * confondre envoie chercher au mauvais endroit — c'est arrivé en production, où un modèle saturé
 * chez Google était annoncé comme une clé manquante, alors qu'elle était en place.</p>
 */
@DisplayName("Diagnostic des pannes du service IA")
class AiClientDiagnosticTest {

    private static String message(String erreurDuWorker) {
        AiClient client = new AiClient("");   // sans adresse : on ne teste que le diagnostic
        Exception traduite = (Exception) ReflectionTestUtils.invokeMethod(
                client, "unavailable", new RuntimeException(erreurDuWorker));
        return traduite.getMessage();
    }

    @Test
    @DisplayName("clé absente : on nomme la variable à remplir")
    void cleAbsente() {
        assertThat(message("{\"detail\":\"Le fournisseur « gemini » n'est pas configuré : "
                + "renseignez GEMINI_API_KEY\"}"))
                .contains("clé API du fournisseur non configurée");
    }

    @Test
    @DisplayName("quota épuisé : on renvoie vers la console du fournisseur")
    void quotaEpuise() {
        assertThat(message("litellm.RateLimitError: MistralException - "
                + "{\"message\":\"Rate limit exceeded\",\"raw_status_code\":429}"))
                .contains("limite de débit ou quota atteint");
    }

    @Test
    @DisplayName("modèle saturé : on invite à réessayer, sans parler de clé ni de facturation")
    void modeleSature() {
        // Le cas réel : Gemini répond ainsi avec une clé valide et un quota disponible.
        String msg = message("litellm.ServiceUnavailableError: GeminiException - "
                + "{\"error\":{\"code\":503,\"message\":\"This model is currently experiencing "
                + "high demand\"}}");
        assertThat(msg).contains("saturé");
        assertThat(msg).doesNotContain("clé API");
        assertThat(msg).doesNotContain("quota");
    }

    @Test
    @DisplayName("panne inconnue : le message du service est plus utile qu'un texte générique")
    void panneInconnue() {
        assertThat(message("{\"detail\":\"Fichier trop volumineux (max 15 Mo).\"}"))
                .contains("Fichier trop volumineux");
    }
}
