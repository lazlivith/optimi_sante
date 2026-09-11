package com.optimisante.backend.domain.reporting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Passerelle vers le worker Python FastAPI de reporting (batch nocturne & exécutions à la
 * demande). Le worker est optionnel : s'il est injoignable, on lève une IllegalStateException
 * remappée en 409 par le GlobalExceptionHandler avec un message clair, sans casser le reste de
 * l'admin (même philosophie que Stripe/Mailtrap non configurés).
 */
@Slf4j
@Component
public class ReportingClient {

    private final RestClient rest;
    private final boolean configured;

    public ReportingClient(@Value("${app.reporting.worker-base-url:}") String baseUrl) {
        this.configured = baseUrl != null && !baseUrl.isBlank();
        // SimpleClientHttpRequestFactory = HttpURLConnection, strictement HTTP/1.1. Le worker
        // Python (uvicorn/h11) est HTTP/1.1 uniquement et rejette les tentatives d'upgrade HTTP/2
        // que le client JDK par défaut négocie ("Unsupported upgrade request").
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(120));
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (configured) {
            builder.baseUrl(baseUrl.trim());
        }
        this.rest = builder.build();
    }

    public boolean isConfigured() {
        return configured;
    }

    public Map<String, Object> health() {
        return get("/health", new ParameterizedTypeReference<>() {
        });
    }

    public List<Map<String, Object>> listReports() {
        return get("/reports", new ParameterizedTypeReference<>() {
        });
    }

    public List<Map<String, Object>> runs(String key, int limit) {
        return get("/reports/" + key + "/runs?limit=" + limit, new ParameterizedTypeReference<>() {
        });
    }

    public Map<String, Object> preview(String key, int limit) {
        return get("/reports/" + key + "/preview?limit=" + limit, new ParameterizedTypeReference<>() {
        });
    }

    public Map<String, Object> run(String key, String trigger, Map<String, Object> params) {
        requireConfigured();
        try {
            return rest.post()
                    .uri("/reports/{key}/run?trigger={trigger}", key, trigger)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(params == null ? Map.of() : params)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    public byte[] downloadLatest(String key, String format) {
        requireConfigured();
        try {
            return rest.get()
                    .uri("/reports/{key}/download/latest?format={format}", key, format)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    private <T> T get(String uri, ParameterizedTypeReference<T> type) {
        requireConfigured();
        try {
            return rest.get().uri(uri).retrieve().body(type);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException(
                    "Le service de reporting Python n'est pas configuré (app.reporting.worker-base-url). "
                            + "Démarrez le conteneur 'analytics' via docker-compose.");
        }
    }

    private IllegalStateException unavailable(Exception cause) {
        log.warn("Reporting worker call failed: {}", cause.getMessage());
        return new IllegalStateException(
                "Service de reporting Python injoignable. Vérifiez que le conteneur 'analytics' est démarré.");
    }
}
