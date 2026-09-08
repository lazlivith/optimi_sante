package com.optimisante.backend.domain.audit.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AuditStatsDto {

    private int windowDays;
    private long totalEntries;
    private long entriesLast24h;
    private List<LabelCount> byAction;
    private List<LabelCount> topActors;

    @Getter
    @Builder
    public static class LabelCount {
        private String label;
        private long total;
    }
}
