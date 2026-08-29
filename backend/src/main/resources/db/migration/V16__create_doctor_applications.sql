-- Candidature médecin payante : un médecin candidat (sans compte existant) choisit une session
-- de formation, renseigne son identité, puis paie des frais de dossier fixes (indépendants du
-- prix de la formation, cf. app.doctor-application.fee-amount). Le webhook Stripe confirme le
-- paiement et déclenche automatiquement : création du compte MEDECIN + DoctorProfile + Enrollment
-- (statut PENDING_REVIEW, réutilise le pipeline existant), puis envoi des identifiants par email.
CREATE TABLE doctor_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    session_id UUID NOT NULL REFERENCES training_sessions(id),
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_whatsapp VARCHAR(30) NOT NULL,
    country_of_residence VARCHAR(100) NOT NULL,
    medical_specialty VARCHAR(150) NOT NULL,
    medical_council_number VARCHAR(100),
    current_hospital VARCHAR(255),
    passport_number VARCHAR(100),
    fee_amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT' CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED')),
    stripe_checkout_session_id VARCHAR(255),
    created_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_enrollment_id UUID REFERENCES enrollments(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_doctor_applications_status ON doctor_applications(status);
CREATE INDEX idx_doctor_applications_session ON doctor_applications(session_id);
