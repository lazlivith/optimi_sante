-- =====================================================================================
-- V26 — Cycle de candidature tripartite (modèle d'agence intermédiaire)
-- =====================================================================================
-- Le cycle de vie d'une candidature passe d'un modèle « le CHU décide, puis l'admin
-- valide » à un modèle d'agence : OptimiSanté pré-qualifie le dossier AVANT de le
-- transmettre au partenaire, puis encaisse la formation avant d'émettre les documents.
--
-- Le cycle de mobilité existant (CONVENTION_ISSUED → VISA_SUBMITTED → VISA_GRANTED →
-- READY_TO_START) n'est PAS remplacé : il est chaîné derrière le nouveau cycle
-- commercial, dont CONFIRMED devient la jonction.
--
-- Migration strictement additive : les trois statuts hérités restent acceptés le temps
-- de la bascule (retirés par V28, une fois le code déployé et vérifié). Tant qu'un
-- ancien .jar peut redémarrer, il doit pouvoir écrire ses anciennes valeurs.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. Contrainte élargie AVANT toute écriture des nouvelles valeurs par le code.
-- -------------------------------------------------------------------------------------
ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS enrollments_status_check;
ALTER TABLE enrollments ADD CONSTRAINT enrollments_status_check CHECK (status IN (
    -- Cycle commercial (nouveau)
    'UNDER_OPTIMI_REVIEW', 'ACTION_REQUIRED', 'SUBMITTED_TO_PARTNER',
    'ACCEPTED_BY_PARTNER', 'PENDING_TUITION_FEE', 'CONFIRMED',
    -- Cycle de mobilité (existant, inchangé)
    'CONVENTION_ISSUED', 'VISA_SUBMITTED', 'VISA_GRANTED', 'READY_TO_START',
    -- Sorties terminales
    'REJECTED', 'CANCELLED',
    -- Hérités, tolérés le temps de la bascule (supprimés en V28)
    'PENDING_REVIEW', 'APPROVED_ACADEMIC', 'APPROVED_ADMINISTRATIVE'
));

-- -------------------------------------------------------------------------------------
-- 2. Reprise des dossiers existants.
--    APPROVED_ADMINISTRATIVE → CONFIRMED : même point du parcours. La convention et
--    l'attestation ont déjà été générées pour ces dossiers ; le déclencheur applicatif
--    ne se rejoue pas sur une simple migration de données, il n'y a donc aucun risque
--    de double génération.
-- -------------------------------------------------------------------------------------
UPDATE enrollments SET status = 'UNDER_OPTIMI_REVIEW'  WHERE status = 'PENDING_REVIEW';
UPDATE enrollments SET status = 'ACCEPTED_BY_PARTNER'  WHERE status = 'APPROVED_ACADEMIC';
UPDATE enrollments SET status = 'CONFIRMED'            WHERE status = 'APPROVED_ADMINISTRATIVE';

-- -------------------------------------------------------------------------------------
-- 3. Traçabilité de la décision : qui, quand, pourquoi.
--    Jusqu'ici seule la colonne `rejection_reason` existait (depuis V1) — et elle n'était
--    même pas mappée côté entité JPA : aucun motif de rejet n'était réellement conservé.
--
--    Pas de clé étrangère sur `optimi_reviewed_by` : leçon de la V25, une colonne de
--    traçabilité ne doit jamais contraindre ni faire échouer la transaction métier
--    qu'elle se contente d'observer.
-- -------------------------------------------------------------------------------------
ALTER TABLE enrollments
    ADD COLUMN IF NOT EXISTS optimi_reviewed_at   TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS optimi_reviewed_by   UUID,
    ADD COLUMN IF NOT EXISTS partner_decided_at   TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS action_required_note TEXT;

-- -------------------------------------------------------------------------------------
-- 4. Index de filtrage : chaque espace (admin, partenaire, médecin) liste ses dossiers
--    par statut. Le partenaire, en particulier, ne doit plus voir que les dossiers
--    déjà transmis — ce filtre devient la requête la plus fréquente de la table.
-- -------------------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_enrollments_status ON enrollments(status);
