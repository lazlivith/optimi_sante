package com.optimisante.backend.domain.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Passerelle vers le service IA Python (extraction Gemini, chat / rédaction Mistral).
 * <p>
 * Le service est <b>optionnel</b> : s'il est absent ou sans clé API, on lève une
 * IllegalStateException remappée en 409 par le GlobalExceptionHandler avec un message clair,
 * sans casser le reste de la plateforme (même philosophie que {@code ReportingClient} et que
 * Stripe/Cloudinary non configurés).
 * <p>
 * {@link SimpleClientHttpRequestFactory} est imposé : uvicorn (le serveur du worker Python) ne
 * parle que HTTP/1.1 et rejette la négociation HTTP/2 du client JDK par défaut — piège déjà
 * rencontré avec le worker de reporting.
 */
@Slf4j
@Component
public class AiClient {

    private final RestClient rest;
    private final String baseUrl;
    private final boolean configured;

    public AiClient(@Value("${app.ai.worker-base-url:}") String baseUrl) {
        this.configured = baseUrl != null && !baseUrl.isBlank();
        this.baseUrl = configured ? baseUrl.trim() : "";

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(180));

        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (configured) {
            builder.baseUrl(this.baseUrl);
        }
        this.rest = builder.build();
    }

    public boolean isConfigured() {
        return configured;
    }

    // ------------------------------------------------------------------ lecture

    public Map<String, Object> health() {
        return get("/health", new ParameterizedTypeReference<>() {
        });
    }

    public List<Map<String, Object>> extractionTypes() {
        return get("/extract/types", new ParameterizedTypeReference<>() {
        });
    }

    public List<Map<String, Object>> draftTypes() {
        return get("/draft/types", new ParameterizedTypeReference<>() {
        });
    }

    // ------------------------------------------------------------------ extraction

    /** Envoie le fichier tel quel au service IA ; aucune copie n'est conservée côté backend. */
    public Map<String, Object> extract(byte[] content, String filename, String contentType, String documentType) {
        requireConfigured();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "document" : filename;
            }
        };
        form.add("file", resource);
        form.add("document_type", documentType == null ? "OTHER" : documentType);

        try {
            return rest.post()
                    .uri("/extract")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    // ------------------------------------------------------------------ rédaction

    public Map<String, Object> draft(Map<String, Object> body) {
        return post("/draft", body);
    }

    public Map<String, Object> summarize(Map<String, Object> body) {
        return post("/summarize", body);
    }

    // ------------------------------------------------------------------ chat

    public Map<String, Object> chat(Map<String, Object> body, String authorization) {
        requireConfigured();
        try {
            return rest.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> forwardAuth(h, authorization))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    /**
     * Relaie le flux SSE du service IA. {@code onEvent} reçoit chaque couple
     * (nom d'événement, donnée) — {@code delta}, {@code done} ou {@code error}.
     */
    public void streamChat(Map<String, Object> body, String authorization,
                           BiConsumer<String, String> onEvent) {
        requireConfigured();
        try {
            rest.post()
                    .uri("/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .headers(h -> forwardAuth(h, authorization))
                    .body(body)
                    .exchange((request, response) -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String event = "delta";
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("event:")) {
                                    event = line.substring(6).trim();
                                } else if (line.startsWith("data:")) {
                                    onEvent.accept(event, line.substring(5).trim().replace("\\n", "\n"));
                                }
                            }
                        }
                        return null;
                    });
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    // ------------------------------------------------------------------ interne

    private <T> T get(String uri, ParameterizedTypeReference<T> type) {
        requireConfigured();
        try {
            return rest.get().uri(uri).retrieve().body(type);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    private Map<String, Object> post(String uri, Map<String, Object> body) {
        requireConfigured();
        try {
            return rest.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    private static void forwardAuth(HttpHeaders headers, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            // Le service IA réutilise ce jeton pour ses outils : tenant et rôles restent appliqués.
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException(
                    "Le service IA n'est pas configuré (app.ai.worker-base-url). "
                            + "Démarrez le conteneur 'ai' via docker-compose.");
        }
    }


    /**
     * Traduit un échec d'appel en message actionnable. Les trois causes n'appellent pas la même
     * réaction de l'exploitant : clé absente, quota du fournisseur épuisé, ou service réellement
     * injoignable. Les confondre envoie chercher au mauvais endroit.
     */
    private IllegalStateException unavailable(Exception cause) {
        String detail = cause.getMessage() == null ? "" : cause.getMessage();
        String lower = detail.toLowerCase();
        log.warn("AI service call failed: {}", detail);

        // Le worker renvoie 503 quand la clé du fournisseur manque : on nomme la variable à remplir.
        if (detail.contains("503") || lower.contains("api_key")) {
            return new IllegalStateException(
                    "Fonction IA indisponible : clé API du fournisseur non configurée. "
                            + "Renseignez GEMINI_API_KEY / MISTRAL_API_KEY dans docker/.env.");
        }

        // Clé valide mais compte sans quota, ou débit dépassé : rien à corriger dans le code.
        if (lower.contains("rate limit") || lower.contains("ratelimit")
                || lower.contains("429") || lower.contains("quota")) {
            return new IllegalStateException(
                    "Fonction IA momentanément indisponible : le fournisseur a refusé la requête "
                            + "(limite de débit ou quota atteint). Vérifiez le plan et les limites "
                            + "du compte sur la console du fournisseur.");
        }

        // Erreur remontée par le modèle : son message est plus utile qu'un texte générique.
        String workerDetail = extractWorkerDetail(detail);
        if (workerDetail != null && !workerDetail.isBlank()) {
            return new IllegalStateException("Fonction IA indisponible : " + workerDetail);
        }

        return new IllegalStateException(
                "Service IA injoignable. Vérifiez que le conteneur 'ai' est démarré.");
    }

    /** Extrait le champ {@code detail} du corps d'erreur FastAPI, quand il est présent. */
    private static String extractWorkerDetail(String message) {
        int idx = message.indexOf("\"detail\":\"");
        if (idx < 0) {
            return null;
        }
        String rest = message.substring(idx + 10);
        int end = rest.indexOf("\",");
        if (end < 0) {
            end = rest.indexOf("\"}");
        }
        String value = (end < 0 ? rest : rest.substring(0, end))
                .replace("\\n", " ")
                .trim();
        return value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }
}
