-- =====================================================================================
-- Jeu de données de recette — cycle de candidature tripartite
-- =====================================================================================
-- Recrée un dossier à CHAQUE étape du cycle, pour que la recette Postman soit rejouable
-- autant de fois que nécessaire sans dépendre de l'état laissé par la précédente.
--
-- Usage :
--   docker exec -i optimisante-postgres psql -U postgres -d optimisante_db < docker/seed_recette.sql
--
-- ⚠️ N'EFFACE QUE les données de recette (préfixe `recette-`). Les comptes et dossiers
-- existants ne sont jamais touchés — ce script est sûr sur une base de travail.
--
-- Les statuts sont posés directement en base : c'est volontaire. L'objectif est de
-- FOURNIR des points de départ, pas de tester les transitions — ce sont les appels API
-- de la recette qui les exercent, avec toutes leurs gardes.
-- =====================================================================================

\set ON_ERROR_STOP on

BEGIN;

-- ---------------------------------------------------------------------------------
-- 1. Nettoyage des données de recette précédentes (et d'elles seules)
-- ---------------------------------------------------------------------------------
DELETE FROM enrollment_payments WHERE enrollment_id IN (
    SELECT e.id FROM enrollments e
    JOIN users u ON u.id = e.doctor_id
    WHERE u.email LIKE 'recette-%@optimisante.test');

DELETE FROM enrollment_documents WHERE enrollment_id IN (
    SELECT e.id FROM enrollments e
    JOIN users u ON u.id = e.doctor_id
    WHERE u.email LIKE 'recette-%@optimisante.test');

DELETE FROM enrollments WHERE doctor_id IN (
    SELECT id FROM users WHERE email LIKE 'recette-%@optimisante.test');

DELETE FROM email_logs WHERE recipient LIKE 'recette-%@optimisante.test';
DELETE FROM doctor_profiles WHERE user_id IN (
    SELECT id FROM users WHERE email LIKE 'recette-%@optimisante.test');
DELETE FROM users WHERE email LIKE 'recette-%@optimisante.test';

-- ---------------------------------------------------------------------------------
-- 2. Contexte : une session ouverte, correctement tarifée
-- ---------------------------------------------------------------------------------
-- Un tarif inférieur à 0,50 € est refusé par Stripe (amount_too_small) : on garantit
-- un montant réaliste, sinon l'ouverture du paiement échoue pour une raison sans
-- rapport avec ce qu'on veut tester.
UPDATE training_sessions SET price = 3200.00 WHERE price IS NULL OR price < 1;
UPDATE trainings         SET price = 3200.00 WHERE price IS NULL OR price < 1;

UPDATE training_sessions SET status = 'OPEN', available_seats = GREATEST(available_seats, 10)
WHERE id = (SELECT id FROM training_sessions ORDER BY start_date LIMIT 1);

-- ---------------------------------------------------------------------------------
-- 3. Cinq médecins de recette, un par étape du cycle
--    Mot de passe : le même hash que les comptes de test existants (password123).
-- ---------------------------------------------------------------------------------
INSERT INTO users (id, tenant_id, email, password_hash, role, is_active, first_name, last_name)
SELECT gen_random_uuid(),
       (SELECT id FROM tenants WHERE code = 'FR_MAIN'),
       'recette-' || step || '@optimisante.test',
       (SELECT password_hash FROM users WHERE email = 'admin@optimi.com'),
       'MEDECIN', TRUE, prenom, 'Recette'
FROM (VALUES
    ('revue',      'Amina'),
    ('correction', 'Kofi'),
    ('transmis',   'Fatou'),
    ('paiement',   'Ibrahim'),
    ('confirme',   'Leïla')
) AS t(step, prenom);

INSERT INTO doctor_profiles (id, user_id, first_name, last_name, phone_whatsapp,
                             country_of_residence, medical_specialty, passport_number)
SELECT gen_random_uuid(), u.id, u.first_name, u.last_name, '+221 77 000 00 00',
       'SN', 'Cardiologie', 'REC' || substr(u.id::text, 1, 6)
FROM users u WHERE u.email LIKE 'recette-%@optimisante.test';

-- ---------------------------------------------------------------------------------
-- 4. Un dossier par étape
-- ---------------------------------------------------------------------------------
INSERT INTO enrollments (id, doctor_id, session_id, status, submitted_at, action_required_note)
SELECT gen_random_uuid(), u.id,
       (SELECT id FROM training_sessions WHERE status = 'OPEN' ORDER BY start_date LIMIT 1),
       CASE split_part(u.email, '-', 2)
           WHEN 'revue@optimisante.test'      THEN 'UNDER_OPTIMI_REVIEW'
           WHEN 'correction@optimisante.test' THEN 'ACTION_REQUIRED'
           WHEN 'transmis@optimisante.test'   THEN 'SUBMITTED_TO_PARTNER'
           WHEN 'paiement@optimisante.test'   THEN 'PENDING_TUITION_FEE'
           ELSE 'CONFIRMED'
       END,
       CURRENT_TIMESTAMP,
       CASE WHEN u.email LIKE 'recette-correction%'
            THEN 'Diplôme illisible : merci de redéposer un scan couleur.' END
FROM users u WHERE u.email LIKE 'recette-%@optimisante.test';

-- Le dossier en attente de paiement a sa ligne de règlement ouverte, comme après une
-- acceptation réelle par le partenaire.
INSERT INTO enrollment_payments (enrollment_id, payment_type, gross_amount,
    commission_rate, commission_amount, partner_payout_amount, status)
SELECT e.id, 'TUITION_FEE', 3200.00, 15.00, 480.00, 2720.00, 'PENDING'
FROM enrollments e
JOIN users u ON u.id = e.doctor_id
WHERE u.email = 'recette-paiement@optimisante.test';

COMMIT;

-- ---------------------------------------------------------------------------------
-- 5. Récapitulatif
-- ---------------------------------------------------------------------------------
SELECT u.email AS compte, e.status AS statut, e.id AS dossier
FROM enrollments e
JOIN users u ON u.id = e.doctor_id
WHERE u.email LIKE 'recette-%@optimisante.test'
ORDER BY
    CASE e.status
        WHEN 'UNDER_OPTIMI_REVIEW'  THEN 1
        WHEN 'ACTION_REQUIRED'      THEN 2
        WHEN 'SUBMITTED_TO_PARTNER' THEN 3
        WHEN 'PENDING_TUITION_FEE'  THEN 4
        ELSE 5
    END;
