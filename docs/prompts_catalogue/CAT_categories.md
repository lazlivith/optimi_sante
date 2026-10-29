# CAT — chantier catégories (chemin critique)

**Appliquer D0**, puis `docs/plan_integration_catalogue.md` § 4 « CAT ». Contradiction :
arrête-toi et dis-le.

Ce lot bloque M3, M4 et l'import La Centrale Médicale. Il se livre en **deux temps** : le
fichier d'arbitrage (sans code), puis le code une fois l'arbitrage rendu.

---

## TEMPS 1 — le fichier d'arbitrage (aucun code, aucune branche)

Produis `docs/arborescence_catalogue_v2.xlsx`, cinq onglets :

1. **Arbre cible** — 3 niveaux : espace › famille › sous-catégorie › type. Chaque famille de
   niveau 1 porte son espace : `ETABLISSEMENTS` (Établissements de santé) ou `DOMICILE`
   (Domicile & officine).
2. **Nos 211 catégories** — une ligne par catégorie, avec son nombre de produits (mesuré, en
   lecture seule), le nœud cible proposé, et une colonne **Décision** vide pour Mounir. Les
   **10 groupes de libellés quasi identiques** sont rapprochés sur des lignes voisines, la
   fusion proposée et le libellé à garder indiqué. La coquille « électro**p**cardiographes »
   porte un produit : dis lequel.
3. **Les 108 `CATEGORIE` de La Centrale Médicale** — rattachement proposé à l'arbre cible.
4. **Les 182 `RAYON FOURNISSEUR`** — même chose, et le sort des **4 398 lignes qui n'en ont
   pas**.
5. **Nature** — colonne produit / prestation sur chaque nœud de l'arbre cible.

Ajoute une feuille de lecture en tête : combien de décisions sont attendues, et lesquelles
bloquent quoi. **Aucune écriture en base. Rien n'est appliqué sans accord.**

Le fichier fournisseur se lit **en lecture seule, sur une copie** de
`D:\fournisseur_optimisanté\Catalogue_LCM_avec_images.xlsx`. Le classeur source ne s'ouvre
jamais en écriture.

---

## TEMPS 2 — le code (branche `feature/cat-arbre-categories`)

À n'ouvrir **qu'après** l'arbitrage rendu.

1. **Migration**, au prochain numéro libre **au moment de la fusion** (V82 au 29 octobre, à
   reconfirmer) : `espace` sur les familles de niveau 1, `image_url` de catégorie, ordre
   d'affichage, indicateur d'activité. `parent_id` existe déjà — ne le recrée pas.
   Les rattachements décidés dans l'arbitrage s'appliquent dans la **même** migration, et
   **comptent les lignes touchées** avec `RAISE EXCEPTION` si le compte n'est pas celui
   attendu : un rattachement muet qui se trompe est invisible.
2. **Filtrage par descendance** dans `ProductSpecification.withCategoryId`, aujourd'hui une
   égalité stricte (`cb.equal(root.get("category").get("id"), categoryId)`). Sans cela **toute
   page famille affiche zéro produit**. C'est le point le plus important du lot. Tests sur les
   trois niveaux, et sur une feuille sans enfant.
3. **Redirections 301** des 211 adresses de rayon vers leur nouvelle adresse, établies depuis
   l'arbitrage. Vérifiées **par appel réel**, pas par lecture du code.
4. **Une seule route publique** pour l'arbre, mise en cache, qui **élague les nœuds sans
   produit actif**, et invalidation du cache quand l'arbre change dans l'administration.
   `CategoryResponseDto` renvoie déjà l'arbre récursivement avec le nombre de produits :
   pars de là.
5. **Écran d'administration** de l'arbre : créer, renommer, rattacher (par sélecteur de
   parent), poser une image, désactiver.

## EXCLU EXPLICITEMENT

Le glisser-déposer dans l'écran d'administration. La fusion automatique des doublons — elle se
décide ligne par ligne dans l'arbitrage. La **suppression** d'une catégorie portant des
produits : on désactive. Toute modification de l'affichage des rayons (c'est M3).

## CRITÈRES D'ACCEPTATION

- Une page famille affiche les produits de **tous** ses descendants — test sur 3 niveaux.
- La route de l'arbre ne renvoie **aucun nœud vide**.
- Les 211 anciennes adresses répondent **301** vers une page qui existe, vérifié par appel.
- Le cache s'invalide à la modification de l'arbre — test.
- **Aucun arbre écrit en dur** nulle part.
- Poids JS **≤ 10 Ko**.

## RAPPORT

Section 7 de D0. Plus : le nombre de lignes touchées par la migration, comparé à l'attendu ;
la sauvegarde de la base faite **avant** la migration ; et les 301 vérifiées une par une ou
par échantillon nommé.
