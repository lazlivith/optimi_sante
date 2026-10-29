# PIL — import pilote La Centrale Médicale : **en attente**

**Appliquer D0**, puis `docs/plan_integration_catalogue.md` § 4 « PIL ». Contradiction :
arrête-toi et dis-le.

**Ce lot est en attente. Ne l'ouvre pas tant que les deux conditions ci-dessous ne sont pas
remplies**, même si le fichier est prêt.

## ÉTAT

Prêt et fusionné dans `main` : `docs/import_pilote_lcm.xlsx` et `docs/import_pilote_lcm.md`.
20 lignes réelles du catalogue fournisseur, plus une **21ᵉ volontairement fautive en position
11** — `PILOTE-FAUTIF-001`, quantité **−5**.

Variété des 20 lignes : prix de **0,14 € à 6 217,55 €**, 10 avec image et 10 sans, catégories
accentuées, `N°10`/`N°11`/`N°12` dans les noms, **deux quantités laissées vides**.

## LES DEUX CONDITIONS

1. **L'arbre des catégories en base (CAT).** Sans lui, les 108 catégories du fournisseur
   créent 108 racines de plus — on aggraverait exactement le désordre que CAT corrige.
2. **La marge fournisseur fixée.** Sans marge paramétrée, le **prix d'achat part en boutique
   tel quel**. L'aperçu le signale déjà : « aucune marge définie (ni sur la catégorie, ni sur
   le fournisseur), prix d'achat porté tel quel ». Ce n'est pas une erreur d'import, c'est un
   tarif de gros affiché au public.

## TROIS ÉCARTS ASSUMÉS, À RELIRE AVANT DE LANCER

1. **Le fichier réel n'a pas de colonne `STOCK`** ; une a été ajoutée au pilote. Sans quantité,
   l'import **n'écrit aucun mouvement** et le contrôle « mouvements portant cet `import_id` »
   ne prouverait rien. **Conséquence pour l'import complet** : tel quel, il créera 7 344
   produits **à stock 0, sans aucun mouvement**. Sans danger depuis le lot A — un produit
   `FOURNISSEUR` n'est ni vérifié ni décrémenté — mais à savoir avant de lire le rapport.
2. **La ligne fautive porte une quantité négative, pas un slug invalide.** Un nom de symboles
   ne produit pas un slug fautif : l'écrivain retombe sur `produit`. Une quantité négative est
   refusée de façon certaine par la validation de ligne.
3. **Le `®` n'existe nulle part dans le fichier du fournisseur** — pas une occurrence sur
   7 344 lignes. Il figure sur la ligne inventée, qui n'ira pas en base : on ne falsifie pas la
   donnée d'un fournisseur pour un test.

## CE QUE L'APERÇU DOIT MONTRER AVANT CONFIRMATION

Exactement :

```
Ligne 11 (PILOTE-FAUTIF-001) : quantité en stock négative (-5).
```

Les vingt autres annoncées comme créées. **Si une ligne saine manque, ou si le numéro cité
n'est pas 11, ne confirme pas.**

Un second motif est attendu et **n'est pas une erreur** : « aucune marge définie…, prix d'achat
porté tel quel ». S'il apparaît encore, c'est que la condition 2 n'est pas remplie — **arrête**.

## CONTRÔLE APRÈS CONFIRMATION, EN LECTURE SEULE

Attendus : **20 produits, tous `FOURNISSEUR`** ; **18 mouvements** portant l'`import_id` (les
20 moins les deux quantités vides) ; **0** produit `PILOTE-FAUTIF-001` ; **0** produit `PROPRE`
touché ; **0** mouvement de vente.

Le script de contrôle existe dans le bac à sable de la session du 27 octobre. S'il n'est plus
là, réécris-le : `MSYS_NO_PATHCONV=1`, `-e URLBASE` **sans valeur**, et **vérifie les noms de
colonnes de `catalog_imports` dans l'entité** avant de les écrire — ils ont déjà été inventés
une fois, et `ON_ERROR_STOP=1` aurait avorté le contrôle entier.

## AVANT L'IMPORT COMPLET — décisions du plan § 8, à confirmer

- les **111 produits « ARRET »** : recommandation, **ne pas les importer** ;
- les **« REMPLACE PAR <sku> »** : importer le remplaçant, pas le remplacé ;
- les **43 « Port en sus sur devis »** : importer **sur devis**, sans prix ;
- les **4 398 lignes sans rayon fournisseur** : rattacher par leur colonne `CATEGORIE`.
