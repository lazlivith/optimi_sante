package com.optimisante.backend.domain.marketing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NewsletterSubscribeRequestDto {

    @NotBlank(message = "L'adresse email est obligatoire.")
    @Email(message = "Adresse email invalide.")
    @Size(max = 255)
    private String email;

    /**
     * Libelle exact accepte, envoye par l'interface.
     *
     * <p>Il vient du client parce que c'est le texte reellement affiche a la personne. Le
     * serveur refuse une inscription sans lui : sans preuve de ce a quoi elle a consenti,
     * l'inscription ne vaut rien.</p>
     */
    @NotBlank(message = "Le consentement est obligatoire.")
    @Size(max = 2000)
    private String consentText;
}
