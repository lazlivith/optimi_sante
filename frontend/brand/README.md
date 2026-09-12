# Sources de marque — Optimi Santé

Ce dossier contient les fichiers **tels que le graphiste les a livrés**. Il n'est pas servi par
le site : rien de ce qui s'y trouve n'atteint un navigateur.

## Pourquoi ils ne sont plus dans `public/`

Ils y étaient, et ils étaient donc servis — 30,7 Mo, dont 234 Ko réellement utilisés.

Ces fichiers sont en résolution d'impression : jusqu'à **19 011 × 19 011 pixels**, soit
361 mégapixels. Un navigateur ne se contente pas de télécharger une image, il la **décode** :
361 Mpx occupent environ **1,4 Go de mémoire vive**, quelle que soit la taille d'affichage. Sur
un téléphone, l'onglet se ferme.

Leurs noms posaient un second problème : espaces, majuscules, et accents en forme **décomposée**
— le « é » de `Carré fond bleu.png` est un « e » suivi d'un accent combinant. Un tel nom se
comporte différemment selon le système de fichiers, le serveur et le cache.

## Ce qui est servi à la place

`public/marque/`, dérivé de `originaux/logo.png` :

| Fichier | Usage |
|---|---|
| `optimi-logo-sombre.webp` | Verrouillage complet, tracé sombre — fonds clairs |
| `optimi-logo-clair.webp` | Verrouillage complet, tracé blanc — fonds sombres |
| `optimi-logotype-sombre.webp` | Logotype seul, sans la signature — barre de navigation |
| `optimi-logotype-clair.webp` | Logotype seul, tracé blanc |
| `optimi-icone-180.png` | Icône d'application (écran d'accueil mobile) |

Chaque variante existe aussi en `.png`, pour les rares navigateurs sans WebP.

## Comment elles ont été dérivées

Aucun fichier livré n'a de **transparence réelle** : tous ont un canal alpha entièrement opaque,
le fond blanc ou bleu étant incrusté dans l'image. La transparence a donc été reconstruite :
l'alpha vaut l'inverse de la luminosité, ce qui rend le blanc invisible, le noir opaque, et
préserve l'anticrénelage des bords.

Cette opération est exacte parce que le tracé est **noir sur blanc**. Elle serait ambiguë sur la
version couleur — on ne peut pas séparer un jaune clair d'un jaune plus saturé mêlé de blanc.
C'est pourquoi aucune version couleur transparente n'a été dérivée.

Les marges blanches ont été rognées (19 008 × 8 640 → 7 323 × 4 656 de contenu utile), et la
signature « soutenir le handicap et le soin. » a été séparée du logotype en détectant la bande
blanche qui les sépare — plutôt qu'en devinant une hauteur de coupe.

## Ce qui manque encore

**La griffe seule** — le S avec sa boucle, que la charte présente page 1 sous l'intitulé
« dynamisme de la griffe seule ». Elle ne figure dans aucun fichier livré, et elle **ne peut pas
être découpée** dans le verrouillage : le S y est lié au reste du lettrage. Les essais de
recadrage ne donnent que des fragments illisibles.

C'est elle qu'il faut pour une favicon : à 16 pixels, un logotype complet n'est qu'une tache.
La favicon actuelle est donc **provisoire**.

**Les fichiers vectoriels** (`.svg`, `.ai`, `.eps`). Un logo vectoriel pèse quelques kilo-octets,
reste net à toutes les tailles, et se recolore par le code — il rendrait tout ce dossier presque
inutile.

**Les références du bleu.** Il porte toute l'identité visuelle mais ne figure pas dans la charte,
qui ne référence que le jaune `#EEF133` et l'orange `#EC7226`. Les valeurs employées
(`#117098` → `#1F99B4`) sont échantillonnées dans les images fournies.

## Versionnement

Le sous-dossier `originaux/` **n'est pas suivi par Git** : 30,7 Mo de fichiers binaires
alourdiraient le dépôt de façon permanente, et chaque clone les retéléchargerait. Conservez-en
une copie sur votre espace de stockage.

La contrepartie est assumée : un dépôt fraîchement cloné ne permet pas de régénérer les
variantes. Si vous préférez l'inverse, retirer une ligne de `.gitignore` suffit — mais il faut
le décider maintenant, avant que l'historique ne se construise.
