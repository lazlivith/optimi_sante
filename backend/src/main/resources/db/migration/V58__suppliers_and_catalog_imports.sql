-- V58 : fournisseurs et imports de catalogue en masse
--
-- CE QUE C'EST
-- Un fournisseur envoie son catalogue — deux mille références, prix et stocks — dans un fichier.
-- Trois tables suffisent : qui est le fournisseur, quel produit lui appartient, et ce qu'a fait
-- chaque dépôt de fichier.
--
-- POURQUOI GARDER LA TRACE DES IMPORTS
-- Un import qui crée 1 840 produits et en ignore 12 doit pouvoir s'expliquer trois mois plus tard :
-- quel fichier, déposé par qui, quelles lignes refusées et pourquoi. Sans cet historique, la seule
-- trace serait le catalogue lui-même, c'est-à-dire le résultat sans la cause.
--
-- CE QUE CETTE MIGRATION NE FAIT PAS
-- Aucun produit existant n'est modifié : `supplier_id` naît NULL sur les 1 518 références déjà en
-- base, et c'est précisément ce qui les protège — un import ne touche jamais un produit qui n'est
-- pas rattaché au fournisseur qui dépose le fichier.

-- -------------------------------------------------------------------------------------
-- 1. Fournisseurs
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS suppliers (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,

    -- Référence courte manipulée par l'équipe (« FOURN-001 »), unique par tenant.
    code            varchar(40)  NOT NULL,
    company_name    varchar(255) NOT NULL,

    -- SIRET, NIU ou équivalent local : la forme varie selon le pays, on ne la contraint pas.
    tax_id          varchar(50),
    contact_name    varchar(150),
    contact_email   varchar(255),
    contact_phone   varchar(30),

    -- Commission négociée, en pourcentage. Informative à ce stade : aucun calcul de reversement
    -- n'est branché dessus, contrairement au taux des CHU (partner_profiles.commission_rate).
    commission_rate numeric(5,2) NOT NULL DEFAULT 0,

    notes           text,
    is_active       boolean NOT NULL DEFAULT true,

    -- Identifiant brut, sans clé étrangère (même raison qu'en V37/V41/V43/V57) : un compte
    -- désactivé ne doit pas rendre l'historique illisible.
    created_by      uuid,
    created_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_suppliers_code UNIQUE (tenant_id, code),
    CONSTRAINT suppliers_code_check CHECK (length(trim(code)) > 0),
    CONSTRAINT suppliers_name_check CHECK (length(trim(company_name)) > 0),
    CONSTRAINT suppliers_commission_check CHECK (commission_rate >= 0 AND commission_rate <= 100)
);

-- -------------------------------------------------------------------------------------
-- 2. Rattachement des produits
-- -------------------------------------------------------------------------------------
-- ON DELETE SET NULL : supprimer un fournisseur ne doit jamais faire disparaître des produits
-- vendus. Ils redeviennent des références du catalogue, comme celles d'avant cette migration.
ALTER TABLE products ADD COLUMN IF NOT EXISTS supplier_id uuid REFERENCES suppliers(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_products_supplier ON products (supplier_id) WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------------------
-- 3. Historique des imports
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog_imports (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    supplier_id     uuid NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,

    file_name       varchar(255) NOT NULL,
    -- Le fichier déposé est conservé : la confirmation le relit, et un import contesté se rejoue.
    storage_key     varchar(255) NOT NULL,

    status          varchar(20) NOT NULL,

    -- Résultat de l'analyse, avant toute écriture.
    total_rows      integer NOT NULL DEFAULT 0,
    to_create       integer NOT NULL DEFAULT 0,
    to_update       integer NOT NULL DEFAULT 0,
    ignored_rows    integer NOT NULL DEFAULT 0,
    error_rows      integer NOT NULL DEFAULT 0,

    -- Avancement pendant le traitement, pour que l'écran affiche autre chose qu'un sablier.
    processed_rows  integer NOT NULL DEFAULT 0,
    created_count   integer NOT NULL DEFAULT 0,
    updated_count   integer NOT NULL DEFAULT 0,
    image_count     integer NOT NULL DEFAULT 0,

    -- Lignes refusées et leur motif, en JSON (texte : la structure appartient au service, pas au
    -- schéma, et le rapport n'est jamais interrogé ligne à ligne en SQL).
    report          text,
    failure_reason  text,

    created_by      uuid,
    created_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at    timestamptz,
    finished_at     timestamptz,

    CONSTRAINT catalog_imports_status_check CHECK (
        status IN ('ANALYSE', 'PRET', 'IMPORT', 'TERMINE', 'ECHEC', 'ANNULE')
    )
);

CREATE INDEX IF NOT EXISTS idx_catalog_imports_supplier ON catalog_imports (supplier_id, created_at DESC);
-- Un seul import en cours par fournisseur : deux fichiers traités en parallèle se disputeraient
-- les mêmes SKU, et le dernier écrirait par-dessus le premier sans que rien ne le signale.
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalog_imports_en_cours
    ON catalog_imports (supplier_id) WHERE status = 'IMPORT';

-- -------------------------------------------------------------------------------------
-- 4. Recherche dans un grand catalogue
-- -------------------------------------------------------------------------------------
-- Les index de la V31 (`text_pattern_ops`) n'accélèrent qu'une recherche par début de mot. La
-- recherche de l'administration cherche « n'importe où » (LIKE '%tensio%') et retombait donc sur
-- un parcours complet de la table — supportable à 1 500 produits, plus à 50 000. Les index trigram
-- couvrent ce cas sans changer une seule requête.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_products_name_trgm ON products USING gin (lower(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_sku_trgm  ON products USING gin (lower(sku) gin_trgm_ops);
