-- ===========================================================================================
-- Reçus de paiement : registre, numérotation, et place dans le coffre-fort.
--
-- Aujourd'hui un seul encaissement sur cinq produit un reçu (la commande boutique). Un médecin
-- règle ses frais de dossier, son acompte puis son solde — trois encaissements — sans jamais
-- recevoir de trace écrite.
-- ===========================================================================================

-- ── 1. Le coffre-fort doit accepter un reçu ────────────────────────────────────────────────
-- La contrainte énumère les types autorisés : sans cette extension, le classement d'un reçu
-- échouerait à l'insertion, et l'échec ne se verrait qu'au premier paiement réel.
ALTER TABLE enrollment_documents
    DROP CONSTRAINT IF EXISTS enrollment_documents_document_type_check;

ALTER TABLE enrollment_documents
    ADD CONSTRAINT enrollment_documents_document_type_check
    CHECK (document_type IN (
        'PASSPORT', 'DIPLOMA', 'MEDICAL_COUNCIL_CERT', 'FINANCIAL_GUARANTEE',
        'VISA_GRANT', 'CONSULAR_LETTER', 'ACCOMMODATION_PROOF',
        'INTERVIEW_CONVOCATION', 'SERVICE_SUBSCRIPTION',
        'PAYMENT_RECEIPT',
        'OTHER'));

-- ── 2. La numérotation ─────────────────────────────────────────────────────────────────────
-- Une séquence PostgreSQL ne convient pas : elle avance même quand la transaction est annulée,
-- et laisse donc des trous. Une ligne verrouillée par exercice, incrémentée dans la même
-- transaction que l'émission, n'en laisse aucun.
CREATE TABLE IF NOT EXISTS document_sequences (
    kind        varchar(40) NOT NULL,
    exercice    int         NOT NULL,
    dernier     int         NOT NULL DEFAULT 0,
    updated_at  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (kind, exercice),
    CONSTRAINT document_sequences_positive CHECK (dernier >= 0)
);

COMMENT ON TABLE document_sequences IS
    'Compteur continu par type de document et par exercice. Verrouillé en écriture '
    '(SELECT ... FOR UPDATE) pour que deux encaissements simultanés ne partagent pas un numéro.';

-- ── 3. Le registre des reçus émis ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_receipts (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    numero         varchar(30)  NOT NULL,
    -- Identifiant de l'encaissement côté source (référence Stripe, ou identifiant de la ligne
    -- comptable quand le règlement n'est pas passé par Stripe).
    reference      varchar(255) NOT NULL,
    motif          varchar(30)  NOT NULL,
    montant        numeric(10,2) NOT NULL,
    devise         varchar(3)   NOT NULL DEFAULT 'EUR',
    beneficiaire_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
    enrollment_id  uuid REFERENCES enrollments(id) ON DELETE SET NULL,
    order_id       uuid REFERENCES orders(id) ON DELETE SET NULL,
    document_key   varchar(255),
    emis_le        timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT payment_receipts_motif_connu CHECK (motif IN (
        'COMMANDE', 'FRAIS_DE_DOSSIER', 'ACOMPTE_SCOLARITE',
        'SOLDE_SCOLARITE', 'OPTIONS_SERVICE')),
    CONSTRAINT payment_receipts_montant_positif CHECK (montant > 0)
);

-- L'idempotence tient à cet index, et non à un test applicatif : deux livraisons du même
-- webhook Stripe arrivent en parallèle, et un « existe déjà ? » suivi d'un INSERT laisse
-- passer les deux. C'est le même garde-fou que sur stripe_checkout_session_id.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_receipts_reference
    ON payment_receipts (reference);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_receipts_numero
    ON payment_receipts (numero);

CREATE INDEX IF NOT EXISTS idx_payment_receipts_beneficiaire
    ON payment_receipts (beneficiaire_user_id, emis_le DESC);

COMMENT ON TABLE payment_receipts IS
    'Un reçu émis par encaissement. L''unicité de « reference » rend l''émission idempotente : '
    'un webhook rejoué ne produit pas un second reçu ni ne consomme un second numéro.';
