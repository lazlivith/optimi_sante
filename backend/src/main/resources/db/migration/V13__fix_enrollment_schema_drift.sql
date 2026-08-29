-- Suite du nettoyage V12 : le schéma V1 initial (design CDC d'origine) et les entités JPA
-- actuelles (design simplifié) ont divergé sur les tables enrollments / enrollment_documents.
-- Hibernate (ddl-auto: update) a ajouté les colonnes correspondant aux entités sans jamais
-- supprimer les anciennes colonnes/contraintes du schéma V1, laissant des pièges NOT NULL
-- et des contraintes obsolètes qui bloquent silencieusement toute écriture réelle.

-- 1. enrollments.enrollment_number : NOT NULL, jamais alimentée par l'entité Enrollment (pas de champ correspondant)
ALTER TABLE enrollments DROP COLUMN IF EXISTS enrollment_number;

-- 2. enrollments.doctor_id portait deux FK simultanées : une vers doctor_profiles(id) (design V1 abandonné)
--    et une vers users(id) (design réel de l'entité Enrollment.doctor). La première est obsolète
--    et bloquerait tout insert (doctor_id contient un users.id, pas un doctor_profiles.id).
ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS enrollments_doctor_id_fkey;

-- 3. enrollment_documents.s3_key : NOT NULL, remplacée par cloudinary_public_id / file_url dans l'entité
ALTER TABLE enrollment_documents DROP COLUMN IF EXISTS s3_key;

-- 4. La contrainte CHECK sur document_type ne reconnaissait pas 'OTHER', valeur pourtant valide
--    dans l'enum DocumentType et proposée dans le formulaire frontend.
ALTER TABLE enrollment_documents DROP CONSTRAINT IF EXISTS enrollment_documents_document_type_check;
ALTER TABLE enrollment_documents ADD CONSTRAINT enrollment_documents_document_type_check
    CHECK (document_type IN ('PASSPORT', 'VISA_GRANT', 'OTHER'));
