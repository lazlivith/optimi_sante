package com.optimisante.backend.common.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendTestEmailRequestDto {
    @NotBlank(message = "L'adresse du destinataire est obligatoire")
    @Email(message = "Format d'email invalide")
    private String recipient;
}
