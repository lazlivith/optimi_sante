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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import com.optimisante.backend.common.security.TemporaryPasswordGenerator;

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


    private final DoctorApplicationRepository doctorApplicationRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final StripePaymentService stripePaymentService;
    private final com.optimisante.backend.domain.training.finance.EnrollmentPaymentService enrollmentPaymentService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /** Pour relire la candidature une fois verrouillée — voir {@link #confirmPayment}. */
    @PersistenceContext
    private EntityManager entityManager;

    /** Repli, appliqué quand la formation ne fixe pas ses propres frais. */
    @Value("${app.doctor-application.fee-amount}")
    private BigDecimal feeAmount;

    /**
     * Frais de dossier applicables à une session.
     *
     * <p>La formation prime, la valeur globale sert de repli. {@code null} sur la formation
     * veut dire « pas de tarif propre », pas « gratuit » : traiter les deux de la même façon
     * rendrait toute nouvelle formation gratuite par omission.</p>
     */
    private BigDecimal resolveApplicationFee(TrainingSession session) {
        BigDecimal propre = session.getTraining() == null
                ? null
                : session.getTraining().getApplicationFee();
        return propre != null ? propre : feeAmount;
    }

    @Value("${app.mail.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public DoctorApplicationResponseDto submitApplication(DoctorApplicationRequestDto dto) {
        String email = dto.getEmail().trim().toLowerCase();
        userRepository.findByEmail(email).ifPresent(this::verifierCandidatureDepuisCompteExistant);

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
                .feeAmount(resolveApplicationFee(session))
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
                    application.getFeeAmount(),
                    email,
                    returnUrl,
                    "Frais de dossier - Candidature " + session.getTraining().getTitle(),
                    Map.of("type", "DOCTOR_APPLICATION"));

            application.setStripeCheckoutSessionId(stripeSession.getId());
            doctorApplicationRepository.save(application);

            eventPublisher.publishEvent(
                    new com.optimisante.backend.domain.notification.event.NotificationEvents.DoctorApplicationSubmitted(
                            dto.getFirstName() + " " + dto.getLastName(), dto.getMedicalSpecialty(), email));

            return toDto(application, stripeSession.getClientSecret());
        } catch (StripeException e) {
            log.error("Échec de création de la session Stripe pour la candidature {}", application.getId(), e);
            throw new RuntimeException("Impossible de créer le paiement pour le moment, réessayez plus tard.");
        }
    }

    /**
     * Une candidature porte sur une adresse qui a déjà un compte. Qui peut continuer ?
     *
     * <p><b>Règle métier.</b> Un client particulier (B2C) devient médecin en candidatant à une
     * formation : son compte reçoit l'espace médecin, il garde ses identifiants. Un client
     * professionnel (B2B) garde son compte entreprise intact — sa remise est liée à son rôle, et
     * un compte n'a qu'un rôle — et candidate avec une autre adresse, qui deviendra un compte
     * médecin distinct.</p>
     *
     * <p><b>Le client doit être connecté à CE compte.</b> Sans cette condition, n'importe qui
     * pourrait déposer une candidature au nom d'une adresse client, la payer, et changer le rôle
     * d'un compte qui n'est pas le sien.</p>
     *
     * <p>Avant cette règle, toute adresse connue était refusée : un client B2C n'avait aucun moyen
     * de devenir médecin — la candidature le rejetait, et l'inscription directe lui répondait 403.</p>
     */
    private void verifierCandidatureDepuisCompteExistant(User compte) {
        switch (compte.getRole()) {
            case CLIENT_B2C -> {
                if (!compte.getId().equals(utilisateurConnecte())) {
                    throw new IllegalStateException("Un compte client existe déjà avec cet email. "
                            + "Connectez-vous à ce compte pour candidater : l'espace médecin s'y ajoutera.");
                }
            }
            case CLIENT_B2B -> throw new IllegalStateException("Cette adresse appartient à un compte "
                    + "professionnel (B2B), qui reste inchangé. Pour devenir médecin, candidatez avec une "
                    + "autre adresse email : elle deviendra votre compte médecin.");
            case MEDECIN -> throw new IllegalStateException("Vous avez déjà un compte médecin. "
                    + "Connectez-vous et inscrivez-vous directement depuis la page de la formation.");
            default -> throw new IllegalStateException(
                    "Un compte existe déjà avec cet email. Candidatez avec une autre adresse.");
        }
    }

    /** Identifiant du compte connecté, ou {@code null} pour un visiteur. */
    private static UUID utilisateurConnecte() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        try {
            return UUID.fromString(auth.getPrincipal().toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Déclenché par le webhook Stripe (checkout.session.completed), et par la page de retour du
     * candidat via {@link DoctorApplicationPaymentReconciler} quand le webhook tarde. Idempotent :
     * un second appel sur une candidature déjà PAID ne recrée rien. La lecture verrouille la ligne,
     * pour que deux appels simultanés ne créent pas deux comptes.
     */
    @Transactional
    public void confirmPayment(UUID applicationId, String stripeCustomerId) {
        DoctorApplication application = doctorApplicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new RuntimeException("Doctor application not found: " + applicationId));

        // RELIRE, une fois le verrou obtenu. Le projet garde `spring.jpa.open-in-view` actif :
        // un même cache d'entités sert toute la requête HTTP. La page de retour a déjà lu la
        // candidature avant d'arriver ici ; après avoir attendu le verrou, Hibernate renvoyait
        // cette copie en cache — « PENDING_PAYMENT » — au lieu de l'état que la requête verrouillée
        // venait de lire en base. Mesuré : cinq confirmations simultanées ont toutes créé le
        // compte ; la contrainte d'unicité de l'e-mail en a rejeté quatre, après que chacune eut
        // tenté d'envoyer des identifiants.
        entityManager.refresh(application);

        if (application.getStatus() == DoctorApplicationStatus.PAID) {
            log.info("Candidature {} déjà marquée payée, événement webhook ignoré (idempotence)", applicationId);
            return;
        }

        // Un compte existe déjà pour cette adresse : c'est un client particulier qui devient
        // médecin (voir verifierCandidatureDepuisCompteExistant, qui n'a laissé passer que lui).
        User existant = userRepository.findByEmail(application.getEmail()).orElse(null);
        if (existant != null && existant.getRole() != Role.CLIENT_B2C) {
            // Le compte a changé de nature entre la candidature et le paiement. Le paiement est
            // encaissé : on ne l'annule pas, mais on ne touche à aucun compte sans règle claire.
            log.error("Candidature {} payée, mais l'adresse {} désigne désormais un compte {} : aucun "
                    + "compte modifié ni créé — suivi manuel admin requis.",
                    applicationId, application.getEmail(), existant.getRole());
            application.setStatus(DoctorApplicationStatus.PAID);
            application.setPaidAt(OffsetDateTime.now());
            doctorApplicationRepository.save(application);
            return;
        }
        final boolean promotion = existant != null;

        String temporaryPassword = null;
        User user;
        if (promotion) {
            // Le client garde son compte, ses commandes et son mot de passe : seul le rôle change.
            // Aucun identifiant n'est donc généré ni envoyé.
            existant.setRole(Role.MEDECIN);
            if (existant.getStripeCustomerId() == null) {
                existant.setStripeCustomerId(stripeCustomerId);
            }
            user = userRepository.save(existant);
        } else {
            temporaryPassword = TemporaryPasswordGenerator.generate();

            // Le Customer Stripe a été créé automatiquement par Stripe pendant le paiement
            // (customer_creation=always, cf. submitApplication) — on le rattache au compte tout
            // juste créé pour que le médecin retrouve sa carte enregistrée à son prochain paiement.
            user = User.builder()
                    .tenant(application.getTenant())
                    .email(application.getEmail())
                    .passwordHash(passwordEncoder.encode(temporaryPassword))
                    .role(Role.MEDECIN)
                    .isActive(true)
                    .stripeCustomerId(stripeCustomerId)
                    .build();
            user = userRepository.save(user);
        }

        if (doctorProfileRepository.findByUserId(user.getId()).isEmpty()) {
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
        }

        try {
            EnrollmentResponseDto enrollmentDto = enrollmentService.createEnrollment(
                    new EnrollmentRequestDto(application.getSession().getId()), user.getId());
            application.setCreatedEnrollment(enrollmentRepository.getReferenceById(enrollmentDto.getId()));

            // Reflet comptable des frais de dossier déjà encaissés par Stripe sur cette
            // candidature. L'encaissement reste porté par doctor_applications (porte d'entrée
            // inchangée) ; le registre en garde une trace pour que l'administration dispose
            // d'un état financier unique plutôt que de deux tables à réconcilier.
            // Idempotent, et volontairement non bloquant : un échec d'écriture comptable ne
            // doit jamais annuler un paiement déjà encaissé ni la création du compte.
            try {
                enrollmentPaymentService.reflectDossierFee(
                        enrollmentDto.getId(), application.getFeeAmount(), application.getPaidAt());
            } catch (Exception reflectError) {
                log.error("Frais de dossier non reflétés au registre pour la candidature {} — "
                        + "à régulariser manuellement.", applicationId, reflectError);
            }
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

        // `user` est transmis pour tracer le destinataire dans email_logs : c'est ce qui
        // permet à l'admin de renvoyer les identifiants depuis l'espace « Emails ».
        //
        // Envoi APRÈS l'enregistrement, et seulement s'il réussit. Envoyé avant, un mot de passe
        // partait même quand la transaction était ensuite annulée : le candidat recevait des
        // identifiants qui n'avaient jamais été enregistrés, et ne pouvait pas se connecter.
        final String destinataire = application.getEmail();
        final String nomComplet = application.getFirstName() + " " + application.getLastName();
        final String motDePasse = temporaryPassword;
        final User compte = user;
        final String formation = application.getSession().getTraining().getTitle();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (promotion) {
                    // Pas de mot de passe à transmettre : on dit ce qui a changé, et le seul geste
                    // à faire — se reconnecter, pour que la session porte le nouveau rôle.
                    emailService.sendHtml(destinataire,
                            "Optimi Santé — Votre espace médecin est ouvert",
                            "Votre espace médecin est ouvert",
                            "<p>Bonjour " + org.springframework.web.util.HtmlUtils.htmlEscape(nomComplet) + ",</p>"
                            + "<p>Votre paiement des frais de dossier pour la formation <strong>"
                            + org.springframework.web.util.HtmlUtils.htmlEscape(formation)
                            + "</strong> est confirmé. Votre compte client devient votre compte médecin.</p>"
                            + "<p>Reconnectez-vous avec <strong>vos identifiants habituels</strong> : "
                            + "votre espace médecin s'ouvrira, et vous y suivrez votre dossier.</p>");
                } else {
                    emailService.sendCredentialsEmail(destinataire, nomComplet, motDePasse, "Médecin", compte);
                }
            }
        });

        eventPublisher.publishEvent(
                new com.optimisante.backend.domain.notification.event.NotificationEvents.DoctorAccountValidated(
                        user.getId(), user.getEmail(),
                        application.getFirstName() + " " + application.getLastName()));

        log.info("Candidature {} payée, compte {} {} et inscrit à la session {}",
                applicationId, user.getEmail(), promotion ? "promu médecin" : "créé",
                application.getSession().getId());
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

    private DoctorApplicationResponseDto toDto(DoctorApplication application, String clientSecret) {
        return DoctorApplicationResponseDto.builder()
                .id(application.getId())
                .status(application.getStatus())
                .feeAmount(application.getFeeAmount())
                .trainingTitle(application.getSession().getTraining().getTitle())
                .createdAt(application.getCreatedAt())
                .paidAt(application.getPaidAt())
                .clientSecret(clientSecret)
                // Un compte plus ancien que la candidature ne peut pas avoir été créé par elle :
                // c'est un compte client promu. Déduit, plutôt que stocké, pour ne pas ajouter de
                // colonne à une table de paiement pour un simple choix de message.
                .existingAccountPromoted(application.getCreatedUser() != null
                        && application.getCreatedUser().getCreatedAt() != null
                        && application.getCreatedAt() != null
                        && application.getCreatedUser().getCreatedAt().toInstant()
                                .isBefore(application.getCreatedAt().toInstant()))
                .build();
    }
}
