-- V32 : reparation du mojibake herite de l'import (UTF-8 relu en Mac OS Roman)
--
-- CONSTAT
-- 57 noms de produits, 619 descriptions et 1 nom de categorie portent des sequences comme
-- « ‚Ñ¢ » a la place de « ™ », « ¬Æ » a la place de « ® », « ‚Äô » a la place de « ’ »,
-- « ¬∞ » a la place de « ° ». Les descriptions sont visibles des clients sur la fiche produit.
--
-- DIAGNOSTIC
-- La corruption est systematique et d'une seule nature : le texte, encode en UTF-8, a ete
-- relu comme du Mac OS Roman. « ™ » (U+2122) s'ecrit E2 84 A2 en UTF-8 ; lus en Mac Roman,
-- ces trois octets donnent « ‚ », « Ñ », « ¢ ». De meme « ® » (C2 AE -> ¬Æ), « ° » (C2 B0 -> ¬∞),
-- « Ø » (C3 98 -> √ò), « € » (E2 82 AC -> ‚Ç¨).
--
-- POURQUOI UNE TABLE DE SEQUENCES ET NON LA TRANSFORMATION INVERSE
-- Reencoder en Mac Roman puis relire en UTF-8 serait plus elegant, et c'est ce que fait
-- naturellement un correctif d'encodage. Ici cela ne marche pas : **la corruption est
-- partielle a l'interieur d'une meme chaine**. « B√ÇTONNET SECURITE BEBE » melange
-- « √Ç » (abime) et « E-accent » (intact) ; reencoder la chaine entiere produit une suite
-- d'octets qui n'est pas de l'UTF-8 valide, et la ligne serait laissee telle quelle
-- **en silence**. On remplace donc sequence par sequence.
--
-- Les 50 premieres entrees ne sont pas ecrites a la main : elles sont calculees en balayant
-- tout le plan multilingue de base et en retenant chaque caractere dont l'encodage UTF-8 est
-- lisible en Mac Roman, puis confrontees
-- aux donnees reelles ; seules celles reellement presentes sont conservees. Le nom Unicode
-- officiel de chaque caractere retabli figure en commentaire, pour relecture.
--
-- Les 2 dernieres sont verifiees a la main, occurrence par occurrence, parce qu'aucun codec
-- ne les produit (le detail est en commentaire de chaque ligne).
--
-- CE QU'ELLE NE FAIT PAS
--   - Elle ne touche pas aux slugs : ils ne contiennent aucun mojibake (verifie), et les
--     regenerer casserait les URL deja indexees.
--   - Elle ne corrige pas les fautes d'accent de la source (CREME ecrit avec un accent aigu,
--     TETE ecrit avec un circonflexe) : ce sont des erreurs de saisie du fournisseur, pas
--     d'encodage. Les traiter ici melangerait deux sujets.

DROP TABLE IF EXISTS mojibake_map;
CREATE TABLE mojibake_map (abime text NOT NULL, correct text NOT NULL, ordre int NOT NULL);

