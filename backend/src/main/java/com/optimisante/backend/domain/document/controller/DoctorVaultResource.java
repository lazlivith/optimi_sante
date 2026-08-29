package com.optimisante.backend.domain.document.controller;

import com.optimisante.backend.domain.document.dto.DocumentItemDto;
import com.optimisante.backend.domain.orders.entity.Order;
import com.optimisante.backend.domain.orders.repository.OrderRepository;
import com.optimisante.backend.domain.training.entity.Enrollment;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctor/vault")
@RequiredArgsConstructor
public class DoctorVaultResource {

    private final OrderRepository orderRepository;
    private final EnrollmentRepository enrollmentRepository;

    @GetMapping
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<List<DocumentItemDto>> getDoctorVault(Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        List<DocumentItemDto> documents = new ArrayList<>();

        // Fetch Orders (Quotes/Invoices)
        List<Order> orders = orderRepository.findByUserId(doctorId);
        for (Order order : orders) {
            if (order.getDocumentS3Key() != null && !order.getDocumentS3Key().isBlank()) {
                documents.add(DocumentItemDto.builder()
                        .id(order.getId())
                        .title(order.getIsQuote() ? "Devis / Facture Pro Forma" : "Facture acquittée")
                        .type(order.getIsQuote() ? "QUOTE" : "INVOICE")
                        .date(order.getCreatedAt())
                        .status(order.getStatus().name())
                        .documentKey(order.getDocumentS3Key())
                        .build());
            }
        }

        // Fetch Enrollments (Conventions + Attestations d'accueil)
        List<Enrollment> enrollments = enrollmentRepository.findByDoctorId(doctorId);
        for (Enrollment enrollment : enrollments) {
            if (enrollment.getConventionS3Key() != null && !enrollment.getConventionS3Key().isBlank()) {
                documents.add(DocumentItemDto.builder()
                        .id(enrollment.getId())
                        .title("Convention Tripartite de Stage - " + enrollment.getSession().getLocation())
                        .type("CONVENTION")
                        .date(enrollment.getSubmittedAt())
                        .status("VALIDATED")
                        .documentKey(enrollment.getConventionS3Key())
                        .build());
            }
            if (enrollment.getAttestationS3Key() != null && !enrollment.getAttestationS3Key().isBlank()) {
                documents.add(DocumentItemDto.builder()
                        .id(enrollment.getId())
                        .title("Attestation d'Accueil / Inscription - " + enrollment.getSession().getLocation())
                        .type("ATTESTATION")
                        .date(enrollment.getSubmittedAt())
                        .status("VALIDATED")
                        .documentKey(enrollment.getAttestationS3Key())
                        .build());
            }
        }

        // Sort descending by date
        documents.sort(Comparator.comparing(DocumentItemDto::getDate).reversed());

        return ResponseEntity.ok(documents);
    }
}
