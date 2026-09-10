-- V34 : fusion des fiches produit reellement en double
--
-- CE QUE L'ANALYSE A CHANGE
-- Le releve initial annoncait « 59 groupes de produits en double », detectes sur le libelle.
-- Le libelle n'est pas l'identite d'un produit : « Siege de douche rabattable SOLO » designe
-- QUATRE references distinctes (VB544W, VB544, 1820041020, 1820041010) a quatre prix
-- differents, et « BLOUSE VISITEUR » deux articles a 9,43 EUR et 74,66 EUR. Fusionner sur le
-- nom aurait supprime de vraies fiches et impose un prix arbitraire.
--
-- L'IDENTITE, C'EST LE SKU
-- Le champ `sku` porte en realite une LISTE de references separees par des tirets, saisie
-- tantot avec des espaces, tantot avec des retours a la ligne, tantot avec un zero de tete
-- (« 0411150010 » et « 411150010 » designent le meme article). Deux fiches sont la meme fiche
-- si et seulement si leurs listes de references, normalisees, sont identiques.
-- Sur ce critere : 34 groupes, et non 59.
--
-- POURQUOI SEULE UNE PARTIE EST FUSIONNEE ICI
-- Fusionner, c'est choisir quelle fiche reste en vente — donc quel prix le client paiera.
-- Or dans 22 des 34 groupes, les deux prix sont dans un rapport de **exactement 1,20** :
-- un passage d'import a enregistre du HT, l'autre du TTC. Choisir revient a modifier le prix
-- de vente de 20 %. **Une migration n'a pas a trancher cela** : ces groupes sont laisses
-- intacts et signales.
--
-- Cinq autres presentent un ecart de prix sans explication (117,25 contre 89,90 ; 78,80 contre
-- 70,27) : meme raison, laisses intacts.
--
-- Restent les groupes ou la fusion n'arbitre aucun prix, et c'est la condition ci-dessous :
-- apres avoir mis de cote le prix de remplissage de l'import, il ne subsiste qu'un seul prix
-- distinct dans le groupe.
--
-- 100,00 EUR EST LE PRIX DE REMPLISSAGE DE L'IMPORT
-- 141 produits du catalogue portent exactement 100,00 EUR, quand le prix suivant le plus
-- frequent n'apparait que 8 fois. Face a un vrai prix, cette valeur ne represente donc pas une
-- alternative commerciale mais une case non remplie : la fiche au vrai prix l'emporte.
-- (Les 141 fiches concernees restent en vente a ce prix : les corriger est un sujet de
-- qualite du catalogue, hors du champ de cette migration.)
--
-- ON MASQUE, ON NE SUPPRIME PAS
-- Les doublons sont marques `deleted_at` / `is_active = false`, jamais supprimes :
-- `order_items` reference `products` sans ON DELETE, et une commande est une piece comptable.
-- Par surcroit, tout groupe dont une fiche non retenue porte une commande est **exclu** de la
-- fusion : masquer une fiche commandee rendait la liste des commandes de l'administration
-- inaccessible (verifie : HTTP 400). Ce defaut est corrige par ailleurs dans `OrderService`,
-- mais l'exclusion reste — l'historique doit montrer l'article reellement commande.

-- ---------------------------------------------------------------------------
-- 1. Normalisation d'une liste de references
-- ---------------------------------------------------------------------------
-- IMMUTABLE pour pouvoir servir dans un index si le besoin s'en presente.
CREATE OR REPLACE FUNCTION sku_key(reference text) RETURNS text[] AS $$
    SELECT coalesce(array_agg(DISTINCT r ORDER BY r), ARRAY[]::text[])
    FROM (
        SELECT coalesce(nullif(ltrim(btrim(part), '0'), ''), '0') AS r
        FROM unnest(regexp_split_to_array(coalesce(reference, ''), '[-[:space:]]+')) AS part
        WHERE btrim(part) <> ''
    ) s;
$$ LANGUAGE sql IMMUTABLE;

