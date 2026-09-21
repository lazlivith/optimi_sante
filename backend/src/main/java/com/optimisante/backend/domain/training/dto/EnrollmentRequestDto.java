package com.optimisante.backend.domain.training.dto;

import com.optimisante.backend.domain.training.entity.RegistrationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Demande d'inscription à une session.
 *
 * <p>Le parcours est facultatif dans la requête : un appel qui l'ignore obtient le parcours
 * international, seul existant jusqu'ici. Les intégrations en place continuent donc de
 * fonctionner sans être modifiées.</p>
 */
public record EnrollmentRequestDto(

    @NotNull(message = "L'ID de la session est obligatoire")
    UUID sessionId,

    /** Absent : parcours international, par continuité avec l'existant. */
    RegistrationType registrationType,

    @Pattern(regexp = "^$|^[0-9]{9}$|^[0-9]{11}$",
             message = "Le numéro RPPS compte 11 chiffres, le numéro ADELI 9.")
    String rppsNumber
) {

    /** Constructeur de continuité : une inscription sans parcours précisé reste internationale. */
    public EnrollmentRequestDto(UUID sessionId) {
        this(sessionId, RegistrationType.INTERNATIONAL_VISA, null);
    }

    public RegistrationType parcours() {
        return registrationType == null ? RegistrationType.INTERNATIONAL_VISA : registrationType;
    }

    /** Numéro normalisé, ou {@code null} : une chaîne vide n'est pas un identifiant. */
    public String rppsNormalise() {
        return rppsNumber == null || rppsNumber.isBlank() ? null : rppsNumber.trim();
    }
}
