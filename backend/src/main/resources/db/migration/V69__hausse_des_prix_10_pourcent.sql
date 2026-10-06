-- Hausse tarifaire de 10 %, octobre 2026.
--
-- ── Ce qui est augmenté, et ce qui ne l'est pas ─────────────────────────────────────────
-- Le prix de vente (`base_price`) et le prix promotionnel (`promo_price`). Le promotionnel
-- suit la même hausse à dessein : le laisser en place creuserait la remise d'autant, alors
-- qu'elle a été décidée en proportion du prix et non en valeur absolue.
--
-- Le prix d'achat (`purchase_price`) ne bouge PAS : c'est ce que le fournisseur facture, et
-- il ne change pas parce que nous vendons plus cher. L'augmenter fausserait toutes les marges.
--
-- Les commandes passées ne bougent pas davantage. Leurs lignes portent le prix figé à
-- l'encaissement : une pièce comptable déjà émise ne se réécrit pas. Les formations non plus,
-- dont les tarifs sont fixés par les établissements partenaires.
--
-- ── Les prix sont TTC ───────────────────────────────────────────────────────────────────
-- Conformément aux conditions générales (§ 2.2). La hausse est donc une simple multiplication :
-- elle n'interagit pas avec la grille de TVA posée en V68, qui sert à EXTRAIRE la taxe que ces
-- prix contiennent déjà. Un prix à 5,5 % et un prix à 20 % augmentent exactement pareil.
--
-- ── Revenir en arrière ──────────────────────────────────────────────────────────────────
-- Les prix d'avant sont conservés dans `products_prix_avant_hausse_202610`. Restauration :
--
--   UPDATE products p SET base_price = a.base_price, promo_price = a.promo_price
--   FROM products_prix_avant_hausse_202610 a WHERE a.product_id = p.id;
--
-- Une hausse de prix se décide ; elle ne se devine pas depuis une sauvegarde de base perdue.

CREATE TABLE products_prix_avant_hausse_202610 (
    product_id  uuid PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    base_price  numeric(10,2) NOT NULL,
    promo_price numeric(10,2),
    releve_le   timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE products_prix_avant_hausse_202610 IS
    'Prix de vente avant la hausse de 10 % d''octobre 2026 (V69). Conservé pour pouvoir '
    'revenir en arrière ; ne sert à aucun calcul.';

INSERT INTO products_prix_avant_hausse_202610 (product_id, base_price, promo_price)
SELECT id, base_price, promo_price FROM products WHERE deleted_at IS NULL;

-- L'arrondi au centime se fait sur le résultat, et non sur un prix hors taxes reconstitué :
-- les prix sont stockés TTC, c'est donc le TTC qui doit tomber juste. 57,09 € donnent 62,80 €.
UPDATE products
SET base_price  = round(base_price * 1.10, 2),
    promo_price = CASE WHEN promo_price IS NULL THEN NULL
                       ELSE round(promo_price * 1.10, 2) END
WHERE deleted_at IS NULL;
