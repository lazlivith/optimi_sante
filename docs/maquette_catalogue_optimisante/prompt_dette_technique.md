# Prompt D0 — dette technique : ce qui ne se casse pas

Écrit pour : toute session qui ouvre un lot de l'intégration du catalogue (M0 à M4).
À coller **en tête de chaque prompt de lot**, avant son contenu propre.

Mesuré sur le dépôt le 29 octobre 2026. Chaque chiffre est vérifiable, et doit être
revérifié à l'ouverture du lot plutôt que recopié : un garde-fou qui porte sur une copie ne
voit pas ce qui a changé.

---

## 0. La règle au-dessus des autres

`CLAUDE.md` prime sur ce document, et ce document prime sur le plan d'implémentation.
Si l'un des trois contredit les autres, tu t'arrêtes et tu le dis — tu ne choisis pas.

Tu ne touches pas, tu ne « nettoies pas au passage », tu ne reformates pas ce qui
fonctionne et qui n'est pas dans le périmètre du lot. Un diff plus large que le lot est un
défaut, même si chaque ligne est bonne.

## 1. Branche, migration, accord

- Une **branche dédiée par lot**, créée avant le premier commit. Jamais de commit sur `main`.
- Numéro de migration : **le prochain libre au moment de la fusion**, pas à la création de la
  branche. Au 29 octobre, la production est à **V81** — le prochain libre est **V82**, à
  reconfirmer à la fusion.
- Une migration appliquée **ne se modifie jamais** : ni édition, ni renumérotation, ni
  suppression. `validate-on-migrate` est actif ; un fichier appliqué qui disparaît empêche le
  backend de démarrer.
- **Aucun déploiement, aucune écriture en production sans accord explicite.** Sauvegarde de la
  base avant toute migration. Un audit en production se fait en lecture seule.
- Toute commande proposée pour la fenêtre Git Bash s'écrit avec `-e URLBASE` **sans valeur**, et
  `-v ON_ERROR_STOP=1` sur tout script qui écrit.

## 2. Ce qui est en place et ne doit pas régresser

| Règle métier | Où elle vit | Ce qui la casserait |
|---|---|---|
| Jamais de photo factice | `VisuelProduit.MOTIFS` (6 motifs) et `ProductImage.KNOWN_BROKEN_URLS` (4 URL) | ajouter un repli image, ou oublier un motif d'un côté |
| Badge « En stock » pour `PROPRE` seulement | `mode_stock` — aucun badge pour `NON_SUIVI` ni `FOURNISSEUR` | un badge écrit depuis `stock_quantity` |
| Filtres côté serveur, état dans l'URL | `lib/filtresRayon.ts`, `CatalogResource` | un filtrage en mémoire dans le navigateur |
| Slugs `[a-z0-9-]` | `SlugValide.exiger`, sur `@PrePersist` de `Product` et `Category` | un slug construit sans passer par là |
| Mention DAP | `EstimationDto.droitsALArrivee`, servi par le serveur | recalculer l'appartenance à l'UE côté navigateur |
| Numérotation sans trou | `DocumentNumberService` | émettre ou annuler un document hors de sa transaction |
| Prix, TVA, panier, paiement | chantier facturation, à part | **aucune modification, même d'apparence** |

## 3. Les garde-fous qui font échouer le build

`npm run build` est la vérification de référence, pas `tsc --noEmit`. Le garde-fou de charte
tourne en `postbuild` et refuse :

- **Contraste** sous 4,5:1 pour tout couple texte/fond.
- **Cliquet des couleurs** : `couleursEnDur` est à **247** dans `charte-reference.json`. Il ne
  doit **jamais** remonter. On ne relève pas la référence pour faire passer un build.
- **Toute nouvelle valeur brute** en hexadécimal ou `rgb()`. Les couleurs des maquettes se
  traduisent en jetons `--color-brand*`, jamais copiées. Trois dérives connues à traduire :
  `#13232C` → `--color-brand-dark`, `#0B4F6C` → `--color-brand-fonce`,
  `#D92D20` → `--color-danger`.
