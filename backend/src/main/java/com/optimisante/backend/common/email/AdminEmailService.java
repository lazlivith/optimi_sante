package com.optimisante.backend.common.email;

import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.common.security.TemporaryPasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pilotage des emails depuis l'espace d'administration : consultation du journal,
 * renvoi des identifiants, envoi de test et statistiques.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminEmailService {

    private final EmailLogRepository emailLogRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Jeton d'API Mailtrap (facultatif) : sans lui, seules les stats locales sont servies. */
    @Value("${app.mail.mailtrap.api-token:}")
    private String mailtrapApiToken;

    @Value("${app.mail.mailtrap.account-id:}")
    private String mailtrapAccountId;

    @Transactional(readOnly = true)
    public Page<EmailLogResponseDto> getLogs(String status, String type, String search, int page, int size) {
        EmailStatus statusFilter = parseEnum(EmailStatus.class, status);
        EmailType typeFilter = parseEnum(EmailType.class, type);
        String searchFilter = (search != null && !search.isBlank()) ? search.trim() : null;

        return emailLogRepository
                .search(statusFilter, typeFilter, searchFilter, PageRequest.of(page, size))
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public EmailStatsDto getStats() {
        EmailStatsDto.EmailStatsDtoBuilder builder = EmailStatsDto.builder()
                .totalSent(emailLogRepository.countByStatus(EmailStatus.SENT))
                .totalFailed(emailLogRepository.countByStatus(EmailStatus.FAILED))
                .mailtrapConnected(false)
                .dnsRecords(List.of());

        if (mailtrapApiToken == null || mailtrapApiToken.isBlank()
                || mailtrapAccountId == null || mailtrapAccountId.isBlank()) {
            return builder
                    .mailtrapError("Jeton d'API Mailtrap non configuré — statistiques locales uniquement.")
                    .build();
        }

        try {
            // Seul endpoint réellement exposé par l'API Mailtrap pour ce jeton : il décrit
            // le(s) domaine(s) d'envoi et l'état de chacun de leurs enregistrements DNS.
            Map<?, ?> body = RestClient.create()
                    .get()
                    .uri("https://mailtrap.io/api/accounts/{accountId}/sending_domains", mailtrapAccountId)
                    .header("Api-Token", mailtrapApiToken)
                    .retrieve()
                    .body(Map.class);

            List<?> domains = (body != null && body.get("data") instanceof List<?> list) ? list : List.of();
            if (domains.isEmpty()) {
                return builder.mailtrapConnected(true)
                        .mailtrapError("Aucun domaine d'envoi déclaré dans Mailtrap.")
                        .build();
            }

            // Un seul domaine d'envoi est utilisé par la plateforme : on prend le premier.
            Map<?, ?> domain = (Map<?, ?>) domains.get(0);
            List<EmailStatsDto.DnsRecordDto> records = readRecords(domain.get("dns_records"));

            return builder
                    .mailtrapConnected(true)
                    .sendingDomain(String.valueOf(domain.get("domain_name")))
                    .domainVerified(!records.isEmpty()
                            && records.stream().allMatch(r -> "passed".equalsIgnoreCase(r.getStatus())))
                    .dnsRecords(records)
                    .build();

        } catch (Exception e) {
            // La panne d'un service externe ne doit jamais casser l'écran d'administration :
            // on retombe proprement sur les seules données locales.
            log.warn("API Mailtrap indisponible : {}", e.getMessage());
            return builder.mailtrapError("API Mailtrap indisponible : " + e.getMessage()).build();
        }
    }

    private List<EmailStatsDto.DnsRecordDto> readRecords(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(r -> EmailStatsDto.DnsRecordDto.builder()
                        .name(str(r.get("name")))
                        .domain(str(r.get("domain")))
                        .type(str(r.get("type")))
                        .value(str(r.get("value")))
                        .status(str(r.get("status")))
                        .build())
                .toList();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Renvoie ses identifiants au destinataire d'un email tracé.
     *
     * ⚠️ Le mot de passe provisoire d'origine n'existe nulle part en clair côté serveur
     * (seul son hash bcrypt est stocké) : ce renvoi génère donc un NOUVEAU mot de passe
     * provisoire et met à jour le compte en conséquence. L'ancien cesse immédiatement
     * de fonctionner — comportement volontaire et affiché à l'administrateur.
     */
    @Transactional
    public void resendCredentials(UUID emailLogId) {
        EmailLog logEntry = emailLogRepository.findById(emailLogId)
                .orElseThrow(() -> new IllegalArgumentException("Email introuvable"));

        if (logEntry.getEmailType() != EmailType.CREDENTIALS) {
            throw new IllegalArgumentException("Seuls les emails d'identifiants peuvent être renvoyés.");
        }

        User user = logEntry.getRecipientUserId() == null ? null
                : userRepository.findById(logEntry.getRecipientUserId()).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException(
                    "Le compte destinataire n'est plus rattaché à cet envoi : renvoi impossible.");
        }

        String newPassword = TemporaryPasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        emailService.sendCredentialsEmail(
                user.getEmail(), displayName(user), newPassword, roleLabel(user.getRole()), user.getId());

        log.info("Identifiants renvoyés à {} (nouveau mot de passe provisoire généré)", user.getEmail());
    }

    @Transactional
    public void sendTestEmail(String recipient) {
        emailService.sendTestEmail(recipient);
    }

    // ---------------------------------------------------------------------------------

    private EmailLogResponseDto toDto(EmailLog entry) {
        // Le journal ne porte plus de clé étrangère vers `users` (V25) : on résout le
        // compte à la lecture. S'il a été supprimé entre-temps, la trace reste lisible
        // et le renvoi est simplement indisponible.
        User user = entry.getRecipientUserId() == null ? null
                : userRepository.findById(entry.getRecipientUserId()).orElse(null);
        return EmailLogResponseDto.builder()
                .id(entry.getId())
                .recipient(entry.getRecipient())
                .subject(entry.getSubject())
                .emailType(entry.getEmailType().name())
                .status(entry.getStatus().name())
                .errorMessage(entry.getErrorMessage())
                .sentAt(entry.getSentAt())
                .canResend(entry.getEmailType() == EmailType.CREDENTIALS && user != null)
                .recipientRole(user != null ? user.getRole().name() : null)
                .build();
    }

    private String displayName(User user) {
        if (user.getFirstName() != null || user.getLastName() != null) {
            return ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                    + (user.getLastName() != null ? user.getLastName() : "")).trim();
        }
        return user.getEmail();
    }

    private String roleLabel(Role role) {
        return switch (role) {
            case MEDECIN -> "Médecin";
            case CENTRE_FORMATION -> "Partenaire CHU";
            case CLIENT_B2B -> "Client professionnel";
            case CLIENT_B2C -> "Client";
            case ADMIN, SUPER_ADMIN -> "Administration";
        };
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Valeur de filtre invalide : " + value);
        }
    }
}
