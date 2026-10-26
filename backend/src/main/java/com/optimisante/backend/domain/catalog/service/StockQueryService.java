package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.domain.catalog.dto.StockDtos.LigneStock;
import com.optimisante.backend.domain.catalog.dto.StockDtos.ResumeStock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lectures de l'écran « Stock ».
 *
 * <p>En SQL et non via JPA : chaque ligne a besoin de la somme des réservations actives du
 * produit, et la calculer en Java imposerait une requête par produit — mille cinq cents
 * allers-retours pour afficher une page. Le calcul se fait ici en une seule passe.</p>
 *
 * <p>Ce que le client peut commander n'est pas le stock, mais le stock <b>moins</b> ce que des
 * paniers en cours retiennent. Afficher le premier en le nommant « disponible » promettrait
 * des unités déjà promises à quelqu'un d'autre.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockQueryService {

    private final JdbcTemplate jdbc;

    /** Réservations encore valides, agrégées par produit. Factorisé : trois requêtes s'en servent. */
    private static final String RESERVATIONS = """
            LEFT JOIN (
                SELECT product_id, COALESCE(SUM(quantity), 0) AS reserve
                FROM stock_reservations
                WHERE expires_at > now()
                GROUP BY product_id
            ) r ON r.product_id = p.id
            """;

    private static final String BASE = """
            SELECT p.id, p.name, p.slug, c.name AS categorie,
                   COALESCE(p.stock_quantity, 0) AS stock,
                   COALESCE(r.reserve, 0) AS reserve,
                   COALESCE(p.stock_threshold, 0) AS seuil,
                   p.stock_inventorie_le, p.is_active
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
            """ + RESERVATIONS + """
            WHERE p.deleted_at IS NULL
            """;

    /**
     * @param filtre « rupture », « critique », « jamais_inventorie » ou rien du tout.
     */
    @Transactional(readOnly = true)
    public List<LigneStock> lignes(String recherche, String filtre, int limite) {
        StringBuilder sql = new StringBuilder(BASE);
        List<Object> args = new ArrayList<>();

        if (recherche != null && !recherche.isBlank()) {
            sql.append(" AND (LOWER(p.name) LIKE ? OR LOWER(p.slug) LIKE ?)");
            String terme = "%" + recherche.trim().toLowerCase() + "%";
            args.add(terme);
            args.add(terme);
        }

        switch (filtre == null ? "" : filtre) {
            case "rupture" -> sql.append(" AND COALESCE(p.stock_quantity, 0) <= 0");
            case "critique" -> sql.append(" AND COALESCE(p.stock_quantity, 0) > 0"
                    + " AND COALESCE(p.stock_quantity, 0) <= COALESCE(p.stock_threshold, 0)");
            case "jamais_inventorie" -> sql.append(" AND p.stock_inventorie_le IS NULL");
            default -> { /* pas de filtre : tout le catalogue */ }
        }

        // Le plus urgent d'abord : ce qui manque, puis ce qui va manquer.
        sql.append(" ORDER BY COALESCE(p.stock_quantity, 0) ASC, p.name ASC LIMIT ?");
        args.add(limite);

        return jdbc.query(sql.toString(), (rs, i) -> {
            int stock = rs.getInt("stock");
            int reserve = rs.getInt("reserve");
            int seuil = rs.getInt("seuil");
            OffsetDateTime inventorie = rs.getObject("stock_inventorie_le", OffsetDateTime.class);
            return new LigneStock(
                    UUID.fromString(rs.getString("id")),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("categorie"),
                    stock,
                    reserve,
                    Math.max(0, stock - reserve),
                    seuil,
                    etat(stock, seuil, inventorie),
                    inventorie,
                    rs.getBoolean("is_active"));
        }, args.toArray());
    }

    /**
     * L'état affiché, du plus grave au plus rassurant.
     *
     * <p>« Jamais inventorié » passe avant « correct » mais après les manques : un produit à
     * zéro est invendable, que son chiffre soit compté ou hérité de l'import. En revanche,
     * annoncer « stock correct » sur une quantité que personne n'a vérifiée serait une
     * affirmation que rien ne soutient.</p>
     */
    private static String etat(int stock, int seuil, OffsetDateTime inventorie) {
        if (stock <= 0) {
            return "RUPTURE";
        }
        if (seuil > 0 && stock <= seuil) {
            return "CRITIQUE";
        }
        return inventorie == null ? "NON_INVENTORIE" : "OK";
    }

    @Transactional(readOnly = true)
    public ResumeStock resume() {
        String sql = """
                SELECT
                  COUNT(*) AS total,
                  COUNT(*) FILTER (WHERE COALESCE(p.stock_quantity, 0) <= 0) AS rupture,
                  COUNT(*) FILTER (WHERE COALESCE(p.stock_quantity, 0) > 0
                                     AND COALESCE(p.stock_quantity, 0) <= COALESCE(p.stock_threshold, 0)) AS sous_seuil,
                  COUNT(*) FILTER (WHERE p.stock_inventorie_le IS NULL) AS jamais,
                  COALESCE(SUM(COALESCE(p.stock_quantity, 0)), 0) AS unites,
                  COALESCE(SUM(COALESCE(r.reserve, 0)), 0) AS reservees
                FROM products p
                """ + RESERVATIONS + """
                WHERE p.deleted_at IS NULL AND p.is_active = true
                """;

        Map<String, Object> row = jdbc.queryForMap(sql);
        return new ResumeStock(
                nombre(row.get("total")),
                nombre(row.get("rupture")),
                nombre(row.get("sous_seuil")),
                nombre(row.get("jamais")),
                nombre(row.get("unites")),
                nombre(row.get("reservees")));
    }

    private static long nombre(Object valeur) {
        return valeur instanceof Number n ? n.longValue() : 0L;
    }
}
