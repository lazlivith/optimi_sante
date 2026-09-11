package com.optimisante.backend.domain.governance.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.audit.service.AuditService;
import com.optimisante.backend.domain.governance.entity.RgpdRequest;
import com.optimisante.backend.domain.governance.repository.RgpdRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Traitement des demandes RGPD sur une personne concernée (identifiée par son email) :
 * <ul>
 *   <li><b>Export</b> (droit d'accès / portabilité) : rassemble toutes les données personnelles
 *       connues (compte, profil, commandes, dossiers, leads, candidatures) en un objet JSON.</li>
 *   <li><b>Anonymisation</b> (droit à l'effacement) : brouille irréversiblement les PII tout en
 *       conservant les lignes structurantes (commandes, inscriptions) pour l'intégrité
 *       référentielle et les obligations comptables/légales. Réservé au SUPER_ADMIN.</li>
 * </ul>
 * Tout passe par du SQL natif : il faut pouvoir lire/écrire des lignes que le filtre global
 * {@code @SQLRestriction} masque, et ne pas déclencher d'effets de bord d'entités JPA.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RgpdService {

    private final JdbcTemplate jdbc;
    private final RgpdRequestRepository rgpdRequestRepository;
    private final AuditService auditService;

    // ------------------------------------------------------------------ lookup / export

    @Transactional(readOnly = true)
    public Map<String, Object> lookup(String email) {
        Map<String, Object> user = findUserByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("Aucun compte connu pour cet email : " + email);
        }
        UUID userId = UUID.fromString(String.valueOf(user.get("id")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("account", user);
        out.put("ordersCount", count("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId));
        out.put("enrollmentsCount", count("SELECT COUNT(*) FROM enrollments WHERE doctor_id = ?", userId));
        out.put("leadsCount", count("SELECT COUNT(*) FROM prospect_leads WHERE lower(email) = lower(?)", email));
        out.put("applicationsCount", count("SELECT COUNT(*) FROM doctor_applications WHERE lower(email) = lower(?)", email));
        out.put("alreadyAnonymized", user.get("anonymized_at") != null);
        return out;
    }

    @Transactional
    public Map<String, Object> export(String email) {
        Map<String, Object> account = findUserByEmail(email);
        if (account == null) {
            throw new IllegalArgumentException("Aucun compte connu pour cet email : " + email);
        }
        UUID userId = UUID.fromString(String.valueOf(account.get("id")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", java.time.OffsetDateTime.now().toString());
        payload.put("subjectEmail", email);
        payload.put("account", account);
        payload.put("doctorProfile", first("SELECT * FROM doctor_profiles WHERE user_id = ?", userId));
        payload.put("companyProfile", first("SELECT * FROM company_profiles WHERE user_id = ?", userId));
        payload.put("partnerProfile", first("SELECT * FROM partner_profiles WHERE user_id = ?", userId));
        payload.put("orders", jdbc.queryForList(
                "SELECT order_number, payment_method, payment_status, status, total_amount, created_at "
                        + "FROM orders WHERE user_id = ? ORDER BY created_at DESC", userId));
        payload.put("enrollments", jdbc.queryForList(
                "SELECT e.id, e.status, e.submitted_at, s.location, s.start_date, s.end_date "
                        + "FROM enrollments e JOIN training_sessions s ON s.id = e.session_id "
                        + "WHERE e.doctor_id = ? ORDER BY e.submitted_at DESC", userId));
        payload.put("prospectLeads", jdbc.queryForList(
                "SELECT email, first_name, last_name, phone_whatsapp, country, specialty, downloaded_at "
                        + "FROM prospect_leads WHERE lower(email) = lower(?)", email));
        payload.put("doctorApplications", jdbc.queryForList(
                "SELECT email, first_name, last_name, phone_whatsapp, country_of_residence, medical_specialty, "
                        + "status, fee_amount, created_at, paid_at FROM doctor_applications WHERE lower(email) = lower(?)", email));

        recordRequest("EXPORT", userId, email, "DONE",
                "Export des données personnelles généré (" + payload.size() + " sections).");
        auditService.record("RGPD_EXPORT", "User", userId.toString(),
                "Export RGPD des données de " + email, null);
        return payload;
    }

    // ------------------------------------------------------------------ anonymize

    @Transactional
    public Map<String, Object> anonymize(String email) {
        Map<String, Object> account = findUserByEmail(email);
        if (account == null) {
            throw new IllegalArgumentException("Aucun compte connu pour cet email : " + email);
        }
        UUID userId = UUID.fromString(String.valueOf(account.get("id")));
        String role = String.valueOf(account.get("role"));

        if (account.get("anonymized_at") != null) {
            throw new IllegalStateException("Ce compte est déjà anonymisé.");
        }
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
            throw new IllegalStateException("Un compte d'administration ne peut pas être anonymisé par cette procédure.");
        }

        String token = userId.toString().substring(0, 8);
        String anonEmail = "anon+" + token + "@anonymized.local";
        String anonText = "ANONYMISÉ";

        int usersRows = jdbc.update("""
                UPDATE users
                   SET email = ?, phone = NULL,
                       password_hash = 'ANONYMIZED-' || gen_random_uuid(),
                       is_active = false,
                       deleted_at = COALESCE(deleted_at, now()),
                       anonymized_at = now()
                 WHERE id = ?
                """, anonEmail, userId);

        int doctorRows = jdbc.update("""
                UPDATE doctor_profiles
                   SET first_name = 'Anonymisé', last_name = 'Anonymisé', phone_whatsapp = ?,
                       country_of_residence = ?, medical_specialty = ?, medical_council_number = NULL,
                       current_hospital = NULL, passport_number = NULL
                 WHERE user_id = ?
                """, anonText, anonText, anonText, userId);

        int companyRows = jdbc.update("""
                UPDATE company_profiles
                   SET company_name = 'Anonymisé', siret_finess = ?, vat_number = NULL, billing_address = ?
                 WHERE user_id = ?
                """, anonText, anonText, userId);

        int partnerRows = jdbc.update("""
                UPDATE partner_profiles
                   SET contact_person_name = 'Anonymisé', contact_email = ?, contact_phone = ?, address = ?
                 WHERE user_id = ?
                """, anonEmail, anonText, anonText, userId);

        int leadRows = jdbc.update("""
                UPDATE prospect_leads
                   SET email = ?, first_name = 'Anonymisé', last_name = 'Anonymisé', phone_whatsapp = ?
                 WHERE lower(email) = lower(?)
                """, anonEmail, anonText, email);

        int applicationRows = jdbc.update("""
                UPDATE doctor_applications
                   SET email = ?, first_name = 'Anonymisé', last_name = 'Anonymisé', phone_whatsapp = ?,
                       medical_council_number = NULL, current_hospital = NULL, passport_number = NULL
                 WHERE lower(email) = lower(?)
                """, anonEmail, anonText, email);

        long ordersKept = count("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId);
        long enrollmentsKept = count("SELECT COUNT(*) FROM enrollments WHERE doctor_id = ?", userId);

        String summary = String.format(
                "Anonymisé : users=%d, doctor_profiles=%d, company_profiles=%d, partner_profiles=%d, "
                        + "prospect_leads=%d, doctor_applications=%d. Conservés (intégrité/finance) : "
                        + "orders=%d, enrollments=%d.",
                usersRows, doctorRows, companyRows, partnerRows, leadRows, applicationRows,
                ordersKept, enrollmentsKept);

        recordRequest("ANONYMIZE", userId, email, "DONE", summary);
        auditService.record("RGPD_ANONYMIZE", "User", userId.toString(),
                "Anonymisation RGPD de " + email + " -> " + anonEmail, summary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "DONE");
        result.put("anonymizedEmail", anonEmail);
        result.put("summary", summary);
        return result;
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> findUserByEmail(String email) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, email, phone, role, is_active, created_at, gdpr_consent_at, anonymized_at, "
                        + "stripe_customer_id FROM users WHERE lower(email) = lower(?)", email);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> first(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private void recordRequest(String type, UUID subjectId, String subjectEmail, String status, String summary) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            UUID requestedBy = null;
            String requestedByEmail = null;
            if (auth != null && auth.getPrincipal() != null) {
                try {
                    requestedBy = UUID.fromString(auth.getPrincipal().toString());
                } catch (Exception ignored) {
                    // principal non-UUID : on laisse null
                }
            }
            rgpdRequestRepository.save(RgpdRequest.builder()
                    .tenantId(TenantContext.getTenantId())
                    .requestType(type)
                    .subjectUserId(subjectId)
                    .subjectEmail(subjectEmail)
                    .status(status)
                    .requestedByUserId(requestedBy)
                    .requestedByEmail(requestedByEmail)
                    .resultSummary(summary)
                    .build());
        } catch (Exception e) {
            log.warn("RGPD request trace not saved (ignored): {}", e.getMessage());
        }
    }
}
