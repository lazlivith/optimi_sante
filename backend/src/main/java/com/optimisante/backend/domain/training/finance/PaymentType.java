package com.optimisante.backend.domain.training.finance;

/**
 * Nature d'un encaissement du registre financier.
 *
 * ⚠️ Toute valeur ajoutée ici doit l'être simultanément dans la contrainte SQL
 * `enrollment_payments_payment_type_check` (V27).
 */
public enum PaymentType {
    /** Frais de dossier fixes, 100 % OptimiSanté. Encaissés via doctor_applications. */
    DOSSIER_FEE,
    /** Frais de formation : encaissés par la plateforme, puis répartis avec le partenaire. */
    TUITION_FEE
}
