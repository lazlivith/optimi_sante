package com.optimisante.backend.domain.orders.entity;

public enum PaymentMethod {

    STRIPE_CARD   ("Carte bancaire"),
    BANK_TRANSFER ("Virement bancaire"),
    QUOTE_REQUEST ("Sur devis");

    private final String libelle;

    PaymentMethod(String libelle) {
        this.libelle = libelle;
    }

    /**
     * Ce qui s'imprime sur un reçu.
     *
     * <p>Sans lui, le document affichait « BANK_TRANSFER » au client — un nom de constante Java
     * sur une pièce comptable.</p>
     */
    public String libelle() {
        return libelle;
    }
}
