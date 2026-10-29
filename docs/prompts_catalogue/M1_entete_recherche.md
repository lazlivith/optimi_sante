# M1 — en-tête et barre de recherche

**Appliquer D0**, puis `docs/plan_integration_catalogue.md` § 4 « M1 ». Contradiction :
arrête-toi et dis-le.

Branche `feature/maquette-m1-entete`. Références :
`docs/maquette_catalogue_optimisante/maquette_catalogue/Main.dc.html` (en-tête) et
`CatalogueMobile.dc.html` (en-tête mobile).

## ORDINATEUR

- Barre de recherche en trois parties dans un même cadre : sélecteur de catégorie à gauche
  (« Toutes catégories » par défaut), champ « Rechercher un produit, une référence… », bouton
  « Rechercher » à droite (fond sombre, icône loupe).
- Le sélecteur liste les catégories qui existent **aujourd'hui** en base. Sur une page famille
  ou rayon, il est **présélectionné** sur la catégorie en cours.
- La recherche envoie sur la page de résultats existante (`/catalog`), avec la catégorie en
  paramètre et **l'état dans l'URL**.
- **`categoryId` existe déjà** sur la route publique (`CatalogResource.java:51`) : il n'y a
  **aucun paramètre à ajouter côté serveur**. Si tu constates le contraire, dis-le avant de
  coder. Rejette une valeur invalide côté serveur si ce n'est pas déjà fait, avec un test.
- Ordre de `NAVIGATION_PRINCIPALE` **inchangé**.

## MOBILE

Champ et bouton loupe sombre, icône seule avec `aria-label="Rechercher"`, **sans** le sélecteur
de catégorie. **Hauteur 48 px, police 16 px** — en dessous, iPhone zoome tout seul à la mise au
point.

## EXCLU

Les suggestions en cours de frappe : rien ne les fournit aujourd'hui. **Note-le pour M3, ne le
code pas.** Aucune modification de `MenuServices` ni de l'ordre de la navigation.

## À DIRE DANS LE RAPPORT, PAS À CORRIGER

Tant que les 211 catégories sont toutes racines, le sélecteur liste 211 entrées plates. Il
devient utile avec CAT, **sans changement de code** : il lit la même route. N'anticipe pas
l'arbre, ne le simule pas.

## TESTS

- vitest : rendu, soumission avec et sans catégorie, présélection sur une page rayon, libellés
  accessibles, soumission au clavier ;
- backend seulement si un rejet de valeur est ajouté ;
- **aucune régression de `MenuMobile`** — un test le prouve.

## CRITÈRES D'ACCEPTATION

Soumission au clavier seul, du champ au bouton. État dans l'URL après recherche. Cible de
44 px minimum sur téléphone. Poids JS **≤ 8 Ko**.

## RAPPORT

Section 7 de D0, captures avant/après en 1 440 px et 375 px, poids rendu à la réserve ou
dépassement signalé.
