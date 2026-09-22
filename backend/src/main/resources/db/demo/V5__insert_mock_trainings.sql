DO $$
DECLARE
    v_tenant_id uuid;
    v_user_id uuid;
    v_partner_id uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM tenants LIMIT 1;
    
    IF v_tenant_id IS NOT NULL THEN
        -- Insert mock partner user if doesn't exist
        IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'mock_partner@optimisante.com') THEN
            INSERT INTO users (id, email, password_hash, role, is_active, tenant_id)
            VALUES (gen_random_uuid(), 'mock_partner@optimisante.com', 'pwd', 'MEDECIN', true, v_tenant_id)
            RETURNING id INTO v_user_id;

            INSERT INTO partner_profiles (id, user_id, institution_name, finess_accreditation, contact_person_name, contact_email, contact_phone, address, is_verified)
            VALUES (gen_random_uuid(), v_user_id, 'CHU Mock', '12345', 'Mock Contact', 'mock@optimisante.com', '123456', 'Address', true)
            RETURNING id INTO v_partner_id;
        ELSE
            SELECT id INTO v_user_id FROM users WHERE email = 'mock_partner@optimisante.com';
            SELECT id INTO v_partner_id FROM partner_profiles WHERE user_id = v_user_id;
        END IF;

        -- Insert mock trainings
        INSERT INTO trainings (id, tenant_id, partner_id, title, slug, medical_specialty, description, brochure_s3_key, duration_days, is_long_stay, price, is_published)
        VALUES ('a1b2c3d4-e5f6-7890-1234-56789abcdef1', v_tenant_id, v_partner_id, 'Échographie clinique appliquée', 'echographie-clinique', 'RADIOLOGIE', 'Desc', 'mock-key', 10, false, 0, true)
        ON CONFLICT DO NOTHING;

        INSERT INTO trainings (id, tenant_id, partner_id, title, slug, medical_specialty, description, brochure_s3_key, duration_days, is_long_stay, price, is_published)
        VALUES ('b2c3d4e5-f6a7-8901-2345-6789abcdef12', v_tenant_id, v_partner_id, 'Chirurgie mini-invasive — stage pratique', 'chirurgie-mini-invasive', 'CHIRURGIE', 'Desc', 'mock-key', 90, true, 0, true)
        ON CONFLICT DO NOTHING;

        INSERT INTO trainings (id, tenant_id, partner_id, title, slug, medical_specialty, description, brochure_s3_key, duration_days, is_long_stay, price, is_published)
        VALUES ('c3d4e5f6-a7b8-9012-3456-789abcdef123', v_tenant_id, v_partner_id, 'Réanimation néonatale', 'reanimation-neonatale', 'PÉDIATRIE', 'Desc', 'mock-key', 15, false, 0, true)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;
