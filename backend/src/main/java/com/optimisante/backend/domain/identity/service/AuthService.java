package com.optimisante.backend.domain.identity.service;

import com.optimisante.backend.config.security.JwtTokenProvider;
import com.optimisante.backend.domain.identity.dto.*;
import com.optimisante.backend.domain.identity.entity.*;
import com.optimisante.backend.domain.identity.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public JwtResponseDTO login(LoginRequestDTO request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new com.optimisante.backend.config.exception.AuthenticationFailedException("Identifiants incorrects"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new com.optimisante.backend.config.exception.AuthenticationFailedException("Identifiants incorrects");
        }

        if (!user.getIsActive()) {
            throw new com.optimisante.backend.config.exception.AuthenticationFailedException("Compte désactivé");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        return JwtResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
    }

    @Transactional
    public void registerB2C(RegisterB2CRequestDTO request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Un compte existe déjà avec cet email");
        }

        Tenant tenant = tenantRepository.findByCode(request.getTenantCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User user = User.builder()
                .tenant(tenant)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.CLIENT_B2C)
                .isActive(true)
                .build();

        userRepository.save(user);
    }

    @Transactional
    public void registerB2B(RegisterB2BRequestDTO request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Un compte existe déjà avec cet email");
        }

        Tenant tenant = tenantRepository.findByCode(request.getTenantCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User user = User.builder()
                .tenant(tenant)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.CLIENT_B2B)
                .isActive(true)
                .build();
        userRepository.save(user);

        CompanyProfile companyProfile = CompanyProfile.builder()
                .user(user)
                .companyName(request.getCompanyName())
                .siretFiness(request.getSiretFiness())
                .vatNumber(request.getVatNumber())
                .billingAddress(request.getBillingAddress())
                .b2bDiscountRate(java.math.BigDecimal.ZERO)
                .build();
        companyProfileRepository.save(companyProfile);
    }

    @Transactional
    public void registerDoctor(RegisterDoctorRequestDTO request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Un compte existe déjà avec cet email");
        }

        Tenant tenant = tenantRepository.findByCode(request.getTenantCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User user = User.builder()
                .tenant(tenant)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.MEDECIN)
                .isActive(true)
                .build();
        userRepository.save(user);

        DoctorProfile doctorProfile = DoctorProfile.builder()
                .user(user)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneWhatsapp(request.getPhoneWhatsapp())
                .countryOfResidence(request.getCountryOfResidence())
                .medicalSpecialty(request.getMedicalSpecialty())
                .medicalCouncilNumber(request.getMedicalCouncilNumber())
                .currentHospital(request.getCurrentHospital())
                .passportNumber(request.getPassportNumber())
                .build();
        doctorProfileRepository.save(doctorProfile);
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getProfile() {
        java.util.UUID userId = currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfileDTO.UserProfileDTOBuilder builder = UserProfileDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantCode(user.getTenant().getCode());

        if (user.getRole() == Role.MEDECIN) {
            doctorProfileRepository.findByUserId(userId).ifPresent(profile -> builder
                    .firstName(profile.getFirstName())
                    .lastName(profile.getLastName())
                    .phoneWhatsapp(profile.getPhoneWhatsapp())
                    .countryOfResidence(profile.getCountryOfResidence())
                    .medicalSpecialty(profile.getMedicalSpecialty())
                    .medicalCouncilNumber(profile.getMedicalCouncilNumber())
                    .currentHospital(profile.getCurrentHospital()));
        } else {
            // Le champ "Téléphone" est affiché pour tous les rôles côté frontend ; en dehors
            // du cas MEDECIN (porté par DoctorProfile), il est stocké directement sur User.
            builder.phoneWhatsapp(user.getPhone());
            if (user.getRole() == Role.CLIENT_B2B) {
                companyProfileRepository.findByUserId(userId).ifPresent(profile -> builder
                        .companyName(profile.getCompanyName())
                        .siretFiness(profile.getSiretFiness())
                        .vatNumber(profile.getVatNumber())
                        .billingAddress(profile.getBillingAddress()));
            }
        }

        return builder.build();
    }

    @Transactional
    public UserProfileDTO updateProfile(UpdateProfileRequestDto dto) {
        java.util.UUID userId = currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRole() == Role.MEDECIN) {
            DoctorProfile profile = doctorProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("Doctor profile not found"));
            if (dto.firstName() != null) profile.setFirstName(dto.firstName());
            if (dto.lastName() != null) profile.setLastName(dto.lastName());
            if (dto.phoneWhatsapp() != null) profile.setPhoneWhatsapp(dto.phoneWhatsapp());
            if (dto.countryOfResidence() != null) profile.setCountryOfResidence(dto.countryOfResidence());
            if (dto.medicalSpecialty() != null) profile.setMedicalSpecialty(dto.medicalSpecialty());
            if (dto.medicalCouncilNumber() != null) profile.setMedicalCouncilNumber(dto.medicalCouncilNumber());
            if (dto.currentHospital() != null) profile.setCurrentHospital(dto.currentHospital());
            doctorProfileRepository.save(profile);
        } else {
            if (dto.phoneWhatsapp() != null) {
                user.setPhone(dto.phoneWhatsapp());
                userRepository.save(user);
            }
            if (user.getRole() == Role.CLIENT_B2B) {
                CompanyProfile profile = companyProfileRepository.findByUserId(userId)
                        .orElseThrow(() -> new RuntimeException("Company profile not found"));
                if (dto.companyName() != null) profile.setCompanyName(dto.companyName());
                if (dto.siretFiness() != null) profile.setSiretFiness(dto.siretFiness());
                companyProfileRepository.save(profile);
            }
        }

        return getProfile();
    }

    @Transactional
    public void changePassword(ChangePasswordRequestDto dto) {
        java.util.UUID userId = currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mot de passe actuel incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    private java.util.UUID currentUserId() {
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("User not authenticated");
        }
        return java.util.UUID.fromString(authentication.getPrincipal().toString());
    }
}
