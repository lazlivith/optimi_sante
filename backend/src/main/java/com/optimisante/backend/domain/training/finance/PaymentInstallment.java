package com.optimisante.backend.domain.training.finance;

/**
 * Rang d'un encaissement dans l'echeancier des frais de formation.
 *
 * <p>Ces valeurs ne remplacent pas {@link PaymentType} : l'acompte et le solde <b>sont</b> tous
 * deux des frais de formation. Ils se repartissent avec le partenaire de la meme facon et
 * alimentent le meme reversement. Ce qui les distingue n'est pas leur nature mais leur moment.
 * C'est pourquoi les lectures existantes — reversements, etat comptable, releves — continuent
 * de filtrer sur {@code TUITION_FEE} sans rien connaitre de cet enum.</p>
 *
 * ⚠️ Toute valeur ajoutee ici doit l'etre simultanement dans
 * {@code enrollment_payments_installment_check} (V45).
 */
public enum PaymentInstallment {
    /** Acompte exige a l'admission, une fois la candidature acceptee par l'etablissement. */
    DEPOSIT,
    /** Solde appele a la delivrance du visa. */
    BALANCE,
    /**
     * Reglement unique, anterieur a la mise en place de l'echeancier (V45).
     *
     * <p>Ces dossiers ont deja tout paye : aucun solde ne leur est reclame. Les reetiqueter en
     * {@code DEPOSIT} reviendrait a leur demander 40 % de plus.</p>
     */
    FULL
}