- **Poids** : JS sous **1 500 Ko**, à **1 333 Ko** aujourd'hui — il reste **167 Ko**. Public
  sous 700 Ko, à 496 Ko. Tout lot annonce son coût en kilooctets, mesuré, pas estimé.

Interdits sans discussion : `dangerouslySetInnerHTML`, une transformation Cloudinary non
signée, un lien construit autrement que par `hrefSur`, un arbre de catégories écrit en dur.

## 4. Les trois pièges propres à ce chantier

1. **Le filtre par catégorie ne descend pas dans l'arbre.** `ProductSpecification.withCategoryId`
   est une égalité stricte. Sans descendance, toute page famille affichera zéro produit dès que
   l'arbre à trois niveaux existera. Ce travail appartient au chantier catégories ; aucun lot
   qui en dépend ne s'ouvre avant.
2. **Le délai de livraison n'existe pas.** `EstimationDto` porte les frais, pas le délai.
   Un délai affiché serait inventé : il se masque, ou il fait l'objet d'un lot avec migration.
3. **Quatre données de la maquette n'existent pas en base** : garantie, documents, caractéristiques
   techniques, marque. Le numéro WhatsApp n'est pas configuré — il irait dans
   `parametres_plateforme`, jamais en dur.

## 5. Aucun texte entre crochets en production

39 occurrences dans les maquettes : `[PRIX]` ×10, `[À COMPLÉTER]` ×6, `[N]` ×5,
`[RÉFÉRENCE]` ×4, `[DURÉE]` ×4, `[À AJOUTER]` ×2, `[MONTANT]` ×2, `[DÉLAI]` ×2,
`[À CONFIRMER]`, `[NUMÉRO]`, et deux descriptions de remplissage.

Un élément dont la donnée n'existe pas est **masqué**. Jamais affiché vide, jamais inventé,
jamais rempli d'un exemple plausible. Tout élément masqué est **listé dans le rapport du lot**.

## 6. Accessibilité, non négociable

Clavier complet, focus visible, cibles de 44 px minimum sur téléphone, `role="search"` et
libellés accessibles sur les formulaires, onglets au clavier (flèches, Entrée, Échap qui rend
le focus), `prefers-reduced-motion` respecté. Hauteur de champ 48 px et police 16 px sur
mobile, sinon iPhone zoome tout seul.

## 7. Ce que tu rapportes à la fin du lot, et dans cet ordre

1. **Build propre** : `npm run build`, garde-fou de charte inclus, et le poids du JS obtenu.
2. **Suite complète verte**, backend et frontend, **avec le nombre de tests ignorés**. Sans
   Docker, les tests d'intégration sont ignorés et non en échec : une suite verte peut n'avoir
   rien exécuté. Un chiffre d'ignorés absent rend le rapport invalide.
3. **Vérification réelle** : la page ouverte dans un vrai navigateur, par émulation d'écran
   (`Emulation.setDeviceMetricsOverride`, cache désactivé), captures **avant et après en
   1 440 px et 375 px**. Une fenêtre `--window-size=375` ne descend pas sous la largeur
   minimale de Chrome sur Windows : la capture est rognée, pas émulée, et ne prouve rien.
4. **Ce qui n'a pas été vérifié**, dit explicitement. Un échec se rapporte avec sa sortie.
5. **Les éléments masqués** faute de donnée, et **les écarts assumés** avec la maquette.

Un test instable est une dette à traiter, pas à tolérer. Si tu découvres un défaut hors
périmètre, tu le signales et tu ne le corriges pas dans ce lot.

## 8. Mesurer avant de corriger

Si la maquette ou le rapport annonce un défaut visuel, tu le **mesures** avant d'y toucher
(`scrollWidth` contre `clientWidth`, capture émulée à la bonne largeur). Corriger une mise en
page correcte sur la foi d'une capture rognée la dégrade. Si la mesure dit qu'il n'y a rien,
tu le dis et tu ne corriges pas.
