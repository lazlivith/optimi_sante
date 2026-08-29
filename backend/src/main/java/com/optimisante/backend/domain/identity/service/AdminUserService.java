package com.optimisante.backend.domain.identity.service;

import com.optimisante.backend.domain.identity.dto.AdminUserSummaryDto;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.CompanyProfileRepository;
import com.optimisante.backend.domain.identity.repository.DoctorProfileRepository;
import com.optimisante.backend.domain.identity.repository.PartnerProfileRepository;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final PartnerProfileRepository partnerProfileRepository;

    @Transactional(readOnly = true)
    public Page<AdminUserSummaryDto> listUsers(Role role, Pageable pageable) {
        Page<User> users = (role != null)
                ? userRepository.findByRole(role, pageable)
                : userRepository.findAll(pageable);
        return users.map(this::toSummaryDto);
    }

    @Transactional
    public AdminUserSummaryDto setUserActive(UUID userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsActive(active);
        return toSummaryDto(userRepository.save(user));
    }

    private AdminUserSummaryDto toSummaryDto(User user) {
        String displayName = switch (user.getRole()) {
            case MEDECIN -> doctorProfileRepository.findByUserId(user.getId())
                    .map(p -> p.getFirstName() + " " + p.getLastName())
                    .orElse(null);
            case CLIENT_B2B -> companyProfileRepository.findByUserId(user.getId())
                    .map(p -> p.getCompanyName())
                    .orElse(null);
            case CENTRE_FORMATION -> partnerProfileRepository.findByUserId(user.getId())
                    .map(p -> p.getInstitutionName())
                    .orElse(null);
            default -> null;
        };

        return AdminUserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .displayName(displayName)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
