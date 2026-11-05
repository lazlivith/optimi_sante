# Intégration du nouveau catalogue — plan qui fait foi

Écrit pour : Mounir, et toute session qui ouvrira un lot.

Ce fichier **remplace** les prompts M0 à M4 de
`docs/maquette_catalogue_optimisante/plan_d'implementation`, conservé comme trace de la
demande initiale. En cas de divergence, c'est ce fichier qui vaut.

Préséance, décidée le 29 octobre 2026 : **`CLAUDE.md`**, puis
**`docs/maquette_catalogue_optimisante/prompt_dette_technique.md` (D0)**, puis ce plan. Une
session qui rencontre une contradiction entre les trois **s'arrête et le signale**.

Maquettes : `docs/maquette_catalogue_optimisante/maquette_catalogue/` (fichiers `.dc.html`,
`canvas.json` donne l'ordre). Références visuelles et fonctionnelles, **pas du code à copier**.

---

## 1. L'état réel, mesuré

Tout ce qui suit a été vérifié dans le dépôt le 29 octobre 2026, et doit être revérifié à
l'ouverture de chaque lot.

| Mesure | Valeur | Source |
|---|---|---|
| Production à la migration | **V81** — prochain numéro libre **V82** | `db/migration/` |
| Catégories en base | **211**, **toutes racines**, profondeur **1** | `megamenu_catalogue.md`, 21 oct. |
| Dont sans aucun produit | 57 | idem |
| Dont à 1 ou 2 produits | 37 | idem |
| Groupes de libellés quasi identiques | 10, dont une coquille | idem |
| Poids JS produit | **1 333 Ko** — plafond **1 500 Ko** | `charte-reference.json` |
| Marge JS disponible | **167 Ko** | idem |
| Poids public | 496 Ko — plafond 700 Ko | idem |
| Cliquet couleurs en dur | **247**, ne doit jamais remonter | idem |
| Textes entre crochets dans les maquettes | **39** | comptés dans les `.dc.html` |

### Ce que le fichier de La Centrale Médicale fournit réellement

Lu en lecture seule sur une copie de `D:\fournisseur_optimisanté\Catalogue_LCM_avec_images.xlsx`,
le 29 octobre. **Neuf colonnes, et pas une de plus :**

`SKU`, `NOM`, `CATEGORIE`, `MARQUE`, `REFERENCE FOURNISSEUR`, `OBSERVATIONS`, `PU HT`,
`IMAGE_URLS`, `RAYON FOURNISSEUR`.

| Mesure | Valeur |
|---|---|
| Lignes | **7 344** |
| Avec au moins une image | 2 946 (40 %) |
| Avec une marque | 5 647 — **1 697 sans** (23 %) |
| Marques distinctes | **79** (JOLETI 528, HARTMANN 322, L&R 289, DIAN 251, BD 240, SPENGLER 194…) |
| `CATEGORIE` distinctes | **108** |
| `RAYON FOURNISSEUR` distincts | **182**, dont **4 398 lignes vides** |
| `OBSERVATIONS` non vides | 891 |

**Conséquence décisive pour M2-bis** : le fichier ne contient **aucune caractéristique
technique, aucune garantie, aucun lien de notice**. `OBSERVATIONS` est du texte libre
commercial — « NOUVEAUTE » (653), « ARRET » (111), « Port en sus sur devis » (43),
« REMPLACE PAR <sku> » (une dizaine). Ce n'est pas une fiche technique.

M2-bis crée donc des champs **que seule la saisie manuelle remplira**. Il est dimensionné
en conséquence : petit, et l'écran d'administration compte autant que l'affichage.

**Conséquence pour M4** : à l'inverse, la marque est **bien fournie** — 79 marques sur
77 % des lignes. La détection par le nom des produits, prévue au plan initial, devient un
complément et non la source.

---

## 2. Les lots, leur ordre, leurs dépendances

> **Révisé le 5 novembre 2026.** Le catalogue n'est plus le premier chantier : la feuille de
> route arrêtée ce jour place **le stockage privé devant tout**, puis les catégories et l'import,
> puis Stripe en live, puis les maquettes. Voir
> `docs/analyse_stockage_prive_et_feuille_de_route.md`, qui fait foi pour l'ordre global ; ce
> plan-ci reste la référence du **contenu** des lots catalogue.
>
> ```
> 1. Stockage privé  ─►  2. Catégories + import  ─►  3. Stripe live  ─►  4. Maquettes
>    (SP, 10,5 j)          (M0, CAT, PIL, IMP : 10–12 j)  (ST, 15,5 j)     (M1→L : 19,5 j)
> ```

| Lot | Objet | Dépend de | Jours | JS |
|---|---|---|---|---|
| **M0** | Cadrage, analyse seule | rien | 1 | 0 |
| **CAT** | Chantier catégories — **chemin critique** | ton arbitrage | 6–8 | 10 Ko |
| **PIL** | Import pilote LCM — **en attente** | CAT + marge fixée | 0,5 | 0 |
| **IMP** | **Import complet LCM, 7 344 lignes** | PIL vert + décisions 7, 8, 9 | 1,5 | 0 |
| **M1** | En-tête et barre de recherche | M0 | 1,5 | 10 Ko |
| **M2** | Fiche produit | M1 | 4 | 40 Ko |
| **M3a** | Méga-menu, page Catalogue | **CAT** | 3 | 25 Ko |
| **M3b** | Famille, rayon, panneau de filtres | **CAT** | 4 | 30 Ko |
| **M4** | Marque | M3b (utile), CAT (filtre) | 2,5 | 12 Ko |
| **M2-bis** | Caractéristiques, documents, garantie | M2, **SP‑1** | 3 | 25 Ko |
| **L** | Délais de livraison par zone | tes délais | 1,5 | 6 Ko |

**Total : 29 à 31 jours, 158 Ko de JS.** Réserve attribuable : **25 Ko**.

**Trois changements par rapport à la version du 28 octobre**, chacun motivé :

- **`M3` est coupé en `M3a` et `M3b`.** Sept écrans, 7 jours et 55 Ko dans un lot unique, c'est
  un lot dont la première capture arrive au septième jour. La coupure suit l'entrée dans le
  catalogue d'un côté, la navigation dans l'arbre de l'autre — et sépare aussi deux niveaux de
  dépendance à `CAT` : `M3a` n'a besoin que de la route d'arbre, `M3b` du filtrage par
  descendance.
- **`IMP` sort de `PIL`.** Un pilote de 50 lignes et un import de 7 344 ne posent pas les mêmes
  questions : le second se mesure en durée de transaction, en volume d'images manquantes et en
  produits à désactiver.
- **`M2-bis` dépend de `SP‑1`.** Il dépose des documents produit — notices, certificats de
  conformité — et tous ne sont pas publics : un certificat porte parfois des coordonnées
  fournisseur. Il dépose donc par `StockageService`, qui n'existe qu'après le lot `SP‑1` du
  chantier stockage.

### Le chemin critique, en une phrase

**CAT bloque M3a, M3b, M4 et l'import.** M1 et M2 n'en dépendent pas et peuvent avancer en
parallèle, y compris pendant l'attente de ton arbitrage. La première production attendue est
donc le **fichier d'arbitrage** — après quoi c'est toi qui bloques, pas la session.

```
SP-1 ──────────────────────────────────┐
                                       ▼
M0 ──┬── CAT ──┬── M3a ── M3b ── M4    │
     │         ├── PIL ── IMP          │
     │         └──────────────┐        │
     └── M1 ── M2 ────────────┴── M2-bis
                  L (indépendant, dès que les délais sont fournis)
```

---

## 3. Le budget de poids, réparti d'avance

**Mesuré le 5 novembre 2026 sur `main` (`40d7ef3`) : 1 289 Ko produits, pour un plafond de
charte de 1 500 Ko — soit 211 Ko disponibles.** Le budget du **catalogue** est de **183 Ko** ;
les **28 Ko** restants sont le budget du **chantier brochures** (`BR‑4` 22 Ko + `BR‑5` 6 Ko), et
non une réserve. `211 = 183 + 28`. Chaque lot connaît son plafond **avant** de commencer, pour
qu'aucun lot ne découvre à son ouverture qu'il ne reste rien.

| Lot | Plafond | Ce qu'il paie |
|---|---|---|
| CAT | 10 Ko | écran d'administration de l'arbre (glisser-déposer exclu, voir périmètre) |
| M1 | 10 Ko | sélecteur de catégorie, formulaire de recherche |
| M2 | 40 Ko | galerie à balayer, onglets accessibles, barre d'achat fixe, sélecteur de pays |
| M3a | 25 Ko | méga-menu (chargé à la demande), page Catalogue et sa version mobile |
| M3b | 30 Ko | pages famille et rayon, leurs versions mobiles, panneau de filtres mobile |
| M4 | 12 Ko | filtre marque, logos |
| M2-bis | 25 Ko | éditeur clé-valeur et dépôt de documents dans l'administration |
| L | 6 Ko | affichage du délai, saisie dans l'écran des tarifs |
| ST‑1, ST‑4 | 4 Ko | statut de commande rendu au client, bouton de téléchargement de facture |
| BR‑4, BR‑5 | 28 Ko | chantier brochures — hors budget catalogue, voir § B.6 bis de l'analyse stockage |
| **Réserve attribuable** | **21 Ko** | imprévus, et de quoi refuser un dépassement sans bloquer un lot |

**Règles de tenue du budget.** Un lot qui dépasse son plafond ne prend pas dans la réserve de
lui-même : il s'arrête et le signale. Un lot qui finit sous son plafond rend la différence à
la réserve, et l'écrit dans son rapport. Le poids se **mesure** (`npm run build`), jamais ne
s'estime au moment du rapport.

**Si le budget craque malgré tout**, dans cet ordre : sortir le méga-menu en module chargé au
premier survol (déjà prévu), remplacer la galerie à balayer par un défilement natif en CSS
(`scroll-snap`, zéro Ko de JS), reporter les logos de marque. Augmenter `plafondJsKo` n'est
pas une option — c'est le garde-fou, pas un paramètre.

---

## 4. Les lots en détail

### M0 — cadrage (aucun code)

**Objectif.** Produire `docs/analyse_integration_maquette.md` : l'inventaire écran par écran,
les dépendances de données, la liste des éléments à masquer, et les trois vérifications en
lecture seule ci-dessous.

**Exclu.** Toute branche de code, toute migration, toute écriture.

**Livre aussi, en lecture seule sur la production :**
1. la requête des visuels factices que le navigateur ne reconnaît pas (§ 6) ;
2. le dénombrement des produits par catégorie, pour dimensionner CAT ;
3. la vérification que `categoryId` est bien le seul paramètre de filtrage par catégorie.

**Critères d'acceptation.** Les 39 textes entre crochets sont listés avec, pour chacun, la
donnée qui l'alimenterait et son existence en base. Aucune donnée inventée, aucun « sans
doute ».

---

### CAT — chantier catégories (chemin critique)

**Objectif.** Un arbre à trois niveaux en base, **une seule source**, servi par **une seule
route publique mise en cache**, et des produits qui remontent correctement à chaque niveau.

**Périmètre, dans cet ordre :**

1. **Le fichier d'arbitrage** `docs/arborescence_catalogue_v2.xlsx`, tel que décrit le
   27 octobre. Cinq onglets : l'arbre cible (3 niveaux), nos **211** catégories avec leur
   rattachement proposé, les **108** catégories de La Centrale Médicale, les **182** rayons
   fournisseur, et la colonne **Nature (produit / prestation)**. Chaque ligne porte une
   proposition ; aucune n'est appliquée sans ton accord. **Aucune écriture en base à cette
   étape.**
2. **Migration** (prochain numéro libre à la fusion, **V82** aujourd'hui) : `parent_id` existe
   déjà, on ajoute l'**espace** (`ETABLISSEMENTS` / `DOMICILE`) sur les familles de niveau 1,
   un `image_url` de catégorie, un ordre d'affichage, et un indicateur d'activité.
3. **Le filtrage par descendance** dans `ProductSpecification.withCategoryId` : aujourd'hui
   une égalité stricte, qui renverrait **zéro produit** sur toute page famille. C'est le
   travail caché le plus important du chantier. Tests sur les trois niveaux.
4. **Les redirections 301** des **211** adresses de rayon indexées vers leur nouvelle adresse,
   établies depuis le fichier d'arbitrage.
5. **La route publique de l'arbre**, mise en cache, qui **élague les nœuds sans produit actif**,
   et l'invalidation du cache quand l'arbre change dans l'administration.
6. **L'écran d'administration de l'arbre** : créer, renommer, rattacher, poser une image.

**Exclu explicitement.** Le glisser-déposer dans l'écran d'administration (coûteux en JS,
remplaçable par un sélecteur de parent). La fusion automatique des doublons — elle se décide
ligne par ligne dans le fichier d'arbitrage. Toute suppression de catégorie portant des
produits.

**Fichiers touchés.** `Category.java`, `CategoryRepository`, `CategoryResponseDto`,
`ProductSpecification.java`, `CatalogResource.java`, `CatalogService.java`, une migration, un
écran d'administration, et la configuration des redirections.

**Risques.** (1) Une catégorie renommée casse une adresse indexée — d'où les 301, qui sont
dans le périmètre et non « prévues plus tard ». (2) Le filtrage par descendance touche la
requête de tous les rayons : une erreur y est visible partout. (3) Les 57 catégories sans
produit et les 10 groupes de doublons demandent **tes** décisions, pas les miennes.

**Critères d'acceptation vérifiables.**
- Une page famille affiche les produits de **tous** ses descendants ; un test le prouve sur
  trois niveaux.
- La route de l'arbre ne renvoie **aucun nœud vide**.
- Les 211 anciennes adresses répondent **301** vers une page qui existe — vérifié par appel
  réel, pas par lecture du code.
- Le cache s'invalide quand l'arbre change dans l'administration ; un test le prouve.
- Aucun arbre écrit en dur nulle part dans le code.

---

### PIL — import pilote La Centrale Médicale : **en attente**

**État.** Le fichier et sa notice sont prêts et fusionnés (`docs/import_pilote_lcm.xlsx`,
`docs/import_pilote_lcm.md`) : 20 lignes réelles, plus une 21ᵉ volontairement fautive en
position 11 (`PILOTE-FAUTIF-001`, quantité −5).

**Deux conditions avant lancement**, aucune négociable :
1. l'arbre des catégories en base (CAT), sans quoi les 108 catégories du fournisseur créent
   108 racines de plus ;
2. **la marge fournisseur fixée** — sans marge paramétrée, le prix d'achat part en boutique
   tel quel. L'aperçu le signale déjà (« aucune marge définie… prix d'achat porté tel quel »).

**Écart assumé, à connaître.** Le fichier réel n'a **pas** de colonne `STOCK` ; j'en ai ajouté
une au pilote pour que les mouvements soient vérifiables. L'import complet créera donc
7 344 produits **à stock 0, sans aucun mouvement** — sans danger depuis le lot A, puisqu'un
produit `FOURNISSEUR` n'est ni vérifié ni décrémenté, mais à savoir avant de lire le rapport.

---

### M1 — en-tête et barre de recherche

**Objectif.** La barre de recherche en trois parties de la maquette, sur ordinateur et
téléphone.

**Périmètre.** Sélecteur de catégorie (« Toutes catégories » par défaut, présélectionné sur
une page rayon), champ, bouton sombre à loupe. Sur téléphone : champ + bouton loupe seule
(`aria-label`), **hauteur 48 px, police 16 px** — sinon iPhone zoome tout seul. Formulaire
`role="search"`, soumission au clavier.

**Bonne nouvelle, contre le plan initial.** `categoryId` **existe déjà** sur la route publique
(`CatalogResource.java:51`). **Aucun paramètre à ajouter côté serveur.**

**Exclu.** Les suggestions en cours de frappe — rien ne les fournit aujourd'hui ; notées pour
M3, pas codées. L'ordre de `NAVIGATION_PRINCIPALE`, inchangé.

**Dépendance à CAT, à dire franchement.** Tant que les 211 catégories sont toutes racines, le
sélecteur liste 211 entrées plates. Il devient utile avec CAT, **sans changement de code** :
il lit la même route.

**Fichiers touchés.** `Navbar.tsx`, `MenuMobile.tsx`, `catalogService.ts`. Aucune migration.

**Critères d'acceptation.** Soumission avec et sans catégorie, état dans l'URL, présélection
sur une page rayon, libellés accessibles, aucune régression de `MenuMobile`. Poids ≤ 8 Ko.

---

### M2 — fiche produit

**Objectif.** Faire **évoluer** `ProductDetailPage` et `ProductGallery`, sans les réécrire.

**Périmètre.** Fil d'Ariane ; galerie (grande image, agrandissement, miniatures, vignette
vidéo **si** le produit a une vidéo — `videoUrl` existe) ; bloc d'achat (badges, titre, réf.,
prix, prix barré si campagne active, quantité, panier, achat immédiat, lien devis) ;
produit sur devis sans prix ni panier ; **badge de stock selon `mode_stock` uniquement, aucun
badge pour `NON_SUIVI` ni `FOURNISSEUR`** ; onglets accessibles ; produits similaires (5 max,
visuel d'abord) ; sur téléphone, galerie à balayer, sections dépliables, barre d'achat fixe
qui ne masque rien.

**Trois corrections de la maquette, mesurées :**

1. **La mention DAP n'est pas « hors Union européenne ».** Le code dit **hors de France**
   (`adminShippingService.ts:22`, `TermsPage.tsx:108`), et le serveur porte déjà le verdict
   dans `EstimationDto.droitsALArrivee`. La fiche **lit ce booléen** et ne recalcule jamais une
   appartenance à l'UE côté navigateur. C'est un texte contractuel.
2. **Le délai de livraison n'existe pas** dans `EstimationDto` (zone, libellé, montant,
   offerte, droits à l'arrivée — pas de délai). Il est **masqué dans M2** et fait l'objet du
   lot L. Les frais, eux, s'affichent.
3. **Le texte DAP est déjà écrit en trois endroits** (`CheckoutPage`, `AdminShippingPage`,
   `TermsPage`). M2 en ferait un quatrième : il est **centralisé en un seul endroit**, les
   trois existants s'y branchent.

**Le WhatsApp entre dans M2** : une ligne dans `parametres_plateforme` (la table et
`ServiceParametres` existent depuis V70), **jamais en dur**, bouton masqué si le paramètre est
vide. Aucune migration.

**Éléments masqués faute de donnée, à lister dans le rapport :** garantie fabricant, onglet
Caractéristiques, onglet Documents, délai de livraison. Quatre éléments de la maquette qui ne
s'afficheront pas — c'est voulu, et c'est l'objet de M2-bis et de L.

**Exclu, sans discussion.** Le calcul des prix, la TVA, le panier, le paiement. Tout texte
entre crochets. Toute donnée inventée.

**Risques.** La barre d'achat fixe masque le bas de page si l'espace réservé est oublié. La
galerie à balayer est le poste de poids le plus lourd du lot — si elle déborde, défilement
natif `scroll-snap`.

**Critères d'acceptation.** Produit avec et sans remise, sur devis, avec et sans vidéo, pays
France et hors France (encadré DAP), onglets au clavier (flèches, Entrée, Échap qui rend le
focus), cibles de 44 px sur téléphone. **Non-régression** : ajout au panier depuis la fiche,
puis panier, puis écran de paiement, sans payer. Captures avant/après sur **trois fiches
réelles** — un produit en promotion, un produit sur devis, un produit à visuel factice — en
1 440 px et 375 px. Poids ≤ 35 Ko.

---

### M3 — méga-menu, Catalogue, famille, rayon

**Prérequis absolu.** CAT livré : arbre en base, espace sur les familles, filtrage par
descendance, 301 en place. **Ce lot ne s'ouvre pas avant.**

**Périmètre.** Méga-menu en trois colonnes au survol **ou au focus** de « Catalogue », avec
survol intentionnel (ouverture ~150 ms, fermeture ~250 ms, tolérance en diagonale), clavier
complet, fermeture au clic extérieur et au changement de page, **chargé à la demande**. Sur
téléphone, `MenuMobile` lit **la même source**, par écrans successifs avec un bouton Retour.
Page Catalogue, page famille, page rayon avec étiquettes de types voisins, tri, filtres
existants, filtres actifs, état dans l'URL, et panneau de filtres plein écran sur téléphone.

**`CatalogPage.tsx` est remplacée.** À conserver explicitement : le branchement sur
`/catalog?promo=true` (l'entrée « Promotions » de `NAVIGATION_PRINCIPALE` en dépend), le
filtrage côté serveur et l'état dans l'URL de `lib/filtresRayon.ts`, le composant de carte
produit, la règle des visuels factices, et le chargement différé de la route. Ce qui disparaît :
la mise en page actuelle, et elle seule.

**Exclu.** Les suggestions de recherche en cours de frappe (notées en M1, toujours hors
périmètre faute de source). Les visuels de familles sans image : **icône de la famille, jamais
de photo factice**.

**Risques.** C'est le lot le plus lourd des trois dimensions — jours, poids, surface touchée.
D'où la **livraison en deux temps** : méga-menu et page Catalogue d'abord, famille et rayon
ensuite, chacun avec ton accord.

**Critères d'acceptation.** Parcours réel Catalogue → famille → rayon → fiche → Retour, vérifié
au navigateur. Aucun nœud vide affiché. Le méga-menu s'ouvre au clavier seul. Les 301
fonctionnent avec les nouvelles pages. Poids ≤ 55 Ko, méga-menu hors du paquet initial.

---

### M4 — marque

**Objectif.** La marque comme **entité**, pas comme texte libre.

**Périmètre.** Table des marques (slug `[a-z0-9-]`, logo facultatif), migration au numéro libre
à la fusion. Affichage « Marque : … » sur la fiche. Filtre marque dans les rayons, côté
serveur, avec compteurs, masqué s'il n'y a aucune marque. Recherche par marque.

**La séparation marque / fournisseur.** `FichierCatalogue.java:52` confond aujourd'hui les
deux :

```java
"marque", List.of("marque", "fournisseur", "fabricant", "brand"),
```

La Centrale Médicale est un **fournisseur** ; Microlife est une **marque**. M4 défait cette
confusion : la colonne `MARQUE` alimente la marque, le fournisseur reste le fournisseur. À
noter : la valeur lue n'est **utilisée nulle part aujourd'hui** — reconnue, puis jetée.

**Reprise de l'existant.** Le fichier fournisseur donne **79 marques sur 77 % des lignes** :
c'est la source, et elle est bonne. La détection par le nom des produits ne sert que pour les
**1 697 lignes sans marque** et pour nos produits existants. Elle se livre en fichier à valider
(`docs/marques_proposees.xlsx`), **aucune écriture en base avant ton accord**.

**Import.** Marque inconnue **signalée dans l'aperçu**, jamais créée automatiquement.

**Critères d'acceptation.** Le filtre compte juste sur les trois niveaux de l'arbre (donc après
CAT). Une marque inconnue apparaît nommée dans l'aperçu d'import. Poids ≤ 10 Ko.

---

### M2-bis — caractéristiques, documents, garantie

**Dimensionné par la mesure, pas par la maquette.** Le fichier fournisseur n'apporte **rien**
pour ces trois champs : ils seront remplis **à la main**, produit par produit. L'écran de
saisie compte donc autant que l'affichage, et le lot reste petit — on ne construit pas une
cathédrale pour des champs que personne ne remplira en masse.

**Périmètre.**
- **Caractéristiques** : liste clé-valeur ordonnée, par produit. Onglet masqué si vide.
- **Documents** : notice, déclaration de conformité. **Fichiers publics**, servis sans
  authentification — à **distinguer explicitement** du stockage privé des pièces des candidats
  « Exercer en France », qui ne doit jamais partager ce chemin. Onglet masqué si aucun document.
- **Garantie** : une **durée** (en mois), affichée « Garantie fabricant : 24 mois ». Masquée si
  vide.
- Écran d'administration pour les trois, et dépôt de documents avec **vérification de la
  signature du fichier** — pas seulement de l'extension.

**Exclu.** L'import en masse de ces champs (aucune source). Les documents privés ou liés à un
client. Toute réutilisation du stockage des pièces EVC.

**Risques.** Le dépôt de fichiers est une surface d'attaque : c'est la **quinzième** route
d'envoi du projet, et la sécurisation par signature de fichier est une dette déjà identifiée
(route `/partnership/requests` et 13 autres). M2-bis ne doit pas en ajouter une non
sécurisée — la vérification de signature est **dans** le périmètre.

**Critères d'acceptation.** Un produit sans aucun des trois champs affiche la fiche de M2 à
l'identique (non-régression). Un document déposé est joignable publiquement, un fichier au
contenu non conforme à son extension est **refusé**. Poids ≤ 20 Ko.

---

### L — délais de livraison par zone

**Objectif.** Afficher un délai **réel**, parce qu'aujourd'hui aucun délai n'existe et qu'un
délai inventé est une promesse fausse.

**Périmètre.** Migration : un délai par zone sur la grille des tarifs (une borne basse et une
borne haute en jours ouvrés). Saisie dans l'écran des tarifs de livraison existant. Ajout du
délai à `EstimationDto`. Affichage sur la fiche produit **et** au panier, en un seul endroit
dans le code.

**Dépend de toi.** Les délais par zone. Le lot ne s'ouvre pas sans eux — il n'y a pas de valeur
par défaut raisonnable.

**Pourquoi ce lot existe.** Pour un client en Afrique, le délai est l'information décisive, au
même rang que le prix. Le masquer en M2 était la seule option honnête ; le laisser masqué
durablement serait un manque.

**Critères d'acceptation.** Une zone sans délai saisi n'affiche **rien** (et non « 0 jour »).
Le délai affiché sur la fiche est celui que renvoie `/shipping/estimation`, vérifié par appel
réel. Poids ≤ 5 Ko.

---

## 5. Ce qui passe avant les vrais paiements

À dire clairement : **la maquette ne remplace rien de ce qui suit.** Ces huit chantiers
conditionnent l'ouverture des paiements réels, indépendamment de l'avancement du catalogue. Ils
sont détaillés, avec leurs dépendances croisées, au § B.4 de
`docs/analyse_stockage_prive_et_feuille_de_route.md` ; voici leur forme courte.

| Lot | Chantier | Pourquoi il bloque | Jours |
|---|---|---|---|
| **ST‑0** | Neutralisation des commandes de test | des ventes fictives en base rendent le premier relevé de chiffre d'affaires illisible | 1 |
| **ST‑1** | Cycle de vie des commandes | `OrderStatus` n'a ni `PAID` ni `DELIVERED` ; une commande payée reste `PENDING` ; aucune règle de transition ; le client ne voit jamais `status` | 3 |
| **ST‑2** | Lot B du stock | pas de remise en stock à l'annulation ni au remboursement ; `paid_at` / `shipped_at` absents ; aucun journal des ajustements manuels | 2,5 |
| **ST‑3** | Test de `charge.refunded` | **le code existe et est complet** ; c'est le test de bout en bout qui manque | 0,5 |
| **ST‑4** | Factures et avoirs | aucune facture n'existe, seulement des reçus `REC-` ; pas d'avoir. **Dépend de `SP‑1`** : une facture nominative ne se dépose pas dans un stockage public | 4 |
| **ST‑5** | TVA | `vatRate` existe par catégorie et par produit, mais rien ne tient compte de la destination. **Aucun pays de l'UE hors France n'est livré** : France au taux du produit, reste **exonéré à l'export** avec mention et justificatif de sortie | 2 |
| **ST‑6** | Acompte d'inscription | le code est correct de bout en bout ; le blocage est opérationnel. Puis **confirmation manuelle par l'admin, vérifiée auprès de Stripe** — l'admin ne déclare pas « payé », il demande au serveur de relire le `PaymentIntent` | 2 |
| **ST‑7** | Passage en live | clés live sur Render et Vercel, **nouveau** point d'entrée de webhook live avec son propre secret, puis un paiement réel de faible montant et son remboursement | 0,5 |

**Calendrier global, dans l'ordre arrêté le 5 novembre 2026 :**

**stockage privé (SP)** → **CAT** (en parallèle : M1, M2) → **PIL** → **ST‑0 à ST‑7** → **IMP**
→ **M3a, M3b** → **M4, M2-bis, L**.

Le catalogue et les paiements ne se croisent qu'à deux endroits, et il faut les tenir : `ST‑4`
attend `SP‑1`, et `M2-bis` aussi. Pour le reste, un catalogue de 7 344 produits sans cycle de
vie des commandes ni facturation n'est pas vendable — d'où l'import complet **après** Stripe.

---

## 5 bis. Les dix écrans des maquettes, et le lot qui livre chacun

« Maquettes » désigne exactement les **dix fichiers** de
`docs/maquette_catalogue_optimisante/maquette_catalogue/`. **Aucune autre page n'est
concernée** — ni l'accueil, ni le panier, ni le tableau de bord.

| # | Écran | Fichier | Lot |
|---|---|---|---|
| 1 | Page Catalogue — ordinateur | `Main.dc.html` | **M3a** |
| 2 | Méga-menu « Catalogue » | `MegaMenu.dc.html` | **M3a** |
| 3 | Catalogue — mobile | `CatalogueMobile.dc.html` | **M3a** |
| 4 | Page famille — ordinateur | `Famille.dc.html` | **M3b** |
| 5 | Page rayon — ordinateur | `Rayon.dc.html` | **M3b** |
| 6 | Famille — mobile | `FamilleMobile.dc.html` | **M3b** |
| 7 | Rayon — mobile | `RayonMobile.dc.html` | **M3b** |
| 8 | Panneau « Filtrer » — mobile | `FiltresMobile.dc.html` | **M3b** |
| 9 | Fiche produit — ordinateur | `Produit.dc.html` | **M2** |
| 10 | Fiche produit — mobile | `ProduitMobile.dc.html` | **M2** |

**Aucun écran n'est orphelin**, et chacun n'est attribué qu'à un seul lot.

**Mais quatre lots ne livrent aucun écran, et c'est à savoir pour les vérifier.** `M1`, `M4`,
`M2-bis` et `L` ne correspondent à aucun des dix fichiers : ce sont des bandes et des blocs
**à l'intérieur** d'écrans livrés par `M2`, `M3a` et `M3b`.

| Lot | Ce qu'il livre, et sur quels écrans |
|---|---|
| **M1** | l'en-tête et la barre de recherche — présentes sur les **six** écrans de M3a et M3b |
| **M4** | le filtre marque sur Famille et Rayon, le libellé de marque sur la fiche produit |
| **M2-bis** | les onglets caractéristiques, documents et garantie **dans** la fiche produit |
| **L** | la ligne de délai de livraison **dans** la fiche produit |

**Conséquence sur le rapport de fin de lot** (§ 7) : pour ces quatre lots, la vérification réelle
se fait sur les **captures des écrans de `M2`, `M3a` et `M3b`**, pas sur une page à eux. Un
rapport pour `M1` qui montrerait une page isolée ne prouverait rien. Les quatre prompts
correspondants doivent le dire.

---

## 6. Les visuels factices : deux listes, une seule devrait exister

**Le constat.** Le serveur connaît **six motifs** (`VisuelProduit.MOTIFS` : `medical-placeholder`,
`prod_fallback_`, `unsplash`, `default-medical-equipment`, `stetho.jpg`, `microscope.jpg`). Le
navigateur connaît **quatre URL exactes** (`ProductImage.KNOWN_BROKEN_URLS`). Le test de parité
existant (`VisuelFacticeTest.memeRegleDesDeuxCotes`) compare **SQL et Java** — pas le
navigateur.

**Donc** un produit dont l'image contient `unsplash` ou `prod_fallback_` est traité comme
factice par le serveur et comme **une vraie photo** par le navigateur. Je ne sais pas si un tel
produit existe encore après V71 : **la requête ci-dessous le dira**, et c'est à faire en M0,
pas à supposer.

### La requête, en lecture seule

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

Zéro ligne : la divergence est théorique, et la correction reste souhaitable sans être urgente.
Une ligne ou plus : autant de fiches qui affichent une photo factice comme une vraie photo, et
la correction entre dans M2.

### La source unique proposée

Le navigateur **cesse de connaître** la liste. Le serveur, qui la connaît déjà, expose un
booléen `visuelFactice` sur chaque produit, calculé par `VisuelProduit.estFactice`.
`ProductImage` lit ce booléen et supprime `KNOWN_BROKEN_URLS`.

Trois raisons, dans l'ordre d'importance :
1. **Un motif ajouté au serveur s'applique aussitôt au navigateur.** C'est exactement la
   divergence constatée qui disparaît.
2. **Le test de parité devient complet** : il n'y a plus que deux côtés (SQL et Java) au lieu de
   trois, et le troisième était celui que personne ne testait.
3. **Le navigateur allège** : quatre URL en dur disparaissent du paquet.

Le repli par `onError` reste en place — il couvre une image réellement cassée, ce qu'aucune
liste ne peut prévoir. Cette correction est **petite et à faible risque** : elle se greffe sur
M2, qui touche déjà la galerie, plutôt que de devenir un lot.

---

## 7. Ce que montre le rapport de fin de lot

Repris de la section 7 de D0, et non négociable pour qu'un lot soit considéré comme livré :

1. **Build propre** — `npm run build`, garde-fou de charte inclus, **et le poids JS obtenu**,
   comparé au plafond du lot (§ 3).
2. **Suite complète verte**, backend et frontend, **avec le nombre de tests ignorés**. Sans
   Docker les tests d'intégration sont ignorés, pas en échec : une suite verte peut n'avoir
   rien exécuté. **Un rapport sans ce chiffre est invalide.**
3. **Vérification réelle** au navigateur, par émulation d'écran (`setDeviceMetricsOverride`,
   cache désactivé), captures **avant et après en 1 440 px et 375 px**. Une fenêtre
   `--window-size=375` sur Windows ne descend pas sous la largeur minimale de Chrome : la
   capture est rognée, pas émulée, et ne prouve rien.
4. **Ce qui n'a pas été vérifié**, dit explicitement. Un échec est rapporté avec sa sortie.
5. **Les éléments masqués** faute de donnée, et **les écarts assumés** avec la maquette.
6. **Le poids rendu à la réserve**, ou le dépassement signalé — jamais absorbé en silence.

---

## 8. Les décisions qui te restent

Chacune avec ma recommandation. Les trois premières bloquent un lot.

| # | Décision | Bloque | Ma recommandation |
|---|---|---|---|
| 1 | **L'arbre cible** : 3 niveaux, rattachement des 211 catégories, espace de chaque famille | CAT, donc tout | Je produis le fichier d'arbitrage ; tu tranches ligne par ligne. Rien ne part en base avant. |
| 2 | **La marge fournisseur** | PIL, import complet | **Par catégorie de l'arbre cible**, pas par fournisseur : la marge d'un tensiomètre n'est pas celle d'une compresse, et `Category.marginRate` existe déjà et prime sur le fournisseur. |
| 3 | **Le numéro WhatsApp** | M2 (bouton masqué sans lui) | Un numéro professionnel dédié, posé en paramètre. S'il n'y en a pas encore, M2 se livre sans — le bouton reste masqué, sans dette. |
| 4 | **Les délais par zone** | L | Deux bornes en jours ouvrés par zone, pas une valeur unique : un délai unique est faux la moitié du temps et abîme la confiance. |
| 5 | **Les 57 catégories sans produit** | CAT | Les **désactiver**, ne pas les supprimer : une suppression casse une adresse indexée, une désactivation la laisse redirigeable. |
| 6 | **Les 10 groupes de doublons**, dont « électro**p**cardiographes » | CAT | Fusionner, en gardant le libellé correct et en **redirigeant 301** l'autre. La coquille porte un produit : il suit la fusion. |
| 7 | **Les 111 produits « ARRET »** du fichier LCM | import complet | **Ne pas les importer.** Un produit arrêté qu'on peut commander est une commande qu'on ne peut pas livrer. Les « REMPLACE PAR » désignent le remplaçant : à importer, lui. |
| 8 | **Les 43 « Port en sus sur devis »** | import complet | Les importer **sur devis** (`is_quote_only`), sans prix affiché : leur prix n'est pas connu à l'avance. |
| 9 | **Les 4 398 lignes sans rayon fournisseur** | CAT, import complet | Les rattacher par leur colonne `CATEGORIE` (108 valeurs), et laisser en attente celles qui n'en ont pas non plus — plutôt qu'un rayon « Divers » qui deviendrait le plus gros du catalogue. |
| 10 | **La garantie : durée seule ou texte libre** | M2-bis | **Durée en mois seule.** Un texte libre devient un fourre-tout commercial non comparable, et c'est la comparaison qui intéresse un acheteur d'hôpital. |

---

## 9. Journal des écarts avec le plan initial

Pour mémoire, et pour qu'aucune session ne retombe dans l'un d'eux.

| Point du plan initial | Ce que dit le code |
|---|---|
| Maquettes dans `docs/maquette_catalogue/` | **Faux chemin** : `docs/maquette_catalogue_optimisante/maquette_catalogue/`. Les cinq prompts étaient concernés. |
| « 1 290 Ko aujourd'hui » | **Mon erreur, et le plan avait raison.** J'avais lu 1 333 Ko dans un commentaire périmé de `charte-reference.json` au lieu de mesurer. La mesure donne **1 289 Ko** — soit 211 Ko de marge, pas 167. Corrigé au § 3. Le commentaire `_plafondJsKo` du fichier de référence reste faux et est à corriger au premier lot qui touche au frontend. |
| « Si l'API ne filtre pas par catégorie, ajoute le paramètre » | `categoryId` **existe déjà**. Rien à ajouter. |
| « Encadré DAP si le pays est hors UE » | Le code dit **hors de France**, et le serveur porte `droitsALArrivee`. |
| « Le sélecteur affiche le délai et les frais réels » | **Aucun délai n'existe** dans `EstimationDto`. D'où le lot L. |
| « Garantie seulement si la donnée existe » | **Aucun champ garantie.** Masqué en permanence sans M2-bis. |
| Filtrage par catégorie | **Égalité stricte** : zéro produit sur toute page famille dès que l'arbre existe. Jamais mentionné. |
| M4 « colonne marque reconnue » | Déjà reconnue, **valeur jetée**, et confondue avec le fournisseur. |
| `CatalogPage.tsx` | Existe, sert `/catalog`, **remplacée par M3** sans que le plan le dise. |
| Redirections 301 | « Prévues au chantier catégories » — **n'existent pas**, 211 adresses indexées. |
| Nombre de tests ignorés | **Demandé dans aucun prompt**, alors que la règle 6 du dépôt l'exige. |

### Écarts relevés le 5 novembre 2026, à la révision de l'ordre

| Point | Ce que dit le code |
|---|---|
| `M3` en un seul lot | Il portait **sept écrans sur dix**, 7 jours et 55 Ko : première capture au septième jour. Coupé en `M3a` / `M3b`. |
| `M1`, `M4`, `M2-bis`, `L` | Ils ne livrent **aucun** des dix écrans. Leur vérification passe par les captures d'autres lots — ce qu'aucun prompt ne disait (§ 5 bis). |
| Import complet LCM | Il était **fondu dans `PIL`**. Il en sort : `IMP`, 1,5 jour. |
| Documents produit de `M2-bis` | Ils ne sont **pas tous publics**. `M2-bis` dépose par `StockageService` et dépend donc de `SP‑1`. |
| « TVA par pays de livraison » | **Aucun pays de l'UE hors France n'est livré** (`ZoneLivraison` : France, Maghreb, Afrique de l'Ouest et centrale, Afrique de l'Est et australe). Pas de guichet unique, pas de vingt-sept taux : France au taux du produit, reste **exonéré à l'export**. `ST‑5` passe de 5 jours à 2. |
| « Webhook de remboursement à écrire » | `PaymentWebhookResource.traiterRemboursement` → `StripeRefundHandler` **existe et est complet**. Ce qui manque est le **test**. |
| Prompts `M3`, `PIL` | `docs/prompts_catalogue/M3_navigation_catalogue.md` couvre désormais deux lots et doit être scindé ; `PIL_import_pilote_lcm.md` affirme à tort que `import/pilote-lcm` est « fusionné dans `main` » — la branche (`65da82a`) a été vérifiée documentaire mais **jamais fusionnée**. |
| Ce plan vit sur `main` | **Non** : il n'a jamais été fusionné et vit sur `docs/dette-catalogue`, comme D0 et les prompts. Trois branches documentaires attendent une décision de fusion : celle-ci, `docs/doc-priv` et `docs/backlog-securite`. |
