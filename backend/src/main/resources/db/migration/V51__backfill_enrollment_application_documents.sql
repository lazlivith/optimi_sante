-- =====================================================================================
-- V51 — Rattacher au coffre-fort les pieces deposees a la candidature
-- =====================================================================================
-- Les trois pieces exigees a la candidature (diplome, attestation d'ordre, passeport)
-- etaient ecrites uniquement dans les colonnes `enrollments.*_url`. Or le coffre-fort
-- consulte par l'administration, par le CHU et par le medecin ne lit QUE la table
-- `enrollment_documents`. Consequence : les pieces essentielles d'un dossier etaient
-- invisibles et non telechargeables pour ceux qui doivent precisement les verifier.
--
-- Le code ecrit desormais dans les deux emplacements ; cette migration fait de meme pour
-- les dossiers deja deposes, sans quoi ils resteraient definitivement vides a l'ecran.
--
-- Idempotente : une piece deja presente pour ce type n'est jamais dupliquee.
-- =====================================================================================

INSERT INTO enrollment_documents (enrollment_id, document_type, cloudinary_public_id, is_verified, uploaded_at)
SELECT e.id, 'DIPLOMA', e.diploma_url, FALSE, COALESCE(e.submitted_at, CURRENT_TIMESTAMP)
FROM enrollments e
WHERE e.diploma_url IS NOT NULL AND e.diploma_url <> ''
  AND NOT EXISTS (SELECT 1 FROM enrollment_documents d
                  WHERE d.enrollment_id = e.id AND d.document_type = 'DIPLOMA');

INSERT INTO enrollment_documents (enrollment_id, document_type, cloudinary_public_id, is_verified, uploaded_at)
SELECT e.id, 'MEDICAL_COUNCIL_CERT', e.medical_board_registration_url, FALSE, COALESCE(e.submitted_at, CURRENT_TIMESTAMP)
FROM enrollments e
WHERE e.medical_board_registration_url IS NOT NULL AND e.medical_board_registration_url <> ''
  AND NOT EXISTS (SELECT 1 FROM enrollment_documents d
                  WHERE d.enrollment_id = e.id AND d.document_type = 'MEDICAL_COUNCIL_CERT');

INSERT INTO enrollment_documents (enrollment_id, document_type, cloudinary_public_id, is_verified, uploaded_at)
SELECT e.id, 'PASSPORT', e.passport_url, FALSE, COALESCE(e.submitted_at, CURRENT_TIMESTAMP)
FROM enrollments e
WHERE e.passport_url IS NOT NULL AND e.passport_url <> ''
  AND NOT EXISTS (SELECT 1 FROM enrollment_documents d
                  WHERE d.enrollment_id = e.id AND d.document_type = 'PASSPORT');
