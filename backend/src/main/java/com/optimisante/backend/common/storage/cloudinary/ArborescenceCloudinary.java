package com.optimisante.backend.common.storage.cloudinary;

import com.optimisante.backend.common.storage.DossierStockage;

import java.util.regex.Pattern;

/**
 * Traduit un {@link DossierStockage} en dossier Cloudinary : {@code racine/environnement/chemin}.
 *
 * <p>La racine et l'environnement sont vérifiés au démarrage : une valeur contenant « / » ou
 * « .. » écrirait hors de la racine, dans les dossiers d'un autre projet du compte.</p>
 */
final class ArborescenceCloudinary {

    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");

    private final String prefixe;

    ArborescenceCloudinary(String racine, String environnement) {
        this.prefixe = segment(racine, "app.cloudinary.root-folder") + "/"
                + segment(environnement, "app.cloudinary.environment");
    }

    /** Dossier complet, ex. {@code optimisante/dev/dossiers-candidats/pieces}. */
    String dossier(DossierStockage dossier) {
        return prefixe + "/" + dossier.chemin();
    }

    String prefixe() {
        return prefixe;
    }

    private static String segment(String valeur, String propriete) {
        String v = valeur == null ? "" : valeur.trim().toLowerCase(java.util.Locale.ROOT);
        if (!SEGMENT.matcher(v).matches()) {
            throw new IllegalStateException(propriete + " doit être un seul segment en minuscules, chiffres et "
                    + "tirets (reçu : « " + valeur + " »).");
        }
        return v;
    }
}