-- L'ordre compte : les sequences les plus longues d'abord, sinon une sequence de deux
-- caracteres consommerait le debut d'une sequence de trois.
INSERT INTO mojibake_map (abime, correct, ordre) VALUES
    ('‚ÄÖ', ' ', 1),  -- FOUR-PER-EM SPACE
    ('‚Äà', ' ', 2),  -- PUNCTUATION SPACE
    ('‚Äâ', ' ', 3),  -- THIN SPACE
    ('‚Äì', '–', 4),  -- EN DASH
    ('‚Äò', '‘', 5),  -- LEFT SINGLE QUOTATION MARK
    ('‚Äô', '’', 6),  -- RIGHT SINGLE QUOTATION MARK
    ('‚Äú', '“', 7),  -- LEFT DOUBLE QUOTATION MARK
    ('‚Äù', '”', 8),  -- RIGHT DOUBLE QUOTATION MARK
    ('‚Ä¶', '…', 9),  -- HORIZONTAL ELLIPSIS
    ('‚Ç¨', '€', 10),  -- EURO SIGN
    ('‚Ñ¢', '™', 11),  -- TRADE MARK SIGN
    ('‚â§', '≤', 12),  -- LESS-THAN OR EQUAL TO
    ('‚åÄ', '⌀', 13),  -- DIAMETER SIGN
    ('¬´', '«', 14),  -- LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
    ('¬Æ', '®', 15),  -- REGISTERED SIGN
    ('¬∞', '°', 16),  -- DEGREE SIGN
    ('¬±', '±', 17),  -- PLUS-MINUS SIGN
    ('¬≤', '²', 18),  -- SUPERSCRIPT TWO
    ('¬≥', '³', 19),  -- SUPERSCRIPT THREE
    ('¬¥', '´', 20),  -- ACUTE ACCENT
    ('¬µ', 'µ', 21),  -- MICRO SIGN
    ('¬∫', 'º', 22),  -- MASCULINE ORDINAL INDICATOR
    ('¬ª', '»', 23),  -- RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
    ('¬º', '¼', 24),  -- VULGAR FRACTION ONE QUARTER
    ('¬Ω', '½', 25),  -- VULGAR FRACTION ONE HALF
    ('√Ä', 'À', 26),  -- LATIN CAPITAL LETTER A WITH GRAVE
    ('√Ç', 'Â', 27),  -- LATIN CAPITAL LETTER A WITH CIRCUMFLEX
    ('√é', 'Î', 28),  -- LATIN CAPITAL LETTER I WITH CIRCUMFLEX
    ('√ñ', 'Ö', 29),  -- LATIN CAPITAL LETTER O WITH DIAERESIS
    ('√ó', '×', 30),  -- MULTIPLICATION SIGN
    ('√ò', 'Ø', 31),  -- LATIN CAPITAL LETTER O WITH STROKE
    ('√õ', 'Û', 32),  -- LATIN CAPITAL LETTER U WITH CIRCUMFLEX
    ('√ú', 'Ü', 33),  -- LATIN CAPITAL LETTER U WITH DIAERESIS
    ('√¢', 'â', 34),  -- LATIN SMALL LETTER A WITH CIRCUMFLEX
    ('√ß', 'ç', 35),  -- LATIN SMALL LETTER C WITH CEDILLA
    ('√™', 'ê', 36),  -- LATIN SMALL LETTER E WITH CIRCUMFLEX
    ('√´', 'ë', 37),  -- LATIN SMALL LETTER E WITH DIAERESIS
    ('√Æ', 'î', 38),  -- LATIN SMALL LETTER I WITH CIRCUMFLEX
    ('√Ø', 'ï', 39),  -- LATIN SMALL LETTER I WITH DIAERESIS
    ('√¥', 'ô', 40),  -- LATIN SMALL LETTER O WITH CIRCUMFLEX
    ('√∏', 'ø', 41),  -- LATIN SMALL LETTER O WITH STROKE
    ('√π', 'ù', 42),  -- LATIN SMALL LETTER U WITH GRAVE
    ('√ª', 'û', 43),  -- LATIN SMALL LETTER U WITH CIRCUMFLEX
    ('≈í', 'Œ', 44),  -- LATIN CAPITAL LIGATURE OE
    ('≈ì', 'œ', 45),  -- LATIN SMALL LIGATURE OE
    ('ÃÄ', '̀', 46),  -- COMBINING GRAVE ACCENT
    ('ÃÅ', '́', 47),  -- COMBINING ACUTE ACCENT
    ('Ãß', '̧', 48),  -- COMBINING CEDILLA
    ('Œº', 'μ', 49),  -- GREEK SMALL LETTER MU
    ('’à', 'Ո', 50),  -- ARMENIAN CAPITAL LETTER VO
    ('‚™', '™', 51),  -- troncature, non derivable d'un codec : verifiee sur les 34 lignes, toujours accolee a une marque (3m, bd, coban, nexcare...)
    ('Â ', 'À ', 52)  -- A-circonflexe + espace : verifie sur les 17 occurrences, toutes des prepositions (POT A EAU, 6 A 8 PERSONNES, SAC A DOS...)
;

DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT abime, correct FROM mojibake_map ORDER BY length(abime) DESC, ordre LOOP
        UPDATE products   SET name        = replace(name, r.abime, r.correct)
         WHERE position(r.abime IN name) > 0;
        UPDATE products   SET description = replace(description, r.abime, r.correct)
         WHERE description IS NOT NULL AND position(r.abime IN description) > 0;
        UPDATE categories SET name        = replace(name, r.abime, r.correct)
         WHERE position(r.abime IN name) > 0;
    END LOOP;
END $$;

DROP TABLE mojibake_map;

-- ---------------------------------------------------------------------------
-- Garde-fous : plus aucun marqueur ne doit subsister
-- ---------------------------------------------------------------------------
DO $$
DECLARE restants integer;
BEGIN
    SELECT count(*) INTO restants FROM products
     WHERE name ~ '[‚√≈¬]' OR name LIKE '%Â %' OR description ~ '[‚√≈¬]';
    IF restants > 0 THEN
        RAISE EXCEPTION 'V32 : % produit(s) portent encore du mojibake', restants;
    END IF;

    SELECT count(*) INTO restants FROM categories
     WHERE name ~ '[‚√≈¬]' OR name LIKE '%Â %';
    IF restants > 0 THEN
        RAISE EXCEPTION 'V32 : % categorie(s) portent encore du mojibake', restants;
    END IF;
END $$;
