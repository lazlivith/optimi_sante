-- Rattache un Customer Stripe à chaque utilisateur pour permettre la sauvegarde et la
-- réutilisation des moyens de paiement (Stripe Elements, ui_mode "elements") d'un achat
-- à l'autre, sans redemander la carte à chaque fois.
ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255);
