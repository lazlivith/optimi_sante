package com.optimisante.backend.domain.training.finance;

/**
 * Nature d'un encaissement du registre financier.
 *
 * ⚠️ Toute valeur ajoutée ici doit l'être simultanément dans la contrainte SQL
 * `enrollment_payments_payment_type_check` (V27, puis V43).
 */
public enum PaymentType {
    /** Frais de dossier fixes, 100 % OptimiSanté. Encaissés via doctor_applications. */
    DOSSIER_FEE,
    /** Frais de formation : encaissés par la plateforme, puis répartis avec le partenaire. */
    TUITION_FEE,
    /**
     * Services organisés autour du séjour (assurance, hébergement, transport), 100 % OptimiSanté.
     *
     * <p>Le CHU ne rend pas ces prestations : c'est l'agence qui les monte et paie ses
     * prestataires. Les faire entrer dans la répartition reverserait à l'établissement une part
     * d'un travail qu'il n'a pas fourni.</p>
     *
     * <p>Contrairement aux deux autres, ce type peut apparaître <b>plusieurs fois</b> sur un
     * même dossier : souscrire une assurance en mars puis un hébergement en juin fait deux
     * encaissements légitimes. L'idempotence repose donc sur la session Stripe et non sur un
     * index « un seul par dossier » (V43).</p>
     */
    SERVICE_OPTIONS
}
