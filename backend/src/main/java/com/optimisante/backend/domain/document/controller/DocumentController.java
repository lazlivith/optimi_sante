package com.optimisante.backend.domain.document.controller;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.document.service.PdfGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final StorageService storageService;
    private final PdfGeneratorService pdfGeneratorService;

    @GetMapping("/{type}/{id}/download")
    public ResponseEntity<Map<String, String>> getDocumentDownloadUrl(
            @PathVariable String type,
            @PathVariable String id) {
        
        // Dans une implémentation réelle, on irait chercher la clé S3/Cloudinary en BDD en fonction du type et de l'id.
        // Exemple: Order pour Devis/Receipt, Enrollment pour Attestation.
        
        // Pour valider le POC Sprint 4, on génère un PDF factice à la volée, on l'upload et on renvoie le lien.
        byte[] pdfBytes;
        String fileName;
        String folderPath = "docs/" + type;
        
        if ("devis".equals(type)) {
            fileName = "DEV-2026-" + id;
            pdfBytes = pdfGeneratorService.generateQuotePdf(Map.of(
                    "clientName", "Dr. " + id,
                    "clientAddress", "Paris",
                    "clientVat", "N/A",
                    "orderReference", fileName,
                    "date", "01/01/2026",
                    "items", java.util.List.of(
                        Map.of("description", "Consultation", "quantity", 1, "unitPrice", 100.0, "subtotal", 100.0)
                    ),
                    "totalAmount", 100.0
            ));
        } else if ("receipt".equals(type)) {
            fileName = "REC-2026-" + id;
            pdfBytes = pdfGeneratorService.generateReceiptPdf(Map.of(
                    "clientName", "Dr. " + id,
                    "clientEmail", "email@example.com",
                    "receiptReference", fileName,
                    "date", "01/01/2026",
                    "paymentMethod", "Stripe",
                    "transactionId", "pi_test",
                    "totalAmount", 100.0
            ));
        } else if ("attestation".equals(type)) {
            fileName = "INS-2026-" + id;
            pdfBytes = pdfGeneratorService.generateEnrollmentAttestationPdf(Map.of(
                    "enrollmentReference", fileName,
                    "doctorName", "John Doe",
                    "doctorSpecialty", "Cardiologie",
                    "passportNumber", "AB123456",
                    "trainingTitle", "Echographie",
                    "hospitalName", "CHU Paris",
                    "startDate", "01/01/2026",
                    "endDate", "31/01/2026",
                    "date", "01/01/2026"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Type de document inconnu"));
        }
        
        // Upload le PDF généré sur Cloudinary
        String publicId = storageService.uploadGeneratedPdf(pdfBytes, folderPath, fileName);
        
        // Génère le Presigned URL (valable 60 minutes)
        String downloadUrl = storageService.generatePresignedOrSignedUrl(publicId, 60);
        
        return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
    }
}
