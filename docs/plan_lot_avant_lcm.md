# Plan du lot « avant La Centrale Médicale »

*Branche `feature/stock-avant-lcm`. Plan soumis avant tout code. 27 octobre 2026.*

Objectif : qu'un import de 7 400 références ne puisse ni effacer un comptage, ni rendre le
catalogue invendable au fil des ventes.

---

## 1. Migrations

Trois migrations. **Les numéros définitifs seront pris au moment de la fusion**, pas
maintenant : une autre branche peut réserver V80 entre-temps. `V79` n'est pas touchée.

### `V80__mode_stock.sql`

```sql
ALTER TABLE products ADD COLUMN mode_stock varchar(20) NOT NULL DEFAULT 'NON_SUIVI';
ALTER TABLE products ADD CONSTRAINT products_mode_stock_check
    CHECK (mode_stock IN ('NON_SUIVI', 'PROPRE', 'FOURNISSEUR'));
```

Aucune donnée réécrite : le `DEFAULT` pose `NON_SUIVI` sur les 1 497 produits, ce qui est
leur état réel. `stock_quantity` est conservé tel quel, à titre indicatif.

### `V81__journal_stock_contraintes.sql`

Trois choses sur la table créée par V79, toutes en `ALTER` :

1. **Trigger d'immuabilité** — `BEFORE UPDATE OR DELETE ON stock_mouvements`, qui lève. Une
   pièce comptable ne se réécrit pas. *Porte de sortie assumée :* une migration future peut
   `DROP TRIGGER`, agir, puis le recréer — c'est explicite et tracé, contrairement à un
   `save()` ajouté par inadvertance.
2. **`auteur_id` en `ON DELETE SET NULL`** — `DROP CONSTRAINT` puis `ADD CONSTRAINT`. Par
   propreté : la gouvernance anonymise plutôt qu'elle ne supprime, mais une suppression
   physique ne doit pas être bloquée par le journal.
3. **Index unique partiel** pour l'idempotence du retour :
   `(order_id, product_id) WHERE motif = 'ANNULATION'`. L'idempotence est ainsi garantie **par
   la base**, pas par une relecture applicative sujette aux courses. Aucune table nouvelle.

### `V82__commandes_dates.sql`

```sql
ALTER TABLE orders ADD COLUMN paid_at    timestamptz;
ALTER TABLE orders ADD COLUMN shipped_at timestamptz;
UPDATE orders SET paid_at = created_at WHERE payment_status = 'PAID' AND paid_at IS NULL;
```

`shipped_at` reste vide pour l'existant, comme décidé.

---

## 2. Fichiers touchés

### Nouveaux

| Fichier | Rôle |
|---|---|
| `catalog/entity/ModeStock.java` | L'énumération, et la règle `estSuivi()` en un seul endroit |
| `catalog/supplier/ValidationLigneCatalogue.java` | Les règles qu'une ligne de fichier doit tenir, et le motif en clair quand elle ne les tient pas |

### Modifiés — domaine du stock

| Fichier | Changement |
|---|---|
| `catalog/entity/Product.java` | `modeStock`, `@Enumerated(STRING)`, défaut `NON_SUIVI` |
| `catalog/entity/MotifMouvementStock.java` | `MISE_A_JOUR_FOURNISSEUR` ajouté |
| `catalog/service/StockService.java` | `majFournisseur()` ; `sortieVente()` et `retourAnnulation()` ne font rien hors `PROPRE` ; idempotence du retour |
| `catalog/repository/MouvementStockRepository.java` | `existsByOrderIdAndProductIdAndMotif(...)` |
| `catalog/web/AdminStockResource.java` | `auditService.record` sur l'ajustement et sur le seuil |

### Modifiés — vente

| Fichier | Changement |
|---|---|
| `catalog/service/StockReservationService.java` | Sortie immédiate si le mode n'est pas `PROPRE` : ni verrou, ni réservation, ni refus — devis de 24 h compris |
| `orders/entity/Order.java` | `paidAt`, `shippedAt` |
| `orders/service/OrderService.java` | Réservation et décrément conditionnés au mode ; `paidAt` posé au paiement ; `shippedAt` posé au passage `SHIPPED` ; remise en stock à `REFUNDED` et à `CANCELLED` |

### Modifiés — import

| Fichier | Changement |
|---|---|
| `catalog/supplier/EcrivainCatalogue.java` | **Plus aucune écriture directe de `stock_quantity`** ; création en `FOURNISSEUR` avec stock 0 puis `majFournisseur()` ; mise à jour : rien si le produit est `PROPRE`, sinon `majFournisseur()` ; validation avant écriture ; `try/catch` par ligne |
| `catalog/supplier/TraitementImportCatalogue.java` | Un lot en échec est signalé et **l'import continue** au lot suivant |

