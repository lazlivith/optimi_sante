-- V35 : les fiches sans prix connu passent « sur devis »
--
-- LE CONSTAT
-- 138 produits vivants portent exactement 100,00 EUR, quand le prix suivant le plus frequent
-- n'apparait que 8 fois dans tout le catalogue. Ce n'est pas un prix, c'est la valeur par
-- defaut d'un import : les 1 513 fiches ont ete creees a la meme minute, le 20 aout 2026.
--
-- CE QUE CES FICHES CONTIENNENT
-- Ce ne sont pas des articles a bas prix. Deux familles, toutes deux sans prix unitaire connu :
--
--   1. 65 references « OPT-WC-… » : de l'equipement de laboratoire — cryostats, PCR en temps
--      reel, microtomes rotatifs, automates d'hematologie, scanners de lames, microscopes a
--      fluorescence, centrifugeuses refrigerees. **Toute cette famille est a 100,00 EUR**
--      (minimum = maximum), soit plusieurs milliers d'euros de materiel affiches a 100 EUR.
--
--   2. 73 gammes multi-references : le champ `sku` porte une LISTE de references
--      (« Drap housse standard » en couvre quinze, « Cathéter bd insyte » davantage). Une
--      gamme n'a pas de prix unitaire : 97 % des fiches a 100,00 EUR sont multi-references,
--      contre 30 % pour le reste du catalogue.
--
-- CE QUE CELA A DEJA COUTE
-- Un « Sterilisateur a air chaud, DOF-HAS70A » a ete **vendu** : 2 unites a 100,00 EUR puis
-- 18 a 95,00 EUR, soit 20 appareils pour 1 910 EUR au total. La commande n'est pas modifiee
-- ici — une piece comptable ne se recrit pas — mais elle mesure le risque.
--
-- CE QUE FAIT CETTE MIGRATION
-- Elle ne devine aucun prix : personne ne peut deduire le tarif d'un cryostat d'une base de
-- donnees. Elle bascule ces fiches sur `is_quote_only`, mecanisme **deja present et deja
-- applique** dans la plateforme :
--   - `OrderService` refuse la commande directe d'un article sur devis (« can only be ordered
--     via Quote Request ») ;
--   - la boutique affiche « Tarif sur demande » a la place du prix, et « Demander un devis »
--     a la place de « Ajouter au panier ».
-- Aucun changement de code n'est donc necessaire : le comportement correct existait, il
-- n'etait simplement pas applique a ces fiches.
--
-- POURQUOI TOUTES LES FICHES A 100,00 EUR, SANS EXCEPTION
-- Un article pourrait couter reellement 100,00 EUR et se retrouver bascule a tort. Le risque
-- est asymetrique : une bascule injustifiee se corrige d'un clic depuis l'administration et
-- oriente entre-temps le client vers un devis ; un cryostat laisse a 100 EUR se vend a
-- 100 EUR. Dans le doute, on demande le prix plutot que d'en inventer un.
--
-- LE PRIX N'EST PAS EFFACE
-- `base_price` reste a 100,00 EUR : la colonne est NOT NULL, et l'ecraser par une valeur
-- arbitraire (0) ferait passer un defaut pour une donnee. Il n'est simplement plus montre ni
-- facturable. Renseigner les vrais tarifs releve du catalogue, pas d'une migration.

UPDATE products
SET is_quote_only = true
WHERE deleted_at IS NULL
  AND base_price = 100.00
  AND is_quote_only = false;

-- ---------------------------------------------------------------------------
-- Garde-fou
-- ---------------------------------------------------------------------------
DO $$
DECLARE achetables integer; bascules integer;
BEGIN
    SELECT count(*) INTO achetables
    FROM products
    WHERE deleted_at IS NULL AND base_price = 100.00 AND is_quote_only = false;
    IF achetables > 0 THEN
        RAISE EXCEPTION 'V35 : % fiche(s) restent achetables au prix de remplissage', achetables;
    END IF;

    SELECT count(*) INTO bascules
    FROM products WHERE deleted_at IS NULL AND base_price = 100.00 AND is_quote_only;
    RAISE NOTICE 'V35 : % fiche(s) sans prix connu basculees sur devis', bascules;
END $$;
