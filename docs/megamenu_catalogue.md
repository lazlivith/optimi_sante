# Méga-menu du catalogue — architecture

Écrit pour : Mounir, pour décision avant développement.

---

## 1. Le constat qui commande tout

Votre proposition décrit une taxonomie à trois niveaux — six familles, une
vingtaine de sous-familles, les types de matériel en bout. **Cette taxonomie
n'existe pas en base.**

Mesuré sur la production, le 21 octobre :

| Mesure | Valeur |
|---|---|
| Catégories | **211** |
| Dont racines | **211** — profondeur maximale : **1** |
| Sans aucun produit | **57** |
| À 1 ou 2 produits | **37** |
| Groupes de libellés quasi identiques | **10** |

Parmi ces derniers, des erreurs franches :

- « Accessoires électrocardiographes » **et** « Accessoires électro**p**cardiographes »
  — une coquille a produit une catégorie, qui contient un produit ;
- « Aide à la posture » **et** « Aide pour la posture » ;
- quatre variantes d'« Instrumentation » ;
- « Mobilier de laboratoire » **et** « Mobilier de laboratoire et médical ».

**Le modèle, lui, est prêt.** `Category` porte déjà `parent_id` et
`subcategories`, et `CategoryResponseDto` renvoie l'arbre **récursivement**, avec
le nombre de produits par nœud. Il n'y a rien à construire côté schéma : il y a
une arborescence à **établir**.

### Pourquoi ce n'est pas un détail d'implémentation

Si l'on code le menu avec la taxonomie écrite en dur dans un fichier React :

1. **Deux vérités coexistent.** Le menu annonce « Échographes portables » ; la
   base ne connaît pas cette catégorie. Le lien mène à une page vide, ou il faut
   inventer une correspondance — c'est-à-dire recréer en TypeScript le travail
   d'appariement qui n'a pas été fait en base.
2. **Les 57 catégories vides restent exposées** ailleurs : filtres de recherche,
   fiches produit, administration. Le menu les masque ; il ne les corrige pas.
3. **Chaque import fournisseur ajoute des catégories** qui n'apparaîtront jamais
   au menu, puisqu'il ne les connaît pas.
4. **La coquille « électropcardiographes » reste en production.** Un client la
   verra dans un filtre, un fournisseur dans un export.

> **Ma recommandation : la taxonomie vit en base, le menu la lit.** Un seul
> endroit à corriger, et tout le site en bénéficie — menu, filtres, recherche,
> fil d'Ariane, administration.

---

## 2. Ce qu'il faut faire, dans l'ordre

### Étape A — établir l'arborescence *(données, pas code)*

Vos six familles deviennent **six catégories racines**. Vos sous-familles
deviennent des catégories de niveau 2. **Les 211 catégories actuelles deviennent
le niveau 3**, rattachées par `parent_id`.

Trois décisions à prendre, catégorie par catégorie :

| Cas | Nombre | Décision |
|---|---|---|
| Catégorie pleine à rattacher | ~117 | sous quelle famille ? |
| Catégorie vide | 57 | **supprimer**, ou garder pour un import à venir ? |
| Doublon ou coquille | ~20 | **fusionner** vers la bonne, en déplaçant les produits |

C'est exactement le travail d'appariement resté en suspens — les « 61 à trancher,
68 à relire ». **Je peux produire le fichier d'arbitrage** : un tableur avec, par
catégorie, le nombre de produits, ma proposition de rattachement, et une colonne
vide pour votre décision. C'est le même format que celui prévu pour le catalogue
LCM.

### Étape B — l'outil d'administration

Aujourd'hui, **rien ne permet de déplacer une catégorie sous une autre**. Il faut
soit un écran, soit un script de reprise à partir du tableur validé. Le script est
plus rapide ; l'écran sert ensuite, à chaque nouvel import.

**Je recommande le script d'abord**, et l'écran au lot suivant : vous avez un
import de 7 400 produits en attente, et il ajoutera des catégories.

### Étape C — le méga-menu

Il se code en deux jours une fois l'arborescence posée. **Pas avant** : écrit
d'abord, il figerait une taxonomie imaginaire.

---

## 3. La barre de navigation — indépendante, faisable tout de suite

L'ordre que vous proposez ne dépend d'aucune donnée :

```
Accueil · Catalogue · Nos Services · Formations Médicales · Devenir Partenaire · Promotions [NEW]
```

C'est un changement d'ordre dans `Navbar.tsx`, sans risque. **Il peut partir
avec le prochain déploiement**, indépendamment du méga-menu.

Une remarque de hiérarchie : « Promotions » en dernier avec un badge est le bon
choix. Un badge attire l'œil **parce qu'il est seul** — si « Nouveautés » en
reçoit un demain, les deux cessent d'exister.

---

## 4. Structure des composants

Trois composants, une source de données, aucune taxonomie en dur.