### Modifiés — exposition

| Fichier | Changement |
|---|---|
| `catalog/dto/ProductResponseDto.java`, `AdminProductResponseDto.java`, `AdminProductRow.java` + sa requête native, `CatalogService.java`, `AdminCatalogService.java` | `modeStock` exposé |
| `frontend/src/api/catalogService.ts`, `adminCatalogService.ts` | Le champ côté écran |
| `frontend/src/pages/ProductDetailPage.tsx` | « Indisponible à la commande » conditionné à `modeStock === 'PROPRE'` |
| `frontend/src/pages/admin/AdminStockPage.tsx` | Colonne « Mode », en lecture seule dans ce lot |

---

## 3. Ordre de travail

Chaque étape laisse la suite verte. L'ordre n'est pas négociable : il évite une fenêtre où
l'import écrirait du stock sans mode, ou l'inverse.

1. **Migrations et entités** — `ModeStock`, champ sur `Product`, dates sur `Order`. Rien ne
   change de comportement : la suite doit rester verte telle quelle.
2. **Garde dans `StockService` et `StockReservationService`** — hors `PROPRE`, on ne fait
   rien. *Test d'abord* : un produit `NON_SUIVI` n'est ni vérifié ni décrémenté à travers
   `OrderService`.
3. **`OrderService`** — dates de paiement et d'expédition, puis remise en stock à l'annulation
   et au remboursement. *Test d'abord* : un remboursement reçu deux fois ne remet le stock
   qu'une fois.
4. **Trigger d'immuabilité et audit des gestes manuels.** *Test* : `UPDATE` et `DELETE`
   refusés en base.
5. **Import** — validation par ligne, puis suppression des écritures directes, puis tolérance
   au lot en échec. *Tests* : ligne fautive au milieu, et import qui ne touche pas un `PROPRE`.
6. **Exposition `modeStock`** et affichage. *Vérification réelle* au navigateur.
7. **Deux paiements simultanés** — écrit en dernier parce que c'est le test le plus fragile,
   et qu'il doit s'appuyer sur le code définitif.

---

## 4. Points qui demandent une décision

1. **La création d'un produit à l'import écrit-elle un mouvement ?** Pour respecter « plus
   aucune écriture directe », je propose de créer le produit à **stock 0**, puis d'appeler
   `majFournisseur()`. Conséquence : **7 400 mouvements** au premier import de La Centrale
   Médicale. C'est traçable et cohérent, mais cela remplit le journal d'un coup.
   *Recommandation : oui, écrire le mouvement.* L'alternative — écrire la quantité initiale
   sans mouvement — rouvre exactement la brèche qu'on ferme.

2. **Le remboursement partiel remet-il du stock ?** Non, dans ma proposition : seul le passage
   à `REFUNDED` (remboursement intégral) et l'annulation déclenchent le retour. Un
   remboursement partiel n'indique pas quelle ligne revient. *À confirmer.*

3. **`AdminCatalogService:121` écrit aussi `stock_quantity` directement**, depuis le
   formulaire produit de l'administration. Ce n'est pas dans ta consigne, mais c'est la même
   brèche que celle de l'import : un stock compté peut être écrasé sans mouvement ni verrou.
   Je propose de le router vers `StockService.inventorier()`. *Un à deux jours de plus.*

---

## 5. Risques

- **Le test des deux paiements simultanés** ne peut pas être `@Transactional` : il lui faut
  deux transactions réellement concurrentes, donc deux fils et des écritures validées. C'est
  le test le plus susceptible d'être instable ; s'il l'est, il sera traité comme une dette, pas
  toléré.
- **Le trigger d'immuabilité** empêchera aussi toute correction légitime du journal. C'est
  voulu ; la porte de sortie est documentée dans la migration elle-même.
- **L'index unique partiel** sur `ANNULATION` suppose qu'une ligne de commande ne revient
  qu'une fois. Un retour partiel ultérieur demandera de revoir cette clé.
- **Aucune donnée de production n'est réécrite** par ce lot, hors `paid_at` rétro-rempli à
  `created_at` sur les commandes déjà payées.

---

## 6. Charge et déploiement

**12 à 16 jours**, tests compris.

Déploiement : sauvegarde de la base **avant** la migration, puis accord explicite. Les trois
migrations partent ensemble ; il n'y a pas d'état intermédiaire cohérent entre elles.
