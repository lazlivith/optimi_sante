package com.optimisante.backend.domain.document.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ConventionDataDto {
    private UUID conventionId;
    
    // Candidat / Professionnel
    private String doctorName;
    private String doctorSpecialty;
    private String doctorEmail;
    
    // Organisme de formation
    private String trainingCenterName;
    private String trainingCenterAddress;
    
    // Établissement / Financeur
    private String hospitalName;
    private String hospitalAddress;
    
    // Détails Session
    private String trainingTitle;
    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
    
    // Conditions financières
    private BigDecimal price;
    
    // Statuts de signature
    private boolean isDoctorSigned;
    private boolean isTrainingCenterSigned;
    private boolean isHospitalSigned;
    
    // Dates de signature (optionnel)
    private LocalDate doctorSignatureDate;
    private LocalDate trainingCenterSignatureDate;
    private LocalDate hospitalSignatureDate;
}
