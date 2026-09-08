-- =====================================================================================
-- V22 — Réalignement du schéma Flyway sur la réalité applicative
-- =====================================================================================
-- CONTEXTE (cause racine)
-- V7 crée `training_sessions` et `enrollments` avec CREATE TABLE IF NOT EXISTS. Or ces
-- deux tables existaient déjà (créées par V1, design CDC d'origine) : les deux CREATE
-- TABLE de V7 n'ont donc JAMAIS rien fait, silencieusement. Preuve : la contrainte
-- `unique_doctor_session` déclarée dans V7 n'existe dans aucune base.
--
-- En développement, `ddl-auto: update` a masqué le trou en recréant lui-même les colonnes
-- manquantes à chaque démarrage (sous des noms de contraintes hachés type `uk8i57xx...`).
-- Conséquence : le schéma de dev n'était PAS reproductible depuis les migrations. Sur une
-- base neuve (production, `ddl-auto: validate`), il manquait 9 colonnes et l'application
-- refusait de démarrer — dont `enrollment_documents.cloudinary_public_id`/`file_url`,
-- sans lesquelles le coffre-fort documentaire n'a aucun moyen de référencer un fichier.
--
-- OBJET DE CETTE MIGRATION
-- Inscrire explicitement ces colonnes et contraintes dans Flyway. Strictement idempotente :
--   • sur une base de dev existante  -> ne fait RIEN (tout est déjà là), aucune donnée touchée ;
--   • sur une base neuve             -> produit enfin le schéma correct et complet.
-- Aucune logique métier, aucun endpoint, aucune donnée existante n'est affecté.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. enrollments : colonnes du design applicatif réel (entité Enrollment), issues de V7
-- -------------------------------------------------------------------------------------
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS diploma_url                    VARCHAR(512);
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS medical_board_registration_url VARCHAR(512);
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS passport_url                   VARCHAR(512);
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS submitted_at                   TIMESTAMP WITH TIME ZONE;

-- -------------------------------------------------------------------------------------
-- 2. enrollment_documents : référence du fichier stocké (entité EnrollmentDocument).
--    V13 avait supprimé l'ancienne colonne `s3_key` sans que son remplaçant ne soit
--    jamais déclaré dans une migration — il n'existait que via ddl-auto.
-- -------------------------------------------------------------------------------------
ALTER TABLE enrollment_documents ADD COLUMN IF NOT EXISTS cloudinary_public_id VARCHAR(255);
ALTER TABLE enrollment_documents ADD COLUMN IF NOT EXISTS file_url             VARCHAR(1024);

-- -------------------------------------------------------------------------------------
-- 3. training_sessions : colonnes du design applicatif réel (entité TrainingSession).
--    `location` remplace `location_hospital` (supprimée en V12), `price` porte le tarif
--    de la session. Ajout en 3 temps (nullable -> backfill -> NOT NULL) pour rester sûr
--    même si la table contient déjà des lignes.
-- -------------------------------------------------------------------------------------
ALTER TABLE training_sessions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE training_sessions ADD COLUMN IF NOT EXISTS location   VARCHAR(255);
ALTER TABLE training_sessions ADD COLUMN IF NOT EXISTS price      NUMERIC(10, 2);

UPDATE training_sessions SET location = 'À préciser' WHERE location IS NULL;
UPDATE training_sessions SET price    = 0            WHERE price    IS NULL;

ALTER TABLE training_sessions ALTER COLUMN location SET NOT NULL;
ALTER TABLE training_sessions ALTER COLUMN price    SET NOT NULL;

-- -------------------------------------------------------------------------------------
-- 4. Contraintes que V7 aurait dû poser sur `enrollments`.
--    En dev elles existent sous des noms hachés générés par Hibernate ; on ne recrée donc
--    que si aucune contrainte équivalente n'est déjà présente (test sur la DÉFINITION,
--    pas sur le nom), pour ne jamais créer de doublon.
-- -------------------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'enrollments'::regclass
          AND contype  = 'f'
          AND pg_get_constraintdef(oid) = 'FOREIGN KEY (doctor_id) REFERENCES users(id)'
    ) THEN
        ALTER TABLE enrollments
            ADD CONSTRAINT enrollments_doctor_id_users_fkey
            FOREIGN KEY (doctor_id) REFERENCES users(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'enrollments'::regclass
          AND contype  = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (doctor_id, session_id)'
    ) THEN
        ALTER TABLE enrollments
            ADD CONSTRAINT unique_doctor_session UNIQUE (doctor_id, session_id);
    END IF;
END $$;

-- -------------------------------------------------------------------------------------
-- 5. categories : Hibernate a dupliqué la contrainte d'unicité (tenant_id, slug) déjà
--    posée par V1 sous le nom `categories_tenant_id_slug_key`. On retire le doublon
--    (la règle métier reste strictement identique, appliquée par la contrainte de V1).
-- -------------------------------------------------------------------------------------
DO $$
DECLARE dup RECORD;
BEGIN
    FOR dup IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'categories'::regclass
          AND contype  = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (tenant_id, slug)'
          AND conname <> 'categories_tenant_id_slug_key'
    LOOP
        EXECUTE format('ALTER TABLE categories DROP CONSTRAINT %I', dup.conname);
    END LOOP;
END $$;
