package com.optimisante.backend.domain.training.entity;

/**
 * Nature d'un document officiel remis au médecin.
 *
 * <p>Deux familles, qui ne se déverrouillent pas au même moment : les documents
 * <b>pédagogiques</b> du CHU dès l'acompte réglé, le <b>kit de départ</b> d'Optimi Santé une fois
 * le visa obtenu et le solde réglé.</p>
 *
 * ⚠️ Toute valeur ajoutée ici doit l'être simultanément dans {@code eod_category_check} (V57).
 */
public enum OfficialDocumentCategory {
    PROGRAMME      ("Programme officiel de la formation", false),
    CONVENTION_CHU ("Convention de formation de l'établissement", false),
    BILLET         ("Billets de voyage", true),
    HEBERGEMENT    ("Réservation d'hébergement", true),
    CONTACTS       ("Contacts sur place", true),
    KIT_AUTRE      ("Document du kit de départ", true);

    private final String libelle;
    private final boolean kitDeDepart;

    OfficialDocumentCategory(String libelle, boolean kitDeDepart) {
        this.libelle = libelle;
        this.kitDeDepart = kitDeDepart;
    }

    public String libelle() {
        return libelle;
    }

    /** Billets, hébergement, contacts : organisés par Optimi Santé, jamais visibles du CHU. */
    public boolean isKitDeDepart() {
        return kitDeDepart;
    }

    /** Le CHU ne dépose que ce qui relève de la pédagogie ; la logistique est l'affaire de l'agence. */
    public boolean isDeposableParPartenaire() {
        return !kitDeDepart;
    }
}
