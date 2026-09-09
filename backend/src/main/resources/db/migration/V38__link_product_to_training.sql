-- V38 : offre liee — un equipement, la formation qui apprend a s'en servir
--
-- LE BESOIN
-- « L'achat d'un equipement ou d'une machine medicale sera relie a une formation pour
-- apprendre a utiliser la machine achetee. » Une formation ne concerne qu'un produit, et un
-- produit ne renvoie qu'a une formation : la relation est **un a un**.
--
-- POURQUOI LA CLE EST PORTEE PAR `products` ET NON PAR `trainings`
-- Le choix n'est pas symetrique, parce que les deux tables n'ont pas le meme auteur :
--
--   - Une **formation est creee et modifiee par le CHU partenaire** (`CENTRE_FORMATION`).
--     L'administration de la mobilite ne fait que l'approuver ou la refuser — il n'existe
--     aucun endpoint d'edition de formation cote administration.
--   - Un **produit est edite par l'administration du negoce**, via un formulaire qui existe
--     deja.
--
-- Or rattacher une formation a un equipement est une **decision commerciale d'OptimiSanté**,
-- pas une decision du CHU : un partenaire ne doit pas pouvoir designer lui-meme le produit
-- que sa formation accompagne. Porter la cle cote `trainings` obligerait donc soit a ouvrir
-- ce choix au partenaire, soit a construire tout un chemin d'edition de formation cote
-- administration. Cote `products`, le formulaire et l'endpoint existent deja.
--
-- L'UNICITE FAIT LE 1:1
-- La colonne seule ne dirait que « un produit renvoie a au plus une formation ». L'index
-- unique ajoute la reciproque : une formation ne peut etre rattachee qu'a un seul produit.
-- PostgreSQL admet plusieurs NULL dans un index unique, ce qui laisse les 1 484 produits sans
-- formation coexister sans contrainte.
--
-- ON DELETE SET NULL : si une formation disparait, le produit reste en vente, simplement sans
-- offre liee. L'inverse — supprimer le produit avec la formation — serait absurde.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS training_id uuid REFERENCES trainings(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_products_training
    ON products (training_id)
    WHERE training_id IS NOT NULL;

COMMENT ON COLUMN products.training_id IS
    'Formation qui apprend a utiliser cet equipement. Relation 1:1 garantie par '
    'uq_products_training. Renseignee par l''administration du negoce, jamais par le '
    'partenaire qui redige la formation.';

-- ---------------------------------------------------------------------------
-- Garde-fou
-- ---------------------------------------------------------------------------
DO $$
DECLARE doublons integer;
BEGIN
    SELECT count(*) INTO doublons FROM (
        SELECT 1 FROM products WHERE training_id IS NOT NULL
        GROUP BY training_id HAVING count(*) > 1
    ) d;
    IF doublons > 0 THEN
        RAISE EXCEPTION 'V38 : % formation(s) rattachees a plusieurs produits', doublons;
    END IF;
END $$;
