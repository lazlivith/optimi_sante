-- La contrainte CHECK d'origine (V1) autorisait déjà PASSPORT/DIPLOMA/MEDICAL_COUNCIL_CERT/
-- FINANCIAL_GUARANTEE/VISA_GRANT, mais le code Java (enum DocumentType) n'exposait que
-- PASSPORT/VISA_GRANT/OTHER — et "OTHER" n'était pas dans la contrainte, ce qui aurait
-- provoqué une erreur SQL si un médecin choisissait "Autre pièce justificative" à l'upload.
-- On élargit la contrainte pour couvrir "OTHER" tout en gardant les valeurs existantes.
ALTER TABLE enrollment_documents DROP CONSTRAINT enrollment_documents_document_type_check;
ALTER TABLE enrollment_documents ADD CONSTRAINT enrollment_documents_document_type_check
    CHECK (document_type IN ('PASSPORT', 'DIPLOMA', 'MEDICAL_COUNCIL_CERT', 'FINANCIAL_GUARANTEE', 'VISA_GRANT', 'OTHER'));
