-- ═════════════════════════════════════════════════════════════════════════════════════════
-- « Exercer en France » : information EVC / AEP et demandes d'accompagnement
--
-- Deux natures de données cohabitent ici, et la frontière décide du tenant.
--
--   RÉFÉRENTIEL ÉDITORIAL (sections, périodes, liens, spécialités, instantanés publiés)
--   Il décrit une procédure administrative française, identique pour qui la lit. Il ne porte
--   donc PAS de tenant_id, comme les autres référentiels du projet.
--
--   DONNÉES D'EXPLOITATION (offres, demandes d'accompagnement)
--   Une offre se vend, une demande se traite et se facture. Elles portent tenant_id, comme
--   les commandes et les dossiers.
--
-- La règle de fait du projet est celle-là : 19 tables sur 50 portent tenant_id, et ce sont
-- les données d'exploitation.
-- ═════════════════════════════════════════════════════════════════════════════════════════


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 1. Les sections de la page
--
-- Le texte est modifiable sans redéploiement : c'est de la réglementation, elle bouge, et
-- celui qui la suit n'est pas celui qui déploie.
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE evc_sections (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Clé stable : le gabarit de la page la cite, le libellé peut changer sans rien casser.
    cle         varchar(60)  NOT NULL UNIQUE,
    sur_titre   varchar(120),
    titre       varchar(255) NOT NULL,
    contenu     text,
    ordre       integer      NOT NULL DEFAULT 0,
    visible     boolean      NOT NULL DEFAULT true,
    modifie_le  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT evc_sections_ordre_check CHECK (ordre >= 0)
);

COMMENT ON COLUMN evc_sections.cle IS
    'Identifiant stable cité par le gabarit de la page publique (bandeau_non_officiel, evc, '
    'aep, dossier, cae, autres_voies, accompagnements).';


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 2. Les périodes de dépôt de l'AEP
--
-- Le statut public — ouvert, à venir, fermé — n'est PAS une colonne. Il se déduit des dates
-- à chaque lecture. Stocké, il se périmerait en silence : une période resterait « ouverte »
-- le lendemain de sa clôture, et personne ne s'en apercevrait avant un candidat éconduit.
--
-- Les dates sont nullables : « Périodes 2027 » existe avant que le CNG n'annonce ses dates,
-- et s'affiche « À venir ».
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE evc_periods (
    id                         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    libelle                    varchar(120) NOT NULL,
    date_debut                 date,
    date_fin                   date,
    specialites_non_ouvertes   text,
    -- Obligatoire : une date de dépôt qui n'est pas adossée à une publication officielle
    -- n'a aucune valeur pour le candidat, et nous ne sommes pas une source.
    source_url                 varchar(1000) NOT NULL,
    visible                    boolean      NOT NULL DEFAULT true,
    ordre                      integer      NOT NULL DEFAULT 0,
    CONSTRAINT evc_periods_dates_check
        CHECK (date_debut IS NULL OR date_fin IS NULL OR date_fin >= date_debut)
);

COMMENT ON TABLE evc_periods IS
    'Périodes officielles de dépôt. Le statut public (ouvert / a_venir / ferme) est CALCULÉ '
    'à la lecture, en Europe/Paris, date de fin incluse. Il n''est jamais stocké.';


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 3. Les liens officiels
--
-- `verifie_le` sert le rappel « non vérifié depuis 90 jours » : un lien d'administration qui
-- change sans prévenir envoie le candidat sur une page morte, et c'est nous qu'il blâmera.
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE evc_links (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cle        varchar(60)   NOT NULL UNIQUE,
    libelle    varchar(255)  NOT NULL,
    -- « A_COMPLETER » tant que l'adresse n'est pas renseignée : la page masque alors le lien
    -- plutôt que d'afficher un bouton qui ne mène nulle part.
    url        varchar(1000) NOT NULL DEFAULT 'A_COMPLETER',
    verifie_le timestamptz
);


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 4. Les professions et spécialités relevant de la commission nationale
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE evc_specialties (
    id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    groupe  varchar(20)  NOT NULL,
    libelle varchar(160) NOT NULL,
    ordre   integer      NOT NULL DEFAULT 0,
    CONSTRAINT evc_specialties_groupe_check CHECK (groupe IN ('profession', 'medecine')),
    CONSTRAINT evc_specialties_unicite UNIQUE (groupe, libelle)
);


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 5. Les offres d'accompagnement  — DONNÉE D'EXPLOITATION, donc tenant_id
--
-- `prix` est nullable, et ce n'est pas un oubli : tant qu'il est vide, la page affiche
-- « Sur devis ». Un prix à 0 voudrait dire « gratuit », ce qui n'est pas la même chose.
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE evc_offers (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cle            varchar(60)  NOT NULL,
    sur_titre      varchar(120),
    titre          varchar(255) NOT NULL,
    -- Les points de l'offre, un par ligne. Une table fille pour quatre puces figées
    -- coûterait une jointure à chaque affichage sans rien apporter.
    points         text,
    prix           numeric(12, 2),
    devise         varchar(3)   NOT NULL DEFAULT 'EUR',
    libelle_bouton varchar(80)  NOT NULL,
    actif          boolean      NOT NULL DEFAULT true,
    ordre          integer      NOT NULL DEFAULT 0,
    CONSTRAINT evc_offers_cle_unicite UNIQUE (tenant_id, cle),
    CONSTRAINT evc_offers_prix_check CHECK (prix IS NULL OR prix >= 0)
);

