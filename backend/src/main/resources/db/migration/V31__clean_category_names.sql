-- V31 : nettoyage des noms de catégories (entités HTML + doublons d'import)
--
-- CONSTAT
-- Le catalogue compte 273 catégories pour 1 519 produits, dont seulement 130 réellement
-- utilisées. Deux défauts, tous deux issus de l'import initial :
--
--   1. 95 noms portent l'entité HTML `&amp;` au lieu du caractère `&` — l'import a stocké
--      du HTML échappé sans le décoder. « Accessoires &amp; tabliers » s'affiche tel quel
--      dans l'interface.
--   2. Le même nom existe en 2 ou 3 exemplaires, distingués par un suffixe de slug (`-2`,
--      `-mobilite-transfert-2`), l'import ayant tourné plusieurs fois. Les produits se sont
--      répartis entre ces copies : « Cannes de marche » en compte 7 d'un côté, 2 de l'autre,
--      0 de la troisième. Aucun filtre par catégorie ne peut fonctionner sur cette base.
--
-- CE QUE FAIT CETTE MIGRATION
--   a. Décode `&amp;` et normalise les espaces dans `categories.name`.
--   b. Fusionne les catégories de nom identique : les produits sont rattachés à la copie
--      canonique, les copies vidées sont supprimées. 273 → 223 catégories.
--
-- CE QU'ELLE NE FAIT PAS, DÉLIBÉRÉMENT
--   • Elle ne touche pas aux 93 catégories qui resteront sans produit. Une catégorie vide
--     est peut-être un rayon à approvisionner : la supprimer relève d'une décision
--     commerciale, pas d'une migration technique.
--   • Elle ne fusionne que les noms STRICTEMENT identiques après décodage. Les quasi-doublons
--     (« Accessoires pré-opératoire » au singulier vs « Accessoires pré-opératoires » au
--     pluriel) sont laissés en place : les rapprocher demande un arbitrage humain, et une
--     migration qui devine se trompe silencieusement.
--   • Elle ne touche pas à `products.name` — vérifié, aucun produit ne porte d'entité HTML.

-- ---------------------------------------------------------------------------
-- a. Décodage des entités et normalisation des espaces
-- ---------------------------------------------------------------------------
-- `&amp;` est la seule entité présente (vérifié : SELECT substring(name from '&[a-zA-Z#0-9]+;')
-- ne remonte qu'elle). Les autres sont décodées par précaution, au cas où un import ultérieur
-- en introduirait avant que ce nettoyage ne soit rejoué sur une base neuve.
-- L'ordre importe : `&amp;` doit être traité EN DERNIER, sinon « &amp;lt; » deviendrait « < »
-- au lieu de « &lt; ».
UPDATE categories
SET name = btrim(regexp_replace(
        replace(replace(replace(replace(replace(replace(
            name,
            '&nbsp;', ' '),
            '&quot;', '"'),
            '&#39;',  ''''),
            '&lt;',   '<'),
            '&gt;',   '>'),
            '&amp;',  '&'),
        '\s+', ' ', 'g'))
WHERE name ~ '&[a-zA-Z#0-9]+;' OR name <> btrim(regexp_replace(name, '\s+', ' ', 'g'));

-- ---------------------------------------------------------------------------
-- b. Fusion des doublons
-- ---------------------------------------------------------------------------
-- Choix de la copie canonique, par ordre de préférence :
--   1. celle qui porte le plus de produits vivants — c'est la référence de fait ;
--   2. à égalité, le slug le plus court : l'import numérote les copies (`-2`), donc le slug
--      le plus court est l'original ;
--   3. à égalité encore, le plus petit `id`, pour que le résultat soit déterministe et que
--      rejouer la migration sur une autre base donne exactement le même schéma.
-- Table de travail ordinaire et non `TEMPORARY ... ON COMMIT DROP` : ce dernier suppose que
-- Flyway enveloppe la migration dans une transaction. C'est le cas aujourd'hui, mais si la
-- configuration changeait, la table serait détruite avant les UPDATE qui la lisent et la
-- fusion ne ferait rien — en silence. Ici, elle est supprimée explicitement à la fin.
DROP TABLE IF EXISTS category_merge_map;
CREATE TABLE category_merge_map AS
WITH ranked AS (
    SELECT c.id,
           c.tenant_id,
           c.name,
           first_value(c.id) OVER (
               PARTITION BY c.tenant_id, c.name
               ORDER BY (SELECT count(*) FROM products p
                          WHERE p.category_id = c.id AND p.deleted_at IS NULL) DESC,
                        length(c.slug) ASC,
                        c.id ASC
           ) AS canonical_id
    FROM categories c
)
SELECT id AS duplicate_id, canonical_id
FROM ranked
WHERE id <> canonical_id;

-- Rattachement des produits à la copie canonique.
-- Requête native volontaire : `products` porte @SQLRestriction côté Hibernate, mais une
-- migration SQL travaille sur la table réelle. Les produits supprimés (deleted_at) sont
-- inclus : leur catégorie doit rester valide, la ligne cible allant disparaître.
UPDATE products p
SET category_id = m.canonical_id
FROM category_merge_map m
WHERE p.category_id = m.duplicate_id;

-- `categories.parent_id` référence `categories(id)` en ON DELETE SET NULL. Aujourd'hui aucune
-- catégorie n'a de parent (les 273 sont racines), mais si une hiérarchie apparaissait avant
-- que cette migration ne tourne sur une base neuve, laisser faire le SET NULL détacherait
-- silencieusement une branche entière. On repointe donc explicitement.
UPDATE categories c
SET parent_id = m.canonical_id
FROM category_merge_map m
WHERE c.parent_id = m.duplicate_id;

DELETE FROM categories c
USING category_merge_map m
WHERE c.id = m.duplicate_id;

DROP TABLE category_merge_map;

-- ---------------------------------------------------------------------------
-- Garde-fous : la migration échoue plutôt que de laisser la base incohérente
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    orphelins   integer;
    entites     integer;
    doublons    integer;
BEGIN
    -- Aucun produit ne doit pointer vers une catégorie disparue. Le FK est en
    -- ON DELETE SET NULL : sans cette vérification, une erreur de fusion se traduirait par
    -- des produits silencieusement décatégorisés, exactement le contraire du but recherché.
    SELECT count(*) INTO orphelins
    FROM products p
    WHERE p.category_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = p.category_id);
    IF orphelins > 0 THEN
        RAISE EXCEPTION 'V31 : % produit(s) ont perdu leur categorie pendant la fusion', orphelins;
    END IF;

    SELECT count(*) INTO entites FROM categories WHERE name ~ '&[a-zA-Z#0-9]+;';
    IF entites > 0 THEN
        RAISE EXCEPTION 'V31 : % categorie(s) portent encore une entite HTML', entites;
    END IF;

    SELECT count(*) INTO doublons FROM (
        SELECT 1 FROM categories GROUP BY tenant_id, name HAVING count(*) > 1
    ) d;
    IF doublons > 0 THEN
        RAISE EXCEPTION 'V31 : % nom(s) de categorie encore en double', doublons;
    END IF;
END $$;

-- Index de recherche : la liste admin filtre sur le nom et le SKU, sur 1 519 lignes appelées
-- à croître. `text_pattern_ops` sert les préfixes ; la recherche « contient » reste un scan,
-- acceptable à cette volumétrie et documenté comme tel plutôt que masqué par un index inutile.
CREATE INDEX IF NOT EXISTS idx_products_name_lower ON products (lower(name) text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_products_sku_lower  ON products (lower(sku) text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_products_category   ON products (category_id) WHERE deleted_at IS NULL;
