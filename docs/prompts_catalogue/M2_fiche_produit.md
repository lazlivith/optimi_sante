# M2 — fiche produit

**Appliquer D0**, puis `docs/plan_integration_catalogue.md` § 4 « M2 ». Contradiction :
arrête-toi et dis-le.

Branche `feature/maquette-m2-fiche-produit`. Références :
`docs/maquette_catalogue_optimisante/maquette_catalogue/Produit.dc.html` et
`ProduitMobile.dc.html`.

Tu fais **évoluer** `ProductDetailPage` et `ProductGallery`. Tu ne les réécris pas.

## ORDINATEUR

- **Fil d'Ariane** complet : Accueil / Catalogue / famille / rayon / produit, avec les
  catégories qui existent aujourd'hui. Il suivra l'arbre après CAT, sans changement de code.
- **Galerie** : grande image, bouton « Agrandir », miniatures, vignette « Vidéo » **seulement
  si** le produit a une vidéo (`videoUrl` / `videoProvider` existent). Règle des visuels
  factices conservée.
- **Bloc d'achat** :
  - badge de catégorie ; badge de remise **seulement si** une campagne de prix est active ;
  - titre, « Réf. … » (`sku`), et « Marque : … » **seulement si** le champ est rempli — il
    n'existe pas encore, donc masqué (M4) ;
  - prix, prix barré si remise, mention « Prix TTC. Livraison calculée selon le pays. » ;
  - sélecteur **Pays de livraison** relié à `/api/v1/shipping/estimation` : il affiche les
    **frais réels**, et **rien** si l'estimation n'existe pas pour ce pays ;
  - quantité, « Ajouter au panier », « Acheter maintenant », lien « Commande en quantité pour
    un établissement ? Demander un devis » vers le parcours devis existant ;
  - produit sur devis : **ni prix ni panier**, seulement le bouton de devis ;
  - badge de stock **selon `mode_stock` uniquement** : aucun badge pour `NON_SUIVI` ni
    `FOURNISSEUR`. Jamais de badge calculé depuis `stock_quantity`.
- **Engagements** : « Paiement sécurisé : carte bancaire ou virement » ; « Garantie
  fabricant » **masquée** (aucun champ, voir M2-bis) ; contact WhatsApp **lu dans
  `parametres_plateforme`**, bouton masqué si le paramètre est vide. Jamais de numéro en dur.
- **Onglets accessibles** (`role="tab"`, flèches, Entrée, Échap qui rend le focus) :
  Caractéristiques **masqué** (aucun champ, voir M2-bis), Description (existante, listes en
  vraies puces, **aucun HTML injecté**), Livraison & frais, Documents **masqué** (aucun).
- **Produits similaires** : même catégorie, produits avec visuel d'abord, **5 au maximum**,
  le même composant de carte.

## MOBILE

Galerie à balayer avec points de position et miniatures défilantes. Onglets transformés en
sections dépliables, **Caractéristiques ouverte par défaut** — donc, tant qu'elle est masquée,
la première section visible. Barre d'achat **fixe** en bas : total qui suit la quantité, et
« Ajouter au panier ». Elle **ne masque aucun contenu** : espace réservé en bas de page,
vérifié à l'écran et non supposé.

## TROIS CORRECTIONS DE LA MAQUETTE — mesurées, à appliquer

1. **La mention DAP n'est pas « hors Union européenne ».** Le code dit **hors de France**
   (`adminShippingService.ts:22`, `CheckoutPage.tsx:360`, `TermsPage.tsx:108`) et le serveur
   porte le verdict dans `EstimationDto.droitsALArrivee`. La fiche **lit ce booléen** et ne
   recalcule **jamais** une appartenance à l'UE côté navigateur. C'est un texte contractuel.
2. **Le délai de livraison n'existe pas** dans `EstimationDto`. Il est **masqué**. Ne l'invente
   pas, ne le déduis pas de la zone. Il fait l'objet du lot L.
3. **Le texte DAP est déjà écrit en trois endroits.** Centralise-le en **un seul**, et branche
   les trois existants dessus. Le texte ne change pas d'un caractère.

## LA SOURCE UNIQUE DES VISUELS FACTICES

Si la requête de M0 (§ 6 du plan) a renvoyé au moins une ligne, applique la correction dans ce
lot : le serveur expose `visuelFactice` (calculé par `VisuelProduit.estFactice`), `ProductImage`
le lit, et `KNOWN_BROKEN_URLS` disparaît. Le repli par `onError` **reste**. Si la requête a
renvoyé zéro ligne, dis-le et laisse en place.

## À NE PAS FAIRE

Aucun changement au calcul des prix, à la TVA, au panier, au paiement. Aucun texte entre
crochets en production. Aucune donnée inventée. Aucune réécriture de `ProductGallery`.

## TESTS

vitest : produit avec et sans remise ; sur devis ; avec et sans vidéo ; sans marque, sans
garantie, sans documents (**éléments masqués, et le test le vérifie**) ; pays France et hors
France (encadré DAP depuis `droitsALArrivee`) ; barre mobile ; onglets au clavier ; WhatsApp
avec et sans paramètre.

**Non-régression obligatoire** : ajout au panier depuis la fiche → panier → écran de paiement,
sans payer.

## CRITÈRES D'ACCEPTATION

Captures avant/après sur **trois fiches réelles** : un produit en promotion, un produit sur
devis, un produit à visuel factice — en 1 440 px **et** 375 px, par émulation réelle. La barre
fixe ne masque rien. Poids JS **≤ 35 Ko** — si la galerie à balayer déborde, remplace-la par un
défilement natif `scroll-snap` avant de demander la réserve.

## RAPPORT

Section 7 de D0. **La liste des éléments masqués faute de donnée** y figure nommément :
garantie, caractéristiques, documents, délai de livraison, et la marque si M4 n'est pas livré.
