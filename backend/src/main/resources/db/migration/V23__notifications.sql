-- SPRINT 5+ — Système d'alertes & notifications in-app.
--
-- Une ligne = une notification destinée à UN utilisateur (les notifications de rôle sont
-- dépliées à l'écriture, cf. NotificationService.notifyRole), ce qui garde un état lu / non lu
-- individuel. Le canal e-mail réutilise EmailService ; le temps réel se fait pour l'instant par
-- interrogation périodique côté front (30 s) — le SSE est prévu en phase 2.
--
-- metadata en TEXT (et non JSONB) : cohérent avec audit_logs / report_runs (V22) et le mode
-- ddl-auto: update en dev, cf. docs/journal_erreurs_defis.

CREATE TABLE notifications (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID REFERENCES tenants(id) ON DELETE SET NULL,
    recipient_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type              VARCHAR(60)  NOT NULL,   -- ORDER_PAID, ENROLLMENT_STATUS, PARTNERSHIP_REQUEST...
    severity          VARCHAR(20)  NOT NULL DEFAULT 'INFO'
                        CHECK (severity IN ('INFO', 'SUCCESS', 'WARNING', 'CRITICAL')),
    title             VARCHAR(180) NOT NULL,
    body              TEXT,
    link_url          VARCHAR(512),            -- lien relatif dans l'app vers l'écran concerné
    metadata          TEXT,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Liste paginée « mes notifications, plus récentes d'abord ».
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_user_id, created_at DESC);

-- Compteur de pastille « non lues » (interrogé toutes les 30 s) : index partiel, très sélectif.
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_user_id) WHERE read_at IS NULL;
