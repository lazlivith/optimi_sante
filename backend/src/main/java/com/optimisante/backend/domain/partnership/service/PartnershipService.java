package com.optimisante.backend.domain.partnership.service;

import com.optimisante.backend.common.email.EmailService;
import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.document.service.PdfGeneratorService;
import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.PartnerProfileRepository;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import com.optimisante.backend.domain.identity.repository.UserRepository;
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

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnershipService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PartnershipRequestRepository partnershipRequestRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PartnerProfileRepository partnerProfileRepository;
    private final StorageService storageService;
    private final PdfGeneratorService pdfGeneratorService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    /**
     * Génère (à la volée, comme les autres documents du projet) le modèle vierge de convention
     * de partenariat et renvoie une URL de téléchargement.
     */
    public String getConventionTemplateUrl() {
        byte[] pdfBytes = pdfGeneratorService.generatePartnershipConventionPdf(java.util.Map.of());
        String publicId = storageService.uploadGeneratedPdf(pdfBytes, "docs/partnership-templates",
                "modele-convention-partenariat");
        return storageService.generatePresignedOrSignedUrl(publicId, 60);
    }

    @Transactional
    public PartnershipRequestResponseDto submitRequest(
            String institutionName, String finessAccreditation, String contactPersonName,
            String contactEmail, String contactPhone, String address, MultipartFile conventionFile) {

        String fileKey = storageService.uploadFile(conventionFile, "docs/partnership-requests");

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

        return toDto(partnershipRequestRepository.saveAndFlush(request));
    }

    @Transactional(readOnly = true)
    public List<PartnershipRequestResponseDto> listRequests() {
        return partnershipRequestRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public PartnershipRequestResponseDto approveRequest(UUID requestId) {
        PartnershipRequest request = partnershipRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Partnership request not found"));

        if (request.getStatus() != PartnershipStatus.PENDING) {
            throw new IllegalStateException("Cette demande a déjà été traitée");
        }

        if (userRepository.existsByEmail(request.getContactEmail())) {
            throw new IllegalStateException("Un compte existe déjà avec cet email");
        }

        UUID tenantId = requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        String temporaryPassword = generateTemporaryPassword();

        User user = User.builder()
                .tenant(tenant)
                .email(request.getContactEmail())
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .role(Role.CENTRE_FORMATION)
                .isActive(true)
                .build();
        user = userRepository.save(user);

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

        request.setStatus(PartnershipStatus.APPROVED);
        request.setCreatedUser(user);
        request.setReviewedAt(OffsetDateTime.now());
        request = partnershipRequestRepository.save(request);

        emailService.sendCredentialsEmail(
                request.getContactEmail(), request.getContactPersonName(), temporaryPassword, "Partenaire CHU");

        log.info("Demande de partenariat {} approuvée, compte {} créé", requestId, user.getEmail());
        return toDto(request);
    }

    @Transactional
    public PartnershipRequestResponseDto rejectRequest(UUID requestId, String reason) {
        PartnershipRequest request = partnershipRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Partnership request not found"));

        if (request.getStatus() != PartnershipStatus.PENDING) {
            throw new IllegalStateException("Cette demande a déjà été traitée");
        }

        request.setStatus(PartnershipStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setReviewedAt(OffsetDateTime.now());
        return toDto(partnershipRequestRepository.save(request));
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
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
