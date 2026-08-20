package com.optimisante.backend.domain.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterB2BRequestDTO {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String tenantCode;

    // CompanyProfile specific fields
    @NotBlank
    private String companyName;

    @NotBlank
    private String siretFiness;

    private String vatNumber;

    @NotBlank
    private String billingAddress;
}
