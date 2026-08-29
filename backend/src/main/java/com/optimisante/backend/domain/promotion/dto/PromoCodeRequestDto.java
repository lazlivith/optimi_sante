package com.optimisante.backend.domain.promotion.dto;

import com.optimisante.backend.domain.promotion.entity.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class PromoCodeRequestDto {
    @NotBlank
    private String code;

    @NotNull
    private DiscountType discountType;

    @NotNull
    private BigDecimal discountValue;

    private BigDecimal minOrderAmount;
    private Integer maxUses;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
}
