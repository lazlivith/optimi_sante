-- V59 : prix d'achat, marges, et validation des devis
--
-- CE QUE C'EST
-- Optimi Santé achète en gros puis revend : le fichier d'un fournisseur porte un PRIX D'ACHAT, et
-- le prix public s'en déduit par une marge. Jusqu'ici, le prix du fichier devenait le prix de
-- vente — la plateforme revendait au tarif grossiste.
--
-- DEUX MARGES, DANS CET ORDRE
--   1. la marge de la catégorie du produit, si elle est renseignée (antalgiques, matériel…) ;
--   2. sinon la commission négociée avec le fournisseur.
-- Aucune des deux : le prix d'achat sert de prix de vente, et l'analyse le signale avant écriture.
--
-- CE QUE CETTE MIGRATION NE FAIT PAS
-- Aucun prix existant n'est recalculé : `purchase_price` naît NULL sur les 1 518 références, dont
-- le prix de vente reste celui d'aujourd'hui. Les marges ne s'appliquent qu'aux imports à venir.

-- -------------------------------------------------------------------------------------
-- 1. Prix d'achat du produit
-- -------------------------------------------------------------------------------------
ALTER TABLE products ADD COLUMN IF NOT EXISTS purchase_price numeric(10,2);

COMMENT ON COLUMN products.purchase_price IS
    'Prix d''achat grossiste, tel que déposé par le fournisseur. NULL pour les références saisies à la main.';

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_purchase_price_check;
ALTER TABLE products ADD CONSTRAINT products_purchase_price_check
    CHECK (purchase_price IS NULL OR purchase_price >= 0);

-- -------------------------------------------------------------------------------------
-- 2. Marge par catégorie
-- -------------------------------------------------------------------------------------
ALTER TABLE categories ADD COLUMN IF NOT EXISTS margin_rate numeric(5,2);

COMMENT ON COLUMN categories.margin_rate IS
    'Marge appliquée au prix d''achat des produits de cette catégorie, en pourcentage. NULL = on retombe sur la commission du fournisseur.';

ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_margin_rate_check;
ALTER TABLE categories ADD CONSTRAINT categories_margin_rate_check
    -- 300 % de marge est déjà énorme ; au-delà, c'est une saisie erronée (300 au lieu de 30).
    CHECK (margin_rate IS NULL OR (margin_rate >= 0 AND margin_rate <= 300));

-- -------------------------------------------------------------------------------------
-- 3. Validation d'un devis : contrainte alignée sur l'énumération Java
-- -------------------------------------------------------------------------------------
-- CORRECTIF. `OrderStatus` connaît VALIDATED et REJECTED, l'écran « Devis B2B » les envoie, mais la
-- contrainte posée en V1 ne les acceptait pas : valider un devis répondait 400 et le statut ne
-- changeait jamais. Vérifié sur la base de travail avant correction.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'VALIDATED', 'REJECTED', 'CANCELLED'));

-- -------------------------------------------------------------------------------------
-- 4. Remise accordée sur un devis
-- -------------------------------------------------------------------------------------
-- `discount_amount` existe déjà (codes promo) et porte le montant. On ajoute le TAUX saisi par
-- l'administration et qui l'a accordé : sans cela, un devis remisé ne dit plus ni de combien ni
-- par qui, une fois le montant fondu dans le total.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS quote_discount_rate numeric(5,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS quote_adjusted_by uuid;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS quote_adjusted_at timestamptz;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_quote_discount_rate_check;
ALTER TABLE orders ADD CONSTRAINT orders_quote_discount_rate_check
    CHECK (quote_discount_rate IS NULL OR (quote_discount_rate >= 0 AND quote_discount_rate <= 100));
