# Outils de reprise du catalogue

## La liaison avec l'ancien site est coupée

Le catalogue Optimi Santé n'interroge plus aucun serveur tiers. Cette coupure est **volontaire
et définitive** : toute tentative de la rétablir ferait retomber le trafic sur l'infrastructure
de l'ancienne équipe, qui nous avait signalé que nos requêtes la dégradaient — à juste titre.

### Ce qui a été retiré

| Élément | Sort |
|---|---|
| `woocommerce_migration.py` | **Supprimé.** Il bouclait sur l'API REST de l'ancien site, 100 produits par page. Chaque page reconstruisait l'objet complet de chaque produit côté WordPress : c'est ce script qui saturait leur serveur. Consultable dans l'historique Git si besoin d'archéologie. |
| `WC_URL`, `WC_CONSUMER_KEY`, `WC_CONSUMER_SECRET` | **Retirés** de `.env` et de `.env.example`. La clé API a été révoquée côté WooCommerce. |
| Lien de brochure par défaut | **Retiré** de `TrainingService`. Il renvoyait le prospect vers `https://optimisante.com/brochures/default-brochure.pdf` — une adresse qui répond **404**, ouverte dans un nouvel onglet. Le visiteur laissait ses coordonnées et recevait une page d'erreur. |

### Comment le catalogue s'entretient désormais

Depuis l'espace **Négoce → Catalogue**, l'équipe administrative dispose de tout :

- le filtre **« Visuel à faire »**, avec son compteur, qui isole les fiches incomplètes ;
- pour chaque produit, un bouton **médias** : visuel principal, galerie de détails, vidéo de
  démonstration (fichier ou lien YouTube / Vimeo / Loom).

Tout part du poste de l'administrateur vers notre propre stockage. Aucune extraction, aucune
dépendance.

---

## `import_woocommerce_csv.py`

Conservé, parce qu'il travaille **à partir d'un fichier** et jamais d'une API. Il sert si
l'ancienne équipe fournit un jour son export CSV (voir `demande_extraction.md`).

> ⚠️ **Une précaution avant de le lancer.** Le script télécharge les images depuis les adresses
> présentes dans le CSV. Si ces adresses pointent vers l'ancien site, l'exécuter y renverrait du
> trafic — précisément ce que la coupure évite. Dans ce cas, demandez plutôt les **fichiers
> images** (archive de `wp-content/uploads`) et déposez-les par l'interface d'administration.

---

## Les fichiers de traçabilité

- **`produits_incomplets.csv`** — les 169 fiches incomplètes au moment de la bascule : 111 sans
  visuel propre, 64 sans description. La colonne `id_woocommerce` porte l'identifiant d'origine
  pour 65 d'entre elles.
- **`demande_extraction.md`** — le message adressé à l'ancienne équipe, et les voies de repli si
  la demande n'aboutit pas.

Ces deux fichiers décrivent un **état daté**, pas la situation courante. Le compteur
« Visuel à faire » de l'interface d'administration fait foi.
