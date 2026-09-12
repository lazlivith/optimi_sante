package com.optimisante.backend.domain.document;

import java.util.Locale;
import java.text.Normalizer;

/**
 * Registre des documents que la plateforme produit elle-même.
 *
 * <p>Un document s'ajoute ici, en une ligne, et <b>rien ne peut le décrire ailleurs</b> : le nom
 * du gabarit, le libellé montré à l'utilisateur et le nom du fichier téléchargé viennent tous de
 * cette énumération. C'est la réponse à un défaut qui s'est déjà manifesté cinq fois dans ce
 * projet — une taxonomie recopiée finit toujours par diverger de l'originale, en silence.</p>
 *
 * <p>À ne pas confondre avec {@code DocumentType}, qui classe les pièces <em>téléversées</em> par
 * les candidats. Ici, ce sont les documents que nous <em>émettons</em>.</p>
 */
public enum DocumentKind {

    DEVIS                    ("devis-b2b",               "Devis"),
    RECU_PAIEMENT            ("recu-paiement",           "Reçu de paiement"),
    ATTESTATION_INSCRIPTION  ("attestation-ins",         "Attestation d'inscription"),
    ATTESTATION_SOUSCRIPTION ("attestation-souscription","Attestation de souscription"),
    CONVENTION_TRIPARTITE    ("convention-tripartite",   "Convention tripartite"),
    CONVENTION_PARTENARIAT   ("convention-partenariat",  "Convention de partenariat"),
    CONVOCATION_ENTRETIEN    ("convocation-entretien",   "Convocation à l'entretien"),
    RELEVE_REVERSEMENT       ("releve-reversement",      "Relevé de reversement");

    private final String gabarit;
    private final String libelle;

    DocumentKind(String gabarit, String libelle) {
        this.gabarit = gabarit;
        this.libelle = libelle;
    }

    /** Nom du fichier Thymeleaf, sans chemin ni extension. */
    public String gabarit() {
        return gabarit;
    }

    /** Ce que lit un humain. */
    public String libelle() {
        return libelle;
    }

    /**
     * Nom proposé au téléchargement : lisible, sans accent ni espace, jamais un identifiant.
     *
     * <p>Les accents sont retirés du <em>nom de fichier</em> — pas du libellé affiché — parce
     * qu'un dossier de téléchargements, un client de messagerie et un système de fichiers ne les
     * traitent pas tous pareil. « recu-de-paiement-OPT-123.pdf » voyage partout.</p>
     */
    public String nomFichier(String reference) {
        String base = Normalizer.normalize(libelle, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")              // ç -> c, é -> e
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return reference == null || reference.isBlank()
                ? base + ".pdf"
                : base + "-" + reference + ".pdf";
    }

    /** Retrouve un document à partir de son gabarit — passerelle pour le code existant. */
    public static DocumentKind parGabarit(String gabarit) {
        for (DocumentKind kind : values()) {
            if (kind.gabarit.equals(gabarit)) return kind;
        }
        throw new IllegalArgumentException(
                "Aucun document déclaré pour le gabarit « " + gabarit + " ». "
                + "Ajoutez-le à DocumentKind plutôt que de le rendre directement.");
    }
}
