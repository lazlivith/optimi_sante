-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 1. Les réglages que l'administration tient elle-même
--
-- `TVA_ACTIVE` était une variable d'environnement : la changer imposait d'ouvrir le tableau de
-- bord de l'hébergeur, de modifier un réglage et de redémarrer le serveur. Pour un
-- interrupteur que le gestionnaire du catalogue doit pouvoir lever le jour où son comptable
-- répond, c'est à la fois trop lourd et au mauvais endroit : la personne qui décide n'est pas
-- celle qui a les accès.
--
-- Ces réglages-là appartiennent donc à la base, et non à l'environnement. La distinction est
-- nette : l'environnement porte ce qui relève du DÉPLOIEMENT — secrets, adresses, clés — que
-- personne ne change en exploitation ; la base porte ce qui relève de l'EXPLOITATION, que l'on
-- change sans redéployer et dont on veut la trace.
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE parametres_plateforme (
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cle         varchar(80) NOT NULL,
    valeur      text NOT NULL,
    modifie_le  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, cle)
);

COMMENT ON TABLE parametres_plateforme IS
    'Réglages d''exploitation, modifiables depuis l''administration sans redéploiement. '
    'Qui les a changés et quand est tracé par le journal d''audit, pas ici.';

-- Fermé, comme l'était la variable : la grille de TVA comporte encore des lignes que le
-- comptable n'a pas confirmées.
INSERT INTO parametres_plateforme (tenant_id, cle, valeur)
SELECT id, 'tva.active', 'false' FROM tenants;

-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 2. Les campagnes de prix
--
-- La hausse de 10 % d'octobre a demandé une migration SQL écrite à la main, avec sa table de
-- sauvegarde dédiée (V69). Rejouer cela à chaque révision tarifaire ferait dépendre une
-- décision commerciale d'un déploiement — et d'un développeur disponible.
--
-- Une campagne enregistre ce qu'elle a changé, produit par produit. C'est ce qui rend
-- l'annulation possible : on ne REDIVISE pas par 1,10 — l'arrondi au centime n'est pas
-- réversible, et un prix à 0,44 € redivisé donne 0,40 € dans un cas, 0,399... dans un autre.
-- On restitue la valeur exacte d'avant.
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE campagnes_prix (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    pourcentage     numeric(6,2) NOT NULL,
    -- Nulle : la campagne portait sur tout le catalogue.
    categorie_id    uuid REFERENCES categories(id) ON DELETE SET NULL,
    produits_touches integer NOT NULL,
    libelle         text,
    appliquee_le    timestamptz NOT NULL DEFAULT now(),
    appliquee_par   uuid REFERENCES users(id) ON DELETE SET NULL,
    annulee_le      timestamptz,
    annulee_par     uuid REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT campagnes_prix_pourcentage_check
        CHECK (pourcentage >= -90 AND pourcentage <= 500 AND pourcentage <> 0)
);

CREATE TABLE campagnes_prix_lignes (
    campagne_id       uuid NOT NULL REFERENCES campagnes_prix(id) ON DELETE CASCADE,
    product_id        uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    base_price_avant  numeric(10,2) NOT NULL,
    promo_price_avant numeric(10,2),
    PRIMARY KEY (campagne_id, product_id)
);

COMMENT ON TABLE campagnes_prix_lignes IS
    'Les prix exacts d''avant la campagne. L''annulation les restitue tels quels : rediviser '
    'par le pourcentage ne rendrait pas le même prix, l''arrondi au centime n''étant pas '
    'réversible.';

-- La hausse de V69 entre dans le registre, pour que l'écran ne commence pas son histoire à
-- la deuxième campagne. Sa table de sauvegarde dédiée alimente les lignes, puis n'a plus
-- d'utilité propre — elle est conservée, une sauvegarde ne se jette pas.
INSERT INTO campagnes_prix (id, tenant_id, pourcentage, produits_touches, libelle, appliquee_le)
SELECT gen_random_uuid(), t.id, 10.00,
       (SELECT count(*) FROM products_prix_avant_hausse_202610),
       'Hausse tarifaire d''octobre 2026, appliquée par migration (V69)',
       (SELECT min(releve_le) FROM products_prix_avant_hausse_202610)
FROM tenants t
WHERE EXISTS (SELECT 1 FROM products_prix_avant_hausse_202610);

INSERT INTO campagnes_prix_lignes (campagne_id, product_id, base_price_avant, promo_price_avant)
SELECT c.id, a.product_id, a.base_price, a.promo_price
FROM campagnes_prix c
CROSS JOIN products_prix_avant_hausse_202610 a
WHERE c.libelle LIKE 'Hausse tarifaire d%octobre 2026%'
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 3. Les six catégories que la grille du comptable ne couvrait pas
--
-- Elles portent le taux normal faute d'arbitrage, et non parce qu'il a été tranché. Les
-- marquer leur donne le même statut que les neuf lignes déjà signalées : l'écran les regroupe,
-- et le marqueur se lève en enregistrant un taux.
-- ─────────────────────────────────────────────────────────────────────────────────────────

UPDATE categories SET vat_rate_a_verifier = true
WHERE vat_rate IS NULL
  AND category_norm(name) IN (
      category_norm('Fauteuils pour Pousser & Coquilles'),
      category_norm('Cannes anglaises & accessoires'),
      category_norm('Béquille mains libres & orthopédie'),
      category_norm('Protections & immobilisation'),
      category_norm('Protection literie & fauteuil'),
      category_norm('Audition'));
