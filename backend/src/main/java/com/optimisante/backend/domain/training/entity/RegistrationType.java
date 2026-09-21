package com.optimisante.backend.domain.training.entity;

/**
 * Parcours emprunté par une candidature, selon le lieu d'exercice du médecin.
 *
 * <p><b>Un discriminant, pas un second automate.</b> Les deux parcours partagent les statuts de
 * {@link EnrollmentStatus} et l'essentiel de leurs transitions ; ce qui les sépare tient en trois
 * points — les étapes de visa, le rythme des échéances, et le contrat signé. Créer des statuts
 * dédiés aurait dédoublé les trois sources de vérité que {@code EnrollmentStatus} met en garde de
 * laisser diverger.</p>
 *
 * <p>Un dossier ouvert avant cette distinction est international : c'est le seul parcours qui ait
 * existé, et la valeur par défaut de la colonne le dit (V60).</p>
 *
 * ⚠️ Toute valeur ajoutée ici doit l'être simultanément dans
 * {@code enrollments_registration_type_check} (V60).
 */
public enum RegistrationType {

    /**
     * Médecin résidant hors de France : accompagnement complet, visa compris.
     *
     * <p>Le parcours passe par le dépôt du dossier consulaire puis la délivrance du visa, et
     * c'est cette délivrance qui appelle le solde — personne ne règle le solde d'un séjour qui
     * pourrait ne pas avoir lieu.</p>
     */
    INTERNATIONAL_VISA,

    /**
     * Praticien déjà établi en France (métropole ou DROM-COM) : parcours direct.
     *
     * <p>Aucune démarche consulaire, donc aucune étape suspensive : la convention délivrée mène
     * directement à la convocation. L'identifiant RPPS ou ADELI y remplace le passeport comme
     * preuve du droit d'exercer.</p>
     */
    LOCAL_FRANCE;

    /** Vrai si ce parcours comporte les étapes de demande et d'obtention de visa. */
    public boolean passeParLeVisa() {
        return this == INTERNATIONAL_VISA;
    }
}