```
Navbar.tsx
└── MegaMenuCatalogue.tsx        ← état d'ouverture, survol, clavier
    ├── useArborescenceCatalogue()   ← lit /catalog/categories, met en cache
    ├── ColonneFamilles.tsx          ← niveau 1
    ├── ColonneSousFamilles.tsx      ← niveau 2
    └── ColonneFeuilles.tsx          ← niveau 3
```

### 4.1 La source de données

```ts
// useArborescenceCatalogue.ts
//
// L'arborescence est la MÊME pour tous les visiteurs et change rarement : elle
// est relevée une fois par session, pas à chaque ouverture du menu. Un menu qui
// déclenche une requête à chaque survol rend le site plus lent à mesure qu'on
// l'explore — exactement l'inverse de ce qu'un menu doit faire.
export function useArborescenceCatalogue() {
  const [arbre, setArbre] = useState<CategorieNoeud[] | null>(null);
  const [erreur, setErreur] = useState(false);

  useEffect(() => {
    let abandonne = false;
    catalogService.categories()
      .then(c => { if (!abandonne) setArbre(elaguer(c)); })
      .catch(() => { if (!abandonne) setErreur(true); });
    return () => { abandonne = true; };
  }, []);

  return { arbre, erreur };
}

/**
 * Retire les branches sans aucun produit.
 *
 * Une catégorie vide au menu est une promesse non tenue : le visiteur clique et
 * trouve une page vide. Tant que les 57 catégories vides existent en base, le
 * menu les écarte — mais c'est un pansement, pas le correctif.
 */
function elaguer(noeuds: CategorieNoeud[]): CategorieNoeud[] {
  return noeuds
    .map(n => ({ ...n, subcategories: elaguer(n.subcategories ?? []) }))
    .filter(n => n.productCount > 0 || n.subcategories.length > 0);
}
```

### 4.2 Le composant de menu

```tsx
// MegaMenuCatalogue.tsx
export function MegaMenuCatalogue() {
  const { arbre, erreur } = useArborescenceCatalogue();
  const [ouvert, setOuvert] = useState(false);
  const [famille, setFamille] = useState<string | null>(null);
  const [sousFamille, setSousFamille] = useState<string | null>(null);
  const conteneur = useRef<HTMLDivElement>(null);

  // Si l'arborescence n'arrive pas, le menu ne s'ouvre PAS : il reste un lien
  // vers /catalogue. Un panneau vide ferait croire à un catalogue vide.
  if (erreur || !arbre?.length) {
    return <Link to="/catalogue">Catalogue</Link>;
  }
  // …
}
```

### 4.3 Les cinq points d'ergonomie qui décident du reste

**L'intention de survol.** Ouvrir au premier pixel survolé fait clignoter le
menu quand la souris ne fait que traverser. Un délai de 120 ms à l'ouverture et
de 250 ms à la fermeture suffit. Le délai de fermeture est le plus important :
sans lui, la souris qui coupe un angle pour atteindre la colonne suivante ferme
le menu.

**Le clavier, à parité.** `Entrée` ou `Espace` ouvre, les flèches haut et bas
parcourent une colonne, la flèche droite entre dans la suivante, `Échap` ferme et
rend le focus au bouton. Un méga-menu uniquement au survol est inutilisable au
clavier — et, pour un site qui vend du matériel médical à des établissements
publics, l'accessibilité n'est pas optionnelle.

**Le mobile n'est pas le même composant.** À 375 px, trois colonnes n'existent
pas. `MenuMobile.tsx` existe déjà : l'arborescence s'y déplie en accordéon, un
niveau à la fois, avec un bouton retour. **Ne partagez pas le composant, partagez
la source de données.**

**« Découvrir la page Cardiologie ».** Votre capture le montre, et c'est juste :
chaque famille doit offrir un accès à sa page complète. Sans lui, le visiteur qui
veut tout voir doit deviner.

**Le compte de produits.** Vous l'avez déjà (`productCount`) : l'afficher en gris
à côté de chaque entrée. Cela dit au visiteur où il y a quelque chose à voir, et
cela vous signale vos catégories faméliques — ce que la mesure du § 1 a révélé.

---

## 5. Ce que je recommande

| | Quoi | Quand |
|---|---|---|
| 1 | **Réordonner la barre de navigation** | prochain déploiement, sans risque |
| 2 | **Fichier d'arbitrage des 211 catégories** | je le produis dès votre accord |
| 3 | Votre arbitrage, puis script de rattachement | après le webinaire |
| 4 | **Méga-menu** | 2 jours, une fois l'arborescence posée |

**Point 2 : donnez-moi le feu vert et je lance.** C'est un tableur, aucun code,
aucun déploiement — et il débloque aussi l'import LCM, qui ajoutera ses propres
catégories dans une structure enfin tenable.
