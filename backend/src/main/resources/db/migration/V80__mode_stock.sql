-- Ce qu'on sait du stock d'un produit, et ce qu'on en fait.
--
-- Jusqu'ici, `stock_quantity` était traité comme vrai pour tout le catalogue : vérifié à la
-- commande, réservé trente minutes (vingt-quatre heures sur un devis), décrémenté au paiement.
-- Or 1 464 produits sur 1 497 portent le 50 posé par défaut à l'import, et aucun n'a jamais
-- été compté. Au cinquantième exemplaire vendu d'une référence jamais inventoriée, elle
-- devient invendable — sans alerte, et sans que rien ne manque en entrepôt.
--
-- Le mode dit « ce chiffre est-il tenu à jour, et par qui », pas « ce produit est-il en
-- stock ». C'est la distinction qui manquait : l'absence d'inventaire et une rupture réelle
-- s'écrivaient de la même façon.
--
--   NON_SUIVI   : personne ne compte. Le chiffre est indicatif, il ne bloque rien.
--   PROPRE      : nous comptons, dans notre entrepôt. Vérifié, réservé, décrémenté.
--   FOURNISSEUR : le fournisseur stocke et expédie. Sa quantité est indicative.
--
-- DEFAULT 'NON_SUIVI' : c'est l'état réel des 1 497 produits actuels, pas une précaution.
-- Aucune donnée n'est réécrite, et `stock_quantity` est conservé tel quel.
ALTER TABLE products ADD COLUMN mode_stock varchar(20) NOT NULL DEFAULT 'NON_SUIVI';

ALTER TABLE products ADD CONSTRAINT products_mode_stock_check
    CHECK (mode_stock IN ('NON_SUIVI', 'PROPRE', 'FOURNISSEUR'));

-- L'import fournisseur et la future veille des stocks propres interrogent le catalogue par
-- mode. Un index partiel plutôt qu'un index complet : les produits PROPRE seront une minorité
-- durable, et c'est sur eux que portent les lectures fréquentes.
CREATE INDEX idx_products_mode_propre ON products (tenant_id)
    WHERE mode_stock = 'PROPRE' AND deleted_at IS NULL;

-- De quel import vient ce mouvement.
--
-- Un import de La Centrale Médicale écrira 7 400 mouvements d'un coup : sans cette référence,
-- il serait impossible de les isoler d'un geste humain ou d'une vente, ni d'en retrouver
-- l'ensemble si l'import s'est mal passé. `order_id` joue déjà ce rôle pour une vente.
ALTER TABLE stock_mouvements ADD COLUMN import_id uuid;

CREATE INDEX idx_stock_mouvements_import ON stock_mouvements (import_id)
    WHERE import_id IS NOT NULL;
