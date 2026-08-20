package com.optimisante.backend.domain.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterB2CRequestDTO {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    // A tenantCode can be sent from the frontend to determine the target tenant
    @NotBlank
    private String tenantCode;
}
