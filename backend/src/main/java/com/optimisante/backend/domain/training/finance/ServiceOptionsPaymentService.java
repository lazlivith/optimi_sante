package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.document.service.PdfGeneratorService;
import com.optimisante.backend.domain.identity.repository.DoctorProfileRepository;
import com.optimisante.backend.domain.orders.service.StripePaymentService;
import com.optimisante.backend.domain.training.dto.ServiceOptionDtos.ServiceCheckoutView;
import com.optimisante.backend.domain.training.entity.*;
import com.optimisante.backend.domain.training.repository.EnrollmentDocumentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentServiceOptionRepository;
import com.optimisante.backend.domain.training.service.ServiceOptionService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reglement des services souscrits : assurance, hebergement, transport.
 *
 * <p>Un seul encaissement couvre tous les services retenus et non encore regles — le medecin
 * paie son panier en une fois. La recette est entierement acquise a la plateforme : ces
 * prestations sont montees par l'agence, qui paie ses propres prestataires.</p>
 *
 * <p><b>Le paiement ne fait pas avancer la candidature.</b> Contrairement aux frais de
 * formation, aucun statut de dossier n'est touche : un sejour se deroule aussi bien sans
 * hebergement souscrit par la plateforme.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceOptionsPaymentService {

    /** Portee par la session Stripe : elle designe la ligne exacte a solder au retour. */
    public static final String METADATA_PAYMENT_ID = "service_options_payment_id";

    private static final DateTimeFormatter FORMAT_JOUR =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentServiceOptionRepository subscriptionRepository;
    private final EnrollmentPaymentRepository paymentRepository;
    private final EnrollmentPaymentService enrollmentPaymentService;
    private final EnrollmentDocumentRepository documentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final StripePaymentService stripePaymentService;
    private final PdfGeneratorService pdfGeneratorService;

    @Value("${app.mail.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * Ouvre le paiement de tous les services retenus et non regles.
     *
     * <p>Les souscriptions sont rattachees a la ligne <b>des maintenant</b>, alors qu'elles
     * restent {@code SELECTED}. Sans ce rattachement, un medecin qui souscrirait un service
     * supplementaire pendant qu'il paie verrait ce service solde par un encaissement qui ne le
     * couvrait pas : le montant preleve et les services rendus divergeraient.</p>
     */
    @Transactional
    public ServiceCheckoutView createServiceCheckoutSession(UUID enrollmentId, UUID doctorId) {
        Enrollment dossier = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable"));
        if (!dossier.getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Ce dossier ne vous appartient pas.");
        }

        List<EnrollmentServiceOption> aRegler = subscriptionRepository
                .findByEnrollmentIdAndStatus(enrollmentId, ServiceOptionStatus.SELECTED);
        if (aRegler.isEmpty()) {
            throw new IllegalStateException("Aucun service en attente de règlement sur ce dossier.");
        }

        BigDecimal total = ServiceOptionService.total(aRegler);
        EnrollmentPayment payment =
                enrollmentPaymentService.openServiceOptionsPayment(enrollmentId, total);

        aRegler.forEach(s -> s.setPayment(payment));
        subscriptionRepository.saveAll(aRegler);

        String intitule = aRegler.size() == 1
                ? aRegler.get(0).getLabel()
                : aRegler.size() + " services — " + dossier.getSession().getTraining().getTitle();

        try {
            Session session = stripePaymentService.createElementsCheckoutSessionForNewCustomer(
                    enrollmentId,
                    total,
                    dossier.getDoctor().getEmail(),
                    frontendBaseUrl + "/doctor/enrollments/" + enrollmentId,
                    "Services Optimi Santé — " + intitule,
                    Map.of(
                            "payment_type", "SERVICE_OPTIONS",
                            "enrollment_id", enrollmentId.toString(),
                            METADATA_PAYMENT_ID, payment.getId().toString()));

            payment.setStripeCheckoutSessionId(session.getId());
            paymentRepository.save(payment);

            log.info("Paiement de {} service(s) ouvert pour le dossier {} : {} EUR",
                    aRegler.size(), enrollmentId, total);

            return ServiceCheckoutView.builder()
                    .clientSecret(session.getClientSecret())
                    .amount(total)
                    .currency(payment.getCurrency())
                    .build();

        } catch (StripeException e) {
            log.error("Échec de création de la session Stripe (services) pour le dossier {}",
                    enrollmentId, e);
            // Meme distinction qu'au paiement de formation : un tarif mal configure se corrige
            // cote plateforme, une panne ne se corrige pas de la meme facon.
            if (e instanceof com.stripe.exception.InvalidRequestException) {
                throw new IllegalStateException(
                        "Le paiement n'a pas pu être ouvert : " + e.getStripeError().getMessage()
                        + " Vérifiez les tarifs des services proposés.");
            }
            throw new IllegalStateException("Le service de paiement est momentanément indisponible.");
        }
    }

    /**
     * Solde les services couverts par une session Stripe confirmee.
     *
     * <p><b>Idempotent</b> : un webhook rejoue — ce qui arrive normalement en production — ne
     * doit ni encaisser deux fois ni regenerer l'attestation. La ligne visee est nommee par la
     * metadonnee de la session ; l'index unique sur {@code stripe_checkout_session_id} (V43)
     * sert de second rempart cote base.</p>
     */
    @Transactional
    public void confirmServicePayment(UUID enrollmentId, Session session) {
        UUID paymentId = lirePaymentId(session);
        EnrollmentPayment payment = paymentId != null
                ? paymentRepository.findById(paymentId).orElse(null)
                : null;

        if (payment == null) {
            // Sans la ligne, on ne sait pas quels services ce paiement couvrait : les solder au
            // jugé facturerait peut-etre autre chose que ce qui a ete preleve. Le paiement est
            // reel, il est donc signale bruyamment pour un traitement manuel.
            log.error("Paiement de services confirmé pour le dossier {} sans ligne identifiable "
                    + "(session {}). Encaissement à rattacher manuellement.",
                    enrollmentId, session.getId());
            return;
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Webhook rejoué pour le paiement de services {} : déjà soldé, ignoré.", paymentId);
            return;
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(OffsetDateTime.now());
        payment.setStripeCheckoutSessionId(session.getId());
        payment.setStripePaymentIntentId(session.getPaymentIntent());
        paymentRepository.save(payment);

        List<EnrollmentServiceOption> couverts = subscriptionRepository.findByPaymentId(paymentId)
                .stream()
                .filter(s -> s.getStatus() == ServiceOptionStatus.SELECTED)
                .toList();
        OffsetDateTime maintenant = OffsetDateTime.now();
        couverts.forEach(s -> {
            s.setStatus(ServiceOptionStatus.PAID);
            s.setPaidAt(maintenant);
        });
        subscriptionRepository.saveAll(couverts);

        log.info("{} service(s) réglé(s) sur le dossier {} : {} EUR, 100 % plateforme",
                couverts.size(), enrollmentId, payment.getGrossAmount());

        deposerAttestation(enrollmentId, payment, couverts);
    }

    /**
     * Depose l'attestation de souscription au coffre du dossier.
     *
     * <p>Les erreurs sont avalees volontairement : l'encaissement est deja constate. Laisser une
     * panne de rendu annuler la transaction ferait perdre la trace d'un paiement reel pour un
     * document qui, lui, peut etre reproduit. Meme regle qu'a la confirmation d'inscription.</p>
     *
     * <p><b>Ce document n'est pas un certificat d'assurance.</b> OptimiSante n'est pas
     * l'assureur : l'attestation etablit que les services ont ete souscrits aupres de la
     * plateforme et regles. Le certificat de l'assureur est emis par celui-ci, en son nom.</p>
     */
    private void deposerAttestation(UUID enrollmentId, EnrollmentPayment payment,
                                    List<EnrollmentServiceOption> services) {
        if (services.isEmpty()) {
            return;
        }
        try {
            Enrollment dossier = enrollmentRepository.findById(enrollmentId).orElseThrow();
            Training formation = dossier.getSession().getTraining();

            List<Map<String, String>> lignes = services.stream()
                    .map(s -> Map.of(
                            "type", ServiceOptionService.libelleType(s.getOptionType()),
                            "label", s.getLabel(),
                            "price", s.getUnitPrice().toPlainString()))
                    .toList();

            Map<String, Object> donnees = new HashMap<>();
            donnees.put("doctorName", nomMedecin(dossier));
            donnees.put("doctorEmail", dossier.getDoctor().getEmail());
            donnees.put("trainingTitle", formation.getTitle());
            donnees.put("institutionName", formation.getPartnerProfile().getInstitutionName());
            donnees.put("services", lignes);
            donnees.put("total", payment.getGrossAmount().toPlainString());
            donnees.put("reference", "SRV-" + payment.getId().toString().substring(0, 8).toUpperCase());
            donnees.put("issuedAt", OffsetDateTime.now().format(FORMAT_JOUR));
            // L'assurance appelle une reserve que les autres services n'appellent pas.
            donnees.put("hasInsurance", services.stream()
                    .anyMatch(s -> s.getOptionType() == ServiceOptionType.INSURANCE));

            String publicId = pdfGeneratorService.generateAndUploadPdf(
                    "attestation-souscription", donnees, "docs/enrollments",
                    "souscription-" + payment.getId());

            documentRepository.save(EnrollmentDocument.builder()
                    .enrollment(dossier)
                    .documentType(DocumentType.SERVICE_SUBSCRIPTION)
                    .cloudinaryPublicId(publicId)
                    // Emise par la plateforme, pas deposee par le candidat : rien a verifier.
                    .isVerified(true)
                    .build());

            log.info("Attestation de souscription déposée au dossier {}", enrollmentId);
        } catch (Exception e) {
            log.error("Attestation de souscription non générée pour le dossier {} : {}",
                    enrollmentId, e.getMessage());
        }
    }

    private UUID lirePaymentId(Session session) {
        Map<String, String> metadata = session.getMetadata();
        String brut = metadata == null ? null : metadata.get(METADATA_PAYMENT_ID);
        if (brut == null || brut.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(brut);
        } catch (IllegalArgumentException e) {
            log.error("Métadonnée {} illisible sur la session {} : {}",
                    METADATA_PAYMENT_ID, session.getId(), brut);
            return null;
        }
    }

    private String nomMedecin(Enrollment dossier) {
        return doctorProfileRepository.findByUserId(dossier.getDoctor().getId())
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse(dossier.getDoctor().getEmail());
    }
}
