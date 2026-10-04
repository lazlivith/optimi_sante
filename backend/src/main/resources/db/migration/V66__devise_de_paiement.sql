-- La devise dans laquelle une commande a réellement été réglée.
--
-- `total_amount` reste en euros, et c'est volontaire : c'est la devise de référence, celle dans
-- laquelle la comptabilité est tenue et dans laquelle les agrégats financiers s'additionnent.
-- Sommer des euros et des francs CFA dans un même chiffre d'affaires n'aurait aucun sens.
--
-- Ces deux colonnes disent ce que le client a vu et payé. Elles servent aux documents — un reçu
-- doit porter le montant que la personne reconnaît sur son relevé, pas sa contre-valeur — et à
-- la réconciliation avec les versements de Stripe, qui sont libellés dans la devise encaissée.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_currency varchar(3) NOT NULL DEFAULT 'EUR',
    ADD COLUMN IF NOT EXISTS payment_amount   numeric(14,2);

ALTER TABLE orders
    ADD CONSTRAINT orders_payment_currency_check CHECK (payment_currency ~ '^[A-Z]{3}$');

-- Les commandes antérieures ont toutes été réglées en euros : leur montant payé est leur total.
UPDATE orders SET payment_amount = total_amount WHERE payment_amount IS NULL;
