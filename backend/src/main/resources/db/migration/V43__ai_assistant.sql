-- IA conversationnelle & automatisation documentaire.
--
--   1) ai_conversations / ai_messages : historique du chat (traçabilité, reprise de fil,
--      audit des réponses données aux utilisateurs)
--   2) ai_document_jobs              : trace des extractions documentaires
--
-- Le contenu JSON (champs extraits) est stocké en TEXT et non en JSONB, comme audit_logs,
-- report_runs et notifications : le projet tourne en `ddl-auto: update` en dev et un binding
-- varchar -> jsonb par Hibernate a déjà causé des dérives de schéma ici.

-- 1. CONVERSATIONS -------------------------------------------------------------
CREATE TABLE ai_conversations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID REFERENCES tenants(id) ON DELETE SET NULL,
    user_id    UUID REFERENCES users(id) ON DELETE CASCADE,  -- NULL = visiteur anonyme
    title      VARCHAR(180),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_conversations_user ON ai_conversations (user_id, updated_at DESC);

CREATE TABLE ai_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant')),
    content         TEXT NOT NULL,
    model           VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_messages_conversation ON ai_messages (conversation_id, created_at);

-- 2. EXTRACTIONS DOCUMENTAIRES -------------------------------------------------
-- status : SUCCESS / FAILED. `applied_at` n'est renseigné que si un humain a validé et
-- reporté les valeurs dans le dossier — l'IA ne modifie jamais un dossier toute seule.
CREATE TABLE ai_document_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID REFERENCES tenants(id) ON DELETE SET NULL,
    requested_by    UUID REFERENCES users(id) ON DELETE SET NULL,
    document_type   VARCHAR(40) NOT NULL,
    file_name       VARCHAR(255),
    content_type    VARCHAR(120),
    size_bytes      BIGINT,
    status          VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' CHECK (status IN ('SUCCESS', 'FAILED')),
    type_matches    BOOLEAN,
    extracted_json  TEXT,
    warnings        TEXT,
    error           TEXT,
    model           VARCHAR(80),
    applied_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_document_jobs_created ON ai_document_jobs (created_at DESC);
CREATE INDEX idx_ai_document_jobs_requester ON ai_document_jobs (requested_by);
