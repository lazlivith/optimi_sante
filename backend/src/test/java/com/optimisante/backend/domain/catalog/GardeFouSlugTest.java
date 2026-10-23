package com.optimisante.backend.domain.catalog;

import com.optimisante.backend.ConfigurationBaseDeTest;
import com.optimisante.backend.domain.catalog.entity.Category;
import com.optimisante.backend.domain.catalog.entity.Product;
import com.optimisante.backend.domain.catalog.entity.SlugValide;
import com.optimisante.backend.domain.catalog.repository.CategoryRepository;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.identity.entity.Tenant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Aucun slug hors {@code [a-z0-9-]} n'atteint la base, quelle que soit la route.
 *
 * <p><b>Ce que ce test aurait empêché.</b> 148 produits et une catégorie portaient un slug
 * contenant un {@code %}, reste d'un {@code ®} encodé par un import ancien. Dix pour cent du
 * catalogue était injoignable : encodé dans l'adresse, ce {@code %} devient {@code %25}, que
 * le pare-feu de Spring Security refuse avant le contrôleur.</p>
 *
 * <p><b>Et pourquoi il passe par le dépôt plutôt que par un service.</b> Le générateur de
 * l'administration était déjà correct au moment de l'incident ; l'import fournisseur ne
 * passait pas par lui. Tester un service n'aurait donc rien montré. Ici l'écriture va
 * jusqu'à PostgreSQL : ce qui est vérifié, c'est que <b>la base</b> est inatteignable avec un
 * mauvais slug — par l'administration, par l'import fournisseur, ou par ce qui viendra avec
 * La Centrale Médicale.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class GardeFouSlugTest {

    @Autowired private ProductRepository produits;
    @Autowired private CategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private UUID tenantId() {
        return jdbc.queryForObject("SELECT id FROM tenants WHERE code = 'FR_MAIN'", UUID.class);
    }

    /** Une reference suffit : ce test ne lit aucun champ du tenant. */
    private Tenant tenant() {
        return em.getReference(Tenant.class, tenantId());
    }

    private Product produit(String slug) {
        return Product.builder()
                .tenant(tenant())
                .name("Tensiomètre d'essai")
                .slug(slug)
                .sku("SKU-" + UUID.randomUUID().toString().substring(0, 8))
                .basePrice(new BigDecimal("10.00"))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Le slug exact de l'incident : un « ® » encodé en texte par l'import.
            "tensiometre-electronique-microlife%c2%acae-watch-bp-vascular-abi",
            // Déjà encodé une fois de plus, tel qu'il partait dans l'adresse.
            "tensiometre%25c2%25ac",
            "Tensiometre-Majuscules",
            "tensiomètre-accentué",
            "tensiometre avec espaces",
            "tensiometre/avec/barres",
            "",
    })
    @DisplayName("un produit au slug invalide n'atteint pas la base")
    void produitRefuse(String slug) {
        assertThatThrownBy(() -> produits.saveAndFlush(produit(slug)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un slug absent est refusé comme un slug invalide")
    void slugNul() {
        // La colonne est « not null » en base ; sans ce contrôle l'erreur remonterait de
        // PostgreSQL, bien plus tard et sans dire quel produit est en cause.
        assertThatThrownBy(() -> produits.saveAndFlush(produit(null)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un slug correct passe, lui")
    void produitAccepte() {
        String slug = "tensiometre-electronique-microlife-watch-bp-" + UUID.randomUUID();
        assertThatCode(() -> produits.saveAndFlush(produit(slug))).doesNotThrowAnyException();
        assertThat(produits.findByTenantIdAndSlug(tenantId(), slug)).isPresent();
    }

    @Test
    @DisplayName("la MODIFICATION d'un produit est contrôlée elle aussi")
    void modificationRefusee() {
        // Une fiche entre une fois et se modifie cent fois : un import qui réécrit le slug
        // d'un produit existant échapperait à un contrôle posé à la seule création.
        Product p = produits.saveAndFlush(produit("slug-correct-" + UUID.randomUUID()));
        p.setSlug("slug%c2%acae-remis-par-un-import");
        assertThatThrownBy(() -> produits.saveAndFlush(p))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("une catégorie au slug invalide n'atteint pas la base non plus")
    void categorieRefusee() {
        // Le cas réel : « Bandes plâtrées & accessoires » rendait ses neuf fiches
        // injoignables pour une seule ligne en base.
        Category c = Category.builder()
                .tenant(tenant())
                .name("Bandes plâtrées & accessoires")
                .slug("bandes-pl%e2%88%9a%c2%a2trees-accessoires-2")
                .build();
        assertThatThrownBy(() -> categories.saveAndFlush(c))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("le motif dit exactement ce qu'il accepte")
    void motif() {
        assertThat(SlugValide.MOTIF.pattern()).isEqualTo("^[a-z0-9-]+$");
    }
}
