package com.optimisante.backend.domain.partnership.service;

import com.optimisante.backend.common.storage.DossierStockage;
import com.optimisante.backend.common.email.EmailService;
import com.optimisante.backend.domain.document.service.DocumentLinkService;
import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.document.service.PdfGeneratorService;
import com.optimisante.backend.domain.identity.entity.CompanyProfile;
import com.optimisante.backend.domain.identity.entity.FacilityType;
import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.CompanyProfileRepository;
import com.optimisante.backend.domain.identity.repository.PartnerProfileRepository;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.partnership.dto.PartnershipApprovalRequestDto;
import com.optimisante.backend.domain.partnership.dto.PartnershipRequestResponseDto;
import com.optimisante.backend.domain.partnership.entity.PartnershipRequest;
import com.optimisante.backend.domain.partnership.entity.PartnershipStatus;
import com.optimisante.backend.domain.partnership.repository.PartnershipRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.optimisante.backend.common.security.TemporaryPasswordGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnershipService {


    private final PartnershipRequestRepository partnershipRequestRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PartnerProfileRepository partnerProfileRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final StorageService storageService;
    private final DocumentLinkService documentLinkService;
    private final PdfGeneratorService pdfGeneratorService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * Génère (à la volée, comme les autres documents du projet) le modèle vierge de convention
     * de partenariat et renvoie une URL de téléchargement.
     */
    public String getConventionTemplateUrl() {
        byte[] pdfBytes = pdfGeneratorService.generatePartnershipConventionPdf(java.util.Map.of());
        // Modèle vierge, non nominatif : clé stable, sinon chaque prospect qui le télécharge
        // laisserait un fichier de plus dans le stockage.
        String publicId = storageService.uploadPublicTemplate(pdfBytes, DossierStockage.PARTENARIATS_MODELES,
                "modele-convention-partenariat");
        return documentLinkService.lienDeTelechargement(publicId);
    }

    @Transactional
    public PartnershipRequestResponseDto submitRequest(
            String institutionName, String finessAccreditation, String contactPersonName,
            String contactEmail, String contactPhone, String address, MultipartFile conventionFile) {

        if (conventionFile == null || conventionFile.isEmpty()) {
            throw new IllegalArgumentException(
                    "La convention de partenariat signée est obligatoire.");
        }

        // Sans ce filtre, une erreur du fournisseur de stockage remontait telle quelle a
        // l'etablissement candidat — « Missing required parameter - api_key » — un message
        // qui ne lui dit rien et qu'il ne peut pas corriger. La cause technique reste dans
        // les logs, cote exploitant, ou elle est reellement actionnable.
        String fileKey;
        try {
            fileKey = storageService.uploadFile(conventionFile, DossierStockage.PARTENARIATS_DEMANDES);
        } catch (Exception e) {
            log.error("Depot de convention impossible pour {} : {}", institutionName, e.getMessage(), e);
            throw new IllegalStateException(
                    "L'envoi de votre convention a échoué. Merci de réessayer dans quelques "
                            + "instants ; si le problème persiste, contactez-nous directement.");
        }

        PartnershipRequest request = PartnershipRequest.builder()
                .institutionName(institutionName)
                .finessAccreditation(finessAccreditation)
                .contactPersonName(contactPersonName)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .address(address)
                .conventionFileKey(fileKey)
                .status(PartnershipStatus.PENDING)
                .build();

        PartnershipRequestResponseDto dto = toDto(partnershipRequestRepository.saveAndFlush(request));
        eventPublisher.publishEvent(
                new com.optimisante.backend.domain.notification.event.NotificationEvents.PartnershipRequestSubmitted(
                        institutionName, contactEmail));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<PartnershipRequestResponseDto> listRequests() {
        return partnershipRequestRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Approuve une demande de partenariat et ouvre l'espace de l'établissement.
     *
     * <p><b>Une adresse déjà connue ne bloque plus l'approbation.</b> Auparavant, toute
     * demande dont l'e-mail appartenait déjà à un compte était refusée ici : l'administrateur
     * ne pouvait rien faire, la demande restait en attente indéfiniment, et l'établissement
     * n'obtenait jamais ses accès. Or un responsable de CHU est souvent déjà client de la
     * boutique — c'est le cas normal, pas l'exception.</p>
     *
     * <p><b>Un compte n'a qu'un rôle.</b> Convertir un compte client ou médecin en compte
     * partenaire lui retire donc l'espace qu'il avait. Ce n'est pas une décision à prendre en
     * silence : elle exige une confirmation explicite de l'administrateur, et le refus nomme
     * précisément ce qui serait perdu. Un compte d'administration n'est jamais converti.</p>
     *
     * <p><b>La remise accompagne le partenariat.</b> Un partenaire achète aussi du matériel :
     * son compte reçoit un profil d'entreprise portant le taux saisi à l'approbation. Le taux
     * est une négociation, pas un barème — il est donc demandé, et non déduit.</p>
     */
    @Transactional
    public PartnershipRequestResponseDto approveRequest(UUID requestId,
                                                       PartnershipApprovalRequestDto decision) {
        PartnershipRequest request = partnershipRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Partnership request not found"));

        if (request.getStatus() != PartnershipStatus.PENDING) {
            throw new IllegalStateException("Cette demande a déjà été traitée");
        }

        final BigDecimal remise = decision == null || decision.getB2bDiscountRate() == null
                ? BigDecimal.ZERO : decision.getB2bDiscountRate();
        final boolean conversionConfirmee = decision != null && decision.isConfirmerConversion();

        UUID tenantId = requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User existant = userRepository.findByEmail(request.getContactEmail()).orElse(null);
        String temporaryPassword = null;
        User user;

        if (existant == null) {
            temporaryPassword = TemporaryPasswordGenerator.generate();
            user = userRepository.save(User.builder()
                    .tenant(tenant)
                    .email(request.getContactEmail())
                    .passwordHash(passwordEncoder.encode(temporaryPassword))
                    .role(Role.CENTRE_FORMATION)
                    .isActive(true)
                    .build());
        } else {
            user = reutiliserCompteExistant(existant, conversionConfirmee);
        }

        if (partnerProfileRepository.findByUserId(user.getId()).isEmpty()) {
            PartnerProfile profile = PartnerProfile.builder()
                    .user(user)
                    .institutionName(request.getInstitutionName())
                    .finessAccreditation(request.getFinessAccreditation())
                    .contactPersonName(request.getContactPersonName())
                    .contactEmail(request.getContactEmail())
                    .contactPhone(request.getContactPhone())
                    .address(request.getAddress())
                    .isVerified(true)
                    .build();
            partnerProfileRepository.save(profile);
        }

        appliquerRemise(user, request, remise);

        request.setStatus(PartnershipStatus.APPROVED);
        request.setCreatedUser(user);
        request.setReviewedAt(OffsetDateTime.now());
        request = partnershipRequestRepository.save(request);

        if (temporaryPassword != null) {
            // `user` transmis pour tracer le destinataire (renvoi possible depuis l'admin).
            emailService.sendCredentialsEmail(
                    request.getContactEmail(), request.getContactPersonName(), temporaryPassword,
                    "Partenaire CHU", user);
        } else {
            // Le compte existait : la personne a deja un mot de passe qu'elle a choisi. En
            // generer un nouveau l'invaliderait et mettrait un identifiant valide dans une
            // boite mail sans necessite. On dit ce qui a change, et le seul geste a faire.
            emailService.sendHtml(request.getContactEmail(),
                    "Optimi Santé — Votre espace partenaire est ouvert",
                    "Votre espace partenaire est ouvert",
                    "<p>Bonjour " + HtmlUtils.htmlEscape(request.getContactPersonName()) + ",</p>"
                    + "<p>Votre demande de partenariat pour <strong>"
                    + HtmlUtils.htmlEscape(request.getInstitutionName())
                    + "</strong> est acceptée. Votre espace partenaire est ouvert.</p>"
                    + "<p>Reconnectez-vous avec <strong>vos identifiants habituels</strong> : "
                    + "vous y publierez vos sessions et suivrez les candidatures reçues.</p>");
        }

        eventPublisher.publishEvent(
                new com.optimisante.backend.domain.notification.event.NotificationEvents.PartnerAccountValidated(
                        user.getId(), user.getEmail(), request.getContactPersonName(), request.getInstitutionName()));

        log.info("Demande de partenariat {} approuvée, compte {} créé", requestId, user.getEmail());
        return toDto(request);
    }

    /**
     * Le compte existe deja : qui peut devenir partenaire, et a quelle condition ?
     *
     * <p>Un compte d'administration n'est jamais converti — ce serait transformer un
     * administrateur en partenaire et lui retirer la plateforme. Un compte deja partenaire est
     * repris tel quel. Les autres — client particulier, client professionnel, medecin —
     * perdent leur espace actuel, et la conversion exige donc une confirmation explicite.</p>
     */
    private User reutiliserCompteExistant(User existant, boolean conversionConfirmee) {
        Role role = existant.getRole();

        if (role == Role.CENTRE_FORMATION) {
            return existant;
        }

        if (role == Role.ADMIN || role == Role.SUPER_ADMIN
                || role == Role.ADMIN_ECOMMERCE || role == Role.ADMIN_MOBILITE) {
            throw new IllegalStateException(
                    "Cette adresse appartient à un compte d'administration de la plateforme. "
                            + "Elle ne peut pas devenir un compte partenaire : demandez à "
                            + "l'établissement une adresse de contact dédiée.");
        }

        if (!conversionConfirmee) {
            throw new IllegalStateException(
                    "Cette adresse appartient déjà à un compte « " + libelle(role) + " ». "
                            + "L'approuver convertira ce compte en compte partenaire : il perdra "
                            + "l'accès à son espace actuel, ses commandes et son historique restant "
                            + "intacts. Confirmez la conversion pour continuer, ou demandez une "
                            + "adresse de contact dédiée à l'établissement.");
        }

        existant.setRole(Role.CENTRE_FORMATION);
        log.info("Compte {} converti de {} en partenaire, sur confirmation de l'administrateur",
                existant.getEmail(), role);
        return userRepository.save(existant);
    }

    private static String libelle(Role role) {
        return switch (role) {
            case CLIENT_B2C -> "client particulier";
            case CLIENT_B2B -> "client professionnel";
            case MEDECIN -> "médecin";
            default -> role.name();
        };
    }

    /**
     * La remise boutique accordée à l'établissement.
     *
     * <p>Un partenaire est aussi un client : il achète du matériel. Le taux vit sur le profil
     * d'entreprise, comme pour tout compte professionnel — c'est la même source que celle que
     * lit le calcul des prix, et non une seconde notion de remise qui finirait par diverger.</p>
     *
     * <p>Le profil d'entreprise existe peut-être déjà, si le compte était client professionnel :
     * on met alors son taux à jour plutôt que d'en créer un second, ce que l'unicité par compte
     * interdit de toute façon.</p>
     */
    private void appliquerRemise(User user, PartnershipRequest request, BigDecimal remise) {
        CompanyProfile profil = companyProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> CompanyProfile.builder()
                        .user(user)
                        .companyName(request.getInstitutionName())
                        // Le numero FINESS identifie l'etablissement de sante : c'est le seul
                        // identifiant dont la demande dispose, et le champ est obligatoire.
                        .taxId(request.getFinessAccreditation() != null
                                ? request.getFinessAccreditation() : "FINESS non communiqué")
                        .facilityType(FacilityType.HOSPITAL)
                        .contactName(request.getContactPersonName())
                        .billingAddress(request.getAddress())
                        .build());
        profil.setB2bDiscountRate(remise);
        companyProfileRepository.save(profil);
    }

    @Transactional
    public PartnershipRequestResponseDto rejectRequest(UUID requestId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Un motif est obligatoire pour refuser une demande de partenariat.");
        }
        PartnershipRequest request = partnershipRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Partnership request not found"));

        if (request.getStatus() != PartnershipStatus.PENDING) {
            throw new IllegalStateException("Cette demande a déjà été traitée");
        }

        request.setStatus(PartnershipStatus.REJECTED);
        request.setRejectionReason(reason.trim());
        request.setReviewedAt(OffsetDateTime.now());
        PartnershipRequest saved = partnershipRequestRepository.save(request);

        // Le candidat n'a pas encore de compte : l'e-mail est le seul canal qui l'atteigne.
        // Sans lui, une demande refusee restait sans reponse, indefiniment.
        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.PartnershipRequestRejected(
                saved.getInstitutionName(), saved.getContactEmail(), saved.getRejectionReason()));

        return toDto(saved);
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        return tenantId;
    }

    private PartnershipRequestResponseDto toDto(PartnershipRequest r) {
        return PartnershipRequestResponseDto.builder()
                .id(r.getId())
                .institutionName(r.getInstitutionName())
                .finessAccreditation(r.getFinessAccreditation())
                .contactPersonName(r.getContactPersonName())
                .contactEmail(r.getContactEmail())
                .contactPhone(r.getContactPhone())
                .address(r.getAddress())
                .conventionFileKey(r.getConventionFileKey())
                .status(r.getStatus().name())
                .rejectionReason(r.getRejectionReason())
                .createdAt(r.getCreatedAt())
                .reviewedAt(r.getReviewedAt())
                .build();
    }
}
