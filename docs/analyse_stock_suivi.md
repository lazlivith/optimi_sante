# Un indicateur « stock suivi » par produit

*Analyse, 23 octobre 2026. Aucun code écrit. Pour décision.*

## Ce que le serveur fait aujourd'hui

Trois endroits lisent `stock_quantity`, et tous trois agissent comme si le chiffre
était vrai :

1. **À la commande** — `StockReservationService.reserveStock()` pose un verrou pessimiste sur
   le produit, calcule `stock_quantity − réservations actives`, et lève
   `InsufficientStockException` si la quantité demandée dépasse. Réservation de 30 minutes
   pour un achat, **24 heures pour un devis**.
2. **Au paiement** — `OrderService` décrémente `stock_quantity` de la quantité payée, sans
   condition, et supprime les réservations.
3. **À l'écran** — depuis le déploiement de ce soir, seul subsiste « Indisponible à la
   commande » sur un produit à zéro.

## Ce que vaut le chiffre

| Mesure | Valeur |
|---|---|
| Produits actifs | 1 497 |
| Produits à `stock_quantity = 50` (défaut d'import) | **1 468** |
| Produits à une autre valeur | 29 |
| Mouvements de stock enregistrés | **aucune table, aucun journal** |
| Entrées d'audit sur un produit ou un stock | **0** |

Sur les 29 valeurs différentes de 50, **19 s'expliquent entièrement par les ventes d'essai** :
`stock_quantity + quantité commandée = 50` à l'unité près. Personne n'a jamais saisi un stock
à la main ; le chiffre n'est que le défaut d'import érodé par des commandes fictives.

## Deux conséquences, dont une déjà réelle

**Dix produits sont invendables, et aucun ne l'est à cause d'une rupture.** Les dix produits à
zéro n'ont **jamais été commandés** — leur stock n'a donc jamais été décrémenté. Leur zéro
vient directement de l'import : `EcrivainCatalogue` écrit `ligne.stock() != null ?
ligne.stock() : 0`, et le fichier du fournisseur **PHARMA-EXPRESS-CONGO** n'apporte pas cette
colonne. Ce sont dix fauteuils releveurs, de 107 € à 908 €, **4 697 € de catalogue** qu'aucun
client ne peut commander parce qu'une colonne manquait dans un tableur.

**Le défaut s'aggrave tout seul.** Chaque vente payée décrémente un chiffre inventé. Onze
produits sont déjà passés de 50 à 49. Au cinquantième exemplaire vendu d'une référence qu'on
n'a jamais comptée, elle deviendra invendable — sans alerte, et sans que rien ne manque en
entrepôt.

**Et une commande de gros se heurte au plafond.** Optimi Santé vend en volume : une demande
de 60 unités sur un produit « à 50 » est refusée au moment où le client s'engage, par une
limite qui n'existe pas.

## L'indicateur proposé

`products.stock_suivi boolean not null default false`.

- **Faux** — ni réservation, ni vérification, ni décrément. Le produit est commandable en
  toute quantité, et `stock_quantity` cesse d'être lu. L'écran n'affiche rien sur la
  disponibilité, ce qui est déjà le cas.
- **Vrai** — exactement le comportement actuel, inchangé : réservation, refus si insuffisant,
  décrément au paiement, et « Indisponible à la commande » à zéro.

Le champ dit **« ce chiffre est tenu à jour »**, pas « ce produit est en stock ». C'est la
distinction qui manquait : aujourd'hui, l'absence d'inventaire et une rupture réelle
s'écrivent de la même façon.

### Valeur par défaut recommandée

**Pour l'existant : `false` pour les 1 497 produits.** Aucun n'a d'inventaire tenu — c'est un
constat, pas une prudence. Mettre `true` par défaut reconduirait le défaut en lui donnant un
nom. Les dix fauteuils redeviennent commandables le jour de la migration.

**Pour l'import de La Centrale Médicale : `false`, sans exception.** Et une règle à tenir :
l'import ne doit **jamais** passer un produit à `true`. Un fournisseur qui envoie une colonne
de stock envoie une photographie de **son** entrepôt, mensuelle, pas un inventaire du nôtre.
Le passage à `true` est une décision d'exploitation, prise produit par produit depuis
l'administration le jour où quelqu'un compte vraiment.

Corollaire : `stock_quantity` venu d'un fournisseur peut continuer à être écrit — il devient
une information indicative, plus une contrainte.

### Migration

`V79__stock_suivi.sql` (V79 était réservée aux règles de statut des demandes
d'accompagnement : à renuméroter, ou celle-ci prend V80).

```sql
ALTER TABLE products ADD COLUMN stock_suivi boolean NOT NULL DEFAULT false;
```

Une seule colonne, un seul défaut, **aucune donnée réécrite** : la migration est réversible
par un `DROP COLUMN`, et rien de ce qui existe n'est perdu. `stock_quantity` reste en place,
avec ses valeurs actuelles.

### Impact sur le paiement

| Étape | `stock_suivi = false` | `stock_suivi = true` |
|---|---|---|
| Ajout au panier | inchangé | inchangé |
| `reserveStock` au passage de commande | **non appelé** | inchangé |
| Verrou pessimiste sur la ligne produit | **non posé** | inchangé |
| Refus pour stock insuffisant | **impossible** | inchangé |
| Décrément au paiement | **non effectué** | inchangé |
| Montant, TVA, reçu, facture | **aucun changement** | aucun changement |

Rien de ce qui touche à l'argent ne bouge : le calcul du prix, la remise B2B, la TVA et le
reçu ne lisent pas le stock. Deux effets de bord à noter, tous deux favorables :

- **Moins de verrous.** Le verrou pessimiste disparaît pour 1 497 produits sur 1 497. Deux
  clients qui commandent la même référence ne s'attendent plus l'un l'autre.
- **Les réservations de devis de 24 heures disparaissent.** Elles bloquaient du stock
  fictif pendant une journée entière sur une simple demande de prix.

Un risque, et il faut le nommer : sur un produit passé à `true` plus tard, **une survente
redevient possible pendant la fenêtre où le chiffre est faux**. C'est pourquoi le passage à
`true` doit suivre un comptage, et non le précéder.

## Ce qui reste à décider

1. Le numéro de migration (V79 ou V80), selon ce qu'on fait des règles de statut.
2. Où se pilote l'indicateur dans l'administration : une case sur la fiche produit, et
   probablement une action groupée — on ne cochera pas 1 497 cases le jour où un inventaire
   existera.
3. Si une alerte doit prévenir qu'un produit `true` approche de zéro. `alert_thresholds`
   existe déjà ; c'est un chantier séparé.
