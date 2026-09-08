package com.optimisante.backend.domain.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Entonnoir d'acquisition mobilité : brochure -> candidature -> paiement -> inscription -> visa. */
@Getter
@Builder
public class FunnelDto {

    private List<Step> steps;

    @Getter
    @Builder
    public static class Step {
        private String label;
        private long count;
        /** Pourcentage relatif à la première étape de l'entonnoir. */
        private double shareOfTop;
    }
}
