-- V57 : documents officiels du dossier et kit de départ
--
-- CE QUE C'EST
-- Deux familles de documents remis AU médecin, et non fournis par lui :
--   - pédagogiques : le programme officiel de la formation et, si l'établissement en a une,
--     sa propre convention de formation. Déposés par le CHU, vérifiés par Optimi Santé avant
--     d'arriver au coffre-fort du médecin ;
--   - kit de départ : billets, réservation d'hébergement, contacts sur place. Déposés par
--     Optimi Santé, qui organise le séjour.
--
-- QUAND LE MÉDECIN Y ACCÈDE
-- La règle n'est pas stockée : elle se déduit du dossier au moment de la lecture (voir
-- OfficialDocumentService). Un document pédagogique s'ouvre une fois l'acompte réglé ; le kit
-- une fois le visa obtenu et le solde réglé. Stocker un booléen « verrouillé » l'aurait figé
-- au jour du dépôt, et un paiement arrivé ensuite ne l'aurait jamais levé.
--
-- CE QUE CETTE MIGRATION NE FAIT PAS
-- Aucun statut de dossier ajouté, aucune transition modifiée. La convention tripartite générée
-- par la plateforme (enrollments.convention_s3_key) reste LA convention qui fait passer le
-- dossier à « Convention émise » ; celle du CHU s'y ajoute, elle ne la remplace pas.

CREATE TABLE IF NOT EXISTS enrollment_official_documents (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id    uuid NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,

    category         varchar(30)  NOT NULL,
    title            varchar(160) NOT NULL,
    storage_key      varchar(255) NOT NULL,
    file_name        varchar(255),

    -- Qui l'a déposé : le médecin lit « CHU de Bordeaux (via Optimi Santé) » ou « Optimi Santé ».
    issuer           varchar(20)  NOT NULL,
    status           varchar(20)  NOT NULL,
    rejection_reason text,

    -- Identifiants bruts, sans clé étrangère, comme en V37/V41/V43 : un compte désactivé ne doit
    -- pas rendre l'historique illisible.
    uploaded_by      uuid,
    reviewed_by      uuid,
    reviewed_at      timestamptz,
    created_at       timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT eod_category_check CHECK (
        category IN ('PROGRAMME', 'CONVENTION_CHU', 'BILLET', 'HEBERGEMENT', 'CONTACTS', 'KIT_AUTRE')
    ),
    CONSTRAINT eod_issuer_check CHECK (issuer IN ('PARTNER', 'OPTIMI')),
    CONSTRAINT eod_status_check CHECK (status IN ('PENDING_REVIEW', 'PUBLISHED', 'REJECTED')),
    CONSTRAINT eod_title_check CHECK (length(trim(title)) > 0),
    -- Un refus sans motif laisse le CHU deviner ce qu'il doit corriger.
    CONSTRAINT eod_rejection_reason_check CHECK (
        status <> 'REJECTED' OR (rejection_reason IS NOT NULL AND length(trim(rejection_reason)) > 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_eod_enrollment ON enrollment_official_documents (enrollment_id);
CREATE INDEX IF NOT EXISTS idx_eod_pending ON enrollment_official_documents (status)
    WHERE status = 'PENDING_REVIEW';
