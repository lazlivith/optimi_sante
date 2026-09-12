package com.optimisante.backend.domain.document.recu;

/**
 * Ce qui a été réglé. Détermine l'intitulé imprimé sur le reçu.
 *
 * <p>Cinq encaissements existent sur la plateforme ; un seul produisait un reçu. Les nommer ici
 * rend visible ce qu'on oubliait : un médecin enchaîne trois règlements — frais de dossier,
 * acompte, solde — et n'en gardait aucune trace.</p>
 */
public enum MotifPaiement {

    COMMANDE          ("Commande", "COM"),
    FRAIS_DE_DOSSIER  ("Frais de dossier de candidature", "FDD"),
    ACOMPTE_SCOLARITE ("Acompte sur frais de formation", "ACO"),
    SOLDE_SCOLARITE   ("Solde des frais de formation", "SOL"),
    OPTIONS_SERVICE   ("Options de service", "OPT");

    private final String libelle;
    private final String trigramme;

    MotifPaiement(String libelle, String trigramme) {
        this.libelle = libelle;
        this.trigramme = trigramme;
    }

    /** Ce qui s'imprime sur le reçu, en toutes lettres. */
    public String libelle() {
        return libelle;
    }

    /** Marqueur court, lisible dans une liste de reçus sans ouvrir les documents. */
    public String trigramme() {
        return trigramme;
    }
}
