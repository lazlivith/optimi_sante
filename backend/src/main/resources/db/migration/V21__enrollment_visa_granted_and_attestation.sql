-- 1. Statut VISA_GRANTED distinct de VISA_SUBMITTED (le CDC distingue "visa soumis" de "visa
--    obtenu"). On en profite pour ajouter CANCELLED, valide dans l'enum Java EnrollmentStatus
--    depuis toujours et déjà utilisé par la logique frontend (Stepper, badges), mais absent de
--    cette contrainte depuis la migration V13 qui avait resserré la liste sans le reporter —
--    un changement de statut vers CANCELLED aurait donc échoué en base.
ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS enrollments_status_check;
ALTER TABLE enrollments ADD CONSTRAINT enrollments_status_check
    CHECK (status IN (
        'PENDING_REVIEW', 'APPROVED_ACADEMIC', 'APPROVED_ADMINISTRATIVE',
        'CONVENTION_ISSUED', 'VISA_SUBMITTED', 'VISA_GRANTED', 'READY_TO_START',
        'REJECTED', 'CANCELLED'
    ));

-- 2. Emplacement de stockage de l'Attestation d'Accueil / Inscription générée automatiquement
--    (même principe que convention_s3_key, déjà en place).
ALTER TABLE enrollments ADD COLUMN attestation_s3_key VARCHAR(255);

-- 3. Documents optionnels que l'admin peut déposer dans le dossier (Lettre d'Accompagnement
--    Consulaire, Attestation d'Hébergement) — actuellement le endpoint d'upload générique
--    accepte déjà les rôles ADMIN/SUPER_ADMIN, il manquait seulement ces valeurs autorisées.
ALTER TABLE enrollment_documents DROP CONSTRAINT enrollment_documents_document_type_check;
ALTER TABLE enrollment_documents ADD CONSTRAINT enrollment_documents_document_type_check
    CHECK (document_type IN (
        'PASSPORT', 'DIPLOMA', 'MEDICAL_COUNCIL_CERT', 'FINANCIAL_GUARANTEE',
        'VISA_GRANT', 'CONSULAR_LETTER', 'ACCOMMODATION_PROOF', 'OTHER'
    ));
