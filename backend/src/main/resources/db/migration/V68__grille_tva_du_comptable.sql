-- La grille de TVA établie par le comptable, octobre 2026.
--
-- ── Le principe que le comptable rappelle ───────────────────────────────────────────────
-- Pour le matériel médical, TOUT est à 20 %, sauf ce qui figure dans une liste précise du
-- Code général des impôts (art. 30-0 B de l'annexe IV). Le taux réduit s'attache au PRODUIT,
-- pas à l'acheteur : un fauteuil roulant est à 5,5 % que l'acheteur soit handicapé ou non, et
-- un produit voisin absent de la liste reste à 20 % même acheté par une personne handicapée.
--
-- ── Ce qui est écrit ici, et ce qui ne l'est pas ────────────────────────────────────────
-- Seules les catégories que le comptable a nommées reçoivent un taux. Les autres restent à
-- NULL et retombent sur 20 % — ce qui est exactement la règle légale, et non un défaut
-- technique. La distinction compte : un taux posé dit « décidé », un taux absent dit « non
-- examiné ». Les confondre ferait croire à un arbitrage là où il n'y en a pas eu.
--
-- ── Les lignes marquées par le comptable d'un avertissement ─────────────────────────────
-- Il n'a pas pu consulter la liste officielle sur Légifrance, l'accès ayant été bloqué. Les
-- catégories concernées sont donc posées à 20 % — le taux PRUDENT — et marquées
-- `vat_rate_a_verifier`. Prudent dans un sens précis : les prix étant annoncés TTC, extraire
-- 20 % là où 5,5 % s'applique fait reverser à l'État plus que dû, ce qui coûte de la marge ;
-- l'inverse est un manquement déclaratif. On préfère perdre de la marge.
--
-- ── Les rayons mixtes ───────────────────────────────────────────────────────────────────
-- « Transfert », « Siège de douche & bain », « Bandes de compression » contiennent des
-- produits relevant de taux différents. Le comptable est explicite : le taux s'y applique
-- PAR PRODUIT, dans la fiche article. La catégorie porte donc le taux majoritaire ou prudent,
-- et chaque exception se saisit sur le produit, où elle prime déjà.

ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS vat_rate_a_verifier boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN categories.vat_rate_a_verifier IS
    'Le comptable a signalé ce taux comme à confirmer sur la liste officielle (CGI ann. IV '
    'art. 30-0 B). Posé au taux prudent en attendant.';

-- La comparaison passe par category_norm() : c'est la fonction qui arbitre déjà l'unicité des
-- catégories, et elle neutralise accents, casse et pluriels. Les intitulés du comptable ne
-- correspondent pas lettre pour lettre à ceux de la base — « Aide à la toillette » y porte sa
-- coquille, « paravants » son orthographe d'origine — et une égalité stricte en aurait manqué
-- plusieurs en silence.
UPDATE categories c SET vat_rate = v.taux, vat_rate_a_verifier = v.a_verifier
FROM (VALUES
    -- ── Taux réduit : 5,5 % ─────────────────────────────────────────────────────────────
    -- Tous les fauteuils roulants, inscrits ou non au remboursement, ainsi que les scooters
    -- médicaux jusqu'à 10 km/h.
    ('Fauteuil roulant & accessoires',                      5.50, false),

    -- ── Taux intermédiaire : 10 % ───────────────────────────────────────────────────────
    -- Tolérance officielle maintenue pour les cannes et déambulateurs.
    ('Cannes de marche',                                   10.00, false),
    ('Cadres de marche & rollator',                        10.00, false),

    -- ── Rayons mixtes : taux prudent en catégorie, exception par produit ────────────────
    -- 5,5 % si fauteuil roulant de transfert ou lève-personne de la liste ; disques et
    -- planches de glisse : 20 %.
    ('Fauteuil & Accessoires de transfert',                20.00, true),
    -- 5,5 % pour les fauteuils et chariots de douche À ROULETTES, 20 % pour les chaises et
    -- sièges sans roulettes (réponse ministérielle de janvier 2024).
    ('Siège de douche & bain',                             20.00, true),
    -- Contention médicale remboursable (orthèses) : 5,5 % ; fixation : 20 %.
    ('Bandes de compression & de fixation',                20.00, true),

    -- ── Taux normal à confirmer : 20 % en attendant ────────────────────────────────────
    -- 5,5 % seulement si le modèle figure dans la liste des aides techniques.
    ('Rampes',                                             20.00, true),
    ('Barres d''appui',                                    20.00, true),
    -- Rehausses WC, chaises percées : à vérifier modèle par modèle.
    ('Aide à la toillette',                                20.00, true),
    -- Leur catégorie de remboursement n'ouvre pas droit au taux réduit.
    ('Prévention d''escarres',                             20.00, true),

    -- ── Taux normal, tranché ───────────────────────────────────────────────────────────
    ('Fauteuils releveurs & de repos',                     20.00, false),  -- mobilier
    ('Oreillers bien-être et ergonomiques',                20.00, false),
    ('Coussins de voyage & du quotidien',                  20.00, false),
    -- 5,5 % seulement pour un home-trainer conçu pour les personnes handicapées.
    ('Vélo d''appartement & Accessoires de rééducation',   20.00, false),
    -- Seuls les défibrillateurs IMPLANTABLES relèvent du taux réduit.
    ('Défibrillateurs',                                    20.00, false),
    ('Secours',                                            20.00, false),
    -- Sauf articles pour personnes stomisées ou incontinentes, à saisir sur le produit.
    ('Pansements techniques',                              20.00, false),
    ('Sparadrap',                                          20.00, false),
    ('Filets tubulaires & compresses',                     20.00, false),
    ('Bandes cohésives & adhésives',                       20.00, false),
    ('Sets de suture, agrafeuses & ôtes-agraphes',         20.00, false),
    ('Bistouris, lame & coupes fils',                      20.00, false),
    ('Instrumentation qualité service',                    20.00, false),
    ('Gants d''examen et de chirurgie',                    20.00, false),
    ('Draps de protection et d''examen',                   20.00, false),
    ('Stéthoscopes de diagnostic & surveillance',          20.00, false),
    ('Tensiomètre électroniques, manuels & accessoires grand public', 20.00, false),
    ('Tensiomètre électroniques',                          20.00, false),
    ('Thermomètres',                                       20.00, false),
    ('Pèse personne à usage privé',                        20.00, false),
    ('Guéridons & paravants',                              20.00, false),
    ('Blouses, pantalons & tuniques',                      20.00, false),
    -- Rayons génériques : 20 %, sauf produit précis de la liste, à saisir sur la fiche.
    ('Équipement du domicile',                             20.00, true),
    ('Équipements divers',                                 20.00, true)
) AS v(nom, taux, a_verifier)
WHERE category_norm(c.name) = category_norm(v.nom);
