package com.optimisante.backend.domain.exercerenfrance.entity;

/**
 * Les énumérations de la fonctionnalité, réunies ici comme {@code SupplierDtos} réunit ses
 * charges utiles : sept fichiers d'une ligne diluent plus qu'ils n'organisent.
 *
 * <p>Chaque valeur correspond mot pour mot à une contrainte CHECK posée en V72. Les deux
 * doivent bouger ensemble : ajouter une valeur ici sans toucher la base ne casse rien à la
 * compilation, seulement la première écriture concernée.</p>
 */
public final class Enums {

    private Enums() {
    }

    /** Où en est le praticien dans la procédure, tel qu'il le déclare lui-même. */
    public enum Etape {
        AVANT_EVC("avant_evc"), LAUREAT("laureat"), AFFECTE("affecte"), AUTRE("autre");

        private final String code;

        Etape(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Etape depuis(String valeur) {
            for (Etape e : values()) {
                if (e.code.equalsIgnoreCase(valeur) || e.name().equalsIgnoreCase(valeur)) {
                    return e;
                }
            }
            return AUTRE;
        }
    }

    /**
     * Le suivi commercial d'une demande.
     *
     * <p>{@link #PAYE} n'est pas un état comme un autre : il fait basculer la demande d'un
     * régime de conservation à l'autre, parce qu'elle devient une pièce contractuelle.</p>
     */
    public enum Statut {
        NOUVEAU("nouveau"), QUALIFIE("qualifie"), DEVIS_ENVOYE("devis_envoye"),
        PAYE("paye"), EN_COURS("en_cours"), TERMINE("termine"), PERDU("perdu"),

        /**
         * Déposée, mais écartée par un filtre anti-robot.
         *
         * <p>La demande est CONSERVÉE, et visible en administration : un filtre se trompe,
         * et un praticien pressé peut remplir le formulaire plus vite que le délai minimum.
         * L'effacer d'office perdrait un client en silence.</p>
         *
         * <p>Elle ne déclenche aucun e-mail — ni accusé au déposant, ni alerte à l'équipe :
         * c'est l'intérêt même de ne pas réveiller l'équipe pour un robot.</p>
         */
        SUSPECT("suspect");

        private final String code;

        Statut(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Statut depuis(String valeur) {
            for (Statut s : values()) {
                if (s.code.equalsIgnoreCase(valeur) || s.name().equalsIgnoreCase(valeur)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Statut inconnu : « " + valeur + " ».");
        }
    }

    /** Ce qu'une ligne du fil raconte. */
    public enum TypeEvenement {
        CREATION("creation"), STATUT("statut"), ASSIGNATION("assignation"), NOTE("note");

        private final String code;

        TypeEvenement(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Les deux familles de la liste du CNG : professions entières, ou spécialités médicales. */
    public enum GroupeSpecialite {
        PROFESSION("profession"), MEDECINE("medecine");

        private final String code;

        GroupeSpecialite(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
