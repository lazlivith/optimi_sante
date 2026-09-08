package com.optimisante.backend.domain.training.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.optimisante.backend.domain.training.entity.EnrollmentStatus.*;

/**
 * Automate du cycle de candidature : source unique de vérité des transitions légales.
 *
 * <p>Avant cette classe, {@code EnrollmentService.updateEnrollmentStatus(id, status)}
 * écrivait n'importe quelle valeur reçue sans le moindre contrôle : un administrateur
 * pouvait faire passer un dossier de la revue initiale directement à « prêt à démarrer »,
 * sans décision du partenaire ni paiement de la formation. Toute transition absente de
 * {@link #ALLOWED} est désormais refusée, y compris pour un administrateur.</p>
 *
 * <p>Les états terminaux ({@code REJECTED}, {@code CANCELLED}) n'ont volontairement aucune
 * sortie : rouvrir un dossier clos est une décision métier qui mérite son propre cas
 * d'usage explicite, pas un effet de bord d'une transition générique.</p>
 */
public final class EnrollmentTransitions {

    /** États depuis lesquels une annulation administrative reste possible. */
    private static final Set<EnrollmentStatus> CANCELLABLE = EnumSet.of(
            UNDER_OPTIMI_REVIEW, ACTION_REQUIRED, SUBMITTED_TO_PARTNER, ACCEPTED_BY_PARTNER,
            PENDING_TUITION_FEE, CONFIRMED, CONVENTION_ISSUED, VISA_SUBMITTED, VISA_GRANTED
    );

    /** Transitions légales, strictement conformes à la matrice de la spécification (§3). */
    private static final Map<EnrollmentStatus, Set<EnrollmentStatus>> ALLOWED;

    static {
        Map<EnrollmentStatus, Set<EnrollmentStatus>> allowed = new EnumMap<>(EnrollmentStatus.class);

        // --- Cycle commercial ---
        allowed.put(UNDER_OPTIMI_REVIEW,  EnumSet.of(SUBMITTED_TO_PARTNER, ACTION_REQUIRED, REJECTED));
        allowed.put(ACTION_REQUIRED,      EnumSet.of(UNDER_OPTIMI_REVIEW, REJECTED));
        // Le CHU peut réclamer une pièce complémentaire plutôt que de rejeter : le dossier
        // repasse alors sous la responsabilité d'OptimiSanté, qui le re-transmettra après
        // correction. Le partenaire ne s'adresse jamais directement au médecin.
        allowed.put(SUBMITTED_TO_PARTNER, EnumSet.of(ACCEPTED_BY_PARTNER, ACTION_REQUIRED, REJECTED));
        allowed.put(ACCEPTED_BY_PARTNER,  EnumSet.of(PENDING_TUITION_FEE));
        allowed.put(PENDING_TUITION_FEE,  EnumSet.of(CONFIRMED));

        // --- Jonction puis cycle de mobilité ---
        allowed.put(CONFIRMED,         EnumSet.of(CONVENTION_ISSUED));
        allowed.put(CONVENTION_ISSUED, EnumSet.of(VISA_SUBMITTED));
        allowed.put(VISA_SUBMITTED,    EnumSet.of(VISA_GRANTED));
        allowed.put(VISA_GRANTED,      EnumSet.of(READY_TO_START));

        // L'annulation est ajoutée uniformément plutôt que répétée ligne à ligne :
        // une omission dans l'une des listes serait invisible et bloquerait un dossier.
        CANCELLABLE.forEach(from ->
                allowed.computeIfAbsent(from, k -> EnumSet.noneOf(EnrollmentStatus.class))
                        .add(CANCELLED));

        ALLOWED = Map.copyOf(allowed);
    }

    private EnrollmentTransitions() {
        // Classe utilitaire : non instanciable.
    }

    /**
     * @throws IllegalStateException si le passage de {@code from} vers {@code to} n'est pas
     *         prévu par l'automate. Le message nomme les transitions réellement possibles,
     *         pour que l'erreur soit exploitable sans relire cette classe.
     */
    public static void assertAllowed(EnrollmentStatus from, EnrollmentStatus to) {
        if (from == to) {
            throw new IllegalStateException(
                    "Le dossier est déjà au statut " + from + ".");
        }
        if (!isAllowed(from, to)) {
            Set<EnrollmentStatus> possible = allowedFrom(from);
            throw new IllegalStateException(
                    "Transition interdite : " + from + " → " + to + ". "
                            + (possible.isEmpty()
                            ? "Ce dossier est dans un état terminal."
                            : "Transitions possibles depuis " + from + " : " + possible + "."));
        }
    }

    public static boolean isAllowed(EnrollmentStatus from, EnrollmentStatus to) {
        return from != null && to != null && allowedFrom(from).contains(to);
    }

    /** Transitions ouvertes depuis un état donné ; ensemble vide si l'état est terminal. */
    public static Set<EnrollmentStatus> allowedFrom(EnrollmentStatus from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }
}
