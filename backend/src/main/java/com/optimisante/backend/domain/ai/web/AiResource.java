package com.optimisante.backend.domain.ai.web;

import com.optimisante.backend.domain.ai.service.AiClient;
import com.optimisante.backend.domain.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * API IA exposée au front. Le navigateur ne parle jamais directement au service Python :
 * tout transite par ici, ce qui garantit l'authentification, le contexte multi-tenant et la
 * journalisation d'audit.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiResource {

    /** Un flux SSE monopolise un thread : pool borné et nommé pour rester diagnosticable. */
    private static final ExecutorService STREAM_POOL = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "ai-chat-stream");
        t.setDaemon(true);
        return t;
    });

    private final AiService aiService;
    private final AiClient aiClient;

    // ------------------------------------------------------------------ état

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Map<String, Object> status() {
        return aiService.status();
    }

    // ------------------------------------------------------------------ extraction

    @GetMapping("/extract/types")
    public List<Map<String, Object>> extractionTypes() {
        return aiService.extractionTypes();
    }

    /**
     * Analyse un document et renvoie une <b>proposition</b> de champs. Rien n'est enregistré
     * dans le dossier : l'écran appelant fait valider les valeurs par un humain.
     */
    @PostMapping(value = "/extract", consumes = "multipart/form-data")
    public Map<String, Object> extract(@RequestParam("file") MultipartFile file,
                                       @RequestParam(defaultValue = "OTHER") String documentType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier fourni.");
        }
        return aiService.extract(file, documentType);
    }

    /** Confirme que les valeurs proposées ont été reportées dans le dossier. */
    @PostMapping("/extract/{jobId}/applied")
    public ResponseEntity<Void> markApplied(@PathVariable UUID jobId) {
        aiService.markApplied(jobId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ rédaction

    @GetMapping("/draft/types")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public List<Map<String, Object>> draftTypes() {
        return aiService.draftTypes();
    }

    public record DraftRequest(String draftType, Map<String, Object> context, String extra) {
    }

    @PostMapping("/draft")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Map<String, Object> draft(@RequestBody DraftRequest request) {
        return aiService.draft(
                request.draftType() == null ? "GENERIC_EMAIL" : request.draftType(),
                request.context(), request.extra());
    }

    public record SummarizeRequest(String kind, Map<String, Object> context) {
    }

    @PostMapping("/summarize")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Map<String, Object> summarize(@RequestBody SummarizeRequest request) {
        return aiService.summarize(request.kind() == null ? "GENERIC" : request.kind(), request.context());
    }

    // ------------------------------------------------------------------ chat

    public record ChatRequest(String question, UUID conversationId) {
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request,
                                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        String question = requireQuestion(request);
        AiService.ConversationContext context =
                aiService.openConversation(request.conversationId(), question);

        Map<String, Object> body = payload(question, context.history());
        Map<String, Object> result = aiClient.chat(body, auth);

        String answer = String.valueOf(result.getOrDefault("answer", ""));
        aiService.recordExchange(context.conversationId(), question, answer,
                String.valueOf(result.get("model")));

        Map<String, Object> response = new HashMap<>(result);
        response.put("conversationId", context.conversationId());
        return response;
    }

    /** Réponse en flux : les morceaux arrivent au fil de la génération (événement {@code delta}). */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request,
                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        String question = requireQuestion(request);
        AiService.ConversationContext context =
                aiService.openConversation(request.conversationId(), question);
        Map<String, Object> body = payload(question, context.history());

        SseEmitter emitter = new SseEmitter(180_000L);
        StringBuilder collected = new StringBuilder();

        STREAM_POOL.submit(() -> {
            try {
                emitter.send(SseEmitter.event().name("conversation")
                        .data(Map.of("conversationId", context.conversationId())));

                aiClient.streamChat(body, auth, (event, data) -> {
                    try {
                        if ("delta".equals(event)) {
                            collected.append(data);
                        }
                        emitter.send(SseEmitter.event().name(event).data(data));
                    } catch (IOException e) {
                        throw new IllegalStateException("Client déconnecté", e);
                    }
                });

                aiService.recordExchange(context.conversationId(), question, collected.toString(), null);
                emitter.complete();
            } catch (Exception e) {
                log.warn("AI chat stream failed: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data(
                            e instanceof IllegalStateException && e.getMessage() != null
                                    ? e.getMessage()
                                    : "Assistant momentanément indisponible."));
                } catch (Exception ignored) {
                    // Le client est déjà parti : rien à signaler.
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    @GetMapping("/conversations/{id}/messages")
    public List<Map<String, Object>> messages(@PathVariable UUID id) {
        return aiService.messages(id);
    }

    // ------------------------------------------------------------------ interne

    private static String requireQuestion(ChatRequest request) {
        String question = request == null || request.question() == null ? "" : request.question().trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("La question est vide.");
        }
        if (question.length() > 4000) {
            throw new IllegalArgumentException("Question trop longue (4000 caractères maximum).");
        }
        return question;
    }

    private Map<String, Object> payload(String question, List<Map<String, String>> history) {
        Map<String, Object> body = new HashMap<>();
        body.put("question", question);
        body.put("history", history);
        body.put("userRole", aiService.currentRoleOrNull());
        return body;
    }
}
