-- Demandes de partenariat CHU / Centre de Formation : un établissement souhaitant rejoindre
-- la plateforme télécharge la convention de partenariat, la remplit, l'envoie avec ses
-- coordonnées. L'admin étudie et valide/rejette. Si validé, un compte CENTRE_FORMATION est
-- créé automatiquement et les identifiants sont envoyés par email.
CREATE TABLE partnership_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_name VARCHAR(255) NOT NULL,
    finess_accreditation VARCHAR(100),
    contact_person_name VARCHAR(150) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    address TEXT NOT NULL,
    convention_file_key VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    rejection_reason TEXT,
    created_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_partnership_requests_status ON partnership_requests(status);
