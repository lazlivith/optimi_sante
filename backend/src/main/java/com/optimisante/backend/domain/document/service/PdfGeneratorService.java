package com.optimisante.backend.domain.document.service;

import com.optimisante.backend.common.storage.DossierStockage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

import com.optimisante.backend.common.storage.StorageService;

@Slf4j
@Service
public class PdfGeneratorService {

    private final DocumentRenderer documentRenderer;
    private final StorageService storageService;

    public PdfGeneratorService(DocumentRenderer documentRenderer, StorageService storageService) {
        this.documentRenderer = documentRenderer;
        this.storageService = storageService;
    }

    /**
     * Génère un fichier PDF en mémoire à partir d'un template Thymeleaf.
     *
     * <p>Délègue à {@link DocumentRenderer}, qui est désormais le seul endroit où un document est
     * rendu — c'est ce qui garantit que l'identité légale figure au pied de chacun. Cette méthode
     * subsiste pour que les appelants existants continuent de fonctionner sans changement.</p>
     *
     * @param templateName Nom du template sans extension (ex: "devis-b2b")
     * @param variables Map contenant les variables à injecter dans le template
     * @return Le tableau d'octets (byte[]) représentant le fichier PDF généré
     */
    public byte[] generatePdfFromTemplate(String templateName, Map<String, Object> variables) {
        return documentRenderer.rendreGabarit(templateName, variables);
    }

    /**
     * Génère un PDF et l'upload directement sur le cloud (Cloudinary/S3).
     */
    public String generateAndUploadPdf(String templateName, Map<String, Object> variables, DossierStockage dossier,
                                       String fileName) {
        byte[] pdfBytes = generatePdfFromTemplate(templateName, variables);
        return storageService.uploadGeneratedPdf(pdfBytes, dossier, fileName);
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

    /**
     * Convention de formation professionnelle continue, pour un praticien exerçant en France.
     *
     * <p>Distincte de la convention tripartite : celle-ci organise un accueil et une mobilité,
     * celle-là un achat de formation opposable à un financeur — DPC, FIF-PL ou employeur.</p>
     */
    public byte[] generateDpcConventionPdf(Map<String, Object> conventionData) {
        return generatePdfFromTemplate("convention-dpc", conventionData);
    }

    public byte[] generatePartnershipConventionPdf(Map<String, Object> data) {
        return generatePdfFromTemplate("convention-partenariat", data);
    }

    /** Relevé de reversement destiné à l'établissement partenaire. */
    public byte[] generatePayoutStatementPdf(Map<String, Object> data) {
        return generatePdfFromTemplate("releve-reversement", data);
    }
}