COMMENT ON COLUMN evc_offers.prix IS
    'Nul tant que le super administrateur ne l''a pas fixé : la page affiche alors '
    '« Sur devis ». Zéro signifierait gratuit, ce qui n''est pas la même chose.';


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 6. Les versions publiées
--
-- Une version est un INSTANTANÉ FIGÉ de la réponse publique, en un seul document.
--
-- Pourquoi pas un versionnement table par table : il faudrait estampiller chaque ligne des
-- cinq tables ci-dessus, et toute lecture publique deviendrait une reconstitution à date.
-- Lourd à écrire, lourd à lire, et faux au premier oubli.
--
-- Ici, les tables ci-dessus SONT le brouillon ; cette table seule est le publié. La page
-- publique ne lit qu'une ligne — la dernière. Restaurer, c'est republier un ancien
-- instantané : l'historique ne se réécrit jamais, il s'allonge.
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE evc_publications (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version       integer     NOT NULL UNIQUE,
    instantane    jsonb       NOT NULL,
    publie_le     timestamptz NOT NULL DEFAULT now(),
    publie_par    uuid        REFERENCES users(id) ON DELETE SET NULL,
    -- Renseigné quand cette version reprend le contenu d'une précédente.
    restaure_de   integer,
    CONSTRAINT evc_publications_version_check CHECK (version > 0)
);

-- La page publique ne demande jamais autre chose que la dernière version.
CREATE INDEX idx_evc_publications_derniere ON evc_publications (version DESC);


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 7. Les demandes d'accompagnement  — DONNÉE D'EXPLOITATION, donc tenant_id
--
-- Le tenant est posé par le SERVEUR, jamais par le visiteur. La route publique est ouverte
-- aux non-connectés : lire le tenant dans un en-tête ou dans le corps reviendrait à laisser
-- l'appelant choisir chez qui sa demande atterrit.
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE accompaniment_requests (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    nom                  varchar(160) NOT NULL,
    email                varchar(255) NOT NULL,
    telephone_whatsapp   varchar(40),
    pays                 varchar(80),
    profession           varchar(80),
    specialite           varchar(160),
    etape                varchar(20)  NOT NULL DEFAULT 'autre',
    -- Clé de l'offre, et non clé étrangère : une offre retirée du catalogue ne doit pas
    -- emporter les demandes qu'elle a suscitées, ni empêcher de les relire.
    offre_cle            varchar(60),
    message              text,

    -- Le consentement se prouve : la case cochée ne vaut rien sans la date.
    consentement_rgpd    boolean      NOT NULL DEFAULT false,
    consentement_le      timestamptz,

    statut               varchar(20)  NOT NULL DEFAULT 'nouveau',
    assigne_a            uuid         REFERENCES users(id) ON DELETE SET NULL,
    notes_internes       text,

    cree_le              timestamptz  NOT NULL DEFAULT now(),
    -- Distincte de cree_le, et c'est tout l'enjeu de la conservation : « 24 mois après la
    -- dernière activité » calculé sur la date de création effacerait un dossier encore suivi.
    -- Mise à jour à chaque changement de statut, assignation ou note.
    derniere_activite_le timestamptz  NOT NULL DEFAULT now(),
    -- Date de fin de prestation, qui ouvre le délai de conservation des demandes réglées.
    prestation_fin_le    timestamptz,
    -- Renseignée par la tâche d'anonymisation : la ligne reste, les données nominatives non.
    anonymise_le         timestamptz,

    CONSTRAINT accompaniment_requests_etape_check
        CHECK (etape IN ('avant_evc', 'laureat', 'affecte', 'autre')),
    CONSTRAINT accompaniment_requests_statut_check
        CHECK (statut IN ('nouveau', 'qualifie', 'devis_envoye', 'paye',
                          'en_cours', 'termine', 'perdu')),
    -- Une demande enregistrée sans consentement n'aurait pas dû l'être.
    CONSTRAINT accompaniment_requests_consentement_check
        CHECK (consentement_rgpd = true AND consentement_le IS NOT NULL)
);

