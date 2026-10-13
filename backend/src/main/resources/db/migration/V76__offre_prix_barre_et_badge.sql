-- ═════════════════════════════════════════════════════════════════════════════════════════
-- Ce qu'il faut pour annoncer un pack : un prix de référence, et une mise en avant
--
-- LE PRIX BARRÉ. « 990 € au lieu de 1 100 € » n'est pas une mise en forme : c'est une
-- affirmation chiffrée, et elle engage. La poser en base plutôt que dans un libellé la rend
-- modifiable par celui qui fixe les prix — le super administrateur — et non par celui qui
-- rédige les textes. Elle reste nulle pour toutes les offres qui ne sont pas des packs : une
-- offre sans prix de référence n'affiche aucune comparaison.
--
-- LE BADGE. Mettre une offre en avant est une décision commerciale qui change tous les
-- trimestres. Un drapeau booléen aurait imposé de redéployer pour changer le mot affiché ;
-- une colonne de texte laisse écrire « Le plus complet » aujourd'hui et autre chose demain.
-- Nulle ou vide, aucun badge n'est affiché.
--
-- Rien n'est rendu obligatoire : les quatre offres déjà en base restent valides telles
-- quelles, et cette migration ne modifie aucune donnée existante.
-- ═════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE evc_offers
    ADD COLUMN IF NOT EXISTS prix_barre NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS badge      VARCHAR(40);

COMMENT ON COLUMN evc_offers.prix_barre IS
    'Prix de référence affiché barré, au-dessus du prix réel. Nul = aucune comparaison '
    'affichée. Exprimé dans la même devise que « prix ».';

COMMENT ON COLUMN evc_offers.badge IS
    'Mention mise en avant sur la carte, par exemple « Le plus complet ». Nulle ou vide = '
    'aucun badge.';
