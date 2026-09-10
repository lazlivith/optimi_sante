package com.optimisante.backend.domain.training.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decoupage des frais de formation en deux echeances : acompte a l'admission, solde a la
 * delivrance du visa.
 *
 * <p>Fonction pure, sans dependance, comme {@link FinancialSplitCalculator} : c'est le seul
 * endroit du code ou l'echeancier est calcule.</p>
 *
 * <p><b>Pourquoi le solde est une soustraction et non un second arrondi :</b> arrondir
 * separement 60 % et 40 % ferait deriver leur somme du prix de la formation d'un centime sur
 * certains montants. Le candidat paierait alors 3 200,01 € pour une formation affichee a
 * 3 200,00 €, ou 3 199,99 € — et le partenaire recevrait un reversement qui ne correspond a
 * rien. En derivant le solde par soustraction, l'egalite {@code acompte + solde = prix} est
 * vraie par construction, exactement comme l'invariant de repartition de la V27.</p>
 */
public final class TuitionInstallmentCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;

    private TuitionInstallmentCalculator() {
        // Classe utilitaire : non instanciable.
    }

    /**
     * @param totalAmount prix total de la formation
     * @param depositRate part exigee a l'admission, en pourcentage (ex. {@code 60})
     */
    public static TuitionSchedule split(BigDecimal totalAmount, BigDecimal depositRate) {
        if (totalAmount == null || depositRate == null) {
            throw new IllegalArgumentException("Montant et taux d'acompte sont obligatoires.");
        }
        if (totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("Le prix de la formation doit être strictement positif.");
        }
        if (depositRate.signum() <= 0 || depositRate.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Le taux d'acompte doit être compris entre 1 et 100.");
        }

        BigDecimal total = totalAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal deposit = total
                .multiply(depositRate)
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);

        // Derive, jamais arrondi separement : garantit l'egalite avec le prix affiche.
        BigDecimal balance = total.subtract(deposit);

        if (deposit.add(balance).compareTo(total) != 0) {
            // Ne peut se produire que si le calcul ci-dessus est modifie a tort. On refuse
            // d'ouvrir un paiement plutot que de prelever un montant qui ne correspond pas au
            // prix annonce.
            throw new IllegalStateException(
                    "Échéancier incohérent : " + deposit + " + " + balance + " != " + total);
        }

        return new TuitionSchedule(total, depositRate, deposit, balance);
    }

    /**
     * Echeancier fige d'une formation.
     *
     * @param balanceAmount peut valoir zero lorsque le taux d'acompte est de 100 % — la
     *                      plateforme n'appelle alors aucun solde.
     */
    public record TuitionSchedule(
            BigDecimal totalAmount,
            BigDecimal depositRate,
            BigDecimal depositAmount,
            BigDecimal balanceAmount) {

        /** Un solde nul ne se reclame pas : il n'y a rien a encaisser. */
        public boolean hasBalance() {
            return balanceAmount.signum() > 0;
        }
    }
}
