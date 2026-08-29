package com.optimisante.backend.domain.promotion.dto;

import com.optimisante.backend.domain.promotion.entity.DiscountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class PromoCodeResponseDto {
    private UUID id;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private Integer maxUses;
    private Integer usedCount;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private Boolean isActive;
    private OffsetDateTime createdAt;
}
