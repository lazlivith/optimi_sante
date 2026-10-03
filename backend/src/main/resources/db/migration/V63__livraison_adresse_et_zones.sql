-- Livraison : adresse de destination sur la commande, et frais par zone.
--
-- CE QUI MANQUAIT. La commande ne portait ni adresse, ni pays, ni frais de port — rien ne
-- disait où envoyer le colis. Les conditions générales annoncent pourtant « hors frais de
-- livraison indiqués avant la validation de la commande » : une promesse que la plateforme
-- ne tenait pas.
--
-- POURQUOI L'ADRESSE EST RECOPIÉE SUR LA COMMANDE, et non lue sur le compte client. Une
-- commande est un engagement daté : elle doit garder l'adresse telle qu'elle était au moment
-- de l'achat. Un client qui déménage ne doit pas voir ses anciennes commandes réécrire leur
-- destination — et le transporteur, lui, a livré à l'ancienne.

ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_recipient   varchar(150);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_line1       varchar(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_line2       varchar(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_postal_code varchar(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_city        varchar(120);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_country     varchar(2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_zone        varchar(30);

-- Zéro par défaut : les commandes déjà passées n'ont supporté aucun frais, et leur en
-- attribuer un rétroactivement fausserait leur total.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_cost numeric(10,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN orders.shipping_country IS 'Code ISO 3166-1 alpha-2 du pays de destination.';
COMMENT ON COLUMN orders.shipping_zone IS
    'Zone retenue au moment de la commande. Figee : un redecoupage ulterieur des zones ne doit pas reecrire les commandes passees.';

-- La grille tarifaire, saisie depuis l'administration. Une table plutot que des valeurs dans
-- le code : les tarifs d'un transporteur changent, et un changement de tarif ne doit pas
-- demander un redeploiement.
CREATE TABLE IF NOT EXISTS shipping_rates (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id),
    zone        varchar(30) NOT NULL,
    amount      numeric(10,2) NOT NULL DEFAULT 0,
    -- Montant de commande a partir duquel la livraison est offerte. NULL : jamais offerte.
    free_from   numeric(10,2),
    is_active   boolean NOT NULL DEFAULT true,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_shipping_rates_tenant_zone
    ON shipping_rates (tenant_id, zone);

ALTER TABLE shipping_rates DROP CONSTRAINT IF EXISTS shipping_rates_zone_check;
ALTER TABLE shipping_rates ADD CONSTRAINT shipping_rates_zone_check
    CHECK (zone IN ('FRANCE', 'AFRIQUE_NORD', 'AFRIQUE_OUEST_CENTRE', 'AFRIQUE_AUTRE'));

ALTER TABLE shipping_rates DROP CONSTRAINT IF EXISTS shipping_rates_amount_check;
ALTER TABLE shipping_rates ADD CONSTRAINT shipping_rates_amount_check
    CHECK (amount >= 0 AND (free_from IS NULL OR free_from >= 0));

-- Les quatre zones, a zero. Un tarif invente par une migration serait facture a de vrais
-- clients : l'administration les renseigne, et une zone a zero se lit comme « non defini »
-- plutot que comme « livraison offerte » — l'ecran le dit explicitement.
INSERT INTO shipping_rates (tenant_id, zone, amount, is_active)
SELECT t.id, z.zone, 0, true
FROM tenants t
CROSS JOIN (VALUES ('FRANCE'), ('AFRIQUE_NORD'), ('AFRIQUE_OUEST_CENTRE'), ('AFRIQUE_AUTRE')) AS z(zone)
ON CONFLICT (tenant_id, zone) DO NOTHING;
