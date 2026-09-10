-- =====================================================================================
-- V30 — Scission de l'administration en deux métiers
-- =====================================================================================
-- L'administration couvre deux domaines sans rapport l'un avec l'autre : le négoce médical
-- (catalogue, commandes, devis, promotions) et la mobilité (formations, candidatures,
-- partenaires, reversements). Deux personnes distinctes les opèrent.
--
-- La séparation est portée par les RÔLES et non par la navigation : un administrateur du
-- négoce ne doit pas pouvoir consulter un dossier médical via l'API, même en connaissant
-- l'URL. Une restriction purement visuelle ne garantirait rien.
--
-- ⚠️ `ADMIN` est CONSERVÉ et reste accepté par les deux univers. Deux raisons :
--   1. le compte administrateur existant ne doit pas perdre son accès au déploiement ;
--   2. réaffecter un administrateur à un métier est une décision d'organisation, pas un
--      effet de bord de migration — aucun compte n'est donc migré automatiquement ici.
-- Son retrait fera l'objet d'une migration dédiée, une fois la bascule constatée.
-- =====================================================================================

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN (
    -- Gouvernance : accès aux deux univers + gestion des comptes
    'SUPER_ADMIN',
    -- Administration métier
    'ADMIN_ECOMMERCE',
    'ADMIN_MOBILITE',
    -- Hérité : accepté par les deux univers le temps de la bascule
    'ADMIN',
    -- Utilisateurs
    'CLIENT_B2C', 'CLIENT_B2B', 'MEDECIN', 'CENTRE_FORMATION'
));

-- Le filtrage par rôle devient la requête la plus fréquente de l'écran de gestion des
-- comptes, qui doit désormais distinguer trois profils d'administrateur.
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role) WHERE deleted_at IS NULL;
