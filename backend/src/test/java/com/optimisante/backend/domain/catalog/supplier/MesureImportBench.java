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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Combien coûte, en temps, d'écrire un mouvement par ligne importée.
 *
 * <p><b>Ce n'est pas un test, et son nom le dit :</b> sans le suffixe {@code Test}, il reste
 * hors de la suite, qui ne doit pas payer deux minutes à chaque exécution pour un chiffre qui
 * ne change qu'avec le code d'écriture. Il se relance à la demande :
 * {@code ./mvnw -o test -Dtest=MesureImportBench -DfailIfNoTests=false}.</p>
 *
 * <p>Mesuré le 27 octobre 2026 sur un poste de développement, PostgreSQL 16 en conteneur :
 * <b>27 s sans mouvement, 61 s avec</b>. Les mouvements doublent donc la durée d'écriture,
 * pour environ une minute sur l'import complet de La Centrale Médicale — négligeable devant
 * le téléchargement des 15 000 visuels, qui domine l'import réel.</p>
 *
 * <p>Mesure demandée avant l'import de La Centrale Médicale : 7 400 références, donc 7 400
 * mouvements au journal là où l'import n'écrivait rien. Le fichier réel sera lu, les images
 * téléchargées et les catégories résolues en plus ; ce test isole la seule part que le
 * changement ajoute — l'écriture en base, par lots de 50 comme en service.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class MesureImportBench {

    private static final int LIGNES = 7_400;
    private static final int TAILLE_LOT = 50;

    @Autowired private EcrivainCatalogue ecrivain;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID fournisseurId;

    @BeforeEach
    void preparer() {
        tenantId = jdbc.queryForObject("SELECT id FROM tenants WHERE code = 'FR_MAIN'", UUID.class);
        TenantContext.setTenantId(tenantId);
        fournisseurId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO suppliers (id, tenant_id, code, company_name, commission_rate)
                VALUES (?, ?, 'MESURE', 'Fournisseur de mesure', 0)
                """, fournisseurId, tenantId);
    }

    @AfterEach
    void nettoyer() {
        TenantContext.clear();
    }

    /** Importe {@code LIGNES} références et rend la durée en secondes. */
    private long importer(String prefixe, Integer stock) {
        List<FichierCatalogue.Ligne> lignes = new ArrayList<>(LIGNES);
        for (int i = 1; i <= LIGNES; i++) {
            lignes.add(new FichierCatalogue.Ligne(i, prefixe + i, "Dispositif " + prefixe + i,
                    "Description " + i, new BigDecimal("120.00"), new BigDecimal("80.00"),
                    stock, null, List.of(), null));
        }
        long debut = System.nanoTime();
        for (int d = 0; d < lignes.size(); d += TAILLE_LOT) {
            List<FichierCatalogue.Ligne> lot = lignes.subList(d, Math.min(d + TAILLE_LOT, lignes.size()));
            List<EcrivainCatalogue.Decision> decisions = ecrivain.decider(tenantId, fournisseurId, lot);
            ecrivain.ecrire(tenantId, fournisseurId, UUID.randomUUID(), decisions, Map.of());
        }
        return (System.nanoTime() - debut) / 1_000_000_000L;
    }

    @Test
    @DisplayName("7 400 lignes importées, avec et sans mouvement de stock par ligne")
    void mesure() {
        // Sans colonne de stock dans le fichier, aucun mouvement n'est écrit : c'est la
        // référence, le coût de l'import tel qu'il était avant.
        long sansMouvements = importer("REF-", null);
        long avecMouvements = importer("MES-", 12);

        int mouvements = jdbc.queryForObject("""
                SELECT count(*) FROM stock_mouvements WHERE motif = 'MISE_A_JOUR_FOURNISSEUR'
                """, Integer.class);

        System.out.printf("%n>>> MESURE sur %d lignes : %d s sans mouvement, %d s avec "
                        + "(%d mouvements écrits)%n%n",
                LIGNES, sansMouvements, avecMouvements, mouvements);

        assertThat(mouvements).isEqualTo(LIGNES);
    }
}