-- La liste de l'administration filtre par statut et trie par date.
CREATE INDEX idx_accompaniment_requests_suivi
    ON accompaniment_requests (tenant_id, statut, cree_le DESC);

-- La tâche d'anonymisation balaie par ancienneté, en ignorant ce qui l'est déjà.
CREATE INDEX idx_accompaniment_requests_conservation
    ON accompaniment_requests (derniere_activite_le)
    WHERE anonymise_le IS NULL;

-- Le garde-fou anti-abus compte les demandes d'une même adresse sur la journée.
CREATE INDEX idx_accompaniment_requests_email_jour
    ON accompaniment_requests (email, cree_le DESC);

COMMENT ON TABLE accompaniment_requests IS
    'Demandes déposées depuis la page publique « Exercer en France ». Conservation : '
    'anonymisation 24 mois après la dernière activité pour les demandes non converties, '
    'et 5 ans après la fin de prestation pour celles passées au statut « payé ». '
    'Les deux durées sont des paramètres d''exploitation (parametres_plateforme).';


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 8. Le fil d'une demande
--
-- Table dédiée, et non le journal d'audit, pour une raison de droit autant que de confort :
-- le fil contient des données personnelles — le texte d'une note parle du candidat — et doit
-- donc être anonymisable avec la demande. Le journal d'audit, lui, est une preuve : il ne se
-- modifie pas. Mêler les deux obligerait soit à réécrire l'audit, soit à conserver des
-- données nominatives au-delà de leur durée.
--
-- Conséquence directe sur ce qui est écrit où : le fil porte le contenu, l'audit ne porte que
-- l'acte — qui, quoi, quand, sur quelle demande. Jamais de nom, d'adresse ni de note.
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE accompaniment_request_events (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id     uuid        NOT NULL REFERENCES accompaniment_requests(id) ON DELETE CASCADE,
    tenant_id      uuid        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type           varchar(20) NOT NULL,
    ancien_statut  varchar(20),
    nouveau_statut varchar(20),
    contenu        text,
    -- Nul pour le premier événement : la demande naît d'un visiteur non connecté.
    -- L'anonymisation vide le contenu mais garde l'auteur, qui n'est pas le candidat.
    auteur_id      uuid        REFERENCES users(id) ON DELETE SET NULL,
    cree_le        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT accompaniment_request_events_type_check
        CHECK (type IN ('creation', 'statut', 'assignation', 'note'))
);

CREATE INDEX idx_accompaniment_request_events_fil
    ON accompaniment_request_events (request_id, cree_le);

COMMENT ON TABLE accompaniment_request_events IS
    'Fil d''une demande, lu par la fiche détail. Contient des données personnelles et '
    'est anonymisé avec la demande — contrairement au journal d''audit, qui est une preuve '
    'et ne se modifie jamais.';


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 9. Les durées de conservation, en paramètres plutôt qu'en dur
--
-- Elles sont juridiques, pas techniques : elles bougeront après l'avis du juriste, et il ne
-- faut pas un déploiement pour cela. Même raisonnement que pour l'interrupteur de TVA (V70).
-- ─────────────────────────────────────────────────────────────────────────────────────────

INSERT INTO parametres_plateforme (tenant_id, cle, valeur)
SELECT t.id, p.cle, p.valeur
FROM tenants t
CROSS JOIN (VALUES
    ('rgpd.accompagnement.mois_avant_anonymisation',        '24'),
    ('rgpd.accompagnement.mois_conservation_apres_paiement', '60')
) AS p(cle, valeur)
ON CONFLICT (tenant_id, cle) DO NOTHING;


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 10. Les deux nouveaux types d'email
--
-- PostgreSQL ne sait pas altérer un CHECK en place : la contrainte est DÉTRUITE puis
-- recréée, et doit donc réénumérer TOUTES les valeurs, y compris celles posées par d'autres
-- travaux (V24, V41, V50). En omettre une ne casse ni la compilation ni le démarrage —
-- seulement l'écriture de la trace du premier email concerné, c'est-à-dire tard et ailleurs.
-- ─────────────────────────────────────────────────────────────────────────────────────────

ALTER TABLE email_logs DROP CONSTRAINT IF EXISTS email_logs_type_check;
ALTER TABLE email_logs ADD CONSTRAINT email_logs_type_check CHECK (
    email_type IN (
        'CREDENTIALS',
        'TEST',
        'INTERVIEW_SLOTS',
        'INTERVIEW_CONFIRMED',
        'NOTIFICATION',
        'PAYMENT_CONFIRMATION',
        -- Accusé de réception envoyé au praticien qui dépose une demande.
        'ACCOMPAGNEMENT_RECU',
        -- Alerte interne : une demande vient d'arriver.
        'ACCOMPAGNEMENT_ALERTE_ADMIN'
    )
);
