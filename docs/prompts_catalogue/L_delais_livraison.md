# L — délais de livraison par zone

**Appliquer D0**, puis `docs/plan_integration_catalogue.md` § 4 « L ». Contradiction :
arrête-toi et dis-le.

Branche `feature/delais-livraison`. Indépendant des autres lots.

## PRÉREQUIS QUI NE SE CONTOURNE PAS

**Les délais par zone, fournis par Mounir.** Il n'existe aucune valeur par défaut raisonnable :
un délai inventé est une promesse fausse, et c'est précisément pour cela qu'il a été masqué en
M2. **Sans les délais, ce lot ne s'ouvre pas** — dis-le et arrête-toi.

## LE CONSTAT

`EstimationDto` porte aujourd'hui `zone`, `zoneLibelle`, `montant`, `offerte`,
`droitsALArrivee`. **Aucun délai, nulle part** dans la chaîne de livraison. La maquette
affiche `[DÉLAI]` à deux endroits : ce lot les remplit, ou ils restent masqués.

## PÉRIMÈTRE

1. **Migration**, au prochain numéro libre **au moment de la fusion** : deux bornes en **jours
   ouvrés** par zone (basse et haute) sur la grille des tarifs de livraison. Deux bornes et non
   une valeur unique : un délai unique est faux la moitié du temps, et un délai dépassé abîme
   la confiance plus qu'une fourchette honnête.
2. **Saisie** dans l'écran des tarifs de livraison **existant** (`AdminShippingPage`), à côté
   du tarif de la zone. Pas de nouvel écran.
3. **Ajout du délai à `EstimationDto`** et à `/api/v1/shipping/estimation`.
4. **Affichage** sur la fiche produit **et** au panier, le texte écrit **en un seul endroit**
   dans le code — comme la mention DAP centralisée en M2, et pour la même raison.

## RÈGLE D'AFFICHAGE

Une zone **sans délai saisi n'affiche rien**. Jamais « 0 jour », jamais « délai inconnu »,
jamais une estimation déduite de la zone voisine. C'est la règle D0 : un élément sans donnée
est masqué.

## POURQUOI CE LOT EXISTE

Pour un client en Afrique, le délai est l'information décisive, au même rang que le prix. Le
masquer en M2 était la seule option honnête ; le laisser masqué durablement serait un manque.

## EXCLU

Tout calcul de délai par transporteur, par produit ou par disponibilité de stock. Toute
modification des tarifs eux-mêmes. Toute modification du paiement.

## TESTS

Zone avec délai : affiché sur la fiche et au panier, identiques au caractère près. Zone sans
délai : **rien affiché**, et le test le vérifie. Bornes inversées à la saisie : **refusées**.
Borne négative ou nulle : refusée.

## CRITÈRES D'ACCEPTATION

Le délai affiché sur la fiche est **celui que renvoie la route**, vérifié par appel réel et non
par lecture du code. Poids JS **≤ 5 Ko**.

## RAPPORT

Section 7 de D0. Plus : les zones renseignées et celles laissées vides, nommément.
