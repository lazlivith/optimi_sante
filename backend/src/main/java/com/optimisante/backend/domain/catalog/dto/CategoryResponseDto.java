package com.optimisante.backend.domain.catalog.dto;

import java.util.List;
import java.util.UUID;

public record CategoryResponseDto(
        UUID id,
        String name,
        String slug,
        List<CategoryResponseDto> subcategories
) {}
