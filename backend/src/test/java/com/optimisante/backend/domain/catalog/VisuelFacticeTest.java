package com.optimisante.backend.domain.catalog;

import com.optimisante.backend.domain.catalog.entity.VisuelProduit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La règle qui distingue une vraie photo d'un visuel de remplissage.
 *
 * <p><b>Pourquoi elle mérite ses propres tests.</b> Elle vivait en quatre exemplaires : trois
 * requêtes SQL et une fonction TypeScript. Les quatre reconnaissaient
 * {@code medical-placeholder} et {@code unsplash} ; aucune ne reconnaissait
 * {@code prod_fallback_}, apparu depuis. Quarante-huit produits sur mille cinq cent
 * vingt-neuf portaient donc une photo d'illustration que tout le système tenait pour une
 * vraie photo : ils n'apparaissaient pas dans « produits à illustrer », et leur rayon passait
 * pour illustré.</p>
 *
 * <p>Quatre copies, c'est quatre occasions d'en oublier une. Une seule règle, et des tests
 * qui disent lesquels des trois cas elle reconnaît.</p>
 */
class VisuelFacticeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // Le visuel générique historique, partagé par 109 fiches.
            "https://res.cloudinary.com/x/image/upload/v1724000000/catalog/medical-placeholder.jpg",
            // L'image de repli déposée aux imports : une PAR PRODUIT, donc indétectable à
            // l'adresse tant qu'on ne cherchait pas le préfixe.
            "https://res.cloudinary.com/x/image/upload/v1787455211/optimisante/products/prod_fallback_902167590.jpg",
            // Une photo reprise d'une banque d'images.
            "https://images.unsplash.com/photo-1576091160550",
            // Les trois adresses posées par la migration V9. Aucune n'a jamais été déposée sur
            // Cloudinary : elles répondent 404 depuis l'origine. Elles manquaient à la règle, et
            // « LOT DE 3 TIRE-TIQUES » passait donc pour illustré avec un cadre vide.
            "https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/default-medical-equipment.jpg",
            "https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/stetho.jpg",
            "https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/microscope.jpg",
    })
    @DisplayName("tous les cas de visuel de remplissage sont reconnus")
    void lesTroisCas(String adresse) {
        assertThat(VisuelProduit.estFactice(adresse)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://res.cloudinary.com/x/image/upload/v1787455213/optimisante/products/prod_902166000.jpg",
            "https://res.cloudinary.com/x/image/upload/v1/catalog/products/7708683a-ecc3.jpg",
            "https://res.cloudinary.com/x/image/upload/echographe-siemens.png",
    })
    @DisplayName("une vraie photo de produit n'est pas prise pour un remplissage")
    void vraiePhoto(String adresse) {
        // « prod_902166000 » ressemble à « prod_fallback_902167590 » à une syllabe près :
        // c'est exactement le genre de voisinage qui fait qu'une règle trop large efface
        // des photos légitimes du catalogue.
        assertThat(VisuelProduit.estFactice(adresse)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("une adresse absente ou vide est un visuel à faire")
    void adresseVide(String adresse) {
        assertThat(VisuelProduit.estFactice(adresse)).isTrue();
    }

    @Test
    @DisplayName("une adresse nulle aussi, sans lever")
    void adresseNulle() {
        // Appelée sur chaque produit d'une page : lever ici ferait échouer le catalogue
        // entier pour une fiche mal remplie.
        assertThat(VisuelProduit.estFactice(null)).isTrue();
    }

    @Test
    @DisplayName("la casse de l'adresse ne change rien")
    void casseIndifferente() {
        assertThat(VisuelProduit.estFactice("HTTPS://X/CATALOG/MEDICAL-PLACEHOLDER.JPG")).isTrue();
        assertThat(VisuelProduit.estFactice("https://X/PROD_FALLBACK_123.JPG")).isTrue();
    }

    @Test
    @DisplayName("les motifs SQL et la règle Java disent la même chose")
    void memeRegleDesDeuxCotes() {
        // Les requêtes reçoivent MOTIFS en paramètre plutôt que de les réécrire : si l'un
        // disparaissait de la liste, les deux côtés cesseraient de le reconnaître ENSEMBLE —
        // ce qui vaut mieux qu'une divergence silencieuse.
        //
        // Cette liste est écrite à la main exprès : ajouter un motif oblige à passer ici, donc
        // à décider sciemment qu'un visuel de plus cesse de compter comme une vraie photo.
        assertThat(VisuelProduit.MOTIFS)
                .containsExactlyInAnyOrder("%medical-placeholder%", "%prod_fallback_%", "%unsplash%",
                        "%default-medical-equipment%", "%stetho.jpg%", "%microscope.jpg%");
        for (String motif : VisuelProduit.MOTIFS) {
            assertThat(VisuelProduit.estFactice("https://x/" + motif.replace("%", "") + ".jpg"))
                    .as("le motif %s doit être reconnu par la règle Java", motif)
                    .isTrue();
        }
    }
}
