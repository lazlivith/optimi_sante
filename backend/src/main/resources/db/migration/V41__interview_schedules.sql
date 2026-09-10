-- V41 : planification de l'entretien de selection
--
-- LE CIRCUIT
-- Le CHU propose des creneaux -> OptimiSante les transmet -> le medecin en choisit un.
--
-- Ce detour par l'administration n'est pas une lourdeur : c'est la regle deja inscrite dans
-- `EnrollmentTransitions` — « le partenaire ne s'adresse jamais directement au medecin ».
-- OptimiSante est l'intermediaire du parcours ; un CHU qui convoquerait lui-meme sortirait du
-- role d'agence et priverait la plateforme de la trace de ce qui a ete promis au candidat.
--
-- CE QUE CETTE TABLE NE FAIT PAS
-- Elle **ne touche pas a la machine a etats** du dossier. Aucun statut d'`enrollments` n'est
-- ajoute, aucune transition n'est modifiee. L'entretien accompagne la decision du partenaire ;
-- il ne la remplace pas. Un dossier peut etre accepte sans entretien, et un entretien peut
-- avoir lieu sans changer le statut du dossier — c'est exactement le choix fait en V37 pour
-- les demandes de pieces, et pour la meme raison : greffer chaque nouvelle etape sur l'automate
-- finirait par rendre celui-ci illisible.

