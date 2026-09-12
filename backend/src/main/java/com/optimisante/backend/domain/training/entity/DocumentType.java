package com.optimisante.backend.domain.training.entity;

/**
 * Pièces justificatives du dossier d'un candidat.
 *
 * <p><b>Le libellé vit ici, et nulle part ailleurs.</b> Trois tables de traduction coexistaient
 * côté navigateur, chacune incomplète à sa manière : celle du coffre-fort ignorait
 * {@code INTERVIEW_CONVOCATION} et {@code SERVICE_SUBSCRIPTION}, pourtant présents en base, si
 * bien qu'un administrateur ou un CHU consultant un dossier lisait le nom technique. Le serveur
 * transmet désormais le libellé avec la pièce : une table côté navigateur ne peut plus diverger
 * de l'énumération, puisqu'il n'y en a plus.</p>
 */
public enum DocumentType {
    PASSPORT              ("Passeport"),
    DIPLOMA               ("Diplôme de médecine"),
    MEDICAL_COUNCIL_CERT  ("Certificat de l'Ordre des Médecins"),
    FINANCIAL_GUARANTEE   ("Garantie financière"),
    VISA_GRANT            ("Attestation de visa"),
    CONSULAR_LETTER       ("Lettre d'accompagnement consulaire"),
    ACCOMMODATION_PROOF   ("Attestation d'hébergement"),
    INTERVIEW_CONVOCATION ("Convocation à l'entretien visio"),
    SERVICE_SUBSCRIPTION  ("Attestation de souscription"),
    PAYMENT_RECEIPT       ("Reçu de paiement"),
    OTHER                 ("Autre pièce justificative");

    private final String libelle;

    DocumentType(String libelle) {
        this.libelle = libelle;
    }

    /** Ce que lit un humain. Jamais le nom de la constante. */
    public String libelle() {
        return libelle;
    }

    /**
     * Le centre partenaire voit-il cette pièce ?
     *
     * <p>Ce qui touche à l'argent du médecin ne le regarde pas : ni l'attestation de
     * souscription, ni les reçus de ses règlements. Il vérifie un dossier de candidature, pas
     * une situation financière.</p>
     */
    public boolean isVisibleToPartner() {
        return this != SERVICE_SUBSCRIPTION && this != PAYMENT_RECEIPT;
    }
}
