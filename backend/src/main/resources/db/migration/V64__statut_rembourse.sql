-- Un remboursement émis depuis Stripe ne redescendait pas dans la plateforme : la commande
-- restait marquée payée. Elle continuait donc de compter dans le chiffre d'affaires, qui
-- n'agrège que les lignes au statut PAID — un revenu annoncé pour de l'argent rendu.
--
-- Ajouter REFUNDED à la contrainte suffit à corriger les deux : le statut devient disponible
-- pour le code, et une commande qui le porte sort d'elle-même des agrégats financiers.
--
-- Le remboursement PARTIEL n'a volontairement pas de statut. La plateforme ne sait pas
-- représenter un montant partiellement rendu, et faire sortir toute la commande du chiffre
-- d'affaires pour un remboursement de dix euros serait plus faux que de ne rien faire. Ces cas
-- sont journalisés en avertissement et traités à la main.

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_status_check;

ALTER TABLE orders ADD CONSTRAINT orders_payment_status_check
    CHECK (payment_status IN (
        'UNPAID', 'PAID', 'PENDING_APPROVAL', 'QUOTE_SENT', 'QUOTE_REJECTED', 'REFUNDED'));

-- La recherche de la commande à partir de l'identifiant de paiement Stripe est le seul chemin
-- dont dispose le webhook de remboursement : l'événement ne porte pas notre identifiant de
-- commande. Sans index, c'est un parcours complet de la table à chaque remboursement.
CREATE INDEX IF NOT EXISTS idx_orders_stripe_payment_intent
    ON orders (stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;
