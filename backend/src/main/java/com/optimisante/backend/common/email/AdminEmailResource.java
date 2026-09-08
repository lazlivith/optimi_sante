package com.optimisante.backend.common.email;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import com.optimisante.backend.config.security.MobilityAdmin;

/**
 * Espace « Emails » de l'administration : journal des envois, renvoi d'identifiants,
 * envoi de test et statistiques.
 */
@RestController
@RequestMapping("/api/v1/admin/emails")
@RequiredArgsConstructor
@MobilityAdmin
public class AdminEmailResource {

    private final AdminEmailService adminEmailService;

    @GetMapping
    public ResponseEntity<Page<EmailLogResponseDto>> listLogs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(adminEmailService.getLogs(status, type, search, page, size));
    }

    @GetMapping("/stats")
    public ResponseEntity<EmailStatsDto> getStats() {
        return ResponseEntity.ok(adminEmailService.getStats());
    }

    @PostMapping("/{id}/resend")
    public ResponseEntity<Map<String, String>> resend(@PathVariable UUID id) {
        adminEmailService.resendCredentials(id);
        return ResponseEntity.ok(Map.of(
                "message", "Identifiants renvoyés avec un nouveau mot de passe provisoire."));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> sendTest(@Valid @RequestBody SendTestEmailRequestDto request) {
        adminEmailService.sendTestEmail(request.getRecipient());
        return ResponseEntity.ok(Map.of(
                "message", "Email de test envoyé à " + request.getRecipient() + "."));
    }
}
