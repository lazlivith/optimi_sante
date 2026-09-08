-- =====================================================================================
-- Socle de donnees de demonstration — sessions de formation et rattachement partenaire
-- =====================================================================================
-- A jouer UNE FOIS sur une base fraiche, AVANT docker/seed_recette.sql, qui suppose
-- l'existence d'au moins une session ouverte.
--
-- Usage :
--   docker exec -i optimisante-postgres psql -U postgres -d optimisante_db < docker/seed_demo_sessions.sql
--
-- Corrige deux trous du jeu de donnees de migration (V5 / V11), qui rendaient le parcours
-- formation intestable sur une base neuve :
--
--   1. V5 insere 3 formations mais AUCUNE session. Or une candidature se rattache a une
--      session, pas a une formation : sans session, aucun dossier ne peut exister.
--   2. Ces 3 formations appartiennent a « CHU Mock », dont le compte porte le role MEDECIN
--      et ne peut donc pas ouvrir l'espace partenaire. Le CHU de test (chu@optimi.com) ne
--      voyait par consequent aucun dossier, quel que soit leur statut.
--
-- Idempotent : rejouable sans effet de bord.
-- =====================================================================================

\set ON_ERROR_STOP on

BEGIN;

-- 1. Rattacher les formations de demo au compte partenaire reellement connectable.
UPDATE trainings
SET partner_id = (SELECT pp.id FROM partner_profiles pp
                  JOIN users u ON u.id = pp.user_id
                  WHERE u.email = 'chu@optimi.com')
WHERE partner_id IN (SELECT pp.id FROM partner_profiles pp
                     JOIN users u ON u.id = pp.user_id
                     WHERE u.email = 'mock_partner@optimisante.com');

-- 2. Une session ouverte par formation qui n'en a pas encore.
INSERT INTO training_sessions (id, training_id, start_date, end_date, capacity,
                               available_seats, status, created_at, location, price)
SELECT gen_random_uuid(), t.id,
       CURRENT_DATE + INTERVAL '45 days',
       CURRENT_DATE + INTERVAL '45 days' + (t.duration_days || ' days')::interval,
       12, 12, 'OPEN', now(), 'CHU de Bordeaux', t.price
FROM trainings t
WHERE NOT EXISTS (SELECT 1 FROM training_sessions s WHERE s.training_id = t.id);

COMMIT;

SELECT t.title, u.email AS partenaire, s.start_date, s.location, s.status
FROM training_sessions s
JOIN trainings t ON t.id = s.training_id
JOIN partner_profiles pp ON pp.id = t.partner_id
JOIN users u ON u.id = pp.user_id
ORDER BY t.title;
