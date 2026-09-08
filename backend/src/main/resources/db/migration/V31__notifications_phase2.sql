-- SPRINT 5+ — Notifications & alertes, phase 2.
--
--   1) notifications : déduplication / regroupement + accusé de réception + mise en veille
--   2) alert_thresholds       : seuils de veille configurables (remplacent les constantes en dur)
--   3) notification_preferences: opt-out par utilisateur et par type (in-app / e-mail)

-- 1. COLONNES SUR notifications ------------------------------------------------
-- dedupe_key      : deux alertes de même nature non acquittées sont regroupées sur une seule
--                   ligne (group_count est incrémenté, la ligne « remonte ») au lieu d'empiler.
-- acknowledged_at : « traité / pris en charge » — distinct de read_at (« vu »). Sert aux alertes.
-- snoozed_until   : mise en veille — la ligne est masquée (liste + compteur) jusqu'à cette date.
ALTER TABLE notifications
    ADD COLUMN dedupe_key      VARCHAR(120),
    ADD COLUMN group_count     INT NOT NULL DEFAULT 1,
    ADD COLUMN acknowledged_at TIMESTAMPTZ,
    ADD COLUMN acknowledged_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN snoozed_until   TIMESTAMPTZ;

-- Recherche du doublon « même destinataire + même clé, non acquitté ».
CREATE INDEX idx_notifications_dedupe
    ON notifications (recipient_user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL AND acknowledged_at IS NULL;

-- 2. SEUILS DE VEILLE --------------------------------------------------------
CREATE TABLE alert_thresholds (
    key             VARCHAR(60) PRIMARY KEY,
    label           VARCHAR(180) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    threshold_value INT NOT NULL DEFAULT 1,      -- déclenche si le compte observé est >= à cette valeur
    window_hours    INT NOT NULL DEFAULT 24,     -- fenêtre d'observation
    severity        VARCHAR(20) NOT NULL DEFAULT 'WARNING'
                      CHECK (severity IN ('INFO', 'SUCCESS', 'WARNING', 'CRITICAL')),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO alert_thresholds (key, label, threshold_value, window_hours, severity) VALUES
    ('REPORT_FAILURE',     'Rapports du worker Python en échec',            1,  24, 'CRITICAL'),
    ('ORDER_UNPAID_STALE', 'Commandes non payées depuis trop longtemps',   1,  72, 'WARNING'),
    ('ENROLLMENT_STALE',   'Dossiers CHU sans évolution (à examiner)',     1, 240, 'WARNING');

-- 3. PRÉFÉRENCES DE NOTIFICATION PAR UTILISATEUR ---------------------------
-- Absence de ligne = tout activé (comportement par défaut). Une ligne permet à l'utilisateur
-- de couper l'in-app et/ou l'e-mail pour un type donné.
CREATE TABLE notification_preferences (
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type           VARCHAR(60) NOT NULL,
    in_app_enabled BOOLEAN NOT NULL DEFAULT true,
    email_enabled  BOOLEAN NOT NULL DEFAULT true,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, type)
);
