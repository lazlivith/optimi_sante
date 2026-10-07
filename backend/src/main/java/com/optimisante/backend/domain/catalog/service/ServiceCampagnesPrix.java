package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Les révisions tarifaires, appliquées depuis l'administration.
 *
 * <p><b>Pourquoi cela ne reste pas une migration SQL.</b> La hausse de 10 % d'octobre a demandé
 * une migration écrite à la main, avec sa table de sauvegarde dédiée. Rejouer cela à chaque
 * révision ferait dépendre une décision commerciale d'un déploiement — et d'un développeur
 * disponible le jour où le fournisseur annonce ses nouveaux tarifs.</p>
 *
 * <p><b>Pourquoi l'annulation restitue les prix et ne redivise pas.</b> L'arrondi au centime
 * n'est pas réversible : 0,40 € augmenté de 10 % donne 0,44 €, que redivisé par 1,10 rend
 * 0,40 € ; mais 0,45 € donne 0,50 €, qui redivisé rend 0,4545… et s'arrondit à 0,45 € — par
 * chance. Sur quinze cents produits, « par chance » ne suffit pas. Chaque prix d'avant est
 * donc conservé, et l'annulation le restitue tel quel.</p>
 *
 * <p><b>Les commandes passées ne bougent jamais.</b> Leur prix est figé à l'encaissement : une
 * pièce comptable déjà émise ne se réécrit pas, ni par une hausse, ni par son annulation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceCampagnesPrix {

    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    /** Ce qu'une révision changerait, sans rien changer. */
    public record Apercu(int produitsTouches, BigDecimal prixMoyenActuel,
                         BigDecimal prixMoyenApres, BigDecimal prixMinApres,
                         BigDecimal prixMaxApres) {}

    /** Une révision déjà appliquée, telle que l'écran la liste. */
    public record Campagne(UUID id, BigDecimal pourcentage, UUID categorieId,
                           String categorieNom, int produitsTouches, String libelle,
                           OffsetDateTime appliqueeLe, String appliqueePar,
                           OffsetDateTime annuleeLe, String annuleePar) {}

    /**
     * Ce que donnerait la révision.
     *
     * <p>Calculé par la base, avec la même expression que l'application : une estimation faite
     * autrement finirait par annoncer un chiffre que l'opération ne produit pas.</p>
     */
    @Transactional(readOnly = true)
    public Apercu apercu(BigDecimal pourcentage, UUID categorieId) {
        verifierPourcentage(pourcentage);
        BigDecimal facteur = facteur(pourcentage);

        return jdbc.queryForObject("""
                SELECT count(*)::int                                        AS n,
                       coalesce(round(avg(base_price), 2), 0)               AS moyenne_avant,
                       coalesce(round(avg(round(base_price * ?, 2)), 2), 0) AS moyenne_apres,
                       coalesce(min(round(base_price * ?, 2)), 0)           AS min_apres,
                       coalesce(max(round(base_price * ?, 2)), 0)           AS max_apres
                FROM products
                WHERE deleted_at IS NULL AND tenant_id = ?
                  AND (CAST(? AS uuid) IS NULL OR category_id = CAST(? AS uuid))
                """,
                (rs, i) -> new Apercu(rs.getInt("n"), rs.getBigDecimal("moyenne_avant"),
                        rs.getBigDecimal("moyenne_apres"), rs.getBigDecimal("min_apres"),
                        rs.getBigDecimal("max_apres")),
                facteur, facteur, facteur, tenantRequis(),
                texte(categorieId), texte(categorieId));
    }

    /**
     * Applique la révision et enregistre de quoi l'annuler.
     *
     * <p>L'ordre compte : les prix d'avant sont relevés <b>avant</b> la mise à jour, dans la
     * même transaction. Les relever après rendrait l'annulation impossible, et les relever
     * dans une transaction séparée laisserait une fenêtre où la hausse serait appliquée sans
     * filet.</p>
     */
    @Transactional
    public UUID appliquer(BigDecimal pourcentage, UUID categorieId, String libelle) {
        verifierPourcentage(pourcentage);
        UUID tenantId = tenantRequis();
        BigDecimal facteur = facteur(pourcentage);
        UUID campagneId = UUID.randomUUID();

        // L'entete d'abord : les lignes la referencent par cle etrangere. Le compte est inconnu
        // a ce stade et se corrige juste apres ; si aucune ligne n'est ecrite, la transaction
        // entiere est annulee et l'entete disparait avec elle.
        jdbc.update("""
                INSERT INTO campagnes_prix (id, tenant_id, pourcentage, categorie_id,
                                            produits_touches, libelle, appliquee_par)
                VALUES (?, ?, ?, CAST(? AS uuid), 0, ?, CAST(? AS uuid))
                """, campagneId, tenantId, pourcentage, texte(categorieId),
                libelle, texte(utilisateurCourant()));

        int touches = jdbc.update("""
                INSERT INTO campagnes_prix_lignes (campagne_id, product_id, base_price_avant,
                                                   promo_price_avant)
                SELECT ?, id, base_price, promo_price
                FROM products
                WHERE deleted_at IS NULL AND tenant_id = ?
                  AND (CAST(? AS uuid) IS NULL OR category_id = CAST(? AS uuid))
                """, campagneId, tenantId, texte(categorieId), texte(categorieId));

        if (touches == 0) {
            throw new IllegalArgumentException(
                    "Aucun produit ne correspond : la révision n'a pas été appliquée.");
        }

        jdbc.update("UPDATE campagnes_prix SET produits_touches = ? WHERE id = ?",
                touches, campagneId);

        jdbc.update("""
                UPDATE products p
                SET base_price  = round(p.base_price * ?, 2),
                    promo_price = CASE WHEN p.promo_price IS NULL THEN NULL
                                       ELSE round(p.promo_price * ?, 2) END
                FROM campagnes_prix_lignes l
                WHERE l.campagne_id = ? AND l.product_id = p.id
                """, facteur, facteur, campagneId);

        auditService.record("PRIX_REVISION", "CAMPAGNE_PRIX", campagneId.toString(),
                "Révision de " + pourcentage + " % sur " + touches + " produit(s)"
                + (categorieId == null ? ", tout le catalogue" : ", une catégorie"), null);
        log.info("Révision tarifaire {} % appliquée à {} produits (campagne {}).",
                pourcentage, touches, campagneId);
        return campagneId;
    }

    /**
     * Annule une révision en restituant les prix d'avant.
     *
     * <p>Une révision postérieure a pu toucher les mêmes produits : annuler la première
     * écraserait alors la seconde. On refuse plutôt que de produire un prix que personne n'a
     * décidé — c'est à l'administrateur d'annuler dans l'ordre inverse.</p>
     */
    @Transactional
    public int annuler(UUID campagneId) {
        UUID tenantId = tenantRequis();

        Boolean dejaAnnulee = jdbc.queryForObject(
                "SELECT annulee_le IS NOT NULL FROM campagnes_prix WHERE id = ? AND tenant_id = ?",
                Boolean.class, campagneId, tenantId);
        if (dejaAnnulee == null) {
            throw new IllegalArgumentException("Révision introuvable.");
        }
        if (dejaAnnulee) {
            throw new IllegalArgumentException("Cette révision a déjà été annulée.");
        }

        Integer posterieures = jdbc.queryForObject("""
                SELECT count(*)::int FROM campagnes_prix c
                WHERE c.tenant_id = ? AND c.annulee_le IS NULL
                  AND c.appliquee_le > (SELECT appliquee_le FROM campagnes_prix WHERE id = ?)
                  AND EXISTS (SELECT 1 FROM campagnes_prix_lignes a
                              JOIN campagnes_prix_lignes b ON b.product_id = a.product_id
                              WHERE a.campagne_id = ? AND b.campagne_id = c.id)
                """, Integer.class, tenantId, campagneId, campagneId);
        if (posterieures != null && posterieures > 0) {
            throw new IllegalArgumentException(
                    "Une révision plus récente porte sur les mêmes produits. Annulez-la "
                    + "d'abord : restituer ces prix écraserait la suivante.");
        }

        int restaures = jdbc.update("""
                UPDATE products p
                SET base_price = l.base_price_avant, promo_price = l.promo_price_avant
                FROM campagnes_prix_lignes l
                WHERE l.campagne_id = ? AND l.product_id = p.id AND p.deleted_at IS NULL
                """, campagneId);

        jdbc.update("""
                UPDATE campagnes_prix SET annulee_le = now(), annulee_par = CAST(? AS uuid)
                WHERE id = ?
                """, texte(utilisateurCourant()), campagneId);

        auditService.record("PRIX_REVISION_ANNULEE", "CAMPAGNE_PRIX", campagneId.toString(),
                "Prix restitués sur " + restaures + " produit(s)", null);
        log.info("Révision {} annulée : {} prix restitués.", campagneId, restaures);
        return restaures;
    }

    @Transactional(readOnly = true)
    public List<Campagne> historique() {
        return jdbc.query("""
                SELECT c.id, c.pourcentage, c.categorie_id, cat.name AS categorie_nom,
                       c.produits_touches, c.libelle, c.appliquee_le, c.annulee_le,
                       ua.email AS applique_par, un.email AS annule_par
                FROM campagnes_prix c
                LEFT JOIN categories cat ON cat.id = c.categorie_id
                LEFT JOIN users ua ON ua.id = c.appliquee_par
                LEFT JOIN users un ON un.id = c.annulee_par
                WHERE c.tenant_id = ?
                ORDER BY c.appliquee_le DESC
                LIMIT 50
                """,
                (rs, i) -> new Campagne(
                        rs.getObject("id", UUID.class), rs.getBigDecimal("pourcentage"),
                        rs.getObject("categorie_id", UUID.class), rs.getString("categorie_nom"),
                        rs.getInt("produits_touches"), rs.getString("libelle"),
                        rs.getObject("appliquee_le", OffsetDateTime.class),
                        rs.getString("applique_par"),
                        rs.getObject("annulee_le", OffsetDateTime.class),
                        rs.getString("annule_par")),
                tenantRequis());
    }

    // ── Interne ─────────────────────────────────────────────────────────────────────────

    /**
     * Le facteur multiplicateur. {@code +10} donne 1,10 ; {@code -5} donne 0,95.
     *
     * <p>Six décimales : une baisse de 33,333 % doit rester fidèle, et tronquer ici décalerait
     * chaque prix du catalogue.</p>
     */
    private static BigDecimal facteur(BigDecimal pourcentage) {
        return BigDecimal.ONE.add(
                pourcentage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
    }

    private static void verifierPourcentage(BigDecimal pourcentage) {
        if (pourcentage == null || pourcentage.signum() == 0) {
            throw new IllegalArgumentException("Indiquez un pourcentage non nul.");
        }
        if (pourcentage.compareTo(BigDecimal.valueOf(-90)) < 0
                || pourcentage.compareTo(BigDecimal.valueOf(500)) > 0) {
            throw new IllegalArgumentException(
                    "Le pourcentage doit être compris entre -90 et +500 %. Au-delà, c'est "
                    + "vraisemblablement une erreur de saisie.");
        }
    }

    private static String texte(UUID valeur) {
        return valeur == null ? null : valeur.toString();
    }

    private static UUID utilisateurCourant() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        try {
            return UUID.fromString(auth.getPrincipal().toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID tenantRequis() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        return tenantId;
    }
}
