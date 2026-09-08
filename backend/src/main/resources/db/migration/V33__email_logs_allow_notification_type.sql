-- =====================================================================================
-- V33 — Autoriser le type d'email `NOTIFICATION` dans le journal des envois
-- =====================================================================================
-- Le NotificationDispatcher (alertes & notifications) envoie des emails transactionnels
-- via EmailService.sendHtml. Depuis la fusion avec le journal des emails (V24), tout envoi
-- est tracé dans `email_logs` — dont la contrainte n'admettait que CREDENTIALS et TEST.
-- Sans cette extension, chaque notification par email echouerait a l'ecriture de sa trace.
--
-- La contrainte est recréée plutôt que modifiée : PostgreSQL ne sait pas altérer un CHECK
-- en place. Opération purement structurelle, aucune ligne existante n'est touchée.
-- =====================================================================================

ALTER TABLE email_logs DROP CONSTRAINT IF EXISTS email_logs_type_check;

ALTER TABLE email_logs ADD CONSTRAINT email_logs_type_check
    CHECK (email_type IN ('CREDENTIALS', 'TEST', 'NOTIFICATION'));
