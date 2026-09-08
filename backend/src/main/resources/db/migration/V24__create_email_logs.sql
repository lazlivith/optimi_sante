-- =====================================================================================
-- V24 — Journal des emails envoyés
-- =====================================================================================
-- Jusqu'ici, l'envoi d'email était « best effort » : un échec SMTP était simplement logué
-- dans la console du serveur et restait totalement invisible côté administration. Un
-- partenaire ou un médecin validé pouvait donc ne jamais recevoir ses identifiants sans
-- que personne ne s'en aperçoive.
--
-- ⚠️ SÉCURITÉ — Le corps du message N'EST VOLONTAIREMENT PAS STOCKÉ : l'email
-- d'identifiants contient un mot de passe provisoire en clair. On ne conserve que des
-- métadonnées. Le renvoi régénère un nouveau mot de passe provisoire (le précédent
-- n'existe nulle part en clair côté serveur, seul son hash est en base).
-- =====================================================================================

CREATE TABLE IF NOT EXISTS email_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    recipient       VARCHAR(255) NOT NULL,
    subject         VARCHAR(500) NOT NULL,

    -- Type fonctionnel de l'email, aligné sur l'enum Java EmailType.
    email_type      VARCHAR(50)  NOT NULL,

    status          VARCHAR(20)  NOT NULL DEFAULT 'SENT',
    error_message   TEXT,

    -- Destinataire applicatif quand il est connu : permet le renvoi des identifiants
    -- (régénération d'un mot de passe provisoire). ON DELETE SET NULL pour conserver
    -- l'historique même si le compte est supprimé.
    recipient_user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    sent_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT email_logs_status_check
        CHECK (status IN ('SENT', 'FAILED')),
    CONSTRAINT email_logs_type_check
        CHECK (email_type IN ('CREDENTIALS', 'TEST'))
);

CREATE INDEX IF NOT EXISTS idx_email_logs_sent_at   ON email_logs(sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_logs_status    ON email_logs(status);
CREATE INDEX IF NOT EXISTS idx_email_logs_recipient ON email_logs(recipient);
