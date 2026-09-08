-- =====================================================================================
-- V28 — Retrait définitif des statuts hérités du cycle de candidature
-- =====================================================================================
-- La V26 avait volontairement laissé PENDING_REVIEW, APPROVED_ACADEMIC et
-- APPROVED_ADMINISTRATIVE dans la contrainte : tant que l'ancien .jar pouvait redémarrer,
-- il devait pouvoir écrire ses valeurs. Ce n'est plus le cas — le code déployé ne les
-- produit plus nulle part (enum nettoyé, `reviewAcademic` et l'endpoint /academic-review
-- supprimés, filtre partenaire mis à jour).
--
-- Garde-fou : la migration échoue explicitement s'il reste la moindre ligne sur un statut
-- hérité, plutôt que de laisser PostgreSQL rejeter la contrainte avec un message obscur.
-- =====================================================================================

DO $$
DECLARE legacy_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO legacy_count
    FROM enrollments
    WHERE status IN ('PENDING_REVIEW', 'APPROVED_ACADEMIC', 'APPROVED_ADMINISTRATIVE');

    IF legacy_count > 0 THEN
        RAISE EXCEPTION
            'V28 interrompue : % dossier(s) encore sur un statut hérité. '
            'Reprendre ces lignes (voir la table de correspondance de V26) avant de rejouer.',
            legacy_count;
    END IF;
END $$;

ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS enrollments_status_check;
ALTER TABLE enrollments ADD CONSTRAINT enrollments_status_check CHECK (status IN (
    -- Cycle commercial
    'UNDER_OPTIMI_REVIEW', 'ACTION_REQUIRED', 'SUBMITTED_TO_PARTNER',
    'ACCEPTED_BY_PARTNER', 'PENDING_TUITION_FEE', 'CONFIRMED',
    -- Cycle de mobilité
    'CONVENTION_ISSUED', 'VISA_SUBMITTED', 'VISA_GRANTED', 'READY_TO_START',
    -- Sorties terminales
    'REJECTED', 'CANCELLED'
));

-- Colonne héritée du schéma V1 (nom du CDC d'origine), jamais alimentée : le code utilise
-- `convention_s3_key`. Conservée jusqu'ici par prudence, retirée maintenant que le cycle
-- documentaire est entièrement recâblé et vérifié.
ALTER TABLE enrollments DROP COLUMN IF EXISTS tripartite_convention_s3_key;
