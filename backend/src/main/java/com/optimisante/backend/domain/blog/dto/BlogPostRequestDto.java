package com.optimisante.backend.domain.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** Ce que la rédaction envoie depuis l'écran d'administration, à la création comme à la mise à jour. */
@Data
public class BlogPostRequestDto {

    @NotBlank(message = "Le titre est obligatoire.")
    @Size(max = 200, message = "Le titre ne peut pas dépasser 200 caractères.")
    private String title;

    @Size(max = 400, message = "Le chapô ne peut pas dépasser 400 caractères.")
    private String excerpt;

    @NotBlank(message = "Le contenu est obligatoire.")
    private String content;

    private String coverImageUrl;

    @Size(max = 160, message = "Le lieu ne peut pas dépasser 160 caractères.")
    private String eventLocation;

    /** Renseignée, elle fait de la publication un événement. */
    private LocalDate eventStartsOn;

    private LocalDate eventEndsOn;

    /**
     * Mise en ligne immédiate ou brouillon. Absent vaut brouillon : une publication qu'on
     * vient de commencer à écrire ne doit pas apparaître sur le site.
     */
    private Boolean published;
}