CREATE TABLE IF NOT EXISTS interview_schedules (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id   uuid NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,

    status          varchar(20) NOT NULL DEFAULT 'PROPOSED',

    -- Le mode conditionne ce que doit contenir `location_or_link` : une adresse pour un
    -- entretien sur place, un lien pour une visio, rien d'obligatoire pour un appel.
    mode            varchar(20) NOT NULL,
    location_or_link varchar(512),

    -- Consignes du CHU (documents a preparer, jury, duree). Distinct de `admin_note` :
    -- l'un vient du partenaire, l'autre de l'administration, et le medecin lit les deux.
    partner_note    text,

    -- Pas de cle etrangere vers `users`, comme en V25 et V37 : un compte desactive ne doit
    -- pas rendre l'historique d'un entretien illisible.
    proposed_by     uuid,
    proposed_at     timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    transmitted_by  uuid,
    transmitted_at  timestamptz,
    admin_note      text,

    -- Renseigne a la confirmation. La cle etrangere vers `interview_slots` est ajoutee plus
    -- bas, une fois la table fille creee : les deux tables se referencent mutuellement.
    confirmed_slot_id uuid,
    confirmed_at    timestamptz,

    cancelled_reason text,

    -- La convocation deposee au coffre du dossier. ON DELETE SET NULL, comme en V37 : si le
    -- fichier disparait, l'entretien subsiste — le rendez-vous a bien ete pris.
    convocation_document_id uuid REFERENCES enrollment_documents(id) ON DELETE SET NULL,

    CONSTRAINT interview_schedules_status_check CHECK (
        status IN ('PROPOSED', 'TRANSMITTED', 'CONFIRMED', 'CANCELLED')
    ),

    CONSTRAINT interview_schedules_mode_check CHECK (
        mode IN ('VIDEO', 'PHONE', 'ON_SITE')
    ),

    -- Un entretien sur place sans adresse enverrait le medecin nulle part ; une visio sans
    -- lien ne se tient pas. La base refuse ces deux cas plutot que de compter sur l'ecran
    -- qui a servi a les saisir.
    CONSTRAINT interview_schedules_location_check CHECK (
        mode = 'PHONE' OR (location_or_link IS NOT NULL AND length(trim(location_or_link)) > 0)
    ),

    -- Coherence entre l'etat et les colonnes qui doivent l'accompagner : un entretien ne peut
    -- pas etre « transmis » sans que l'on sache quand, ni « confirme » sans creneau retenu.
    CONSTRAINT interview_schedules_coherent CHECK (
        (status = 'PROPOSED'    AND transmitted_at IS NULL AND confirmed_slot_id IS NULL)
     OR (status = 'TRANSMITTED' AND transmitted_at IS NOT NULL AND confirmed_slot_id IS NULL)
     OR (status = 'CONFIRMED'   AND transmitted_at IS NOT NULL
                                AND confirmed_slot_id IS NOT NULL AND confirmed_at IS NOT NULL)
     OR (status = 'CANCELLED'   AND cancelled_reason IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS interview_slots (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id uuid NOT NULL REFERENCES interview_schedules(id) ON DELETE CASCADE,

    starts_at   timestamptz NOT NULL,
    ends_at     timestamptz NOT NULL,

    CONSTRAINT interview_slots_order_check CHECK (ends_at > starts_at)
);

-- Le lien retour, ajoute apres coup pour rompre le cycle de creation. L'ordre d'ecriture le
-- suit naturellement : l'entretien nait sans creneau retenu, les creneaux sont inseres, puis
-- le choix du medecin remplit cette colonne.
ALTER TABLE interview_schedules
    DROP CONSTRAINT IF EXISTS interview_schedules_confirmed_slot_fk;
ALTER TABLE interview_schedules
    ADD CONSTRAINT interview_schedules_confirmed_slot_fk
    FOREIGN KEY (confirmed_slot_id) REFERENCES interview_slots(id) ON DELETE SET NULL;

-- Deux creneaux identiques sur le meme entretien sont un doublon de saisie, jamais une
-- intention : le medecin verrait deux fois la meme proposition.
CREATE UNIQUE INDEX IF NOT EXISTS uq_interview_slots_moment
    ON interview_slots (schedule_id, starts_at);

CREATE INDEX IF NOT EXISTS idx_interview_slots_schedule
    ON interview_slots (schedule_id, starts_at);

-- Un seul entretien vivant par dossier. Sans cette regle, un CHU qui reproposerait des
-- creneaux sans annuler les precedents laisserait le medecin devant deux convocations
-- concurrentes, sans savoir laquelle fait foi. Les entretiens annules restent en base et
-- n'entrent pas dans le compte : reprogrammer suppose donc d'annuler explicitement.
CREATE UNIQUE INDEX IF NOT EXISTS uq_interview_schedule_actif
    ON interview_schedules (enrollment_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX IF NOT EXISTS idx_interview_schedules_status
    ON interview_schedules (status);

-- Nouveau type d'email. L'enum Java `EmailType` et cette contrainte sont deux sources de
-- verite qu'il faut faire evoluer ensemble — l'oubli se manifeste par un echec d'ecriture de
-- la trace, apres que l'email est parti.
ALTER TABLE email_logs DROP CONSTRAINT IF EXISTS email_logs_type_check;
ALTER TABLE email_logs ADD CONSTRAINT email_logs_type_check
    CHECK (email_type IN ('CREDENTIALS', 'TEST', 'INTERVIEW_SLOTS', 'INTERVIEW_CONFIRMED'));

COMMENT ON TABLE interview_schedules IS
    'Entretien de selection : le CHU propose des creneaux, OptimiSante transmet, le medecin '
    'choisit. Independant du statut du dossier — aucun statut d''enrollment n''est ajoute.';

-- La convocation entre au coffre du dossier sous son propre nom. La ranger dans « OTHER »
-- l'aurait rendue indiscernable au moment de reconstituer le dossier de visa — c'est
-- precisement ce que la taxonomie de la V37 sert a eviter. Les deux contraintes qui portent
-- cette taxonomie evoluent donc ensemble, ainsi que l'enum Java `DocumentType`.
ALTER TABLE enrollment_documents DROP CONSTRAINT IF EXISTS enrollment_documents_document_type_check;
ALTER TABLE enrollment_documents ADD CONSTRAINT enrollment_documents_document_type_check
    CHECK (document_type IN ('PASSPORT', 'DIPLOMA', 'MEDICAL_COUNCIL_CERT', 'FINANCIAL_GUARANTEE',
                             'VISA_GRANT', 'CONSULAR_LETTER', 'ACCOMMODATION_PROOF',
                             'INTERVIEW_CONVOCATION', 'OTHER'));

ALTER TABLE enrollment_document_requests DROP CONSTRAINT IF EXISTS enrollment_document_requests_type_check;
ALTER TABLE enrollment_document_requests ADD CONSTRAINT enrollment_document_requests_type_check
    CHECK (document_type IN ('PASSPORT', 'DIPLOMA', 'MEDICAL_COUNCIL_CERT', 'FINANCIAL_GUARANTEE',
                             'VISA_GRANT', 'CONSULAR_LETTER', 'ACCOMMODATION_PROOF',
                             'INTERVIEW_CONVOCATION', 'OTHER'));
