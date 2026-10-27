package com.optimisante.backend.domain.catalog;

import com.optimisante.backend.ConfigurationBaseDeTest;
import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.orders.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Le stock ne se décrémente que là où nous le tenons — vérifié à travers {@code OrderService}.
 *
 * <p><b>Pourquoi à travers la commande, et non sur le service de stock seul.</b> Le défaut
 * vivait dans le chemin de la vente : {@code OrderService} appelait {@code sortieVente} pour
 * chaque ligne payée, sans regarder ce que valait le chiffre. Un test sur {@code StockService}
 * isolé n'aurait rien montré, puisque le service faisait exactement ce qu'on lui demandait.</p>
 *
 * <p><b>Ce que le défaut coûtait.</b> 1 464 produits sur 1 497 portaient le 50 posé par défaut
 * à l'import. Chaque vente érodait ce nombre inventé ; onze étaient déjà à 49. Au cinquantième
 * exemplaire vendu, la référence serait devenue invendable sans que rien ne manque en
 * entrepôt, et sans qu'aucune alerte ne le dise.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class ModeStockVenteTest {

    @Autowired private OrderService commandes;
    @Autowired private JdbcTemplate jdbc;

    private static final int STOCK_DEPART = 50;

    /**
     * Le reçu de paiement lit les paramètres de la plateforme, qui exigent un tenant.
     *
     * <p>En service, il est posé par l'en-tête de la requête ; ici, par ce bloc. Sans lui, la
     * confirmation lève au moment d'émettre le reçu et la transaction est annulée — le stock
     * paraîtrait alors intact pour une raison sans rapport avec ce qu'on teste.</p>
     */
    @BeforeEach
    void poserLeTenant() {
        TenantContext.setTenantId(tenant());
    }

    @AfterEach
    void retirerLeTenant() {
        TenantContext.clear();
    }

    private UUID tenant() {
        return jdbc.queryForObject("SELECT id FROM tenants WHERE code = 'FR_MAIN'", UUID.class);
    }

    private UUID poserProduit(String mode) {
        UUID id = UUID.randomUUID();
        String suffixe = id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, name, slug, sku, base_price,
                                      stock_quantity, mode_stock, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                """, id, tenant(), "Tensiomètre d'essai", "tensiometre-essai-" + suffixe,
                "SKU-" + suffixe, new BigDecimal("100.00"), STOCK_DEPART, mode);
        return id;
    }

    private UUID poserClient() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, password_hash, role)
                VALUES (?, ?, ?, 'x', 'CLIENT_B2C')
                """, id, tenant(), "client-" + id + "@essai.test");
        return id;
    }

    /** Une commande impayée, prête à être confirmée. */
    private UUID poserCommande(UUID clientId, UUID produitId, int quantite) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, order_number, user_id, payment_status,
                                    status, is_quote, total_amount, created_at)
                VALUES (?, ?, ?, ?, 'UNPAID', 'PENDING', false, ?, now())
                """, id, tenant(), "CMD-" + id.toString().substring(0, 8), clientId,
                new BigDecimal("100.00"));
        jdbc.update("""
                INSERT INTO order_items (id, order_id, product_id, unit_price, quantity, subtotal)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, produitId, new BigDecimal("100.00"), quantite,
                new BigDecimal("100.00").multiply(BigDecimal.valueOf(quantite)));
        return id;
    }

    private int stockDe(UUID produitId) {
        return jdbc.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, produitId);
    }

    private int mouvementsDe(UUID produitId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM stock_mouvements WHERE product_id = ?", Integer.class, produitId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NON_SUIVI", "FOURNISSEUR"})
    @DisplayName("un stock que nous ne tenons pas n'est ni décrémenté ni journalisé")
    void stockNonTenu(String mode) {
        UUID produit = poserProduit(mode);
        UUID commande = poserCommande(poserClient(), produit, 3);

        commandes.confirmOrderPayment(commande);

        assertThat(stockDe(produit))
                .as("un chiffre que personne ne tient ne doit pas bouger à la vente")
                .isEqualTo(STOCK_DEPART);
        assertThat(mouvementsDe(produit))
                .as("et il ne doit pas encombrer le journal d'un mouvement qui ne décrit rien")
                .isZero();
    }

    @Test
    @DisplayName("un stock que nous tenons est décrémenté, et la vente laisse une trace")
    void stockPropre() {
        UUID produit = poserProduit("PROPRE");
        UUID commande = poserCommande(poserClient(), produit, 3);

        commandes.confirmOrderPayment(commande);

        assertThat(stockDe(produit)).isEqualTo(STOCK_DEPART - 3);
        assertThat(mouvementsDe(produit)).isEqualTo(1);
        // La somme des deltas d'un produit doit égaler son stock courant : c'est ce qui rend
        // le journal vérifiable, et c'est la seule propriété qu'on ne peut pas rattraper après
        // coup si elle est rompue.
        Integer sommeDeltas = jdbc.queryForObject(
                "SELECT sum(delta) FROM stock_mouvements WHERE product_id = ?", Integer.class, produit);
        assertThat(STOCK_DEPART + sommeDeltas).isEqualTo(stockDe(produit));
    }

    @Test
    @DisplayName("confirmer deux fois le même paiement ne retire pas deux fois la quantité")
    void confirmationRejouee() {
        // Stripe rejoue ses webhooks : la garde d'idempotence existait déjà sur le statut de
        // paiement, mais rien ne la vérifiait du côté du stock.
        UUID produit = poserProduit("PROPRE");
        UUID commande = poserCommande(poserClient(), produit, 2);

        commandes.confirmOrderPayment(commande);
        commandes.confirmOrderPayment(commande);

        assertThat(stockDe(produit)).isEqualTo(STOCK_DEPART - 2);
        assertThat(mouvementsDe(produit)).isEqualTo(1);
    }
}
