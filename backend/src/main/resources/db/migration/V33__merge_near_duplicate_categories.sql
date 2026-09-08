-- V33 : fusion des quasi-doublons de categories + garde-fou d'unicite
--
-- POURQUOI V31 NE SUFFISAIT PAS
-- V31 n'a fusionne que les noms STRICTEMENT identiques. Il restait 12 groupes que l'oeil
-- reconnait immediatement comme un seul rayon, mais qu'aucune egalite de chaine ne rapproche :
--
--   « Compresses chaudes et froides »  /  « Compresses chaudes & froides »
--   « Gants d'examen et de chirurgie » /  « Gants d'examen & de chirurgie »
--   « Autour du lit & Canape »         /  « Autour du lit &canape »        (espace manquante)
--   « Equipements divers » accentue    /  « Equipements divers » sans accent
--   « Relaxation et Massage »          /  « Relaxation et massage »        (casse)
--   « Accessoires pre-operatoire »     /  « Accessoires pre-operatoires »  (pluriel)
--   « Echelle de lecture »             /  « Echelles de lecture »          (pluriel)
--   ... et deux libelles visuellement identiques, qui ne different que par la forme de
--   normalisation Unicode de leur accent (a-circonflexe precompose contre a + accent combinant).
--
-- LA CONDITION DE RAPPROCHEMENT
-- Une fonction de normalisation, `category_norm`, reduit un libelle a une cle comparable :
--   1. minuscules ;
--   2. decomposition Unicode puis suppression des accents — c'est ce qui rapproche les deux
--      formes d'un meme a-circonflexe, invisibles a l'oeil ;
--   3. « & » devient « et » ;
--   4. toute ponctuation devient une espace, les espaces sont compactees ;
--   5. le « s » final des mots de plus de trois lettres est retire (pluriel naif).
--
-- L'etape 5 est la seule qui puisse se tromper : elle rapprocherait a tort deux rayons dont
-- les noms ne different que par un pluriel porteur de sens. **Les 12 groupes produits par
-- cette regle sur les donnees actuelles ont ete lus un par un** ; les 3 que seul le pluriel
-- rapproche sont « Accessoires pre-operatoire(s) », « Echelle(s) de lecture » et
-- « Produit(s) de ventilation de laboratoire » — trois fusions correctes.
--
-- CE QUI EMPECHE LE PROBLEME DE REVENIR
-- Fusionner ne suffit pas : sans contrainte, un prochain import recreerait les memes
-- doublons. Un index unique sur la cle normalisee est donc pose en fin de migration. C'est la
-- base, et non le code applicatif, qui refuse desormais le doublon — aucun chemin d'ecriture
-- ne peut le contourner.

-- ---------------------------------------------------------------------------
-- 1. La fonction de normalisation
-- ---------------------------------------------------------------------------
-- IMMUTABLE est indispensable : un index d'expression n'accepte que des fonctions dont le
-- resultat ne depend que des arguments. `unaccent()` ne l'est pas (elle depend d'un
-- dictionnaire installe), d'ou la table de correspondance explicite via `translate`.
CREATE OR REPLACE FUNCTION category_norm(libelle text) RETURNS text AS $$
    SELECT btrim(regexp_replace(
        -- 5. pluriel naif : « accessoires » et « accessoire » donnent la meme cle,
        --    mais « et » ou « bas » (3 lettres ou moins) restent intacts.
        regexp_replace(
            -- 4. ponctuation -> espace, puis compactage
            regexp_replace(
                -- 3. l'esperluette vaut « et »
                replace(
                    -- 2. accents supprimes, quelle que soit la forme Unicode d'origine
                    translate(
                        -- 1. minuscules ; NFD separe l'accent de sa lettre pour que les deux
                        --    formes du meme caractere se reduisent pareillement
                        normalize(lower(libelle), NFD),
                        'àáâãäåçèéêëìíîïñòóôõöùúûüýÿœæ' ||
                        E'̧̀́̂̃̄̈̊',
                        'aaaaaaceeeeiiiinooooouuuuyyoa'),
                    '&', ' et '),
                '[^a-z0-9]+', ' ', 'g'),
            '([a-z0-9]{3,})s\y', '\1', 'g'),
        '\s+', ' ', 'g'));
