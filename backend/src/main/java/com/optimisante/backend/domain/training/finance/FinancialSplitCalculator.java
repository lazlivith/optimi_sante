package com.optimisante.backend.domain.training.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Répartition d'un encaissement entre la commission d'agence OptimiSanté et la part nette
 * reversée au partenaire. Fonction pure, sans dépendance : elle se teste seule et constitue
 * le seul endroit du code où un partage est calculé.
 *
 * <p><b>Pourquoi la part partenaire est une soustraction et non un second arrondi :</b>
 * arrondir séparément les deux montants ferait dériver leur somme du montant encaissé d'un
 * centime (par exemple 3&nbsp;333,33 € à 15 % : 499,9995 → 500,00 et 2&nbsp;833,3305 →
 * 2&nbsp;833,33, dont la somme vaut 3&nbsp;333,33 par chance, mais d'autres montants
 * cassent l'égalité). La contrainte SQL {@code enrollment_payments_split_coherent}
 * rejetterait alors l'insertion. En dérivant la part partenaire par soustraction,
 * l'invariant {@code commission + partenaire = brut} est vrai par construction.</p>
 */
public final class FinancialSplitCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;

    private FinancialSplitCalculator() {
        // Classe utilitaire : non instanciable.
    }

    /**
     * @param grossAmount    montant encaissé auprès du médecin
     * @param commissionRate taux de commission en pourcentage (ex. {@code 15.00})
     */
    public static FinancialSplitResult calculateSplit(BigDecimal grossAmount, BigDecimal commissionRate) {
        if (grossAmount == null || commissionRate == null) {
            throw new IllegalArgumentException("Montant et taux de commission sont obligatoires.");
        }
        if (grossAmount.signum() < 0) {
            throw new IllegalArgumentException("Le montant encaissé ne peut pas être négatif.");
        }
        if (commissionRate.signum() < 0 || commissionRate.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Le taux de commission doit être compris entre 0 et 100.");
        }

        BigDecimal gross = grossAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal commission = gross
                .multiply(commissionRate)
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);

        // Dérivée, jamais arrondie séparément : garantit l'invariant comptable.
        BigDecimal partnerPayout = gross.subtract(commission);

        if (commission.add(partnerPayout).compareTo(gross) != 0) {
            // Ne peut se produire que si le calcul ci-dessus est modifié à tort : on refuse
            // d'écrire en base plutôt que de laisser la contrainte SQL échouer plus loin,
            // avec un message autrement moins clair.
            throw new IllegalStateException(
                    "Répartition incohérente : " + commission + " + " + partnerPayout + " != " + gross);
        }

        return new FinancialSplitResult(gross, commissionRate, commission, partnerPayout);
    }

    /** Résultat figé d'une répartition, tel qu'il sera enregistré sur la ligne de paiement. */
    public record FinancialSplitResult(
            BigDecimal grossAmount,
            BigDecimal commissionRate,
            BigDecimal commissionAmount,
            BigDecimal partnerPayoutAmount) {
    }
}
