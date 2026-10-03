-- TVA : un taux par produit, et le taux figé sur chaque ligne de commande.
--
-- POURQUOI LES PRIX NE CHANGENT PAS. Les prix du catalogue sont annoncés toutes taxes
-- comprises, et les CGV l'écrivent noir sur blanc. La TVA est donc EXTRAITE de ce prix, pas
-- ajoutée : un article à 971 € reste à 971 €, et la facture montre 809,17 € HT + 161,83 € de
-- TVA. Ajouter 20 % aurait augmenté tout le catalogue du jour au lendemain.
--
-- POURQUOI LE TAUX EST FIGÉ SUR LA LIGNE DE COMMANDE. Un document fiscal doit refléter le
-- taux applicable au jour de la vente. Si le taux d'un produit est corrigé plus tard — ou si
-- la loi change — les factures déjà émises ne doivent pas se mettre à jour toutes seules :
-- elles décriraient alors une opération qui n'a jamais eu lieu.
--
-- POURQUOI LA VALEUR PAR DÉFAUT EST NULLE, ET NON 20. Un taux inscrit par défaut sur
-- 1500 produits serait une déclaration fiscale faite par un script. `NULL` dit « non
-- renseigné » et laisse la résolution se faire en cascade — produit, puis catégorie, puis
-- taux normal — jusqu'à ce que le comptable fournisse la table des taux par famille.

ALTER TABLE products   ADD COLUMN IF NOT EXISTS vat_rate numeric(4,2);
ALTER TABLE categories ADD COLUMN IF NOT EXISTS vat_rate numeric(4,2);

COMMENT ON COLUMN products.vat_rate IS
    'Taux de TVA applicable a ce produit, en pourcentage. NULL : hériter de la catégorie.';
COMMENT ON COLUMN categories.vat_rate IS
    'Taux par defaut des produits de la categorie, en pourcentage. NULL : taux normal.';

-- Les taux en vigueur en France. La contrainte interdit une saisie fantaisiste — 19,6 % n'a
-- plus cours depuis 2014, et une faute de frappe sur un taux se paie en redressement.
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_vat_rate_check;
ALTER TABLE products ADD CONSTRAINT products_vat_rate_check
    CHECK (vat_rate IS NULL OR vat_rate IN (0.00, 2.10, 5.50, 10.00, 20.00));

ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_vat_rate_check;
ALTER TABLE categories ADD CONSTRAINT categories_vat_rate_check
    CHECK (vat_rate IS NULL OR vat_rate IN (0.00, 2.10, 5.50, 10.00, 20.00));

-- Le taux retenu au moment de la vente, recopié sur la ligne. Les commandes déjà passées
-- prennent le taux normal : c'est ce qui leur a été appliqué de fait, puisque les prix
-- affichés étaient TTC et qu'aucun autre taux n'existait.
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS vat_rate numeric(4,2) NOT NULL DEFAULT 20.00;

COMMENT ON COLUMN order_items.vat_rate IS
    'Taux applique au moment de la vente, fige. Ne suit pas les corrections ulterieures.';
