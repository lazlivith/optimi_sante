package com.optimisante.backend.domain.orders.shipping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le découpage des destinations desservies.
 *
 * <p>Ce qui est vérifié ici n'est pas la liste pour elle-même, mais deux propriétés qu'une
 * erreur de copie ferait sauter sans bruit : aucun pays ne figure dans deux zones — il serait
 * alors facturé selon l'ordre de déclaration —, et une destination inconnue n'est rattachée à
 * aucune zone plutôt qu'à la première venue.</p>
 */
class ZoneLivraisonTest {

    @Test
    @DisplayName("la France est sa propre zone")
    void france() {
        assertThat(ZoneLivraison.pour("FR")).contains(ZoneLivraison.FRANCE);
    }

    @Test
    @DisplayName("les pays d'où viennent les praticiens sont dans la zone principale")
    void afriqueOuestCentre() {
        for (String pays : new String[]{"SN", "CI", "CM", "CG", "CD", "GA"}) {
            assertThat(ZoneLivraison.pour(pays))
                    .as("pays %s", pays)
                    .contains(ZoneLivraison.AFRIQUE_OUEST_CENTRE);
        }
    }

    @Test
    @DisplayName("le code est lu quelle que soit sa casse, et les espaces sont tolérés")
    void casseEtEspaces() {
        assertThat(ZoneLivraison.pour("ma")).contains(ZoneLivraison.AFRIQUE_NORD);
        assertThat(ZoneLivraison.pour("  SN  ")).contains(ZoneLivraison.AFRIQUE_OUEST_CENTRE);
    }

    @Test
    @DisplayName("une destination non desservie n'est rattachée à aucune zone")
    void destinationInconnue() {
        // Ni rattachee a une zone voisine, ni rangee dans un « reste du monde » : l'appelant
        // est oblige de traiter le cas, et le client recoit une invitation a demander un devis.
        assertThat(ZoneLivraison.pour("US")).isEmpty();
        assertThat(ZoneLivraison.pour("DE")).isEmpty();
        assertThat(ZoneLivraison.pour("")).isEmpty();
        assertThat(ZoneLivraison.pour(null)).isEmpty();
    }

    @Test
    @DisplayName("aucun pays n'appartient à deux zones")
    void aucunRecouvrement() {
        Set<String> vus = new HashSet<>();
        for (ZoneLivraison zone : ZoneLivraison.values()) {
            for (String pays : zone.pays()) {
                assertThat(vus.add(pays))
                        .as("le pays %s figure dans plusieurs zones : il serait facture selon "
                                + "l'ordre de declaration de l'enumeration", pays)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("tous les codes sont au format ISO à deux lettres majuscules")
    void formatDesCodes() {
        Arrays.stream(ZoneLivraison.values())
                .flatMap(z -> z.pays().stream())
                .forEach(pays -> assertThat(pays)
                        .as("code pays %s", pays)
                        .matches("[A-Z]{2}"));
    }
}
