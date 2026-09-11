-- =====================================================================================
-- V50 — Autoriser le type d'email `NOTIFICATION` dans le journal des envois
-- =====================================================================================
-- Le NotificationDispatcher (alertes & notifications) envoie des emails transactionnels via
-- EmailService. Depuis la V24, tout envoi est trace dans `email_logs` — dont la contrainte
-- n'admettait que CREDENTIALS et TEST. Sans cette extension, chaque notification par email
-- echouerait a l'ecriture de sa trace.
--
-- ⚠️ POURQUOI CETTE MIGRATION REENUMERE CINQ VALEURS ET NON TROIS
--
-- PostgreSQL ne sait pas altérer un CHECK en place : il faut le supprimer et le recreer. Toute
-- recreation doit donc reenumerer **l'integralite** des valeurs admises, y compris celles
-- posees par d'autres travaux.
--
-- Cette migration a ete ecrite sur une branche qui ignorait la V41, laquelle avait ajoute
-- INTERVIEW_SLOTS et INTERVIEW_CONFIRMED pour les entretiens visio. En l'etat, elle les aurait
-- **supprimes** : la contrainte se serait refermee sur trois valeurs, et la premiere convocation
-- d'entretien aurait echoue a l'ecriture de sa trace — apres que l'email soit parti.
--
-- Rien n'aurait signale la regression. Aucun conflit Git : les deux migrations portaient des
-- noms de fichiers differents. Aucune erreur de compilation : l'enum Java et la contrainte SQL
-- sont deux sources de verite independantes. Aucun echec au demarrage : la contrainte est
-- syntaxiquement valide. La panne ne serait apparue qu'au premier entretien planifie.
--
-- La regle a retenir : **une contrainte recreee doit toujours partir de l'enum Java complet**
-- (`EmailType`), jamais des seules valeurs que l'on croit connaitre.
-- =====================================================================================

ALTER TABLE email_logs DROP CONSTRAINT IF EXISTS email_logs_type_check;
ALTER TABLE email_logs ADD CONSTRAINT email_logs_type_check
    CHECK (email_type IN (
        'CREDENTIALS',           -- V24 : identifiants d'un compte provisionne
        'TEST',                  -- V24 : verification manuelle depuis l'administration
        'INTERVIEW_SLOTS',       -- V41 : creneaux d'entretien transmis au medecin
        'INTERVIEW_CONFIRMED',   -- V41 : confirmation du rendez-vous visio
        'NOTIFICATION'           -- V50 : notification transactionnelle du dispatcher
    ));

COMMENT ON CONSTRAINT email_logs_type_check ON email_logs IS
    'Miroir de l''enumeration Java EmailType. Toute recreation de cette contrainte doit '
    'reenumerer l''integralite des valeurs : PostgreSQL ne sait pas etendre un CHECK.';
