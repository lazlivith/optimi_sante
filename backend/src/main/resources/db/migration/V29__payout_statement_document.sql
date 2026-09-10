-- =====================================================================================
-- V29 — Relevé de reversement au format PDF
-- =====================================================================================
-- Chaque reversement peut porter un relevé téléchargeable par l'établissement partenaire,
-- sur le même modèle que la convention tripartite et l'attestation d'accueil : le document
-- est généré une fois, déposé sur le stockage, et seule sa clé est conservée ici.
--
-- Nullable : les reversements déjà générés avant cette migration n'ont pas de relevé, et
-- la génération reste possible à la demande depuis l'administration.
-- =====================================================================================

ALTER TABLE partner_payouts
    ADD COLUMN IF NOT EXISTS statement_s3_key VARCHAR(255);
