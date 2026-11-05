# M4 — marque des produits

**Appliquer D0**, puis `docs/plan_integration_catalogue.md` § 4 « M4 ». Contradiction :
arrête-toi et dis-le.

Branche `feature/marque`. Prérequis : CAT (pour que les compteurs du filtre soient justes sur
les trois niveaux).

## 1. LA MARQUE COMME ENTITÉ, PAS COMME TEXTE

Table des marques : slug `[a-z0-9-]` (même garde-fou que produits et catégories, par
`SlugValide.exiger` sur `@PrePersist`), logo facultatif. Migration au **prochain numéro libre
au moment de la fusion**.

Un texte libre produirait « Microlife », « MICROLIFE » et « Micro-life » comme trois marques :
le filtre deviendrait inutilisable, ce qui est précisément ce qu'on vient de corriger sur les
catégories.

## 2. LA SÉPARATION MARQUE / FOURNISSEUR

`FichierCatalogue.java:52` confond aujourd'hui les deux :

```java
"marque", List.of("marque", "fournisseur", "fabricant", "brand"),
```

La Centrale Médicale est un **fournisseur**. Microlife est une **marque**. Un produit a les
deux, et ce ne sont pas les mêmes. Défais cette confusion : `MARQUE` et `fabricant`/`brand`
alimentent la marque ; `fournisseur` ne l'alimente **pas**.

À savoir avant de coder : la valeur lue aujourd'hui n'est **utilisée nulle part en aval** —
reconnue, puis jetée. Il n'y a donc aucun comportement existant à préserver, seulement un
en-tête à ne pas casser.

## 3. LA REPRISE DE L'EXISTANT

Le fichier fournisseur donne **79 marques distinctes sur 77 % de ses 7 344 lignes** (mesuré le
29 octobre, en lecture seule) : JOLETI 528, HARTMANN 322, L&R 289, DIAN 251, BD 240,
SPENGLER 194… **C'est la source, et elle est bonne.**

La détection par le nom des produits ne sert donc que pour les **1 697 lignes sans marque** et
pour nos produits existants. Elle se livre en fichier à valider, `docs/marques_proposees.xlsx`,
une ligne par marque proposée avec le nombre de produits concernés et trois exemples.
**Aucune écriture en base avant accord.**

## 4. IMPORT FOURNISSEUR

La colonne marque est reconnue et **utilisée**. Une marque inconnue est **signalée nommément
dans l'aperçu** — jamais créée automatiquement. L'aperçu dit combien, et lesquelles.

## 5. AFFICHAGE

« Marque : … » sur la fiche produit, **seulement si** le champ est rempli (l'élément était
masqué depuis M2 : il s'allume ici). Filtre « Marque » dans les rayons, **côté serveur**, avec
compteurs, **masqué s'il n'y a aucune marque** dans le rayon. Recherche par marque.

## EXCLU

Toute création de marque sans accord. Tout logo déposé sans vérification de la signature du
fichier. Toute modification de la fiche hors de la ligne « Marque : … » et du filtre.

## TESTS

Filtre avec compteurs justes sur les trois niveaux de l'arbre (donc la descendance de CAT est
en jeu : un test le prouve). Rayon sans marque → filtre masqué. Marque inconnue → nommée dans
l'aperçu, non créée. Slug invalide → refusé.

## CRITÈRES D'ACCEPTATION

Les compteurs du filtre égalent le nombre de produits réellement affichés — vérifié sur un
rayon réel, pas seulement en test. Poids JS **≤ 10 Ko**.

## RAPPORT

Section 7 de D0. Plus : le nombre de marques créées, et combien de produits restent sans
marque après reprise.
