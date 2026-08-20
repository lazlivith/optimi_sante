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
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials")); // Replace with proper exception later

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (!user.getIsActive()) {
            throw new RuntimeException("User account is inactive");
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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Tenant tenant = tenantRepository.findByCode(request.getTenantCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User user = User.builder()
                .tenant(tenant)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.CLIENT_B2C)
                .isActive(true)
                .build();

        userRepository.save(user);
    }

    @Transactional
    public void registerB2B(RegisterB2BRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Tenant tenant = tenantRepository.findByCode(request.getTenantCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User user = User.builder()
                .tenant(tenant)
                .email(request.getEmail())
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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Tenant tenant = tenantRepository.findByCode(request.getTenantCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        User user = User.builder()
                .tenant(tenant)
                .email(request.getEmail())
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
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("User not authenticated");
        }
        
        java.util.UUID userId = java.util.UUID.fromString(authentication.getPrincipal().toString());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        return UserProfileDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantCode(user.getTenant().getCode())
                .build();
    }
}
