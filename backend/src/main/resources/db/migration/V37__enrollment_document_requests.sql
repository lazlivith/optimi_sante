-- V37 : demandes de pieces adressees au candidat
--
-- CE QUI EXISTAIT
-- Pour reclamer une piece a un medecin, l'administration disposait d'un seul moyen :
-- `requestAction`, qui bascule le dossier en ACTION_REQUIRED avec **une note en texte libre**.
-- Trois limites :
--   1. Une seule demande a la fois. Reclamer trois pieces obligeait a les enumerer dans une
--      phrase, sans pouvoir suivre laquelle est arrivee.
--   2. Le dossier entier est bloque. Or les pieces du dossier de visa se rassemblent **au fil
--      de la procedure** — apres la convention, apres l'acceptation du CHU — moments ou le
--      dossier ne doit surtout pas repartir en revue.
--   3. Rien ne dit ce qu'il reste a fournir. Ni le medecin ni l'administration ne peuvent
--      repondre a « mon dossier est-il complet ? ».
--
-- CE QUE CETTE TABLE APPORTE
-- Une demande = une piece attendue, suivie individuellement de la demande a l'acceptation.
-- Elle est **independante de la machine a etats** : demander une piece ne change pas le statut
-- du dossier. `requestAction` reste le mecanisme *bloquant* de la revue initiale ; les deux
-- coexistent et ne servent pas au meme moment.
--
-- CE QU'ELLE NE FAIT PAS
-- Elle ne remplace pas `enrollment_documents`, qui reste le magasin des fichiers. Une demande
-- *pointe* vers la piece deposee ; elle ne la stocke pas. Un document peut donc exister sans
-- demande — c'est le cas de ceux que l'administration ajoute elle-meme (convention,
-- attestation, courrier consulaire), qui ne sont reclames a personne.

CREATE TABLE IF NOT EXISTS enrollment_document_requests (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id    uuid NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,

    -- Meme taxonomie que `enrollment_documents` : la demande et la piece qui la satisfait
    -- doivent se classer pareil, sinon le dossier de visa se reconstitue mal.
    document_type    varchar(50)  NOT NULL,

    -- Le libelle est libre parce que la taxonomie ne suffit pas : « OTHER » ne dit pas au
    -- medecin qu'on attend un acte de naissance traduit. C'est ce texte qu'il lira.
    label            varchar(255) NOT NULL,
    instructions     text,

    status           varchar(20)  NOT NULL DEFAULT 'PENDING',
    due_date         date,

    -- Pas de cle etrangere vers `users`, comme pour `email_logs` (V25) : un administrateur
    -- desactive ne doit pas empecher de lire l'historique des demandes qu'il a emises.
    requested_by     uuid,
    requested_at     timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- La piece qui satisfait la demande. ON DELETE SET NULL : si le fichier disparait, la
    -- demande subsiste et redevient a satisfaire, plutot que de s'effacer avec lui.
    document_id      uuid REFERENCES enrollment_documents(id) ON DELETE SET NULL,
    submitted_at     timestamptz,

    reviewed_by      uuid,
    reviewed_at      timestamptz,
    rejection_reason text,

    CONSTRAINT enrollment_document_requests_type_check CHECK (
        document_type IN ('PASSPORT', 'DIPLOMA', 'MEDICAL_COUNCIL_CERT', 'FINANCIAL_GUARANTEE',
                          'VISA_GRANT', 'CONSULAR_LETTER', 'ACCOMMODATION_PROOF', 'OTHER')
    ),

    CONSTRAINT enrollment_document_requests_status_check CHECK (
        status IN ('PENDING', 'SUBMITTED', 'ACCEPTED', 'REJECTED', 'CANCELLED')
    ),

    -- Coherence entre l'etat et les colonnes qui doivent l'accompagner. Sans cette contrainte,
    -- une demande pourrait etre « acceptee » sans piece attachee, ou « refusee » sans motif —
    -- et le medecin verrait un refus qu'on ne lui explique pas.
    CONSTRAINT enrollment_document_requests_coherent CHECK (
        (status = 'PENDING'   AND document_id IS NULL     AND reviewed_at IS NULL)
     OR (status = 'SUBMITTED' AND document_id IS NOT NULL AND submitted_at IS NOT NULL)
     OR (status = 'ACCEPTED'  AND document_id IS NOT NULL AND reviewed_at IS NOT NULL)
     OR (status = 'REJECTED'  AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL)
     OR (status = 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_edr_enrollment_status
    ON enrollment_document_requests (enrollment_id, status);

-- Empeche de reclamer deux fois la meme piece tant que la premiere demande n'est pas reglee —
-- un double clic, ou deux administrateurs travaillant sur le meme dossier, produiraient sinon
-- deux lignes identiques et le medecin verrait la meme piece demandee en double.
-- Restreint aux demandes OUVERTES : une fois acceptee, refusee ou annulee, la meme piece peut
-- legitimement etre redemandee (passeport perime, traduction a refaire).
CREATE UNIQUE INDEX IF NOT EXISTS uq_edr_open_label
    ON enrollment_document_requests (enrollment_id, lower(label))
    WHERE status IN ('PENDING', 'SUBMITTED');

COMMENT ON TABLE enrollment_document_requests IS
    'Pieces reclamees au candidat, suivies une par une. Independant du statut du dossier : '
    'demander une piece ne renvoie pas le dossier en revue (voir requestAction pour cela).';
