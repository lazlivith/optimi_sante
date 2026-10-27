package com.optimisante.backend.domain.catalog.supplier;

import com.optimisante.backend.ConfigurationBaseDeTest;
import com.optimisante.backend.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qu'un import fournisseur a le droit d'écrire, et ce qu'il doit laisser tranquille.
 *
 * <p><b>Deux défauts constatés le 27 octobre, sur le même chemin.</b> L'import écrivait
 * {@code stock_quantity} directement puis sauvegardait : aucun mouvement, aucun verrou, aucune
 * trace. Un inventaire compté le matin disparaissait à l'import du soir. Et une seule ligne
 * refusée faisait lever au moment d'écrire, ce qui perdait son lot de cinquante lignes
 * <b>et</b> arrêtait l'import — sur un fichier de 7 432 lignes, une faute à la trois-millième
 * emportait les 4 400 suivantes.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class ImportStockEtLignesFautivesTest {

    @Autowired private EcrivainCatalogue ecrivain;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID fournisseurId;

    @BeforeEach
    void preparer() {
        tenantId = jdbc.queryForObject("SELECT id FROM tenants WHERE code = 'FR_MAIN'", UUID.class);
        TenantContext.setTenantId(tenantId);
        fournisseurId = poserFournisseur();
    }

    @AfterEach
    void nettoyer() {
        TenantContext.clear();
    }

    private UUID poserFournisseur() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO suppliers (id, tenant_id, code, company_name, commission_rate)
                VALUES (?, ?, ?, ?, 0)
                """, id, tenantId, "ESSAI-" + id.toString().substring(0, 6), "Fournisseur d'essai");
        return id;
    }

    /** Une ligne de fichier fournisseur, telle que la lecture la produit. */
    private FichierCatalogue.Ligne ligne(int numero, String sku, String nom, Integer stock) {
        return new FichierCatalogue.Ligne(numero, sku, nom, "Une description", new BigDecimal("120.00"),
                new BigDecimal("80.00"), stock, null, List.of(), null);
    }

    private EcrivainCatalogue.Resultat importer(List<FichierCatalogue.Ligne> lignes, UUID importId) {
        List<EcrivainCatalogue.Decision> decisions = ecrivain.decider(tenantId, fournisseurId, lignes);
        return ecrivain.ecrire(tenantId, fournisseurId, importId, decisions, java.util.Map.of());
    }

    private UUID produitParSku(String sku) {
        return jdbc.queryForObject(
                "SELECT id FROM products WHERE lower(sku) = lower(?)", UUID.class, sku);
    }

    // ── Ce que l'import écrit ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("un produit créé par import est en FOURNISSEUR, et son stock arrive par un mouvement")
    void creationTracee() {
        String sku = "SKU-CREATION-" + UUID.randomUUID().toString().substring(0, 6);
        UUID importId = UUID.randomUUID();

        EcrivainCatalogue.Resultat resultat = importer(List.of(ligne(1, sku, "Otoscope d'essai", 12)), importId);

        assertThat(resultat.crees()).isEqualTo(1);
        UUID produit = produitParSku(sku);
        assertThat(jdbc.queryForObject("SELECT mode_stock FROM products WHERE id = ?", String.class, produit))
                .as("un import décrit l'entrepôt d'un tiers : jamais PROPRE")
                .isEqualTo("FOURNISSEUR");
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, produit))
                .isEqualTo(12);
        // La quantité est arrivée par un mouvement, pas par une écriture directe : c'est ce
        // qui rend la somme des mouvements égale au stock, et donc le journal vérifiable.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM stock_mouvements
                 WHERE product_id = ? AND motif = 'MISE_A_JOUR_FOURNISSEUR' AND import_id = ?
                """, Integer.class, produit, importId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("l'import ne touche jamais au stock d'un produit que nous comptons")
    void produitPropreIntact() {
        String sku = "SKU-PROPRE-" + UUID.randomUUID().toString().substring(0, 6);
        UUID produit = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, name, slug, sku, base_price,
                                      stock_quantity, mode_stock, created_at)
                VALUES (?, ?, 'Produit inventorié', ?, ?, 100, 7, 'PROPRE', now())
                """, produit, tenantId, "produit-inventorie-" + produit.toString().substring(0, 8), sku);

        // Le fournisseur annonce 999 : c'est SON entrepôt, pas le nôtre.
        importer(List.of(ligne(1, sku, "Produit inventorié", 999)), UUID.randomUUID());

        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, produit))
                .as("notre comptage fait foi, pas la photographie mensuelle d'un tiers")
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT mode_stock FROM products WHERE id = ?", String.class, produit))
                .as("et l'import ne doit pas non plus le faire redescendre en FOURNISSEUR")
                .isEqualTo("PROPRE");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM stock_mouvements WHERE product_id = ?", Integer.class, produit))
                .isZero();
    }

    // ── Ce que l'import refuse, sans s'arrêter ──────────────────────────────────────────

    @Test
    @DisplayName("une ligne fautive au milieu est écartée, nommée, et les autres passent")
    void ligneFautiveAuMilieu() {
        String bon1 = "SKU-BON1-" + UUID.randomUUID().toString().substring(0, 6);
        String bon2 = "SKU-BON2-" + UUID.randomUUID().toString().substring(0, 6);
        String fautif = "SKU-FAUTIF-" + UUID.randomUUID().toString().substring(0, 6);

        EcrivainCatalogue.Resultat resultat = importer(List.of(
                ligne(1, bon1, "Otoscope A", 5),
                // Un nom vide ne produit aucune adresse : c'est le cas qui faisait lever au
                // flush, après que les deux autres lignes aient été écrites dans la même
                // transaction — donc perdues avec elle.
                ligne(2, fautif, "", 5),
                ligne(3, bon2, "Otoscope B", 5)), UUID.randomUUID());

        assertThat(resultat.crees()).as("les deux lignes saines sont écrites").isEqualTo(2);
        assertThat(resultat.ignores()).isEqualTo(1);
        assertThat(resultat.motifs())
                .as("le rapport doit nommer la ligne du fichier, pas seulement signaler un échec")
                .anyMatch(m -> m.startsWith("Ligne 2 "));
        assertThat(produitParSku(bon1)).isNotNull();
        assertThat(produitParSku(bon2)).isNotNull();
    }

    @Test
    @DisplayName("une quantité négative est refusée sans emporter la ligne suivante")
    void quantiteNegative() {
        String fautif = "SKU-NEG-" + UUID.randomUUID().toString().substring(0, 6);
        String bon = "SKU-APRES-" + UUID.randomUUID().toString().substring(0, 6);

        EcrivainCatalogue.Resultat resultat = importer(List.of(
                ligne(7, fautif, "Otoscope négatif", -3),
                ligne(8, bon, "Otoscope suivant", 4)), UUID.randomUUID());

        assertThat(resultat.ignores()).isEqualTo(1);
        assertThat(resultat.crees()).isEqualTo(1);
        assertThat(resultat.motifs()).anyMatch(m -> m.startsWith("Ligne 7 "));
        assertThat(produitParSku(bon)).isNotNull();
    }
}
