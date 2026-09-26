package com.optimisante.backend.domain.blog.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une publication telle que le site et l'administration la lisent.
 *
 * @param isEvent      vrai dès que {@code eventStartsOn} est renseigné. Calculé ici plutôt
 *                     que côté React : la règle appartient au domaine, et trois écrans la
 *                     réinterpréteraient chacun à leur façon.
 * @param isPublished  le public ne reçoit que des publications en ligne ; le champ n'a de
 *                     sens que pour l'écran d'administration, qui montre aussi les brouillons.
 */
public record BlogPostResponseDto(
        UUID id,
        String title,
        String slug,
        String excerpt,
        String content,
        String coverImageUrl,
        String eventLocation,
        LocalDate eventStartsOn,
        LocalDate eventEndsOn,
        boolean isEvent,
        boolean isPublished,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt
) {}
