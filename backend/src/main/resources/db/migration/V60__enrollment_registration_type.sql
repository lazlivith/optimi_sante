-- Parcours d'inscription : international avec visa, ou direct pour un praticien exerçant en France.
--
-- Pourquoi une colonne plutôt que de nouveaux statuts : les onze statuts d'EnrollmentStatus, la
-- contrainte enrollments_status_check (V26) et ENROLLMENT_STEPS côté React doivent rester alignés.
-- Un discriminant laisse ces trois sources intactes ; le parcours France emprunte les mêmes
-- statuts en sautant les deux étapes de visa.
--
-- La valeur par défaut vaut pour les dossiers existants : tous ont été ouverts au titre d'une
-- mobilité internationale, et aucun ne doit changer de parcours du fait de cette migration.

ALTER TABLE enrollments
    ADD COLUMN IF NOT EXISTS registration_type varchar(20) NOT NULL DEFAULT 'INTERNATIONAL_VISA';

ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS enrollments_registration_type_check;
ALTER TABLE enrollments
    ADD CONSTRAINT enrollments_registration_type_check
    CHECK (registration_type IN ('INTERNATIONAL_VISA', 'LOCAL_FRANCE'));

COMMENT ON COLUMN enrollments.registration_type IS
    'INTERNATIONAL_VISA : parcours complet, accompagnement consulaire et visa. '
    'LOCAL_FRANCE : praticien déjà établi en France, parcours direct sans étape visa.';

-- Identifiant du praticien au répertoire national : RPPS (11 chiffres) ou ADELI (9 chiffres).
-- Exigé pour un parcours France, sans objet pour un candidat qui n'exerce pas encore en France.
ALTER TABLE enrollments
    ADD COLUMN IF NOT EXISTS rpps_number varchar(11);

ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS enrollments_rpps_number_check;
ALTER TABLE enrollments
    ADD CONSTRAINT enrollments_rpps_number_check
    CHECK (rpps_number IS NULL OR rpps_number ~ '^[0-9]{9}$|^[0-9]{11}$');

COMMENT ON COLUMN enrollments.rpps_number IS
    'Numéro RPPS (11 chiffres) ou ADELI (9 chiffres) du praticien exerçant en France.';

-- Un dossier France sans identifiant ordinal n'a pas lieu d'être : la règle est portée par le
-- service, et cette contrainte empêche qu'une écriture directe en base la contourne.
ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS enrollments_france_requires_rpps;
ALTER TABLE enrollments
    ADD CONSTRAINT enrollments_france_requires_rpps
    CHECK (registration_type <> 'LOCAL_FRANCE' OR rpps_number IS NOT NULL);

-- Les écrans d'administration filtrent par parcours ; l'index sert ce filtre sans peser sur
-- les écritures, la colonne ne prenant que deux valeurs.
CREATE INDEX IF NOT EXISTS idx_enrollments_registration_type
    ON enrollments (registration_type);
