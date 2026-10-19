package com.optimisante.backend.domain.exercerenfrance.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * L'état public d'une période de dépôt : ouverte, à venir, ou fermée.
 *
 * <p><b>Calculé, jamais stocké.</b> Une colonne « statut » se périme en silence : la veille
 * de la clôture elle dit vrai, le lendemain elle ment, et personne ne s'en aperçoit avant un
 * praticien qui dépose un dossier hors délai. Le seul moyen qu'elle reste juste serait une
 * tâche qui la réécrit chaque nuit — c'est-à-dire ce calcul, mais en retard d'un jour.</p>
 *
 * <p><b>Europe/Paris, et le jour de fin compte.</b> Les périodes sont fixées par
 * l'administration française : une clôture « au 1er octobre » court jusqu'au 1er octobre à
 * minuit. Calculer en UTC fermerait la période deux heures trop tôt en été, soit toute une
 * soirée de dépôts refusés.</p>
 */
public final class StatutPeriode {

    /** Le fuseau de l'administration qui fixe les dates, pas celui du serveur. */
    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    public enum Etat {
        /** Entre la date de début et la date de fin, bornes comprises. */
        OUVERT,
        /** Avant la date de début, ou dates pas encore annoncées. */
        A_VENIR,
        /** Après la date de fin. */
        FERME
    }

    private StatutPeriode() {
    }

    public static Etat calculer(LocalDate debut, LocalDate fin) {
        return calculer(debut, fin, Clock.system(PARIS));
    }

    /**
     * @param horloge injectée pour que les cas limites — veille, jour de début, jour de fin,
     *                lendemain — soient éprouvables sans attendre le calendrier
     */
    public static Etat calculer(LocalDate debut, LocalDate fin, Clock horloge) {
        LocalDate aujourdHui = LocalDate.now(horloge.withZone(PARIS));

        // Dates pas encore annoncées : « Périodes 2027 » existe avant que le CNG ne publie.
        // C'est le cas le plus fréquent en début d'année, et il doit dire « à venir » plutôt
        // que de laisser une carte vide.
        if (debut == null && fin == null) {
            return Etat.A_VENIR;
        }
        // Une seule borne connue : on ne conclut que sur ce qui est su.
        if (debut == null) {
            return aujourdHui.isAfter(fin) ? Etat.FERME : Etat.OUVERT;
        }
        if (fin == null) {
            return aujourdHui.isBefore(debut) ? Etat.A_VENIR : Etat.OUVERT;
        }

        if (aujourdHui.isBefore(debut)) {
            return Etat.A_VENIR;
        }
        // isAfter(fin) et non !isBefore(fin) : le jour de fin est INCLUS.
        return aujourdHui.isAfter(fin) ? Etat.FERME : Etat.OUVERT;
    }
}
