-- Option « remplacer les visuels existants » d'un import fournisseur.
--
-- Pourquoi la colonne plutot qu'un simple parametre d'appel : le traitement s'execute en
-- arriere-plan et relit l'import en base. Le choix doit donc y figurer. Il y reste ensuite,
-- et c'est le second interet : l'historique des imports dit si un lot a remplace des
-- vignettes ou seulement complete les manques — la seule trace, une fois les anciennes
-- images remplacees.
--
-- Defaut a FALSE : un import mensuel ne doit pas ecraser un visuel choisi a la main. Le
-- remplacement se coche, volontairement, import par import.
ALTER TABLE catalog_imports
    ADD COLUMN IF NOT EXISTS replace_images BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN catalog_imports.replace_images IS
    'Vrai si cet import a remplace les visuels des produits qui en avaient deja un.';
