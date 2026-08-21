package com.optimisante.backend.domain.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadCaptureResponseDto {
    private String brochureDownloadUrl;
}
