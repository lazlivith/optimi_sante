package com.optimisante.backend.domain.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import com.optimisante.backend.common.storage.StorageService;

@Slf4j
@Service
public class PdfGeneratorService {

    private final TemplateEngine templateEngine;
    private final StorageService storageService;

    public PdfGeneratorService(@org.springframework.beans.factory.annotation.Qualifier("pdfTemplateEngine") TemplateEngine templateEngine, StorageService storageService) {
        this.templateEngine = templateEngine;
        this.storageService = storageService;
    }

    /**
     * Génère un fichier PDF en mémoire à partir d'un template Thymeleaf.
     * 
     * @param templateName Nom du template sans extension (ex: "pdf/devis-b2b")
     * @param variables Map contenant les variables à injecter dans le template
     * @return Le tableau d'octets (byte[]) représentant le fichier PDF généré
     */
    public byte[] generatePdfFromTemplate(String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            
            // Render HTML
            String html = templateEngine.process(templateName, context);
            
            // Convert HTML to PDF using Flying Saucer (OpenPDF)
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                ITextRenderer renderer = new ITextRenderer();
                
                // Set Document String and Base URL (for finding classpath resources like CSS/Images)
                renderer.setDocumentFromString(html);
                renderer.layout();
                renderer.createPDF(outputStream);
                
                return outputStream.toByteArray();
            }
        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF pour le template {}: {}", templateName, e.getMessage(), e);
            throw new RuntimeException("Erreur de génération PDF", e);
        }
    }

    /**
     * Génère un PDF et l'upload directement sur le cloud (Cloudinary/S3).
     */
    public String generateAndUploadPdf(String templateName, Map<String, Object> variables, String folder, String fileName) {
        byte[] pdfBytes = generatePdfFromTemplate(templateName, variables);
        return storageService.uploadGeneratedPdf(pdfBytes, folder, fileName);
    }

    public byte[] generateQuotePdf(Map<String, Object> quoteData) {
        return generatePdfFromTemplate("devis-b2b", quoteData);
    }

    public byte[] generateReceiptPdf(Map<String, Object> receiptData) {
        return generatePdfFromTemplate("recu-paiement", receiptData);
    }

    public byte[] generateEnrollmentAttestationPdf(Map<String, Object> attestationData) {
        return generatePdfFromTemplate("attestation-ins", attestationData);
    }

    public byte[] generateTripartiteConventionPdf(Map<String, Object> conventionData) {
        return generatePdfFromTemplate("convention-tripartite", conventionData);
    }

    public byte[] generatePartnershipConventionPdf(Map<String, Object> data) {
        return generatePdfFromTemplate("convention-partenariat", data);
    }
}
