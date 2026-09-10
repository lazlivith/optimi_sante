package com.optimisante.backend.domain.training.entity;

/**
 * Taxonomie des pieces d'un dossier de mobilite.
 *
 * ⚠️ Toute valeur ajoutee ici doit l'etre simultanement dans **deux** contraintes SQL :
 * {@code enrollment_documents_document_type_check} et
 * {@code enrollment_document_requests_type_check} (voir V37, puis V41 et V43).
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
    /**
     * Attestation des services souscrits aupres d'OptimiSante (assurance, hebergement,
     * transport) et de leur reglement.
     *
     * <p>Ce n'est <b>pas</b> un certificat d'assurance : OptimiSante n'est pas l'assureur.
     * L'attestation etablit la souscription et le paiement ; le certificat est emis par
     * l'assureur en son nom propre.</p>
     */
    SERVICE_SUBSCRIPTION,
    OTHER;

    /**
     * Cette piece peut-elle etre montree a l'etablissement d'accueil ?
     *
     * <p>Le partenaire instruit le <b>dossier de mobilite</b> du candidat : passeport, diplome,
     * visa, hebergement. Il n'a pas a connaitre ce que le medecin achete a OptimiSante ni a
     * quel prix — l'attestation de souscription enonce les tarifs de l'agence et les achats
     * personnels du candidat, deux informations qui ne le regardent pas.</p>
     *
     * <p>La regle est portee ici, et non dans les ecrans : la liste des pieces et leur
     * telechargement sont deux chemins distincts, et n'en proteger qu'un laisserait la piece
     * accessible a qui connait son identifiant.</p>
     */
    public boolean isVisibleToPartner() {
        return this != SERVICE_SUBSCRIPTION;
    }
}
