# M0 — analyse d'intégration des maquettes du catalogue

**Lot `M0`. Analyse seule : aucun code, aucune migration, aucune écriture en base.**

Mesuré sur `main` (`5f2fb65`) le **17 novembre 2026**. Préséance appliquée :
`CLAUDE.md`, puis `prompt_dette_technique.md` (D0), puis `plan_integration_catalogue.md`,
puis le prompt du lot. **Aucune contradiction rencontrée entre les quatre.**

Les dix écrans sont les dix fichiers de
`docs/maquette_catalogue_optimisante/maquette_catalogue/`. Aucune autre page n'est concernée.

---

## 1. Les chiffres du plan, revérifiés aujourd'hui

D0 § 0 demande de revérifier plutôt que de recopier. Fait, un par un.

| Mesure | Valeur au plan (29 oct.) | Mesurée le 17 nov. | Verdict |
|---|---|---|---|
| Dernière migration | V81 → prochain libre **V82** | `V81__provenance_client_stripe.sql` est la dernière | **confirmé** |
| Poids JS produit | 1 333 Ko | **1 292 Ko** | **corrigé — 41 Ko de moins** |
| Marge JS | 167 Ko | **208 Ko** | **corrigé** |
| Poids public | 496 Ko (plafond 700) | 496 Ko | confirmé |
| Cliquet des couleurs en dur | 247 | 247 dans `charte-reference.json` | confirmé |
| Textes entre crochets | 39 | **39**, répartition exacte (§ 4) | confirmé |
| Motifs de visuel factice, serveur | 6 | 6 dans `VisuelProduit.MOTIFS` | confirmé |
| URL factices, navigateur | 4 | 4 dans `ProductImage.KNOWN_BROKEN_URLS` | confirmé |
| Filtrage par catégorie | égalité stricte | `cb.equal(root.get("category").get("id"), …)` | **confirmé** |
| Catégories en base | 211, toutes racines | **211, toutes racines** (base de dév.) | **confirmé** en dév. ; production à mesurer |
| Catégories sans produit | 57 | **57** (base de dév.) | **le plan a raison**, le code dit 85 à tort |

### Deux points à régler avant d'ouvrir `CAT`

**Le budget JS ne tient plus à 3 Ko près.** La marge mesurée est de **208 Ko**, et le budget
arrêté au plan est de **211 Ko** (183 Ko catalogue + 28 Ko brochures). Il manque 3 Ko.
Le plan le disait lui-même : « la marge est consommée **exactement**, sans réserve » — elle
l'est désormais à découvert. Trois issues, et c'est un arbitrage, pas un détail de mise en
œuvre : ramener le catalogue à 180 Ko, ramener les brochures à 25 Ko, ou relever le plafond
de charte. **La troisième est à écarter** : `charte-reference.json` dit en toutes lettres
qu'on ne relève pas une référence pour faire passer un build.

> Au passage, le commentaire `_plafondJsKo` de `charte-reference.json` annonce encore
> « 1 333 Ko produits aujourd'hui ». C'est faux de 41 Ko. La correction est déjà inscrite
> au lot `QUA‑1` du cahier des charges ; elle y reste, ce lot ne touche à rien.

**Le budget de `M1` est écrit deux fois, et pas pareil.** La table des lots du plan (§ 2) dit
**10 Ko** ; le détail du même plan (§ 4 « M1 ») et le prompt du lot disent **≤ 8 Ko**, comme
critère d'acceptation. C'est une contradiction *interne* au plan, pas entre les documents de la
préséance — je la signale et je retiens **8 Ko**, parce qu'un critère d'acceptation est plus
engageant qu'une ligne de table, et parce que le plus strict des deux ne fait courir aucun
risque. Les 2 Ko d'écart retournent à la réserve de 25 Ko du plan, et non au budget de `M1`.

**Deux dénombrements de catégories vides se contredisent — et le plan a raison.** Le plan dit
57 catégories sans produit ; le commentaire de `CategoryResponseDto` dit « 85 des 211
catégories sont vides ». **Mesuré le 17 novembre** sur la base de développement locale, par la
route publique `GET /catalog/categories` :

