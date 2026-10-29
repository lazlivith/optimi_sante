# Prompts du chantier catalogue

Un fichier par lot. Chacun commence par **« Appliquer D0 »** et renvoie au plan.

Préséance : `CLAUDE.md`, puis `docs/maquette_catalogue_optimisante/prompt_dette_technique.md`
(D0), puis `docs/plan_integration_catalogue.md`, puis le prompt du lot. Une session qui
rencontre une contradiction **s'arrête et le signale**.

Maquettes : `docs/maquette_catalogue_optimisante/maquette_catalogue/`.

| Ordre | Fichier | Lot | Ouvrable quand |
|---|---|---|---|
| 1 | `M0_cadrage.md` | cadrage, analyse seule | maintenant |
| 2 | `CAT_categories.md` | chantier catégories — **chemin critique** | temps 1 maintenant ; temps 2 après arbitrage |
| 3 | `M1_entete_recherche.md` | en-tête et recherche | après M0 |
| 4 | `M2_fiche_produit.md` | fiche produit | après M1 |
| 5 | `M3_navigation_catalogue.md` | méga-menu, Catalogue, famille, rayon | **après CAT, pas avant** |
| 6 | `M4_marque.md` | marque | après M3 |
| 7 | `M2bis_caracteristiques_documents_garantie.md` | caractéristiques, documents, garantie | après M2 |
| 8 | `L_delais_livraison.md` | délais par zone | dès que les délais sont fournis |
| — | `PIL_import_pilote_lcm.md` | import pilote LCM — **en attente** | après CAT **et** marge fixée |

Trois lots ont un prérequis qui ne se contourne pas, et dont la session doit vérifier
l'existence avant la première ligne de code : **M3** attend CAT, **L** attend les délais,
**PIL** attend CAT et la marge.
