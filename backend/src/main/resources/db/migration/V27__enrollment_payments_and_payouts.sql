-- =====================================================================================
-- V27 — Registre financier et reversements partenaires
-- =====================================================================================
-- OptimiSanté agit en tiers de confiance : la plateforme encaisse le médecin, prélève sa
-- commission d'agence, puis reverse la part nette au CHU / organisme de formation.
--
-- Jusqu'ici, `trainings.price` et `training_sessions.price` étaient stockés mais jamais
-- facturés, et aucune notion de commission ni de reversement n'existait dans le code.
--
-- Choix de conception : le taux de commission est COPIÉ sur la ligne de paiement au moment
-- de l'encaissement, jamais relu dynamiquement. Renégocier le taux d'un partenaire ne doit
-- pas réécrire l'historique comptable déjà constaté.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. Taux de commission négocié par partenaire, avec défaut plateforme.
-- -------------------------------------------------------------------------------------
ALTER TABLE partner_profiles
    ADD COLUMN IF NOT EXISTS commission_rate NUMERIC(5,2) NOT NULL DEFAULT 15.00;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'partner_profiles'::regclass
                     AND conname  = 'partner_profiles_commission_rate_check')
    THEN
        ALTER TABLE partner_profiles
            ADD CONSTRAINT partner_profiles_commission_rate_check
            CHECK (commission_rate >= 0 AND commission_rate <= 100);
    END IF;
END $$;

-- -------------------------------------------------------------------------------------
-- 2. Reversements : regroupent plusieurs paiements en un virement partenaire.
--    Créée avant `enrollment_payments`, qui la référence.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS partner_payouts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_profile_id UUID NOT NULL REFERENCES partner_profiles(id) ON DELETE RESTRICT,
    total_amount       NUMERIC(10,2) NOT NULL CHECK (total_amount >= 0),
    currency           VARCHAR(3) NOT NULL DEFAULT 'EUR',
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    reference          VARCHAR(50) UNIQUE,
    period_start       DATE,
    period_end         DATE,
    paid_at            TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_partner_payouts_partner ON partner_payouts(partner_profile_id);

-- -------------------------------------------------------------------------------------
-- 3. Journal financier unique : frais de dossier ET frais de formation.
--
--    Les frais de dossier restent encaissés par `doctor_applications` (porte d'entrée
--    inchangée, éprouvée) ; ils sont simplement REFLÉTÉS ici, afin que l'administration
--    dispose d'un seul état comptable plutôt que de deux tables à réconcilier.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS enrollment_payments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id         UUID NOT NULL REFERENCES enrollments(id) ON DELETE RESTRICT,

    payment_type          VARCHAR(20) NOT NULL
        CHECK (payment_type IN ('DOSSIER_FEE', 'TUITION_FEE')),

    gross_amount          NUMERIC(10,2) NOT NULL CHECK (gross_amount >= 0),
    commission_rate       NUMERIC(5,2)  NOT NULL DEFAULT 0,
    commission_amount     NUMERIC(10,2) NOT NULL DEFAULT 0,
    partner_payout_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    currency              VARCHAR(3)    NOT NULL DEFAULT 'EUR',

    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),

    stripe_checkout_session_id VARCHAR(255),
    stripe_payment_intent_id   VARCHAR(255),
    partner_payout_id     UUID REFERENCES partner_payouts(id),

    paid_at               TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Invariant comptable : la répartition somme toujours au montant encaissé. Une erreur
    -- d'arrondi dans le code est rejetée par PostgreSQL plutôt que comptabilisée en silence.
    CONSTRAINT enrollment_payments_split_coherent
        CHECK (commission_amount + partner_payout_amount = gross_amount)
);

-- Un seul paiement de formation encaissé par dossier : filet de sécurité en base contre
-- un webhook Stripe rejoué (l'idempotence est aussi assurée côté applicatif).
CREATE UNIQUE INDEX IF NOT EXISTS uq_enrollment_paid_tuition
    ON enrollment_payments(enrollment_id)
    WHERE payment_type = 'TUITION_FEE' AND status = 'PAID';

-- Idem pour les frais de dossier : un seul reflet encaissé par candidature.
CREATE UNIQUE INDEX IF NOT EXISTS uq_enrollment_paid_dossier
    ON enrollment_payments(enrollment_id)
    WHERE payment_type = 'DOSSIER_FEE' AND status = 'PAID';

CREATE INDEX IF NOT EXISTS idx_enrollment_payments_enrollment ON enrollment_payments(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_enrollment_payments_payout     ON enrollment_payments(partner_payout_id);

-- -------------------------------------------------------------------------------------
-- 4. Reflet des frais de dossier déjà encaissés avant cette migration.
--    100 % OptimiSanté : aucune commission, aucune part partenaire — l'invariant tient
--    (0 + 0 = 0 pour la part reversée, la totalité restant hors répartition partenaire).
-- -------------------------------------------------------------------------------------
INSERT INTO enrollment_payments (
    enrollment_id, payment_type, gross_amount,
    commission_rate, commission_amount, partner_payout_amount,
    status, paid_at, created_at
)
SELECT da.created_enrollment_id,
       'DOSSIER_FEE',
       da.fee_amount,
       100.00,           -- la totalité revient à la plateforme
       da.fee_amount,    -- commission = montant encaissé
       0.00,             -- rien à reverser au partenaire
       'PAID',
       da.paid_at,
       da.created_at
FROM doctor_applications da
WHERE da.status = 'PAID'
  AND da.created_enrollment_id IS NOT NULL
  AND da.fee_amount IS NOT NULL
  AND NOT EXISTS (
        SELECT 1 FROM enrollment_payments ep
        WHERE ep.enrollment_id = da.created_enrollment_id
          AND ep.payment_type = 'DOSSIER_FEE');
