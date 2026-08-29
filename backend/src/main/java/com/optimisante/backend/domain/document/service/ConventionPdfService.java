package com.optimisante.backend.domain.document.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.optimisante.backend.domain.document.dto.ConventionDataDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class ConventionPdfService {

    public byte[] generateConventionPdf(ConventionDataDto data) {
        log.info("Generating Tripartite Convention PDF for convention {}", data.getConventionId());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, out);
            document.open();

            // Font configurations
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
            Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.DARK_GRAY);
            Font boldTextFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);

            // Title
            Paragraph title = new Paragraph("CONVENTION DE FORMATION TRIPARTITE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(30);
            document.add(title);

            // Reference
            Paragraph ref = new Paragraph("Référence : CONV-2026-" + (data.getConventionId() != null ? data.getConventionId().toString().substring(0, 8).toUpperCase() : "DRAFT"), textFont);
            ref.setAlignment(Element.ALIGN_RIGHT);
            ref.setSpacingAfter(20);
            document.add(ref);

            // Section 1: Les Parties
            document.add(new Paragraph("1. LES PARTIES ENGAGÉES", sectionFont));
            document.add(new Paragraph("Entre les soussignés :", textFont));
            
            PdfPTable partiesTable = new PdfPTable(1);
            partiesTable.setWidthPercentage(100);
            partiesTable.setSpacingBefore(10);
            partiesTable.setSpacingAfter(20);

            partiesTable.addCell(createNoBorderCell("L'organisme de formation : " + data.getTrainingCenterName() + "\nAdresse : " + data.getTrainingCenterAddress(), textFont));
            partiesTable.addCell(createNoBorderCell("\nLe candidat (Professionnel de santé) : " + data.getDoctorName() + "\nSpécialité : " + data.getDoctorSpecialty() + "\nEmail : " + data.getDoctorEmail(), textFont));
            partiesTable.addCell(createNoBorderCell("\nL'établissement ou financeur : " + data.getHospitalName() + "\nAdresse : " + data.getHospitalAddress(), textFont));

            document.add(partiesTable);

            // Section 2: Objet de la Convention
            document.add(new Paragraph("2. OBJET DE LA FORMATION", sectionFont));
            String datePattern = "dd/MM/yyyy";
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern);
            String period = (data.getStartDate() != null ? data.getStartDate().format(formatter) : "") + " au " + (data.getEndDate() != null ? data.getEndDate().format(formatter) : "");
            
            Paragraph objetText = new Paragraph("Le candidat s'engage à participer à la formation intitulée : " + data.getTrainingTitle() + 
                "\nCette formation se déroulera du " + period + " à " + data.getLocation() + ".", textFont);
            objetText.setSpacingBefore(10);
            objetText.setSpacingAfter(20);
            document.add(objetText);

            // Section 3: Conditions Financières
            document.add(new Paragraph("3. CONDITIONS FINANCIÈRES", sectionFont));
            Paragraph costText = new Paragraph("Le coût total de la formation s'élève à " + (data.getPrice() != null ? data.getPrice() : "0.00") + " EUR.\nL'établissement s'engage à prendre en charge ce montant selon les termes convenus.", textFont);
            costText.setSpacingBefore(10);
            costText.setSpacingAfter(30);
            document.add(costText);

            // Section 4: Signatures
            document.add(new Paragraph("4. SIGNATURES DES PARTIES", sectionFont));
            
            PdfPTable signatureTable = new PdfPTable(3);
            signatureTable.setWidthPercentage(100);
            signatureTable.setSpacingBefore(15);
            
            signatureTable.addCell(createSignatureCell("L'Organisme de Formation", data.isTrainingCenterSigned(), data.getTrainingCenterSignatureDate(), boldTextFont, textFont));
            signatureTable.addCell(createSignatureCell("Le Candidat", data.isDoctorSigned(), data.getDoctorSignatureDate(), boldTextFont, textFont));
            signatureTable.addCell(createSignatureCell("L'Établissement Financeur", data.isHospitalSigned(), data.getHospitalSignatureDate(), boldTextFont, textFont));
            
            document.add(signatureTable);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating convention PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private PdfPCell createNoBorderCell(String content, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(content, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(5);
        return cell;
    }

    private PdfPCell createSignatureCell(String title, boolean isSigned, java.time.LocalDate signatureDate, Font titleFont, Font textFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.addElement(new Paragraph(title, titleFont));
        
        if (isSigned) {
            Paragraph signedPhrase = new Paragraph("\nSigné électroniquement", textFont);
            signedPhrase.getFont().setColor(Color.GREEN);
            cell.addElement(signedPhrase);
            if (signatureDate != null) {
                cell.addElement(new Paragraph("Le : " + signatureDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), textFont));
            }
        } else {
            Paragraph pendingPhrase = new Paragraph("\n[ En attente de signature ]", textFont);
            pendingPhrase.getFont().setColor(Color.RED);
            cell.addElement(pendingPhrase);
        }
        
        return cell;
    }
}
