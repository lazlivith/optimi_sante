-- 1. Adaptation de prospect_leads sans destruction
ALTER TABLE prospect_leads ADD COLUMN IF NOT EXISTS source VARCHAR(100);

-- 2. Création de la table training_sessions
CREATE TABLE IF NOT EXISTS training_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    training_id UUID NOT NULL REFERENCES trainings(id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    capacity INT NOT NULL,
    available_seats INT NOT NULL,
    location VARCHAR(255) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_seats CHECK (available_seats >= 0 AND available_seats <= capacity)
);

-- 3. Création de la table enrollments
CREATE TABLE IF NOT EXISTS enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES training_sessions(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_DOCS',
    diploma_url VARCHAR(512),
    medical_board_registration_url VARCHAR(512),
    passport_url VARCHAR(512),
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_doctor_session UNIQUE (doctor_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_sessions_training ON training_sessions(training_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_doctor ON enrollments(doctor_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_session ON enrollments(session_id);
