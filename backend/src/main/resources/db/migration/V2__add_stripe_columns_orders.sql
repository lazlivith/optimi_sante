-- Add Stripe tracking columns to orders table
ALTER TABLE orders ADD COLUMN stripe_payment_intent_id VARCHAR(255);
ALTER TABLE orders ADD COLUMN stripe_checkout_session_id VARCHAR(255);
