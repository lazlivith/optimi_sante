-- Gestion des stocks : un journal des mouvements, et de quoi distinguer un chiffre compté
-- d'un chiffre hérité de l'import.
--
-- Jusqu'ici, `products.stock_quantity` était décrémenté au paiement sans laisser de trace :
-- aucune table, aucun journal, aucune entrée d'audit. Quand un chiffre devenait faux, rien ne
-- permettait de dire quand ni pourquoi. Ce journal rend chaque variation explicable.
--
-- `stock_threshold` existe déjà sur les produits (défaut 5) et n'était lu par personne : la
-- veille s'en sert désormais comme seuil critique par produit.

CREATE TABLE stock_mouvements (
    id            uuid         PRIMARY KEY,
    product_id    uuid         NOT NULL REFERENCES products (id),
    -- Signé : négatif pour une sortie, positif pour une entrée. La somme des deltas d'un
    -- produit doit égaler son stock courant — c'est ce qui rend le journal vérifiable.
    delta         integer      NOT NULL,
    stock_apres   integer      NOT NULL,
    motif         varchar(30)  NOT NULL,
    -- Renseignés selon le motif : la commande pour une vente, l'auteur pour un geste humain.
    order_id      uuid,
    auteur_id     uuid         REFERENCES users (id),
    commentaire   varchar(500),
    created_at    timestamptz  NOT NULL DEFAULT now()
);

-- L'historique d'un produit se lit du plus récent au plus ancien : c'est la seule lecture
-- fréquente, et elle doit rester instantanée quand le journal aura grossi.
CREATE INDEX idx_stock_mouvements_produit ON stock_mouvements (product_id, created_at DESC);
CREATE INDEX idx_stock_mouvements_date ON stock_mouvements (created_at DESC);

-- Date du dernier comptage réel. NULL signifie « jamais inventorié », ce qui est le cas de
-- tout le catalogue : 1 468 produits sur 1 497 portent encore le 50 par défaut de l'import.
-- Sans cette colonne, un stock inventé et un stock compté s'affichent de la même façon, et
-- personne ne peut savoir à quels chiffres se fier.
ALTER TABLE products ADD COLUMN stock_inventorie_le timestamptz;

-- Sonde de veille horaire, filet de sécurité derrière l'alerte immédiate posée au moment du
-- franchissement. Désactivée tant qu'aucun inventaire n'existe : alerter sur 1 497 produits
-- dont le stock est fictif noierait le signal dès le premier passage.
INSERT INTO alert_thresholds (key, label, enabled, threshold_value, window_hours, severity, updated_at)
VALUES ('STOCK_CRITIQUE', 'Produits sous leur seuil de stock critique', false, 1, 24, 'WARNING', now())
ON CONFLICT (key) DO NOTHING;
