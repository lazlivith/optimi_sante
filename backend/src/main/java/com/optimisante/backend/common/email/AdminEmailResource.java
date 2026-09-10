package com.optimisante.backend.common.email;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import com.optimisante.backend.config.security.AnyAdmin;
import com.optimisante.backend.config.security.PlatformAdmin;

/**
 * Espace « Emails » de l'administration : journal des envois, renvoi d'identifiants,
 * envoi de test et statistiques.
 *
 * <p><b>Périmètre transverse.</b> Le journal des envois relève de la supervision de
 * l'infrastructure, pas d'un métier : un administrateur du négoce doit pouvoir vérifier
 * qu'un email est bien parti sans dépendre de son homologue mobilité. D'où
 * {@link AnyAdmin} à la place de {@code @MobilityAdmin}.</p>
 *
 * <p><b>Exception : le renvoi d'identifiants.</b> Cet endpoint ne consulte rien, il
 * <i>régénère le mot de passe</i> du compte destinataire. C'est une opération d'identité,
 * pas de messagerie : ouverte à tous les administrateurs, elle offrirait à chacun un moyen
 * de reprendre la main sur n'importe quel compte ayant reçu ses identifiants — y compris un
 * autre administrateur. Elle reste donc sous {@link PlatformAdmin}.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/emails")
@RequiredArgsConstructor
@AnyAdmin
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
    @PlatformAdmin
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
