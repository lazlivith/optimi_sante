package com.optimisante.backend.domain.doctorapplication.service;

import com.optimisante.backend.common.email.EmailService;
import com.optimisante.backend.domain.doctorapplication.dto.DoctorApplicationRequestDto;
import com.optimisante.backend.domain.doctorapplication.dto.DoctorApplicationResponseDto;
import com.optimisante.backend.domain.doctorapplication.entity.DoctorApplication;
import com.optimisante.backend.domain.doctorapplication.entity.DoctorApplicationStatus;
import com.optimisante.backend.domain.doctorapplication.repository.DoctorApplicationRepository;
import com.optimisante.backend.domain.identity.entity.DoctorProfile;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.DoctorProfileRepository;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.orders.service.StripePaymentService;
import com.optimisante.backend.domain.training.dto.EnrollmentRequestDto;
import com.optimisante.backend.domain.training.dto.EnrollmentResponseDto;
import com.optimisante.backend.domain.training.entity.SessionStatus;
import com.optimisante.backend.domain.training.entity.TrainingSession;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import com.optimisante.backend.domain.training.repository.TrainingSessionRepository;
import com.optimisante.backend.domain.training.service.EnrollmentService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Candidature médecin payante : remplace l'ancienne auto-inscription médecin. Un candidat sans
 * compte choisit une session de formation, renseigne son identité, puis paie des frais de dossier
 * fixes (indépendants du prix de la formation — cf. app.doctor-application.fee-amount). Le compte
 * MEDECIN + DoctorProfile + Enrollment (statut PENDING_REVIEW, pipeline existant inchangé) ne sont
 * créés qu'une fois le paiement confirmé par le webhook Stripe, jamais avant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorApplicationService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DoctorApplicationRepository doctorApplicationRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final StripePaymentService stripePaymentService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.doctor-application.fee-amount}")
    private BigDecimal feeAmount;

    @Value("${app.mail.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public DoctorApplicationResponseDto submitApplication(DoctorApplicationRequestDto dto) {
        String email = dto.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                    "Un compte existe déjà avec cet email. Connectez-vous pour candidater à une formation supplémentaire.");
        }

        Tenant tenant = tenantRepository.findByCode(dto.getTenantCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        TrainingSession session = trainingSessionRepository.findById(dto.getSessionId())
                .orElseThrow(() -> new RuntimeException("Session introuvable"));

        if (session.getStatus() != SessionStatus.OPEN || session.getAvailableSeats() <= 0) {
            throw new IllegalStateException("Cette session n'accepte plus de nouvelles candidatures");
        }

        DoctorApplication application = DoctorApplication.builder()
                .tenant(tenant)
                .session(session)
                .email(email)
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .phoneWhatsapp(dto.getPhoneWhatsapp())
                .countryOfResidence(dto.getCountryOfResidence())
                .medicalSpecialty(dto.getMedicalSpecialty())
                .medicalCouncilNumber(dto.getMedicalCouncilNumber())
                .currentHospital(dto.getCurrentHospital())
                .passportNumber(dto.getPassportNumber())
                .feeAmount(feeAmount)
                .status(DoctorApplicationStatus.PENDING_PAYMENT)
                .build();
        application = doctorApplicationRepository.saveAndFlush(application);

        try {
            // Pas encore de compte à ce stade (candidat anonyme) : Stripe crée lui-même un
            // Customer pendant le paiement (customer_creation=always). ui_mode "elements" —
            // le formulaire de carte s'affiche intégré dans la page, pas de redirection externe.
            String returnUrl = frontendBaseUrl + "/candidature/success?session_id={CHECKOUT_SESSION_ID}";
            Session stripeSession = stripePaymentService.createElementsCheckoutSessionForNewCustomer(
                    application.getId(),
                    feeAmount,
                    email,
                    returnUrl,
                    "Frais de dossier - Candidature " + session.getTraining().getTitle(),
                    Map.of("type", "DOCTOR_APPLICATION"));

            application.setStripeCheckoutSessionId(stripeSession.getId());
            doctorApplicationRepository.save(application);

            return toDto(application, stripeSession.getClientSecret());
        } catch (StripeException e) {
            log.error("Échec de création de la session Stripe pour la candidature {}", application.getId(), e);
            throw new RuntimeException("Impossible de créer le paiement pour le moment, réessayez plus tard.");
        }
    }

    /**
     * Déclenché par le webhook Stripe (checkout.session.completed). Idempotent : un événement
     * dupliqué sur une candidature déjà PAID ne recrée rien.
     */
    @Transactional
    public void confirmPayment(UUID applicationId, String stripeCustomerId) {
        DoctorApplication application = doctorApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Doctor application not found: " + applicationId));

        if (application.getStatus() == DoctorApplicationStatus.PAID) {
            log.info("Candidature {} déjà marquée payée, événement webhook ignoré (idempotence)", applicationId);
            return;
        }

        String temporaryPassword = generateTemporaryPassword();

        // Le Customer Stripe a été créé automatiquement par Stripe pendant le paiement
        // (customer_creation=always, cf. submitApplication) — on le rattache au compte tout
        // juste créé pour que le médecin retrouve sa carte enregistrée à son prochain paiement.
        User user = User.builder()
                .tenant(application.getTenant())
                .email(application.getEmail())
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .role(Role.MEDECIN)
                .isActive(true)
                .stripeCustomerId(stripeCustomerId)
                .build();
        user = userRepository.save(user);

        DoctorProfile profile = DoctorProfile.builder()
                .user(user)
                .firstName(application.getFirstName())
                .lastName(application.getLastName())
                .phoneWhatsapp(application.getPhoneWhatsapp())
                .countryOfResidence(application.getCountryOfResidence())
                .medicalSpecialty(application.getMedicalSpecialty())
                .medicalCouncilNumber(application.getMedicalCouncilNumber())
                .currentHospital(application.getCurrentHospital())
                .passportNumber(application.getPassportNumber())
                .build();
        doctorProfileRepository.save(profile);

        try {
            EnrollmentResponseDto enrollmentDto = enrollmentService.createEnrollment(
                    new EnrollmentRequestDto(application.getSession().getId()), user.getId());
            application.setCreatedEnrollment(enrollmentRepository.getReferenceById(enrollmentDto.getId()));
        } catch (Exception e) {
            // Edge case rare : les places se sont épuisées entre la soumission de la candidature
            // et la confirmation du paiement. Le paiement a bien eu lieu, le compte est créé quand
            // même (le médecin ne doit pas perdre ses identifiants payés) mais l'inscription à la
            // session échoue — nécessite un suivi manuel admin (changer de session ou rembourser).
            log.error("Paiement confirmé pour la candidature {} mais l'inscription à la session {} a échoué " +
                    "(places épuisées entre-temps ?) — compte créé quand même, suivi manuel admin requis.",
                    applicationId, application.getSession().getId(), e);
        }

        application.setCreatedUser(user);
        application.setStatus(DoctorApplicationStatus.PAID);
        application.setPaidAt(OffsetDateTime.now());
        doctorApplicationRepository.save(application);

        emailService.sendCredentialsEmail(application.getEmail(),
                application.getFirstName() + " " + application.getLastName(), temporaryPassword, "Médecin");

        log.info("Candidature {} payée, compte {} créé et inscrit à la session {}",
                applicationId, user.getEmail(), application.getSession().getId());
    }

    @Transactional(readOnly = true)
    public DoctorApplicationResponseDto getStatus(UUID applicationId) {
        DoctorApplication application = doctorApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Doctor application not found: " + applicationId));
        return toDto(application, null);
    }

    /**
     * Utilisé par la page de succès après redirection Stripe : le navigateur ne connaît que
     * l'identifiant de session Stripe (paramètre session_id), pas l'UUID interne de la candidature.
     */
    @Transactional(readOnly = true)
    public DoctorApplicationResponseDto getStatusByStripeCheckoutSessionId(String stripeCheckoutSessionId) {
        DoctorApplication application = doctorApplicationRepository.findByStripeCheckoutSessionId(stripeCheckoutSessionId)
                .orElseThrow(() -> new RuntimeException("Doctor application not found for Stripe session: " + stripeCheckoutSessionId));
        return toDto(application, null);
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private DoctorApplicationResponseDto toDto(DoctorApplication application, String clientSecret) {
        return DoctorApplicationResponseDto.builder()
                .id(application.getId())
                .status(application.getStatus())
                .feeAmount(application.getFeeAmount())
                .trainingTitle(application.getSession().getTraining().getTitle())
                .createdAt(application.getCreatedAt())
                .paidAt(application.getPaidAt())
                .clientSecret(clientSecret)
                .build();
    }
}
