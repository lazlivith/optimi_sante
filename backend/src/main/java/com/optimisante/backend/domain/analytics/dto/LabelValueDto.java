package com.optimisante.backend.domain.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Point catégoriel générique : un libellé, une valeur principale, une valeur secondaire. */
@Getter
@Builder
public class LabelValueDto {
    private String label;
    private BigDecimal value;
    private BigDecimal secondary;
}
