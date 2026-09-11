-- SPRINT 5 — Gouvernance Dual-Admin, Journal d'audit RGPD, socle Analytics & Reporting.
--
-- Aucune table métier existante n'est modifiée de façon destructive. On ajoute :
--   1) audit_logs      : journal d'audit horodaté (qui a fait quoi, quand, depuis où)
--   2) rgpd_requests   : trace des demandes RGPD traitées (export / anonymisation)
--   3) report_runs     : exécutions du worker Python de reporting (batch nocturne & à la demande)
--   4) users.gdpr_*    : consentement et date d'anonymisation (droit à l'effacement)
--
-- Le contenu JSON (metadata / params) est stocké en TEXT et non en JSONB : le projet tourne en
-- `ddl-auto: update` en dev (cf. docs/journal_erreurs_defis) et un binding varchar -> jsonb par
-- Hibernate a déjà causé des dérives de schéma ici. TEXT est sans risque et suffit à un journal.

-- 1. JOURNAL D'AUDIT --------------------------------------------------------------
CREATE TABLE audit_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID REFERENCES tenants(id) ON DELETE SET NULL,
    actor_user_id  UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_email    VARCHAR(255),
    actor_role     VARCHAR(30),
    action         VARCHAR(80) NOT NULL,   -- ex: HTTP_WRITE, RGPD_ANONYMIZE, RGPD_EXPORT
    entity_type    VARCHAR(60),            -- ex: User, Enrollment, Order
    entity_id      VARCHAR(64),
    http_method    VARCHAR(10),
    path           VARCHAR(512),
    status_code    INT,
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(512),
    summary        TEXT,
    metadata       TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_tenant_created ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX idx_audit_logs_actor          ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_logs_action         ON audit_logs (action);
CREATE INDEX idx_audit_logs_entity         ON audit_logs (entity_type, entity_id);

-- 2. DEMANDES RGPD ---------------------------------------------------------------
CREATE TABLE rgpd_requests (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID REFERENCES tenants(id) ON DELETE SET NULL,
    request_type         VARCHAR(20) NOT NULL CHECK (request_type IN ('EXPORT', 'ANONYMIZE')),
    subject_user_id      UUID REFERENCES users(id) ON DELETE SET NULL,
    subject_email        VARCHAR(255) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'DONE' CHECK (status IN ('DONE', 'FAILED')),
    requested_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    requested_by_email   VARCHAR(255),
    result_summary       TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rgpd_requests_subject ON rgpd_requests (subject_user_id);
CREATE INDEX idx_rgpd_requests_created ON rgpd_requests (created_at DESC);

-- 3. EXECUTIONS DE RAPPORTS (worker Python FastAPI) ----------------------------
CREATE TABLE report_runs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_key  VARCHAR(60) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    trigger     VARCHAR(20) NOT NULL DEFAULT 'MANUAL' CHECK (trigger IN ('MANUAL', 'SCHEDULED')),
    params      TEXT,
    row_count   INT,
    formats     VARCHAR(120),
    csv_path    TEXT,
    xlsx_path   TEXT,
    error       TEXT,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);
CREATE INDEX idx_report_runs_key_started ON report_runs (report_key, started_at DESC);

-- 4. COLONNES RGPD SUR users --------------------------------------------------
-- gdpr_consent_at : horodatage du consentement explicite au traitement des données.
--   Rétro-rempli à created_at pour les comptes existants (consentement implicite à
--   l'inscription, cohérent avec le fonctionnement actuel).
-- anonymized_at   : date d'exécution du droit à l'effacement. La ligne est conservée
--   (intégrité référentielle des commandes / dossiers) mais toutes les PII sont brouillées.
ALTER TABLE users
    ADD COLUMN gdpr_consent_at TIMESTAMPTZ,
    ADD COLUMN anonymized_at   TIMESTAMPTZ;

UPDATE users SET gdpr_consent_at = created_at WHERE gdpr_consent_at IS NULL;
