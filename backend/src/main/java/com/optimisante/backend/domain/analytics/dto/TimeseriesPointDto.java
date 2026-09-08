package com.optimisante.backend.domain.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class TimeseriesPointDto {
    private LocalDate day;
    private BigDecimal revenue;
    private long count;
}
