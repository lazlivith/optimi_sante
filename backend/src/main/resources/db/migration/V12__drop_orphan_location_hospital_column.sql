-- La colonne location_hospital est un résidu d'une ancienne version du schéma (CDC initial).
-- L'entité TrainingSession et la migration V7 utilisent la colonne "location".
-- location_hospital est NOT NULL sans défaut et n'est jamais alimentée par l'application,
-- ce qui bloquait toute création de session de formation. On la supprime.
ALTER TABLE training_sessions DROP COLUMN IF EXISTS location_hospital;
