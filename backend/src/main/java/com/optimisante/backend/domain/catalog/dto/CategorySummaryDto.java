package com.optimisante.backend.domain.catalog.dto;

import java.util.UUID;

public record CategorySummaryDto(
        UUID id,
        String name,
        String slug
) {}
