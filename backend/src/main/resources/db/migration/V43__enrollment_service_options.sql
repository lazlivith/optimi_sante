-- V43 : options de services (assurance, hebergement, transport)
--
-- CE QUE C'EST
-- Des prestations qu'OptimiSante organise autour du sejour, en plus de la formation :
-- l'assurance du candidat, son hebergement, ses transferts. Le medecin les choisit, les regle
-- en une fois, et recoit une attestation de souscription au coffre de son dossier.
--
-- POURQUOI 100 % OPTIMISANTE
-- Ces services ne sont pas rendus par le CHU : c'est l'agence qui les monte et les paie a ses
-- prestataires. Les faire entrer dans la repartition avec le partenaire reverserait a
-- l'etablissement une part d'un travail qu'il n'a pas fourni. Le taux est donc 100, comme pour
-- les frais de dossier — et l'invariant `commission + part partenaire = brut` tient sans
-- exception : commission = brut, part partenaire = 0.
--
-- CE QUE CETTE MIGRATION NE FAIT PAS
-- Aucun statut de dossier n'est ajoute, aucune transition modifiee. Souscrire une option
-- n'avance pas la candidature — elle s'y greffe, comme les pieces (V37) et les entretiens
-- (V41). Une inscription reste valable sans aucune option.

-- -------------------------------------------------------------------------------------
-- 1. Catalogue : ce qui est propose, et a quel prix, sur une formation donnee.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS training_service_options (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    training_id  uuid NOT NULL REFERENCES trainings(id) ON DELETE CASCADE,

    option_type  varchar(20) NOT NULL,

    -- Le libelle est libre parce que le type ne suffit pas : « HOUSING » ne dit pas au medecin
    -- s'il s'agit d'un studio meuble ou d'une chambre en internat.
    label        varchar(255) NOT NULL,
    description  text,

    -- Le tarif depend de la ville, de la duree, du prestataire : il se fixe formation par
    -- formation, jamais globalement.
    price        numeric(10,2) NOT NULL,

    -- Retirer une option du catalogue ne doit pas effacer celles deja souscrites : on la
    -- desactive, la ligne survit et les souscriptions passees restent lisibles.
    is_active    boolean NOT NULL DEFAULT true,

    -- Pas de cle etrangere vers `users`, comme en V25/V37/V41 : un administrateur desactive ne
    -- doit pas rendre l'historique du catalogue illisible.
    created_by   uuid,
    created_at   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT training_service_options_type_check CHECK (
        option_type IN ('INSURANCE', 'HOUSING', 'TRANSPORT')
    ),
    CONSTRAINT training_service_options_price_check CHECK (price >= 0),
    CONSTRAINT training_service_options_label_check CHECK (length(trim(label)) > 0)
);

-- Une seule offre vivante par type et par formation. Deux hebergements actifs sur la meme
-- formation obligeraient le medecin a choisir entre deux propositions concurrentes de la meme
-- plateforme, sans rien pour les departager.
CREATE UNIQUE INDEX IF NOT EXISTS uq_tso_active_type
    ON training_service_options (training_id, option_type)
    WHERE is_active;

CREATE INDEX IF NOT EXISTS idx_tso_training ON training_service_options (training_id);

-- -------------------------------------------------------------------------------------
-- 2. Souscriptions : ce que le medecin a retenu sur son dossier.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS enrollment_service_options (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id uuid NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,

    -- ON DELETE SET NULL : si l'offre du catalogue disparait, la souscription subsiste. Elle
    -- porte deja tout ce qu'il faut pour etre lue — c'est l'objet des trois colonnes copiees
    -- ci-dessous.
    training_service_option_id uuid REFERENCES training_service_options(id) ON DELETE SET NULL,

    -- Type, libelle et prix sont COPIES au moment du choix, jamais relus dynamiquement. Meme
    -- regle qu'en V27 pour le taux de commission : renegocier un tarif ne doit pas reecrire ce
    -- qui a deja ete propose et accepte. Sans cette copie, un changement de prix modifierait
    -- retroactivement le montant d'une souscription deja reglee.
    option_type   varchar(20)  NOT NULL,
    label         varchar(255) NOT NULL,
    unit_price    numeric(10,2) NOT NULL,

    status        varchar(20) NOT NULL DEFAULT 'SELECTED',

    -- Le paiement qui a couvert cette option. Plusieurs options partagent la meme ligne :
    -- le medecin regle son panier en une fois.
    payment_id    uuid REFERENCES enrollment_payments(id) ON DELETE SET NULL,

    selected_at   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at       timestamptz,

    CONSTRAINT eso_type_check CHECK (
        option_type IN ('INSURANCE', 'HOUSING', 'TRANSPORT')
    ),
    CONSTRAINT eso_status_check CHECK (
        status IN ('SELECTED', 'PAID', 'CANCELLED')
    ),
    CONSTRAINT eso_price_check CHECK (unit_price >= 0),

    -- Une option « payee » sans paiement rattache ni date serait invendable a un controle
    -- comptable : on saurait qu'elle a ete reglee sans pouvoir dire par quel encaissement.
    CONSTRAINT eso_coherent CHECK (
        (status = 'SELECTED'  AND paid_at IS NULL     AND payment_id IS NULL)
     OR (status = 'PAID'      AND paid_at IS NOT NULL AND payment_id IS NOT NULL)
     OR (status = 'CANCELLED')
    )
);

