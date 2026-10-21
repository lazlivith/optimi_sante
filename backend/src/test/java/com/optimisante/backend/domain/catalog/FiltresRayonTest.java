package com.optimisante.backend.domain.catalog;

import com.optimisante.backend.ConfigurationBaseDeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les filtres d'un rayon, vérifiés côté serveur.
 *
 * <p><b>Pourquoi côté serveur et pas dans l'écran.</b> Un rayon contient plus de cent
 * références et se parcourt page par page : filtrer la page affichée ne filtrerait qu'un
 * vingtième du rayon, et le visiteur conclurait qu'il n'y a rien. Les critères partent donc
 * dans la requête, et c'est la base qui répond.</p>
 *
 * <p><b>Et pourquoi un test sur les valeurs refusées.</b> Un intervalle de prix inversé rend
 * naturellement zéro résultat. Le visiteur en déduirait que le rayon est vide dans cette
 * tranche, alors que sa question n'avait pas de sens. Un 400 dit laquelle.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
@WithAnonymousUser
class FiltresRayonTest {

    private static final String ROUTE = "/api/v1/catalog/products";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** Le tenant est posé par en-tête, comme le fait le navigateur. */
    private MockHttpServletRequestBuilder requete() {
        return get(ROUTE).header("X-Tenant-Id", "FR_MAIN");
    }

    private UUID poserCategorie(String nom) {
        UUID tenant = jdbc.queryForObject(
                "SELECT id FROM tenants WHERE code = 'FR_MAIN'", UUID.class);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO categories (id, tenant_id, name, slug)
                VALUES (?, ?, ?, ?)
                """, id, tenant, nom, nom.toLowerCase().replace(' ', '-') + "-" + id);
        return id;
    }

    /** Un produit minimal : seuls les champs que ces filtres interrogent comptent ici. */
    private void poserProduit(UUID categorie, String nom, String prix, boolean surDevis) {
        UUID tenant = jdbc.queryForObject(
                "SELECT id FROM tenants WHERE code = 'FR_MAIN'", UUID.class);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, category_id, name, slug, sku,
                                      base_price, is_quote_only, stock_quantity, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 10, now())
                """, UUID.randomUUID(), tenant, categorie, nom,
                nom.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID(),
                "SKU-" + UUID.randomUUID().toString().substring(0, 8),
                new java.math.BigDecimal(prix), surDevis);
    }

    // ── Ce que les filtres doivent rendre ───────────────────────────────────────────────

    @Test
    @DisplayName("le filtre « sur devis » sépare les deux modes d'achat")
    void filtreSurDevis() throws Exception {
        UUID rayon = poserCategorie("Rayon devis");
        poserProduit(rayon, "Chaise en ligne", "100", false);
        poserProduit(rayon, "Table en ligne", "200", false);
        poserProduit(rayon, "Échographe sur devis", "0", true);

        mvc.perform(requete().param("categoryId", rayon.toString()).param("surDevis", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(requete().param("categoryId", rayon.toString()).param("surDevis", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Sans le critère, le rayon entier.
        mvc.perform(requete().param("categoryId", rayon.toString()))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("un produit sans mention explicite se vend en ligne")
    void mentionAbsenteVautEnLigne() throws Exception {
        UUID rayon = poserCategorie("Rayon ancien");
        poserProduit(rayon, "Fiche ancienne", "50", false);
        jdbc.update("UPDATE products SET is_quote_only = NULL WHERE category_id = ?", rayon);

        // Comparer la colonne seule l'aurait écarté des DEUX réponses : invisible en ligne,
        // invisible sur devis, et introuvable pour le visiteur.
        mvc.perform(requete().param("categoryId", rayon.toString()).param("surDevis", "false"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("l'intervalle de prix porte sur tout le rayon")
    void filtrePrix() throws Exception {
        UUID rayon = poserCategorie("Rayon prix");
        poserProduit(rayon, "Petit", "10", false);
        poserProduit(rayon, "Moyen", "100", false);
        poserProduit(rayon, "Grand", "1000", false);

        mvc.perform(requete().param("categoryId", rayon.toString())
                        .param("minPrice", "50").param("maxPrice", "500"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Moyen"));
    }

    @Test
    @DisplayName("les trois tris rendent l'ordre annoncé")
    void tris() throws Exception {
        UUID rayon = poserCategorie("Rayon tri");
        poserProduit(rayon, "Cher", "900", false);
        poserProduit(rayon, "Bon marché", "10", false);

        mvc.perform(requete().param("categoryId", rayon.toString()).param("tri", "prix-croissant"))
                .andExpect(jsonPath("$.content[0].name").value("Bon marché"));

        mvc.perform(requete().param("categoryId", rayon.toString()).param("tri", "prix-decroissant"))
                .andExpect(jsonPath("$.content[0].name").value("Cher"));

        mvc.perform(requete().param("categoryId", rayon.toString()).param("tri", "nouveautes"))
                .andExpect(status().isOk());

        // « pertinence » est l'ordre naturel : accepté, et traité comme l'absence de tri.
        mvc.perform(requete().param("categoryId", rayon.toString()).param("tri", "pertinence"))
                .andExpect(status().isOk());
    }

    // ── Ce que le serveur doit REFUSER ──────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"basePrice", "name", "category.name", "tenant.id", "'; DROP TABLE"})
    @DisplayName("un tri hors de la liste fermée est refusé, jamais traduit en SQL")
    void triInconnuRefuse(String tri) throws Exception {
        // Pageable laisse passer n'importe quel nom de champ dans « sort= » et Hibernate le
        // traduit. Un nom d'association permettrait de trier sur des tables que cette route
        // n'expose pas ; un nom inconnu ferait une 500. Ici, c'est NOUS qui traduisons.
        mvc.perform(requete().param("tri", tri))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Tri inconnu")));
    }

    @Test
    @DisplayName("un intervalle de prix inversé est une erreur, pas une liste vide")
    void intervalleInverse() throws Exception {
        mvc.perform(requete().param("minPrice", "500").param("maxPrice", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("ne peut pas dépasser")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"minPrice", "maxPrice"})
    @DisplayName("un prix négatif est refusé")
    void prixNegatif(String parametre) throws Exception {
        mvc.perform(requete().param(parametre, "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("négatif")));
    }

    @Test
    @DisplayName("le catalogue répond toujours sans aucun filtre")
    void sansAucunFiltre() throws Exception {
        // Le cas nominal, celui qui doit survivre à tous les ajouts de critères.
        mvc.perform(requete()).andExpect(status().isOk());
    }
}
