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

    /**
     * Dossier complet suivi d'un sous-dossier, ex.
     * {@code optimisante/dev/catalogue/produits/lcm}.
     *
     * <p>Le sous-dossier est assaini avant d'être ajouté : il vient du code d'un fournisseur,
     * donc d'une saisie, et un segment porteur de « / » ou de « .. » écrirait ailleurs dans
     * l'arborescence.</p>
     */
    String dossier(DossierStockage dossier, String sousDossier) {
        if (sousDossier == null || sousDossier.isBlank()) {
            return dossier(dossier);
        }
        String assaini = assainir(sousDossier);
        return assaini.isEmpty() ? dossier(dossier) : dossier(dossier) + "/" + assaini;
    }

    /**
     * Ramène un libellé libre à un segment de dossier sûr.
     *
     * <p><b>Assaini plutôt que refusé.</b> La racine et l'environnement viennent de la
     * configuration : une valeur fautive est une erreur de déploiement, et la refuser au
     * démarrage est juste. Un sous-dossier vient du code d'un fournisseur, donc d'une saisie :
     * refuser « LCM Médical » interromprait un import de sept mille lignes pour un espace et un
     * accent. Les signes hors de l'alphabet sont donc remplacés, pas rejetés.</p>
     *
     * @return le segment, ou une chaîne vide si rien d'utilisable n'en reste
     */
    private static String assainir(String libelle) {
        String sansAccent = java.text.Normalizer.normalize(libelle, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String v = sansAccent.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return v.length() > 40 ? v.substring(0, 40).replaceAll("-+$", "") : v;
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
