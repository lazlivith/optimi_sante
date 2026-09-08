-- =====================================================================================
-- V25 — Découplage du journal d'emails de la table users
-- =====================================================================================
-- BUG CORRIGÉ (régression introduite par V24 + EmailLogWriter) :
-- Les traces d'emails sont écrites dans une transaction INDÉPENDANTE (REQUIRES_NEW), afin
-- qu'une trace d'ÉCHEC survive au rollback provoqué par l'erreur d'envoi elle-même.
-- Mais cette transaction indépendante ne voit pas les données non encore commitées de la
-- transaction appelante : lors d'une validation de partenariat, le compte partenaire vient
-- d'être créé et n'est pas encore visible → la clé étrangère `recipient_user_id` explosait
-- et faisait échouer TOUTE la validation (400 : violates foreign key constraint).
--
-- CORRECTIF : le journal ne référence plus `users` par clé étrangère. `recipient_user_id`
-- devient un simple identifiant, résolu à la lecture. C'est le comportement attendu d'une
-- table de journalisation : elle observe le métier sans jamais le contraindre, et reste
-- lisible même si le compte concerné est supprimé par la suite.
-- =====================================================================================

ALTER TABLE email_logs DROP CONSTRAINT IF EXISTS email_logs_recipient_user_id_fkey;

-- Index conservé/ajouté : la résolution du destinataire se fait désormais par recherche.
CREATE INDEX IF NOT EXISTS idx_email_logs_recipient_user ON email_logs(recipient_user_id);
