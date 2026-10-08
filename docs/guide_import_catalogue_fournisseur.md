# Importer le catalogue d'un fournisseur

Guide d'exploitation de l'espace fournisseur, écrit pour le cas réel : la gamme
La Centrale Médicale, 7 344 références, 10 322 visuels.

---

## 1. Pourquoi votre import a été refusé

> « Colonnes « sku » et « nom » introuvables dans l'en-tête. »

Vous avez déposé **`01-Tarification Globale 2026 RV.xlsx`**, le fichier brut du
fournisseur. Il ne peut pas être lu tel quel, pour deux raisons :

| | Le fichier brut de LCM | Ce que la plateforme cherche |
|---|---|---|
| Ligne de l'en-tête | la **3ᵉ** (les deux premières portent le titre et la date de mise à jour) | la 1ʳᵉ |
| Nom de la référence | `Réf.` | `sku` |
| Nom du produit | `Désignation` | `nom` |
| Images | *aucune colonne* | `image_urls` |

La plateforme lit la première ligne, y trouve `TARIFICATION FEVRIER 2026`, et
n'y reconnaît évidemment rien.

**Le fichier à déposer est `Catalogue_LCM_avec_images.xlsx`**, dans
`D:\fournisseur_optimisanté\`. C'est le fichier de LCM traduit dans les colonnes
attendues, et enrichi des adresses d'images relevées sur leur site public — celles
que LCM vous a dit d'aller chercher vous-même.

---

## 2. À faire AVANT d'importer : déployer

La production tourne sur une version antérieure aux corrections de l'import.
Déposer la gamme maintenant produirait un catalogue qu'il faudrait refaire :

| Sans déploiement | Conséquence |
|---|---|
| Les prix importés ne sont pas taxés | **Tous les prix 20 % trop bas** en boutique (vos prix sont TTC, ceux de LCM sont HT) |
| Pas de bouton « Rattacher » | Vos 1 519 produits restent intouchables, **toutes leurs lignes sont écartées** |
| Les adresses d'images n'ont pas d'extension | Les 10 322 visuels sont déposés, **facturés, et invisibles** (réponse 401) |
| Pas de galerie | Une seule photo par produit au lieu de quatre ou cinq |

Déployez d'abord. L'ordre inverse coûte un catalogue à reprendre et un quota
Cloudinary dépensé pour rien.

---

## 3. Le déroulé, dans l'ordre

### Étape 1 — Rattacher vos références existantes

Vos 1 519 produits historiques n'ont **aucun fournisseur**. L'import refuse d'y
toucher, et c'est voulu : sans cette règle, un simple fichier suffirait à réécrire
des fiches construites à la main.

1. Ouvrez **LA CENTRALE MEDICALE → Catalogue**.
2. Déposez `Catalogue_LCM_avec_images.xlsx`.
3. L'aperçu annoncera beaucoup de lignes **écartées**, avec le motif
   « Référence déjà utilisée par un produit du catalogue, sans fournisseur ».
   C'est normal, et c'est ce que l'étape suivante lève.
4. Cliquez **« Rattacher les références existantes »**.
   Cette opération **n'écrit aucun prix** : elle déclare une appartenance.
   Elle ne touche que les produits sans fournisseur — ceux d'un autre
   fournisseur ne sont jamais repris.
5. Notez le nombre annoncé (en local : 615).

### Étape 2 — Déposer à nouveau le même fichier

Le rattachement ayant changé la situation, l'aperçu doit être refait.
Redéposez le même fichier. Les lignes écartées sont devenues des
**mises à jour**.

Vérifiez l'aperçu avant d'aller plus loin :

```
lignes 7344 | à créer ~6 700 | à mettre à jour ~615 | écartées 4 | erreurs 4
```

Les 4 erreurs sont de vraies anomalies du fichier de LCM, et non des défauts de
la plateforme : un « SUR DEVIS » en guise de prix, un prix absent, et deux
titres de section (`Produits Hors Catalogue`, `A FIN DE STOCK`) pris pour des
produits. Rien à corriger.

### Étape 3 — Cocher « Remplacer les visuels existants »

**Pour ce premier chargement, cochez la case.** C'est exactement le cas qu'elle
sert : vos 615 produits portent une vignette, mais aucune galerie, et les photos
de LCM sont meilleures.

Pour les imports mensuels suivants, **laissez-la décochée**. L'import comblera
alors les manques sans écraser une photo que vous auriez choisie à la main. La
case se remet à zéro à chaque fichier déposé, pour que le choix soit toujours
délibéré.

Chaque import garde la trace de ce choix dans l'historique. Une fois les
anciennes images remplacées, c'est la seule.

### Étape 4 — Confirmer, puis laisser faire

Cliquez **« Écrire N produits au catalogue »**.

L'import tourne en arrière-plan : **vous pouvez fermer la page**. Comptez
**quatre à cinq heures** pour la gamme complète, le temps de télécharger,
convertir et déposer 10 322 images.

---

## 4. Vérifier le résultat

Après l'import, l'écran annonce `N créés · N mis à jour · N visuels`.

Trois contrôles valent la peine :

1. **Ouvrez trois fiches produit au hasard** et vérifiez que la photo s'affiche
   et que la galerie contient plusieurs vues.
2. **Comparez un prix** à celui du fichier LCM : le prix affiché doit être le
   prix d'achat HT majoré de votre marge, puis de la TVA de la famille.
   Un fauteuil roulant (5,5 %) et un thermomètre (20 %) ne sont pas majorés
   pareil.
3. **Lisez les lignes écartées**, repliées sous l'aperçu. Elles disent
   précisément pourquoi chaque ligne n'est pas passée.

---

## 5. Ce que l'import ne fera pas

**4 400 références n'auront aucune image.** Elles n'ont pas de fiche publique
chez LCM — leur site publie 3 024 produits, leur tarif en compte 7 344. Il n'y a
rien à aller chercher.

**Les grilles par marque** (`TARIF HEINE`, `TENA`, `ABENA`…) ne sont pas
importables en l'état : en-têtes sur plusieurs lignes, titres de section
intercalés entre les produits. Elles demandent un traitement séparé.

**Les tarifs dégressifs** (colonne « Quantité à partir de ») ne sont pas
représentables : la plateforme porte un prix par produit. Seul le premier palier
est repris.

**Les produits arrêtés** ne sont pas encore désactivés automatiquement. Attention
au raccourci dangereux : un produit absent de la tarification globale **n'est pas
un produit arrêté**. 872 de vos références viennent d'autres fournisseurs et
n'ont aucune raison d'y figurer. Seuls comptent le fichier `04-Produits
arrêtés.xlsx` et la mention `ARRET` du fichier `02`.

---

## 6. Où vont les images

```
optimisante/<environnement>/catalogue/
  produits/<code-fournisseur>/<identifiant>_1605227370.jpg   la vignette
  galerie/<code-fournisseur>/<identifiant>_1605227370.jpg    les vues secondaires
```

Le fichier porte **la référence du produit**, ce qui permet de retrouver tous ses
visuels depuis la console Cloudinary sans passer par la base. Le sous-dossier
porte le code du fournisseur, ce qui permet de reprendre un lot sans toucher au
reste.

Prévoyez environ **1,2 Go** pour la gamme complète. Vérifiez votre quota avant de
lancer : c'est le point qui peut coincer.

---

## 7. Refaire le fichier plus tard

Quand LCM enverra une nouvelle tarification, le fichier enrichi se reconstruit en
deux temps, avec les scripts conservés avec ce guide :

```sh
# 1. Relever les références et les images sur le site public (environ 50 min)
python moissonner.py products.xml fiches.jsonl 0 999999 3

# 2. Croiser avec la nouvelle tarification
python enrichir.py "01-Tarification Globale.xlsx" fiches.jsonl Catalogue_LCM_avec_images.xlsx
```

La jointure se fait sur la **référence LCM lue sur la fiche** (`Ref. : 1605300020`),
et non sur le numéro présent dans l'adresse de la page : celui-ci est un
identifiant interne au site, sans rapport avec vos références.