-- Un type d'option ne peut etre souscrit qu'une fois par dossier tant qu'il n'est pas annule.
-- Sans cette regle, un double clic facturerait deux fois le meme hebergement.
CREATE UNIQUE INDEX IF NOT EXISTS uq_eso_active_type
    ON enrollment_service_options (enrollment_id, option_type)
    WHERE status <> 'CANCELLED';

CREATE INDEX IF NOT EXISTS idx_eso_enrollment ON enrollment_service_options (enrollment_id, status);
CREATE INDEX IF NOT EXISTS idx_eso_payment    ON enrollment_service_options (payment_id);

-- -------------------------------------------------------------------------------------
-- 3. Nouveau type d'encaissement au registre financier.
-- -------------------------------------------------------------------------------------
-- L'enum Java `PaymentType` et cette contrainte sont deux sources de verite a faire evoluer
-- ensemble (voir V27).
ALTER TABLE enrollment_payments DROP CONSTRAINT IF EXISTS enrollment_payments_payment_type_check;
ALTER TABLE enrollment_payments ADD CONSTRAINT enrollment_payments_payment_type_check
    CHECK (payment_type IN ('DOSSIER_FEE', 'TUITION_FEE', 'SERVICE_OPTIONS'));

-- Volontairement PAS d'index unique « un seul paiement d'options par dossier », contrairement
-- aux frais de dossier et de formation. Le medecin peut souscrire une assurance en mars puis
-- un hebergement en juin : ce sont deux encaissements legitimes sur le meme dossier. C'est
-- l'index ci-dessous qui porte l'idempotence, au niveau ou elle a un sens — la session Stripe.
CREATE UNIQUE INDEX IF NOT EXISTS uq_enrollment_payments_stripe_session
    ON enrollment_payments (stripe_checkout_session_id)
    WHERE stripe_checkout_session_id IS NOT NULL;

-- -------------------------------------------------------------------------------------
-- 4. L'attestation de souscription entre au coffre sous son propre nom.
-- -------------------------------------------------------------------------------------
-- OptimiSante n'est pas l'assureur : le document atteste de la SOUSCRIPTION des services
-- aupres de la plateforme et du reglement, il ne tient pas lieu de certificat d'assurance,
-- que l'assureur emet en son nom propre. Le libelle du type le dit.
ALTER TABLE enrollment_documents DROP CONSTRAINT IF EXISTS enrollment_documents_document_type_check;
ALTER TABLE enrollment_documents ADD CONSTRAINT enrollment_documents_document_type_check
    CHECK (document_type IN ('PASSPORT', 'DIPLOMA', 'MEDICAL_COUNCIL_CERT', 'FINANCIAL_GUARANTEE',
                             'VISA_GRANT', 'CONSULAR_LETTER', 'ACCOMMODATION_PROOF',
                             'INTERVIEW_CONVOCATION', 'SERVICE_SUBSCRIPTION', 'OTHER'));

ALTER TABLE enrollment_document_requests DROP CONSTRAINT IF EXISTS enrollment_document_requests_type_check;
ALTER TABLE enrollment_document_requests ADD CONSTRAINT enrollment_document_requests_type_check
    CHECK (document_type IN ('PASSPORT', 'DIPLOMA', 'MEDICAL_COUNCIL_CERT', 'FINANCIAL_GUARANTEE',
                             'VISA_GRANT', 'CONSULAR_LETTER', 'ACCOMMODATION_PROOF',
                             'INTERVIEW_CONVOCATION', 'SERVICE_SUBSCRIPTION', 'OTHER'));

COMMENT ON TABLE training_service_options IS
    'Catalogue des services proposes autour d''une formation (assurance, hebergement, '
    'transport). Tarifes par l''administration de la mobilite : recette 100 % OptimiSante.';

COMMENT ON TABLE enrollment_service_options IS
    'Services souscrits par le medecin sur son dossier. Type, libelle et prix sont copies au '
    'moment du choix : renegocier un tarif ne reecrit pas une souscription deja acceptee.';
