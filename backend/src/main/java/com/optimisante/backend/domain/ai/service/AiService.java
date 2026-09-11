package com.optimisante.backend.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.ai.entity.AiConversation;
import com.optimisante.backend.domain.ai.entity.AiDocumentJob;
import com.optimisante.backend.domain.ai.entity.AiMessage;
import com.optimisante.backend.domain.ai.repository.AiConversationRepository;
import com.optimisante.backend.domain.ai.repository.AiDocumentJobRepository;
import com.optimisante.backend.domain.ai.repository.AiMessageRepository;
import com.optimisante.backend.domain.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestration des fonctions IA côté backend : appel du service Python, persistance des
 * conversations et des extractions, journalisation d'audit.
 * <p>
 * Deux invariants tenus ici :
 * <ul>
 *   <li>Une extraction est une <b>proposition</b> — rien n'est reporté dans un dossier sans
 *       validation humaine explicite ({@code appliedAt}).</li>
 *   <li>Toute utilisation de l'IA est tracée dans le journal d'audit (acteur, type, document).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final int MAX_HISTORY = 20;

    /**
     * Instance locale plutôt qu'injectée : ce contexte Spring n'expose pas de bean
     * {@code ObjectMapper} (le démarrage échouait avec « No qualifying bean of type ObjectMapper »).
     * On n'en a besoin que pour sérialiser les champs extraits dans une colonne TEXT.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AiClient aiClient;
    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiDocumentJobRepository documentJobRepository;
    private final AuditService auditService;

    // ------------------------------------------------------------------ état

    public Map<String, Object> status() {
        if (!aiClient.isConfigured()) {
            return Map.of("configured", false, "reachable", false,
                    "error", "Service IA non configuré (app.ai.worker-base-url).");
        }
        try {
            Map<String, Object> health = aiClient.health();
            Map<String, Object> result = new HashMap<>();
            result.put("configured", true);
            result.put("reachable", true);
            result.put("worker", health);
            return result;
        } catch (Exception e) {
            return Map.of("configured", true, "reachable", false, "error", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ extraction

    @Transactional
    public Map<String, Object> extract(MultipartFile file, String documentType) {
        String type = documentType == null || documentType.isBlank() ? "OTHER" : documentType.toUpperCase();
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Fichier illisible : " + e.getMessage());
        }

        AiDocumentJob job = AiDocumentJob.builder()
                .tenantId(TenantContext.getTenantId())
                .requestedBy(currentUserIdOrNull())
                .documentType(type)
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes((long) content.length)
                .build();

        try {
            Map<String, Object> result = aiClient.extract(
                    content, file.getOriginalFilename(), file.getContentType(), type);

            job.setStatus("SUCCESS");
            job.setTypeMatches(asBoolean(result.get("type_matches")));
            job.setModel(asString(result.get("model")));
            job.setExtractedJson(toJson(result.get("fields")));
            job.setWarnings(toJson(result.get("warnings")));
            documentJobRepository.save(job);

            auditService.record("AI_DOCUMENT_EXTRACT", "AiDocumentJob", String.valueOf(job.getId()),
                    "Extraction " + type + " sur « " + file.getOriginalFilename() + " »", null);

            Map<String, Object> response = new HashMap<>(result);
            response.put("jobId", job.getId());
            return response;
        } catch (RuntimeException e) {
            job.setStatus("FAILED");
            job.setError(truncate(e.getMessage(), 2000));
            try {
                documentJobRepository.save(job);
            } catch (Exception ignored) {
                // La trace ne doit pas masquer l'erreur d'origine.
            }
            throw e;
        }
    }

    /** Marque une extraction comme réellement reportée dans le dossier par un humain. */
    @Transactional
    public void markApplied(UUID jobId) {
        documentJobRepository.findById(jobId).ifPresent(job -> {
            job.setAppliedAt(java.time.OffsetDateTime.now());
            documentJobRepository.save(job);
            auditService.record("AI_DOCUMENT_APPLIED", "AiDocumentJob", String.valueOf(jobId),
                    "Valeurs extraites reportées dans le dossier après validation humaine", null);
        });
    }

    public List<Map<String, Object>> extractionTypes() {
        return aiClient.extractionTypes();
    }

    // ------------------------------------------------------------------ rédaction

    public Map<String, Object> draft(String draftType, Map<String, Object> context, String extra) {
        Map<String, Object> body = new HashMap<>();
        body.put("draftType", draftType);
        body.put("context", context);
        body.put("extra", extra);
        Map<String, Object> result = aiClient.draft(body);
        auditService.record("AI_DRAFT", "Draft", draftType, "Brouillon IA généré", null);
        return result;
    }

    public Map<String, Object> summarize(String kind, Map<String, Object> context) {
        Map<String, Object> body = new HashMap<>();
        body.put("kind", kind);
        body.put("context", context);
        return aiClient.summarize(body);
    }

    public List<Map<String, Object>> draftTypes() {
        return aiClient.draftTypes();
    }

    // ------------------------------------------------------------------ conversation

    /** Crée le fil si besoin et renvoie son identifiant, avec l'historique déjà échangé. */
    @Transactional
    public ConversationContext openConversation(UUID conversationId, String firstQuestion) {
        UUID userId = currentUserIdOrNull();

        AiConversation conversation = null;
        if (conversationId != null) {
            conversation = (userId == null
                    ? conversationRepository.findById(conversationId)
                    : conversationRepository.findByIdAndUserId(conversationId, userId)).orElse(null);
        }
        if (conversation == null) {
            conversation = conversationRepository.save(AiConversation.builder()
                    .tenantId(TenantContext.getTenantId())
                    .userId(userId)
                    .title(truncate(firstQuestion, 180))
                    .build());
        }

        List<Map<String, String>> history = new ArrayList<>();
        for (AiMessage m : messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())) {
            history.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }
        if (history.size() > MAX_HISTORY) {
            history = history.subList(history.size() - MAX_HISTORY, history.size());
        }
        return new ConversationContext(conversation.getId(), history);
    }

    @Transactional
    public void recordExchange(UUID conversationId, String question, String answer, String model) {
        try {
            messageRepository.save(AiMessage.builder()
                    .conversationId(conversationId).role("user").content(question).build());
            messageRepository.save(AiMessage.builder()
                    .conversationId(conversationId).role("assistant")
                    .content(answer == null ? "" : answer).model(model).build());
            conversationRepository.findById(conversationId).ifPresent(conversationRepository::save);
        } catch (Exception e) {
            // L'historique est un confort : son échec ne doit pas casser la réponse déjà rendue.
            log.warn("AI conversation persistence failed (ignored): {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> messages(UUID conversationId) {
        UUID userId = currentUserIdOrNull();
        boolean allowed = userId != null
                ? conversationRepository.findByIdAndUserId(conversationId, userId).isPresent()
                : conversationRepository.findById(conversationId).map(c -> c.getUserId() == null).orElse(false);
        if (!allowed) {
            throw new IllegalArgumentException("Conversation introuvable");
        }
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(m -> Map.<String, Object>of(
                        "role", m.getRole(),
                        "content", m.getContent(),
                        "createdAt", m.getCreatedAt()))
                .toList();
    }

    public record ConversationContext(UUID conversationId, List<Map<String, String>> history) {
    }

    // ------------------------------------------------------------------ utilitaires

    public String currentRoleOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "")).orElse(null);
    }

    public static UUID currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null
                || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        try {
            return UUID.fromString(auth.getPrincipal().toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static Boolean asBoolean(Object value) {
        return value instanceof Boolean b ? b : null;
    }

    private static String asString(Object value) {
        return value == null ? null : truncate(String.valueOf(value), 80);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String flat = value.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max);
    }
}
