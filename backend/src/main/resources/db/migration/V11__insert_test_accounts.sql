-- Migration V11 : Insertion des comptes de test par défaut
-- Mot de passe commun pour tous les comptes : password123

-- 1. Récupération du Tenant 'FR_MAIN'
DO $$ 
DECLARE
    v_tenant_id UUID;
    v_super_admin_id UUID := gen_random_uuid();
    v_chu_id UUID := gen_random_uuid();
    v_medecin_id UUID := gen_random_uuid();
    v_b2c_id UUID := gen_random_uuid();
    v_b2b_id UUID := gen_random_uuid();
BEGIN
    SELECT id INTO v_tenant_id FROM tenants WHERE code = 'FR_MAIN' LIMIT 1;

    -- Si le tenant n'existe pas, on arrête
    IF v_tenant_id IS NULL THEN
        RAISE EXCEPTION 'Tenant FR_MAIN non trouvé';
    END IF;

    -- 2. Insertion des Users (password123)
    INSERT INTO users (id, tenant_id, email, password_hash, role, is_active)
    VALUES 
        (v_super_admin_id, v_tenant_id, 'admin@optimi.com', '$2a$10$wKfTpHTHuWtlIYieNOI8hefs3Ql16p0sP0XQc4M472Drpk2Rtjzbe', 'SUPER_ADMIN', TRUE),
        (v_chu_id, v_tenant_id, 'chu@optimi.com', '$2a$10$wKfTpHTHuWtlIYieNOI8hefs3Ql16p0sP0XQc4M472Drpk2Rtjzbe', 'CENTRE_FORMATION', TRUE),
        (v_medecin_id, v_tenant_id, 'medecin@optimi.com', '$2a$10$wKfTpHTHuWtlIYieNOI8hefs3Ql16p0sP0XQc4M472Drpk2Rtjzbe', 'MEDECIN', TRUE),
        (v_b2c_id, v_tenant_id, 'b2c@optimi.com', '$2a$10$wKfTpHTHuWtlIYieNOI8hefs3Ql16p0sP0XQc4M472Drpk2Rtjzbe', 'CLIENT_B2C', TRUE),
        (v_b2b_id, v_tenant_id, 'b2b@optimi.com', '$2a$10$wKfTpHTHuWtlIYieNOI8hefs3Ql16p0sP0XQc4M472Drpk2Rtjzbe', 'CLIENT_B2B', TRUE)
    ON CONFLICT (email) DO NOTHING;

    -- 3. Insertion des Profils Associés (si le user vient d'être inséré)
    -- Profil Partner (CHU)
    INSERT INTO partner_profiles (user_id, institution_name, finess_accreditation, contact_person_name, contact_email, contact_phone, address, is_verified)
    SELECT id, 'CHU Test Partenaire', 'FINESS12345', 'Jean Partenaire', 'chu@optimi.com', '+33123456789', '123 Rue de l''Hôpital, Paris', TRUE
    FROM users WHERE email = 'chu@optimi.com'
    ON CONFLICT (user_id) DO NOTHING;

    -- Profil Médecin
    INSERT INTO doctor_profiles (user_id, first_name, last_name, phone_whatsapp, country_of_residence, medical_specialty, medical_council_number, current_hospital, passport_number)
    SELECT id, 'Marc', 'Médecin', '+33612345678', 'FR', 'Cardiologie', 'ORDRE123', 'Hôpital Central', 'PASS123'
    FROM users WHERE email = 'medecin@optimi.com'
    ON CONFLICT (user_id) DO NOTHING;

    -- Profil B2B
    INSERT INTO company_profiles (user_id, company_name, siret_finess, vat_number, billing_address, b2b_discount_rate)
    SELECT id, 'Clinique B2B Test', 'SIRET987654321', 'FR12987654321', '45 Avenue Pro, Lyon', 5.00
    FROM users WHERE email = 'b2b@optimi.com'
    ON CONFLICT (user_id) DO NOTHING;

END $$;