| Mesure | Valeur mesurée | Plan |
|---|---|---|
| Catégories servies | **211** | 211 ✓ |
| Dont racines | **211** — aucune n'a d'enfant | « toutes racines, profondeur 1 » ✓ |
| Dont sans aucun produit | **57** | 57 ✓ |
| Libellés exactement identiques | **0** (les 10 groupes du plan sont des quasi-doublons) | — |

Le commentaire « 85 » de `CategoryResponseDto` est donc **périmé**, et c'est lui qu'il faudra
corriger, pas le plan. Je ne l'ai pas fait : c'est hors du périmètre de `M0`, qui n'écrit pas
de code.

**Réserve, et elle compte** : cette mesure porte sur la base de **développement**, pas sur la
production. Elle vaut comme forte présomption, pas comme réponse. La requête du § 5.2 reste à
lancer, et c'est elle qui dimensionne `CAT`.

---

## 2. Écran par écran

Le détail des éléments est en § 3 ; cette table dit pour chaque écran ce qui existe, ce qui
bouge et ce qui naît.

| # | Écran | Lot | Ce qui existe et se réutilise | À modifier | À créer |
|---|---|---|---|---|---|
| 1 | Page Catalogue — ordinateur | `M3a` | `CatalogPage.tsx` (355 l.), `Navbar`, route `/categories` déjà arborescente | la page entière : aujourd'hui une grille de produits, la maquette veut deux groupes de familles | les deux groupes de niveau 1, les cartes de famille avec leurs trois sous-niveaux en sous-titre, la bande « Accès rapide » |
| 2 | Méga-menu « Catalogue » | `M3a` | `MenuServices.tsx` (154 l.) comme modèle de panneau déroulant au survol | `Navbar` : ajouter le ▾ sur « Catalogue » | les trois colonnes famille → sous-catégorie → type, la navigation au clavier, le repli « cette catégorie n'a pas de sous-niveau » |
| 3 | Catalogue — mobile | `M3a` | `MenuMobile.tsx` (176 l.) | — | la version téléphone de l'écran 1 : familles en liste, « Sélection du moment » en bande défilante |
| 4 | Page famille — ordinateur | `M3b` | `CategoryPage.tsx` (496 l.), `ProductCard.tsx` | `CategoryPage` doit distinguer *famille* (choix d'un rayon) et *rayon* (grille filtrée) — aujourd'hui elle ne connaît que le second | le bloc « Choisissez une catégorie », le bloc « Familles voisines », le chapeau de famille |
| 5 | Page rayon — ordinateur | `M3b` | `CategoryPage.tsx`, `PanneauFiltres.tsx` (175 l.), `filtresRayon.ts` (98 l.), les 4 tris serveur | `PanneauFiltres` : ajouter le bloc « Type » (sous-catégories) | les puces de type au-dessus de la grille, le compteur « 8 produits affichés sur N » |
| 6 | Famille — mobile | `M3b` | idem écran 4 | — | version téléphone : familles en liste avec leurs sous-niveaux en sous-titre |
| 7 | Rayon — mobile | `M3b` | idem écran 5 | — | la barre « Filtrer (2) · Trier par », les puces de type |
| 8 | Panneau « Filtrer » — mobile | `M3b` | `PanneauFiltres.tsx` : Prix, Type d'achat, Promotions, « Réinitialiser » **existent déjà** | l'ouvrir en panneau plein écran sur téléphone | le bloc « Type », le bloc « Marque » (→ `M4`), le pied « Voir les N produits » |
| 9 | Fiche produit — ordinateur | `M2` | `ProductDetailPage.tsx` (596 l.), `ProductGallery` (257 l.), `ProductMediaDialog` (335 l.), `ProductVideo` (191 l.), `/shipping/destinations` et `/shipping/estimation`, le sélecteur de quantité, la mention DAP | la colonne de droite se réorganise ; le sélecteur de pays passe **dans** la fiche | les onglets (`role="tablist"`), le bloc caractéristiques (→ `M2‑bis`), le bloc documents (→ `M2‑bis`), « Produits similaires » |
| 10 | Fiche produit — mobile | `M2` | idem écran 9 | — | la barre d'achat collée en bas (« Total » + « Ajouter au panier »), les sections dépliables |

### Ce qui est déjà en place et qu'on sous-estimait

Trois choses que le plan présentait comme à construire existent :

1. **La route d'arbre.** `GET /api/v1/catalog/categories` construit déjà l'arborescence
   récursivement (`CatalogService.mapToCategoryDto`), part des racines
   (`findByTenantIdAndParentIsNull`), et sert pour chaque catégorie son `productCount` **et**
   une `imageUrl` — la photo d'un vrai produit du rayon, les visuels factices écartés par
   `VisuelProduit.MOTIFS`, en deux requêtes et non deux cent onze. `M3a` n'a donc pas de route
   à écrire : il a un arbre à **remplir** (`CAT`) et, le cas échéant, un cache à poser.
2. **Le « Visuel de la catégorie » de la maquette a déjà sa donnée.** C'est l'`imageUrl`
   ci-dessus. Contrairement à ce qu'on pouvait craindre, ce n'est pas une donnée manquante :
   `categories` ne porte pas d'illustration, et le serveur a choisi de montrer ce que le rayon
   contient réellement plutôt que d'en inventer une. **À ne pas défaire.**
3. **Le panneau de filtres.** `PanneauFiltres.tsx` porte déjà Prix, Type d'achat, « Seulement
   les promotions » et « Réinitialiser », n'affiche jamais un bloc vide, et reçoit déjà un
   `nombreSousRayons`. Il manque deux blocs sur cinq : « Type » et « Marque ».

Et une quatrième, pour `M1` : **la barre de navigation de la maquette est exactement celle du
code.** Accueil · Catalogue · Nos Services · Formations Médicales · Devenir Partenaire ·
Promotions `New` — l'ordre et les libellés de `NAVIGATION_PRINCIPALE` au mot près, badge
unique compris. `M1` n'a rien à y changer, et le test `navigationPrincipale.test.ts` reste
vert.

---

## 3. Dépendances de données

### Les sept tranchées par le plan, revérifiées

| Donnée | Verdict du plan | Vérifié aujourd'hui | Où |
|---|---|---|---|
| Arbre à 3 niveaux | n'existe pas en données | `Category` **porte** `parent` et `subcategories` ; la structure est là, la profondeur non | `Category.java:60‑66` |
| Marque du produit | n'existe pas | confirmé : aucun champ. `components/marque/` ne contient que le logotype d'Optimi Santé | `Product.java` |
| Garantie | n'existe pas | confirmé, **et elle a été retirée exprès** (voir ci-dessous) | `ProductDetailPage.tsx:430‑437` |
| Documents produit | n'existe pas | confirmé | — |
| Caractéristiques techniques | n'existe pas | confirmé | — |
| Délai de livraison | n'existe pas | confirmé : `EstimationDto(zone, zoneLibelle, montant, offerte, droitsALArrivee)` — **pas de délai** | `ShippingResource.java:76‑77` |
| Numéro WhatsApp | non configuré | confirmé : aucune occurrence | — |

> **La garantie n'est pas un oubli, c'est une décision.** Le code porte un commentaire en
> capitales : « Garantie 2 ans incluse » s'affichait sur les 1 529 fiches du catalogue sans
> qu'aucune donnée ne la porte, « une promesse contractuelle affichée au jugé engage la
> maison sur des gants à usage unique comme sur un échographe ». Elle a été retirée, et
> « reviendra le jour où la durée sera une donnée du produit ».
>
> La maquette demande « Garantie fabricant : `[DURÉE]` » sur les deux fiches. **La remplir
> d'une valeur par défaut rétablirait exactement le défaut corrigé.** Elle se masque, et
> `M2‑bis` lui donne son champ.

### Trois dépendances que le plan n'avait pas listées

| Donnée | Ce que la maquette en fait | État |
|---|---|---|
| **Description de famille** | le chapeau des pages famille : « Tensiomètres, oxymètres, thermomètres et pesage pour suivre un patient à domicile comme en cabinet. » | `Category` n'a **ni description ni chapeau**. Un champ à ajouter dans `CAT`, ou le bloc se masque |
| **Sous-titre des cartes de famille** | « Lits médicalisés · Mobilier d'examen · Chariots » sous chaque carte, écran 1 | **dérivable** : ce sont les noms des trois premiers enfants. Aucun champ à créer, une fois l'arbre rempli |
| **Familles voisines** | un bloc de 4 liens en bas des pages famille | Aucune notion de voisinage en base. Dérivable par le parent commun une fois l'arbre à 3 niveaux — à confirmer dans `M3b`, sinon masqué |

### Une dépendance cachée de `M1` à `CAT`

Le plan dit que `M1` ne dépend pas de `CAT`. C'est vrai de la barre de recherche, **faux du
sélecteur « Catégorie »** que la maquette place à sa gauche. Sur chaque écran, ce sélecteur
propose un contexte : « Tensiomètres / Auto-surveillance / Toutes catégories » sur le rayon,
« Auto-surveillance / Toutes catégories / Établissements de santé / Domicile & officine » sur
la famille. Ces entrées **sont** les niveaux de l'arbre. Aujourd'hui, les 211 catégories étant
toutes racines, le sélecteur ne peut proposer que « Toutes catégories » et une liste plate de
211 entrées.

**La décision est déjà prise, et pas par moi.** Le plan § 4 « M1 » et le prompt du lot disent
tous deux la même chose : le sélecteur liste les catégories **qui existent aujourd'hui**, il
deviendra utile avec `CAT` **sans changement de code** puisqu'il lit la même route, et cela
« se dit dans le rapport, ne se corrige pas ». Le prompt ajoute : « n'anticipe pas l'arbre, ne
le simule pas. »

J'avais d'abord recommandé de le masquer. **Je retire cette recommandation** : la préséance
donne raison au plan, et D0 § 5 ne s'y oppose pas — il masque un élément *dont la donnée
n'existe pas*, et ici la donnée existe, elle est seulement peu utile. Un sélecteur de 211
entrées plates est médiocre ; ce n'est pas une affirmation fausse, et c'est la différence qui
compte. Il se livre, et le rapport de `M1` le dit.

Ce qui reste vrai et mérite d'être su : **`M1` dépend de `CAT` pour la qualité de ce
sélecteur, pas pour son existence.** Le plan avait raison de dire que `M1` ne dépend pas de
`CAT` ; il manquait cette nuance.

Le reste de `M1` est net et sans dépendance : le champ de recherche existe
(`Navbar.tsx:119`), il lui manque `role="search"`, un libellé accessible, le bouton
« Rechercher » visible de la maquette, et le texte d'invite passe de « Quel produit
recherchez-vous ? » à « Rechercher un produit, une référence… ».

---

## 4. Les 39 textes entre crochets, un par un

Comptés dans les dix `.dc.html`. **Le total de D0 est exact, et sa répartition aussi.**

| Texte | × | Écrans | Donnée qui l'alimenterait | Existe ? | Décision |
|---|---|---|---|---|---|
| `[PRIX]` | 10 | Rayon, RayonMobile | `basePrice` / `promoPrice` / `finalPrice` | **oui** | rempli par `M3b` |
| `[À COMPLÉTER]` | 6 | Produit, ProduitMobile | caractéristiques techniques | non | **masqué** jusqu'à `M2‑bis` |
| `[N]` | 5 | Rayon, RayonMobile, FiltresMobile | total de la page de résultats | **oui** | rempli par `M3b` |
| `[RÉFÉRENCE]` | 4 | Produit, ProduitMobile | `Product.sku` | **oui** | rempli par `M2` — déjà affiché aujourd'hui (`Réf. {product.sku}`) |
| `[DURÉE]` | 4 | Produit, ProduitMobile | garantie | non | **masqué** jusqu'à `M2‑bis`. Voir l'avertissement du § 3 |
| `[À AJOUTER]` | 2 | Produit, ProduitMobile | notice d'utilisation (PDF) | non | **masqué** jusqu'à `M2‑bis`, qui dépend de `SP‑1` |
| `[MONTANT]` | 2 | Produit, ProduitMobile | `EstimationDto.montant` | **oui** | rempli par `M2` |
| `[DÉLAI]` | 2 | Produit, ProduitMobile | délai par zone | non | **masqué** jusqu'à `L` |
| `[À CONFIRMER]` | 1 | Produit | une caractéristique | non | **masqué** jusqu'à `M2‑bis` |
| `[NUMÉRO]` | 1 | Produit | numéro WhatsApp | non | **masqué**. Paramètre de `parametres_plateforme`, jamais en dur |
| descriptions de remplissage | 2 | Produit, ProduitMobile | `Product.description` | **oui** | rempli par `M2` |

**Bilan : 19 des 39 ont leur donnée** (`[PRIX]`, `[N]`, `[RÉFÉRENCE]`, `[MONTANT]`, les deux
descriptions) et se remplissent dans `M2` et `M3b`. **Les 20 autres se masquent**, et la
répartition par lot est au § 7.

> Trois précisions de méthode. Le `[N]` du pied de `FiltresMobile` (« Voir les `[N]`
> produits ») demande le nombre de résultats **que les filtres en cours donneraient**, pas le
> total du rayon : c'est un appel de dénombrement, à compter dans le coût de `M3b`. Les treize
> crochets de `MegaMenu.dc.html` ne sont **pas** des textes de remplissage mais du code
> JavaScript de la maquette (`[this.state.fam]`, `['Potences', 'Tables de lit', …]`) : ils ne
> comptent pas dans les 39, et c'est bien ce que fait D0. Enfin, `−5 %` et les prix
> `1 645,18 € / 1 724,18 €` des deux fiches sont des valeurs d'exemple **en dur** dans la
> maquette, pas des crochets : à ne pas recopier.

---

## 5. Les trois vérifications en production — **non faites**

**Aucune n'a été exécutée.** Je n'ai pas l'adresse de la base, et D0 § 1 comme `CLAUDE.md` § 4
réservent tout audit de production à ton accord explicite, en lecture seule. Les trois
commandes sont ci-dessous, à coller dans ta fenêtre Git Bash où `URLBASE` est déjà exportée.
Aucune n'écrit, aucune n'ouvre de transaction, et aucune ne sort de donnée personnelle.

**Tant que leurs sorties ne sont pas dans ce document, `M0` n'est pas clos** et `CAT` n'est pas
dimensionné.

### 5.1 Les visuels factices ignorés par le navigateur

Reprise **telle quelle** du § 6 du plan, comme le prompt l'exige.

```bash
MSYS_NO_PATHCONV=1 docker run -i --rm -e URLBASE postgres:16-alpine \
  sh -c 'psql "$URLBASE"' <<'SQL'
-- Produits dont le visuel est factice pour le serveur mais pas pour le navigateur.
-- Lecture seule : aucun UPDATE, aucun DELETE, pas de transaction ouverte.
SELECT CASE
         WHEN image_url ILIKE '%unsplash%'        THEN 'unsplash'
         WHEN image_url ILIKE '%prod_fallback_%'  THEN 'prod_fallback_'
       END                                   AS motif_ignore_par_le_navigateur,
       count(*)                              AS produits,
       count(*) FILTER (WHERE is_active)     AS dont_actifs,
       min(sku)                              AS exemple_sku
  FROM products
 WHERE deleted_at IS NULL
   AND (image_url ILIKE '%unsplash%' OR image_url ILIKE '%prod_fallback_%')
 GROUP BY 1
 ORDER BY 2 DESC;
SQL
```

*Zéro ligne* : la divergence est théorique, la correction reste souhaitable sans être urgente.
*Une ligne ou plus* : autant de fiches qui affichent une photo factice comme une vraie photo,
et la correction entre dans `M2`.

**Sortie :** *à coller ici.*

### 5.2 Dimensionnement de `CAT` : produits par catégorie, et profondeur réelle

```bash
MSYS_NO_PATHCONV=1 docker run -i --rm -e URLBASE postgres:16-alpine \
  sh -c 'psql "$URLBASE"' <<'SQL'
-- Lecture seule. Dimensionne CAT : combien de categories, a quelle profondeur,
-- et combien n'ont aucun produit.
SELECT count(*)                                      AS categories,
       count(*) FILTER (WHERE parent_id IS NULL)     AS racines,
       count(*) FILTER (WHERE parent_id IS NOT NULL) AS avec_un_parent
  FROM categories;

SELECT CASE
         WHEN n = 0            THEN '0 produit'
         WHEN n BETWEEN 1 AND 2 THEN '1 a 2 produits'
         WHEN n BETWEEN 3 AND 9 THEN '3 a 9 produits'
         ELSE '10 produits et plus'
       END        AS tranche,
       count(*)   AS categories
  FROM (SELECT c.id,
               count(p.id) FILTER (WHERE p.deleted_at IS NULL AND p.is_active) AS n
          FROM categories c
          LEFT JOIN products p ON p.category_id = c.id
         GROUP BY c.id) t
 GROUP BY 1
 ORDER BY 2 DESC;
SQL
```

**Sortie :** *à coller ici.* Elle tranche la contradiction 57 / 85 du § 1.

### 5.3 Aucun chemin ne contourne `ProductSpecification`

Celle-ci se vérifie **dans le dépôt**, et je l'ai faite : `categoryId` est le seul paramètre de
filtrage par catégorie de la route publique (`CatalogResource:51`), il passe par
`ProductSpecification.withCategoryId` (`CatalogService:110`), et c'est le **seul** appelant de
cette spécification. Les deux autres usages de `categoryId` dans le domaine sont dans
`AdminCatalogService` (lignes 176 et 211), sur des listes d'administration, hors route
publique.

**Le filtrage est donc bien une égalité stricte, en un seul endroit** —
`cb.equal(root.get("category").get("id"), categoryId)`,
`ProductSpecification.java:34`. C'est une bonne nouvelle pour `CAT` : la descendance se
pose à un seul endroit.

**Conséquence, à écrire noir sur blanc** : le jour où l'arbre à trois niveaux existe, **toute
page famille affichera zéro produit** jusqu'à ce que `CAT` ait posé la descendance. Les deux
ne se livrent pas séparément.

---

## 6. La source unique des visuels factices — proposition confirmée

La proposition du plan § 6 tient, et je n'ai rien de mieux.

Le serveur expose un booléen `visuelFactice` par produit, calculé par
`VisuelProduit.estFactice` ; `ProductImage` le lit et `KNOWN_BROKEN_URLS` disparaît. Le repli
par `onError` reste — il couvre une image réellement cassée, ce qu'aucune liste ne prévoit.

**Vérifié aujourd'hui, et c'est ce qui rend la correction nécessaire** : les deux listes
divergent toujours. Le serveur connaît six motifs, le navigateur quatre URL exactes, et
`unsplash` comme `prod_fallback_` ne sont connus que du serveur. Un produit dont l'image porte
l'un des deux est écarté par le serveur et affiché comme une vraie photo par le navigateur.

**Coût en kilooctets rendus : ~0,4 Ko.** Quatre URL de 100 caractères et le test
d'appartenance quittent le paquet ; le booléen voyage dans un JSON déjà envoyé. Ce n'est pas
l'argument — l'argument est qu'un motif ajouté au serveur s'applique aussitôt au navigateur.

Elle se greffe sur `M2`, qui touche déjà la galerie. Pas un lot.

---

## 7. Ce qui sera masqué, lot par lot

D0 § 5 : un élément dont la donnée n'existe pas est masqué — jamais vide, jamais inventé,
jamais rempli d'un exemple plausible — et tout élément masqué est listé dans le rapport du lot.
Voici la liste à reprendre par chaque lot.

| Lot | Masqué, et pourquoi |
|---|---|
| `M1` | **rien.** Le sélecteur « Catégorie » se livre avec les catégories existantes ; le rapport dit qu'elles sont 211 et plates (§ 3) |
| `M2` | « Garantie fabricant » (4 crochets), « Notice d'utilisation (PDF) » (2), le bloc caractéristiques (7), la ligne « Livraison estimée » (2), « WhatsApp » (1) — **soit les 20 crochets sans donnée** |
| `M3a` | le chapeau de description des familles, si `CAT` ne lui donne pas de champ |
| `M3b` | le bloc « Marque » du panneau de filtres, jusqu'à `M4` ; « Familles voisines » si le voisinage ne se dérive pas |
| `M4` | — il *apporte* la marque ; rien à masquer, mais le filtre ne s'affiche que là où la donnée est renseignée |
| `M2‑bis` | les onglets dont la donnée reste vide produit par produit, fiche par fiche |
| `L` | — il *apporte* le délai |

**Au total, `M2` livre les deux fiches produit avec cinq éléments masqués.** C'est assumé et
c'est le bon choix : les cinq sont des affirmations que nous ne pouvons pas tenir aujourd'hui,
et l'une d'elles a déjà été retirée pour cette raison exacte.

---

## 8. Ce qui n'a pas été vérifié

Dit explicitement, comme D0 § 7.4 l'exige.

- **Les deux requêtes de production des § 5.1 et 5.2.** Non exécutées, faute d'accord et
  d'adresse. Ce sont les seuls trous de cette analyse, et ils bloquent le dimensionnement
  de `CAT`.
- **Aucune capture d'écran.** Il n'y a pas de code dans ce lot, donc rien à montrer. D0 § 7
  dispense explicitement `M0` du build et des captures. Le `npm run build` a tout de même
  été lancé, pour mesurer le poids du § 1 : **vert, 496 Ko / 1 292 Ko, charte respectée.**
- **La dérivation des « familles voisines »** n'est pas démontrée, seulement jugée plausible
  par le parent commun. À trancher dans `M3b`, sur l'arbre réel.
- **Le contenu exact des maquettes mobiles** a été lu par extraction du texte, des liaisons de
  données et des couleurs, pas rendu dans un navigateur. Les dix fichiers appellent un
  `support.js` absent du dossier : ils ne s'affichent donc pas tels quels, et les comparer
  pixel à pixel aurait demandé de reconstruire leur moteur de gabarit. Pour une analyse
  d'éléments et de données, l'extraction suffit ; pour la **vérification réelle** d'un lot, ce
  sont les captures de nos pages à nous qui comptent, pas celles des maquettes.
- **Les couleurs des maquettes** n'ont pas été traduites en jetons. **26 valeurs
  hexadécimales distinctes** et 3 `rgba()` reviennent sur les dix écrans ; les trois dérives
  connues de D0 (`#13232C`, `#0B4F6C`, `#D92D20`) sont bien parmi elles, et le reste se
  traduira lot par lot. Aucune n'a été ajoutée au dépôt : le cliquet reste à 247.

---

## 9. Ce que ce lot demande de toi

Dans l'ordre de ce qu'il bloque.

1. **Les deux requêtes du § 5**, dont la sortie clôt `M0` et dimensionne `CAT`.
2. **Les 3 Ko de budget JS** (§ 1) : catalogue à 180 Ko, ou brochures à 25 Ko.
3. **Le fichier d'arbitrage de l'arborescence.** `docs/arborescence_catalogue_v2.xlsx` n'est
   pas dans le dépôt. Sans lui, `CAT` ne s'ouvre pas, et avec lui `M3a`, `M3b`, `M4`, `PIL`
   et `IMP` restent fermés. C'est le chemin critique, et il est de ton côté.
**`M1` et `M2` s'ouvrent sans attendre aucun des trois.** Ils ne touchent ni à l'arbre, ni au
budget au-delà de leur propre enveloppe (8 Ko et 40 Ko), ni à la production.
