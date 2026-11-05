# M3 — méga-menu, Catalogue, famille, rayon

**Appliquer D0**, puis `docs/plan_integration_catalogue.md` § 4 « M3 ». Contradiction :
arrête-toi et dis-le.

**Prérequis absolu, à vérifier avant la première ligne de code** : CAT est livré — arbre à
3 niveaux en base, `espace` sur les familles de niveau 1, **filtrage par descendance** en
place, 301 fonctionnelles, route de l'arbre en cache. Si l'un manque, **ce lot ne s'ouvre
pas** : dis-le et arrête-toi.

Branche `feature/maquette-m3-catalogue`. Références :
`docs/maquette_catalogue_optimisante/maquette_catalogue/` — `MegaMenu`, `Main`, `Famille`,
`Rayon`, et `CatalogueMobile`, `FamilleMobile`, `RayonMobile`, `FiltresMobile`.

**Une seule source : l'arbre en base**, servi par la route publique de CAT. **Aucun arbre
écrit en dur.** Le menu déroulant actuel de « Catalogue » est remplacé par cette source.

## 1. MÉGA-MENU (ordinateur), au survol **ou au focus** de « Catalogue »

- colonne 1 : familles regroupées sous « Établissements de santé » et « Domicile & officine »,
  avec vignette ;
- colonne 2 : « Découvrir la page {famille} », puis les sous-catégories ;
- colonne 3 : les types.

Survol avec intention : ouverture ~150 ms, fermeture ~250 ms, tolérance de trajectoire en
diagonale. Clavier complet (flèches, Entrée, Échap qui **rend le focus** à « Catalogue »).
Fermeture au clic extérieur et au changement de page. **Chargé à la demande** : il ne pèse pas
sur le paquet initial.

Sur téléphone, `MenuMobile` lit **la même source**, par écrans successifs avec un bouton
Retour. Aucune régression du tiroir existant.

## 2. PAGE CATALOGUE — `CatalogPage.tsx` est remplacée

Accès rapides (Promotions, Nouveautés, Produits sur devis, **seulement s'ils ont du
contenu**), puis les deux espaces avec leurs familles en vignettes (icône ou visuel, nom,
3 sous-catégories), une sélection de produits, et le bandeau « Vous équipez un
établissement ? Demander un devis ». Sur téléphone : grille de 2.

**À conserver de l'existant, explicitement :**
- le branchement sur `/catalog?promo=true` — l'entrée « Promotions » de
  `NAVIGATION_PRINCIPALE` en dépend, et son badge est le seul de la barre ;
- le filtrage côté serveur et l'état dans l'URL de `lib/filtresRayon.ts` ;
- le composant de carte produit, tel quel ;
- la règle des visuels factices ;
- le chargement différé de la route dans `App.tsx`.

**Ce qui disparaît : la mise en page actuelle, et elle seule.**

## 3. PAGE FAMILLE

En-tête de la famille, sous-catégories en vignettes avec leurs types en liens (en liste sur
téléphone), « incontournables » (produits de la famille, **donc de tous ses descendants** —
c'est la descendance de CAT qui le permet, visuel d'abord), familles voisines.

## 4. PAGE RAYON

Fil d'Ariane, étiquettes de navigation entre types voisins (défilement horizontal sur
téléphone), tri, **colonne de filtres existante** — le bloc Sous-catégorie apparaît désormais
tout seul —, filtres actifs, chargement continu ou bouton « Afficher plus », état dans l'URL.
Sur téléphone : bouton « Filtrer (n) » et panneau plein écran (`FiltresMobile`).

## 5. VISUELS DES FAMILLES

Champ image géré dans l'administration de l'arbre (CAT). Sans image : **l'icône de la
famille, jamais une photo factice.**

## 6. REDIRECTIONS

Les 301 de CAT fonctionnent avec les nouvelles pages — vérifié **par appel réel** sur un
échantillon nommé, pas par lecture du code.

## EXCLU

Les suggestions de recherche en cours de frappe (reportées depuis M1, toujours sans source).
Toute modification de la fiche produit (M2). Tout nœud vide affiché.

## LIVRAISON EN DEUX TEMPS

1. méga-menu et page Catalogue ;
2. page famille et page rayon.

Chacun avec accord avant déploiement. C'est le lot le plus lourd du chantier en jours, en
poids et en surface touchée : ne le livre pas d'un bloc.

## TESTS

- vitest : méga-menu (survol, intention, clavier, Échap qui rend le focus, clic extérieur),
  élagage des nœuds vides, présélection et état dans l'URL, panneau mobile, grille de 2 ;
- backend : la route de l'arbre, son cache, son invalidation à la modification ;
- **vérification réelle** du parcours Catalogue → famille → rayon → fiche → Retour.

## CRITÈRES D'ACCEPTATION

Le méga-menu s'ouvre et se parcourt **au clavier seul**. Aucun nœud vide. Le parcours complet
fonctionne au navigateur. Poids JS **≤ 55 Ko**, **méga-menu hors du paquet initial** — prouve-le
par le découpage des fichiers produits, pas par intention.

## RAPPORT

Section 7 de D0, pour chacune des deux livraisons. Captures avant/après en 1 440 px et 375 px.
