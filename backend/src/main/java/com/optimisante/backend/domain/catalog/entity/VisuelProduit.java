package com.optimisante.backend.domain.catalog.entity;

/**
 * Ce qui distingue une vraie photo de produit d'un visuel de remplissage.
 *
 * <p><b>Une seule règle, et c'est tout l'objet de cette classe.</b> Elle existait en quatre
 * exemplaires : trois requêtes SQL — deux dans {@code ProductRepository}, une dans
 * {@code CategoryRepository} — et une fonction TypeScript. Les quatre reconnaissaient
 * {@code medical-placeholder} et {@code unsplash}. Aucune ne reconnaissait
 * {@code prod_fallback_}, apparu depuis : <b>48 produits sur 1 529</b> portaient donc une
 * photo d'illustration que tout le système tenait pour une vraie photo. Ils ne figuraient
 * pas dans « produits à illustrer », et leur rayon passait pour illustré.</p>
 *
 * <p>Quatre copies d'une règle, c'est quatre occasions d'en oublier une. Les motifs vivent
 * désormais ici, et les requêtes les reçoivent en paramètre plutôt que de les réécrire.</p>
 */
public final class VisuelProduit {

    /**
     * Les motifs, au format attendu par {@code LIKE} de PostgreSQL.
     *
     * <p>{@code medical-placeholder.jpg} est le visuel générique historique, partagé par 109
     * fiches. {@code prod_fallback_} préfixe les images de repli déposées lors des imports,
     * une par produit — ce qui les rendait indétectables à l'adresse seule. {@code unsplash}
     * désigne les photos d'illustration reprises d'une banque d'images.</p>
     */
    public static final String[] MOTIFS = {
            "%medical-placeholder%",
            "%prod_fallback_%",
            "%unsplash%",
    };

    private VisuelProduit() {
    }

    /**
     * Ce produit porte-t-il un visuel de remplissage ?
     *
     * <p>Une adresse absente ou vide en est un : il n'y a rien à montrer. Le reste se
     * reconnaît aux motifs ci-dessus.</p>
     */
    public static boolean estFactice(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return true;
        }
        String adresse = imageUrl.toLowerCase(java.util.Locale.ROOT);
        for (String motif : MOTIFS) {
            // Les « % » encadrent le motif pour SQL ; ici on cherche la sous-chaîne nue.
            if (adresse.contains(motif.replace("%", ""))) {
                return true;
            }
        }
        return false;
    }
}
