-- =====================================================================================
-- V23 — Inscription B2B à portée internationale + identité B2C
-- =====================================================================================
-- Contexte : le formulaire d'inscription B2B collectait des champs franco-centrés
-- (SIRET/FINESS, TVA, adresse de facturation) et l'inscription B2C ne collectait aucune
-- identité (ni prénom ni nom). Cette migration prépare le schéma pour les deux nouveaux
-- formulaires, sans rien retirer ni casser des données existantes.
-- Idempotente : rejouable sans effet de bord.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. users : identité générique (utilisée par le B2C ; les médecins gardent leur
--    DoctorProfile comme source de vérité, ces colonnes restent simplement NULL pour eux).
-- -------------------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name  VARCHAR(100);

-- -------------------------------------------------------------------------------------
-- 2. company_profiles : passage d'un identifiant légal franco-centré à un identifiant
--    fiscal générique international. `siret_finess` est RENOMMÉE (et non dupliquée) afin
--    de conserver toutes les données existantes des comptes B2B déjà inscrits.
--    NB : la colonne réelle s'appelle bien `siret_finess` (V1), pas `ice_siret`.
-- -------------------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'company_profiles'
                 AND column_name = 'siret_finess')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = 'public' AND table_name = 'company_profiles'
                         AND column_name = 'tax_id')
    THEN
        ALTER TABLE company_profiles RENAME COLUMN siret_finess TO tax_id;
    END IF;
END $$;

-- Champs de la nouvelle fiche établissement (nullables : les comptes B2B déjà créés
-- avant cette migration ne les renseignent pas, ils restent parfaitement valides).
ALTER TABLE company_profiles ADD COLUMN IF NOT EXISTS country       VARCHAR(100);
ALTER TABLE company_profiles ADD COLUMN IF NOT EXISTS facility_type VARCHAR(50);
ALTER TABLE company_profiles ADD COLUMN IF NOT EXISTS contact_name  VARCHAR(150);

-- -------------------------------------------------------------------------------------
-- 3. L'adresse de facturation n'est plus demandée à l'inscription (elle sera collectée
--    au moment du devis/de la commande, quand elle a réellement du sens). Sans ce
--    DROP NOT NULL, toute inscription B2B via le nouveau formulaire échouerait en base.
--    Les valeurs déjà enregistrées sont conservées telles quelles.
-- -------------------------------------------------------------------------------------
ALTER TABLE company_profiles ALTER COLUMN billing_address DROP NOT NULL;

-- -------------------------------------------------------------------------------------
-- 4. Contrainte de cohérence sur le type d'établissement, tenue volontairement alignée
--    sur l'enum Java `FacilityType`. NULL autorisé pour les comptes antérieurs.
--    (Leçon de l'entrée #40 du journal : enum Java et CHECK SQL doivent être posés
--    ensemble, sinon ils divergent silencieusement.)
-- -------------------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'company_profiles'::regclass
                     AND conname  = 'company_profiles_facility_type_check')
    THEN
        ALTER TABLE company_profiles
            ADD CONSTRAINT company_profiles_facility_type_check
            CHECK (facility_type IS NULL OR facility_type IN (
                'CLINIC', 'HOSPITAL', 'MEDICAL_PRACTICE', 'LABORATORY', 'DISTRIBUTOR', 'OTHER'
            ));
    END IF;
END $$;