-- ---------------------------------------------------------------------------
-- 2. Selection des groupes fusionnables
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS product_dedupe;
CREATE TABLE product_dedupe AS
WITH vivants AS (
    SELECT p.id, p.tenant_id, p.base_price, p.category_id, p.image_url,
           length(coalesce(p.description, '')) AS taille_desc,
           sku_key(p.sku) AS cle,
           (SELECT count(*) FROM order_items oi WHERE oi.product_id = p.id) AS nb_commandes
    FROM products p
    WHERE p.deleted_at IS NULL
),
-- Taille de chaque categorie : a qualite egale, on prefere la categorie la plus specifique.
-- Le fourre-tout de l'import concentre plusieurs centaines d'articles ; la categorie metier,
-- quelques dizaines. Aucun libelle n'est ecrit en dur : c'est la volumetrie qui departage.
volumes AS (
    SELECT category_id, count(*) AS n FROM products WHERE deleted_at IS NULL GROUP BY category_id
),
groupes AS (
    SELECT tenant_id, cle
    FROM vivants
    WHERE cardinality(cle) > 0
    GROUP BY tenant_id, cle
    HAVING count(*) > 1
       -- La condition centrale : une fois le prix de remplissage ecarte, il ne doit rester
       -- qu'un seul prix. Sinon la fusion trancherait entre deux prix de vente reels.
       AND count(DISTINCT base_price) FILTER (WHERE base_price <> 100.00) <= 1
       -- Aucune fiche du groupe ne doit porter de commande. Volontairement plus strict que
       -- necessaire : il suffirait d'exclure les groupes ou la fiche ECARTEE est commandee.
       -- Mais la fiche retenue depend d'un classement ; exclure le groupe entier des qu'une
       -- commande existe rend la regle independante de ce classement, donc verifiable d'un
       -- coup d'oeil. Sur les donnees actuelles, les deux formulations designent les memes
       -- deux groupes.
       AND sum(nb_commandes) = 0
),
classe AS (
    SELECT v.*,
           first_value(v.id) OVER (
               PARTITION BY v.tenant_id, v.cle
               ORDER BY (v.base_price <> 100.00) DESC,      -- un vrai prix avant le remplissage
                        v.taille_desc DESC,                  -- la fiche la plus documentee
                        (v.image_url IS NOT NULL) DESC,
                        coalesce(vo.n, 2147483647) ASC,      -- la categorie la plus specifique
                        v.id ASC                             -- deterministe
           ) AS id_retenu
    FROM vivants v
    LEFT JOIN volumes vo ON vo.category_id = v.category_id
    WHERE (v.tenant_id, v.cle) IN (SELECT tenant_id, cle FROM groupes)
)
SELECT id, id_retenu FROM classe WHERE id <> id_retenu;

-- ---------------------------------------------------------------------------
-- 3. Mise a l'ecart des doublons
-- ---------------------------------------------------------------------------
UPDATE products p
SET deleted_at = now(),
    is_active  = false
FROM product_dedupe d
WHERE p.id = d.id;

-- ---------------------------------------------------------------------------
-- 4. Garde-fous
-- ---------------------------------------------------------------------------
DO $$
DECLARE commandes integer; masques integer;
BEGIN
    -- Une fiche commandee n'a jamais du etre masquee. Si cela arrivait, la liste des commandes
    -- afficherait « Article retire du catalogue » a la place de l'article reel.
    SELECT count(*) INTO commandes
    FROM product_dedupe d
    WHERE EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = d.id);
    IF commandes > 0 THEN
        RAISE EXCEPTION 'V34 : % fiche(s) commandee(s) allaient etre masquees', commandes;
    END IF;

    -- Chaque fiche retenue doit rester vivante : sans cela, un groupe entier disparaitrait.
    SELECT count(*) INTO commandes
    FROM product_dedupe d
    JOIN products p ON p.id = d.id_retenu
    WHERE p.deleted_at IS NOT NULL;
    IF commandes > 0 THEN
        RAISE EXCEPTION 'V34 : % fiche(s) retenue(s) ont ete masquees par erreur', commandes;
    END IF;

    SELECT count(*) INTO masques FROM product_dedupe;
    RAISE NOTICE 'V34 : % fiche(s) en double masquee(s)', masques;
END $$;

DROP TABLE product_dedupe;
