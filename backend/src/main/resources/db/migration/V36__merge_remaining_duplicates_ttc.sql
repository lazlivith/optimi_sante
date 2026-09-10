-- V36 : fusion des doublons restants — le prix TTC fait foi
--
-- CE QUI ETAIT EN SUSPENS
-- V34 n'avait fusionne que les groupes ou la fusion n'arbitrait aucun prix (5 groupes). Il
-- restait 29 groupes de fiches partageant la meme liste de references :
--   - 22 dont les deux prix sont dans un rapport de **exactement 1,20** — un passage d'import
--     a enregistre du HT, l'autre du TTC ;
--   - 5 dont l'ecart de prix n'est pas un taux de TVA (rapports 1,073 / 1,121 / 1,164 /
--     1,182 / 1,304) ;
--   - 2 dont une fiche porte une commande, qui restent intouchables.
--
-- LA DECISION
-- **Le prix TTC fait foi** (arbitrage metier). Pour les 22 groupes concernes, la fiche
-- conservee porte donc le prix le plus eleve des deux.
--
-- QUELLE FICHE SURVIT : LA CATEGORIE DESIGNE LA PASSE D'IMPORT DE REFERENCE
-- Les doublons viennent de deux passes d'import. L'une range les articles dans leur categorie
-- metier (« Armoires a pharmacie », « Mallettes & sacs a dos », « Oxymetres de pouls »),
-- l'autre les verse tous dans le fourre-tout. **Mesure faite sur les 22 groupes a TVA : dans
-- 20 d'entre eux, le prix TTC est porte par la fiche rangee dans une categorie metier.**
-- C'est donc la specificite de la categorie — et non la longueur de la description — qui
-- identifie la passe de reference. Aucun libelle n'est ecrit en dur : c'est la volumetrie de
-- la categorie qui la departage, le fourre-tout comptant plusieurs centaines d'articles
-- contre quelques dizaines pour une categorie metier.
--
-- Le prix, lui, ne depend pas de ce classement : dans les 2 groupes ou la passe de reference
-- portait le HT, le prix TTC est **reporte** sur la fiche conservee. Classement et prix sont
-- deux decisions distinctes, comme dans V33.
--
-- CE QUI EST RECUPERE PLUTOT QUE PERDU
-- La fiche conservee recupere la description la plus fournie du groupe, et une image si elle
-- n'en a pas. Il ne s'agit pas d'un arbitrage : les fiches d'un meme groupe decrivent le meme
-- article, et le texte le plus complet est la meilleure fiche catalogue. Sans cela,
-- « Accessoires pour contec » (categorie metier, description vide) perdrait les 305 caracteres
-- portes par son doublon, et « Adaptateur secteur Veroval » garderait 5 caracteres au lieu
-- de 86. Le prix, lui, n'est jamais melange : il vient de la regle TTC ci-dessus.
--
-- LES 5 GROUPES SANS EXPLICATION DE PRIX — A VERIFIER
-- Leur ecart n'est pas un taux de TVA. La fiche de la passe de reference est conservee avec
-- son propre prix, par coherence avec les 22 autres, mais **ces cinq tarifs meritent d'etre
-- confrontes au tarif fournisseur** :
--
--     TENSIOMETRE BRAS OMRON M7 IT V2      conserve 117,25   (l'autre fiche : 89,90)
--     SAC D'INTERVENTION A BANDOULIERE     conserve 181,88   (l'autre fiche : 156,28)
--     SAC A DOS D'INTERVENTION LEGER       conserve 168,24   (l'autre fiche : 156,78)
--     CAPTEURS POUR NELLCOR                conserve 117,22   (l'autre fiche : 99,14)
--     ARMOIRE 1 PORTE METAL                conserve  78,80   (l'autre fiche : 70,27)
--
-- COMME EN V34 : ON MASQUE, ON NE SUPPRIME PAS, ET LES FICHES COMMANDEES SONT EXCLUES.

DROP TABLE IF EXISTS product_dedupe_ttc;
CREATE TABLE product_dedupe_ttc AS
WITH vivants AS (
    SELECT p.id, p.tenant_id, p.base_price, p.category_id, p.image_url, p.description,
           length(coalesce(p.description, '')) AS taille_desc,
           sku_key(p.sku) AS cle,
           (SELECT count(*) FROM order_items oi WHERE oi.product_id = p.id) AS nb_commandes
    FROM products p
    WHERE p.deleted_at IS NULL
),
volumes AS (
    SELECT category_id, count(*) AS n FROM products WHERE deleted_at IS NULL GROUP BY category_id
),
groupes AS (
    SELECT tenant_id, cle,
           max(base_price) AS prix_haut,
           min(base_price) AS prix_bas
    FROM vivants
    WHERE cardinality(cle) > 0
    GROUP BY tenant_id, cle
    HAVING count(*) > 1
       AND sum(nb_commandes) = 0
),
classe AS (
    SELECT v.*, g.prix_haut, g.prix_bas,
           first_value(v.id) OVER (
               PARTITION BY v.tenant_id, v.cle
               ORDER BY coalesce(vo.n, 2147483647) ASC,   -- categorie la plus specifique
                        v.taille_desc DESC,
                        (v.image_url IS NOT NULL) DESC,
                        v.id ASC
           ) AS id_retenu,
           -- Meilleurs contenus disponibles dans le groupe, pour combler un manque.
           first_value(v.description) OVER (
               PARTITION BY v.tenant_id, v.cle ORDER BY v.taille_desc DESC, v.id ASC
           ) AS meilleure_description,
           first_value(v.image_url) OVER (
               PARTITION BY v.tenant_id, v.cle
               ORDER BY (v.image_url IS NOT NULL) DESC, v.id ASC
           ) AS meilleure_image
    FROM vivants v
    JOIN groupes g ON g.tenant_id = v.tenant_id AND g.cle = v.cle
    LEFT JOIN volumes vo ON vo.category_id = v.category_id
)
SELECT id, id_retenu, prix_haut, prix_bas, meilleure_description, meilleure_image
FROM classe;

-- ---------------------------------------------------------------------------
-- 1. Le prix TTC est reporte sur la fiche conservee
-- ---------------------------------------------------------------------------
-- Uniquement lorsque l'ecart est exactement un taux de TVA a 20 %. Ailleurs, la fiche garde
-- son propre prix : rien ne permet d'affirmer que l'ecart soit de la TVA, et forcer le plus
-- eleve reviendrait a inventer une regle tarifaire.
UPDATE products p
SET base_price = d.prix_haut
FROM (SELECT DISTINCT id_retenu, prix_haut, prix_bas FROM product_dedupe_ttc) d
WHERE p.id = d.id_retenu
  AND d.prix_bas > 0
  AND abs(d.prix_haut / d.prix_bas - 1.20) < 0.002
  AND p.base_price <> d.prix_haut;

-- ---------------------------------------------------------------------------
-- 2. Recuperation des contenus manquants
-- ---------------------------------------------------------------------------
UPDATE products p
SET description = d.meilleure_description
FROM (SELECT DISTINCT id_retenu, meilleure_description FROM product_dedupe_ttc) d
WHERE p.id = d.id_retenu
  AND length(coalesce(d.meilleure_description, '')) > length(coalesce(p.description, ''));

UPDATE products p
SET image_url = d.meilleure_image
FROM (SELECT DISTINCT id_retenu, meilleure_image FROM product_dedupe_ttc) d
WHERE p.id = d.id_retenu AND p.image_url IS NULL AND d.meilleure_image IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. Mise a l'ecart des doublons
-- ---------------------------------------------------------------------------
UPDATE products p
SET deleted_at = now(), is_active = false
FROM product_dedupe_ttc d
WHERE p.id = d.id AND d.id <> d.id_retenu;

-- ---------------------------------------------------------------------------
-- 4. Garde-fous
-- ---------------------------------------------------------------------------
DO $$
DECLARE commandes integer; perdues integer; restants integer; masques integer;
BEGIN
    SELECT count(*) INTO commandes
    FROM product_dedupe_ttc d
    WHERE d.id <> d.id_retenu
      AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = d.id);
    IF commandes > 0 THEN
        RAISE EXCEPTION 'V36 : % fiche(s) commandee(s) allaient etre masquees', commandes;
    END IF;

    SELECT count(*) INTO perdues
    FROM (SELECT DISTINCT id_retenu FROM product_dedupe_ttc) d
    JOIN products p ON p.id = d.id_retenu
    WHERE p.deleted_at IS NOT NULL;
    IF perdues > 0 THEN
        RAISE EXCEPTION 'V36 : % fiche(s) retenue(s) ont ete masquees par erreur', perdues;
    END IF;

    -- Plus aucun groupe fusionnable ne doit subsister, hormis ceux portant une commande.
    -- Le comptage des commandes est calcule ligne par ligne AVANT le regroupement : place
    -- dans le HAVING, la sous-requete correlee porterait sur une colonne absente du GROUP BY.
    SELECT count(*) INTO restants FROM (
        SELECT 1
        FROM (
            SELECT p.tenant_id, sku_key(p.sku) AS cle,
                   (SELECT count(*) FROM order_items oi WHERE oi.product_id = p.id) AS nb
            FROM products p
            WHERE p.deleted_at IS NULL AND cardinality(sku_key(p.sku)) > 0
        ) lignes
        GROUP BY tenant_id, cle
        HAVING count(*) > 1 AND sum(nb) = 0
    ) groupes_restants;
    IF restants > 0 THEN
        RAISE EXCEPTION 'V36 : % groupe(s) de doublons subsistent hors commandes', restants;
    END IF;

    SELECT count(*) INTO masques FROM product_dedupe_ttc WHERE id <> id_retenu;
    RAISE NOTICE 'V36 : % fiche(s) en double masquee(s)', masques;
END $$;

DROP TABLE product_dedupe_ttc;
