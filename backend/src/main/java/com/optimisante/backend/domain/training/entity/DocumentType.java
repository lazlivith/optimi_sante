package com.optimisante.backend.domain.training.entity;

/**
 * Taxonomie des pieces d'un dossier de mobilite.
 *
 * ⚠️ Toute valeur ajoutee ici doit l'etre simultanement dans **deux** contraintes SQL :
 * {@code enrollment_documents_document_type_check} et
 * {@code enrollment_document_requests_type_check} (voir V37, puis V41).
 */
public enum DocumentType {
    PASSPORT,
    DIPLOMA,
    MEDICAL_COUNCIL_CERT,
    FINANCIAL_GUARANTEE,
    VISA_GRANT,
    CONSULAR_LETTER,
    ACCOMMODATION_PROOF,
    /** Convocation a l'entretien de selection, deposee automatiquement a la confirmation. */
    INTERVIEW_CONVOCATION,
    OTHER
}