$$ LANGUAGE sql IMMUTABLE;

-- ---------------------------------------------------------------------------
-- 2. Choix de la ligne survivante et du libelle conserve
-- ---------------------------------------------------------------------------
-- Deux decisions distinctes, volontairement separees :
--
--   • La LIGNE qui survit est celle qui porte le plus de produits. C'est elle qui limite le
--     nombre de rattachements a reecrire, et c'est la reference de fait.
--   • Le LIBELLE affiche est celui de la variante la mieux orthographiee : d'abord celle qui
--     porte le plus de caracteres accentues, puis la plus longue. Sans cette separation,
--     « Equipements divers » (8 produits, sans accent) l'emporterait sur
--     « Équipements divers » (0 produit, correctement accentue) et la plateforme conserverait
--     la moins bonne des deux graphies.
DROP TABLE IF EXISTS category_dedupe;
CREATE TABLE category_dedupe AS
WITH scored AS (
    SELECT c.id,
           c.tenant_id,
           c.name,
           category_norm(c.name) AS cle,
           (SELECT count(*) FROM products p
             WHERE p.category_id = c.id AND p.deleted_at IS NULL) AS nb_produits,
           length(regexp_replace(c.name, '[ -~]', '', 'g')) AS nb_accents
    FROM categories c
),
choix AS (
    SELECT s.*,
           first_value(s.id) OVER (
               PARTITION BY s.tenant_id, s.cle
               ORDER BY s.nb_produits DESC, length(s.name) ASC, s.id ASC
           ) AS id_survivant,
           first_value(s.name) OVER (
               PARTITION BY s.tenant_id, s.cle
               ORDER BY s.nb_accents DESC, length(s.name) DESC, s.id ASC
           ) AS libelle_retenu
    FROM scored s
)
SELECT id, id_survivant, libelle_retenu FROM choix;

-- ---------------------------------------------------------------------------
-- 3. Fusion
-- ---------------------------------------------------------------------------
UPDATE products p
SET category_id = d.id_survivant
FROM category_dedupe d
WHERE p.category_id = d.id AND d.id <> d.id_survivant;

-- `categories.parent_id` est en ON DELETE SET NULL : sans ce repointage explicite, une
-- eventuelle sous-categorie d'une ligne absorbee serait silencieusement detachee.
UPDATE categories c
SET parent_id = d.id_survivant
FROM category_dedupe d
WHERE c.parent_id = d.id AND d.id <> d.id_survivant;

DELETE FROM categories c
USING category_dedupe d
WHERE c.id = d.id AND d.id <> d.id_survivant;

-- Le libelle le mieux orthographie est reporte sur la ligne survivante.
UPDATE categories c
SET name = d.libelle_retenu
FROM category_dedupe d
WHERE c.id = d.id_survivant AND c.name <> d.libelle_retenu;

DROP TABLE category_dedupe;

-- ---------------------------------------------------------------------------
-- 4. Garde-fous
-- ---------------------------------------------------------------------------
DO $$
DECLARE orphelins integer; doublons integer;
BEGIN
    SELECT count(*) INTO orphelins
    FROM products p
    WHERE p.category_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = p.category_id);
    IF orphelins > 0 THEN
        RAISE EXCEPTION 'V33 : % produit(s) ont perdu leur categorie pendant la fusion', orphelins;
    END IF;

    SELECT count(*) INTO doublons FROM (
        SELECT 1 FROM categories GROUP BY tenant_id, category_norm(name) HAVING count(*) > 1
    ) d;
    IF doublons > 0 THEN
        RAISE EXCEPTION 'V33 : % groupe(s) de doublons subsistent', doublons;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 5. La condition qui empeche le retour du probleme
-- ---------------------------------------------------------------------------
-- Desormais la base refuse elle-meme un doublon, quel que soit le chemin d'ecriture : import,
-- script ou interface. Sans cet index, la fusion ci-dessus serait a refaire au prochain import.
CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_tenant_norm
    ON categories (tenant_id, category_norm(name));

COMMENT ON INDEX uq_categories_tenant_norm IS
    'Empeche deux categories dont les libelles ne different que par la casse, les accents, '
    'la ponctuation, l''esperluette ou un pluriel. Voir la fonction category_norm (V33).';
