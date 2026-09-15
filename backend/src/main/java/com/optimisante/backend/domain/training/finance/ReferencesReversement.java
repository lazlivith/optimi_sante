package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.training.entity.Enrollment;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Les références lisibles d'un reversement : code du CHU, code du dossier, rang de la tranche.
 *
 * <p><b>Pourquoi une référence qui nomme le dossier.</b> Le CHU reçoit un virement et doit savoir
 * à qui il correspond. Un « REV-2026-09-A1B2C3 » l'oblige à ouvrir le bordereau ; un
 * « REV-CHUTESTP-3F2A1C-T1 » dit, dans le seul libellé bancaire, l'établissement, le dossier et
 * l'acompte concerné. Le comptable rapproche sans rien ouvrir.</p>
 *
 * <p><b>Pourquoi 35 caractères au plus.</b> La référence sert d'identifiant de bout en bout
 * dans le fichier SEPA ({@code EndToEndId}), limité à 35 caractères par la norme, et c'est aussi
 * la longueur que la plupart des banques conservent dans le libellé affiché au bénéficiaire.</p>
 *
 * <p>Les dossiers d'inscription n'ont pas de numéro lisible en base. Le code en est déduit — année
 * de la session et début de l'identifiant — plutôt que stocké : cela évite d'ajouter une
 * numérotation à la création des inscriptions, qui fonctionne et que ce chantier ne touche pas.
 * Le code est stable : un même dossier donne toujours le même.</p>
 */
public final class ReferencesReversement {

    private ReferencesReversement() {
    }

    /** « CHU Test Partenaire » → « CHUTESTP » : lettres et chiffres, sans accent, huit au plus. */
    public static String codeEtablissement(String nom) {
        String code = Normalizer.normalize(nom == null ? "" : nom, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (code.isEmpty()) {
            return "PART";
        }
        return code.length() > 8 ? code.substring(0, 8) : code;
    }

    /** Six caractères stables, tirés de l'identifiant du dossier. */
    public static String empreinteDossier(Enrollment inscription) {
        return inscription.getId().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    /** « DOS-2026-3F2A1C » : l'année de la session, puis l'empreinte du dossier. */
    public static String codeDossier(Enrollment inscription) {
        var debut = inscription.getSession() != null ? inscription.getSession().getStartDate() : null;
        String annee = debut != null ? String.valueOf(debut.getYear()) : "0000";
        return "DOS-" + annee + "-" + empreinteDossier(inscription);
    }

    /** T1 pour l'acompte, T2 pour le solde, TOT pour un règlement en une fois. */
    public static String codeTranche(PaymentInstallment tranche) {
        if (tranche == null) {
            return "TOT";
        }
        return switch (tranche) {
            case DEPOSIT -> "T1";
            case BALANCE -> "T2";
            case FULL -> "TOT";
        };
    }

    public static String libelleTranche(PaymentInstallment tranche, java.math.BigDecimal tauxAcompte) {
        if (tranche == null || tranche == PaymentInstallment.FULL) {
            return "Règlement intégral";
        }
        String taux = tauxAcompte == null ? "" : " (" + tauxAcompte.stripTrailingZeros().toPlainString() + " %)";
        return tranche == PaymentInstallment.DEPOSIT
                ? "Acompte" + taux
                : "Solde" + (tauxAcompte == null ? ""
                        : " (" + java.math.BigDecimal.valueOf(100).subtract(tauxAcompte)
                                .stripTrailingZeros().toPlainString() + " %)");
    }
}
