-- Promotions produit : prix promotionnel actif uniquement sur une fenêtre de dates déterminée
-- par l'admin. Aucune tâche planifiée nécessaire — l'expiration se fait par simple comparaison
-- de date à la lecture (promo_starts_at / promo_ends_at), toujours recalculée à chaud.
ALTER TABLE products ADD COLUMN promo_price NUMERIC(10, 2);
ALTER TABLE products ADD COLUMN promo_starts_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE products ADD COLUMN promo_ends_at TIMESTAMP WITH TIME ZONE;

-- Codes promo réutilisables au checkout (indépendants des promotions produit ci-dessus).
CREATE TABLE promo_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(50) NOT NULL,
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    discount_value NUMERIC(10, 2) NOT NULL,
    min_order_amount NUMERIC(10, 2),
    max_uses INTEGER,
    used_count INTEGER NOT NULL DEFAULT 0,
    starts_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_promo_codes_code ON promo_codes(code);

-- Trace le code promo effectivement appliqué à une commande (pour l'affichage et l'historique).
ALTER TABLE orders ADD COLUMN promo_code_id UUID REFERENCES promo_codes(id);
ALTER TABLE orders ADD COLUMN discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0;
