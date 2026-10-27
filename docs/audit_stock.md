# Audit de conformité du module Stock en production

*27 octobre 2026. Lecture seule, aucun code écrit. Module audité : `e2a4626`, déployé le
26 octobre, migration `V79__stock.sql` appliquée en production le 26/10/2026 à 17:04:06.*

---

## Correction préalable : les « 10 fauteuils releveurs » n'existent pas en production

J'ai affirmé à plusieurs reprises que **10 produits à stock 0 étaient invendables**, dont dix
fauteuils releveurs, pour 4 697 € de catalogue. **C'est faux en production.** Ce chiffre vient
de la base de développement locale, pas de la base de production.

Mesure faite aujourd'hui sur l'API publique de production, 1 497 produits actifs :

| Stock | Produits |
|---|---|
| 50 | 1 464 |
| 49 | 14 |
| 48 / 47 / 45 | 1 chacun |
| **0** | **0** |

Les 27 fauteuils releveurs présents en production sont à 50, 49 ou 45. **Aucun produit n'est
aujourd'hui bloqué par son stock.** La consigne du prompt S1 « les 10 fauteuils releveurs
redeviennent commandables » est donc sans objet, et le risque commercial que j'avais classé en
tête disparaît.

Ce qui reste vrai : **1 464 produits sur 1 497 portent le 50 par défaut de l'import**, aucun
n'a jamais été compté, et le module traite ce chiffre comme s'il était vrai.

---

## A. Conformité au prompt S1 (lot 1)

| № | Consigne S1 | Statut | Preuve | Ce qui manque ou diffère |
|---|---|---|---|---|
| 1.1 | `products.mode_stock` (NON_SUIVI / PROPRE / FOURNISSEUR), NOT NULL DEFAULT, CHECK | **ABSENT** | `V79__stock.sql` n'ajoute que `stock_inventorie_le` ; aucun champ de mode dans `Product.java` | Tout. Le module n'a pas de notion de mode : il n'existe qu'une date de dernier comptage, qui n'a **aucun effet** sur la vente |
| 1.2 | `stock_quantity` conservé, aucune donnée réécrite | **CONFORME** | `V79__stock.sql` : un seul `ALTER TABLE ... ADD COLUMN` | — |
| 1.3 | Table `entrepots`, une ligne FR-01 | **ABSENT** | Aucune table `entrepot*` dans `V79` ni en base | Tout |
| 1.4 | Journal des mouvements avec `entrepot_id` NOT NULL | **PARTIEL** | Table `stock_mouvements` créée ; colonnes : `product_id`, `delta`, `stock_apres`, `motif`, `order_id`, `auteur_id`, `commentaire`, `created_at` | Pas d'`entrepot_id`. Le journal est mono-entrepôt implicite |
| 1.5 | Journal sans modification ni suppression possibles, protection en base | **PARTIEL** | Aucune route d'écriture dans `AdminStockResource` ; **aucun trigger** dans `V79` | Protégé par absence de route seulement. `MouvementStockRepository` hérite de `save()` et `delete()` : un futur appel passerait sans résistance |
| 1.6 | Journal : quantité **avant** et après | **PARTIEL** | `stock_apres` + `delta` en base | `quantite_avant` absente ; elle se déduit (`apres − delta`) mais n'est pas lisible directement |
| 1.7 | Journal : auteur, date, référence, motif | **CONFORME** | `MouvementStock.java` : `auteur`, `createdAt`, `orderId`, `motif`, `commentaire` | — |
| 1.8 | Numérotation : V79 réservée aux règles de statut | **CONTRAIRE** | `V79__stock.sql` appliquée en production le 26/10 | V79 est prise et **intouchable** désormais. Les règles de statut prendront V80+ |
| 2.1 | NON_SUIVI et FOURNISSEUR : ni vérification, ni réservation, ni décrément, ni verrou | **ABSENT** | `OrderService:143` appelle `reserveStock()` pour **tout** produit ; `OrderService:497` appelle `sortieVente()` pour **tout** produit | Aucun produit n'échappe au contrôle. Les 1 464 produits au stock fictif de 50 sont vérifiés, réservés et décrémentés comme s'ils étaient comptés |
| 2.2 | PROPRE : comportement actuel inchangé (verrou, plancher, journal) | **CONFORME** | `StockService.appliquer()` : `findByIdWithPessimisticLock`, `PLANCHER = 0`, écriture du mouvement | Appliqué à tous les produits, pas seulement aux comptés |
| 2.3 | Réservations de devis de 24 h sans effet pour NON_SUIVI / FOURNISSEUR | **ABSENT** | `OrderService:142` : `ttlMinutes = isQuote ? 24*60 : 30`, sans condition de mode | Un devis bloque 24 h du stock fictif |
| 2.4 | Les 10 fauteuils redeviennent commandables | **SANS OBJET** | Aucun produit à stock 0 en production (voir correction ci-dessus) | — |
| 2.5 | Aucun changement sur prix, remise B2B, TVA, reçu, facture | **CONFORME** | `e2a4626` ne touche aucun fichier de tarification ni de facturation | — |
| 3.x | Inventaire initial : export de feuille de comptage, import, aperçu, date et heure de comptage, déduction des ventes | **ABSENT** | `AdminStockResource` expose 6 routes : liste, résumé, mouvements, historique, ajustement unitaire, seuil. Aucun export, aucun import | Tout l'inventaire de masse. `StockService.inventorier()` existe mais **produit par produit**, sans date de comptage ni déduction des ventes |
| 3.y | Réservé à SUPER_ADMIN et ADMIN_ECOMMERCE, inscrit à l'audit | **PARTIEL** | `@EcommerceAdmin` sur `AdminStockResource:40`, qui couvre `ADMIN_ECOMMERCE`, `ADMIN`, `SUPER_ADMIN` | Rôle conforme. **Aucun appel à `auditService.record`** dans `AdminStockResource` : les ajustements manuels ne sont pas au journal d'audit |
| 4 | L'import fournisseur ne passe jamais un produit en PROPRE, et ne touche pas un stock compté | **CONTRAIRE** | `EcrivainCatalogue:248-250` : `if (ligne.stock() != null) produit.setStockQuantity(ligne.stock())` puis `productRepository.save()` | L'import **écrase directement** `stock_quantity`, sans passer par `StockService`, **sans mouvement, sans verrou, sans trace**. Un inventaire compté le matin est effacé par l'import du soir |
| 5 | Affichage public : rien pour NON_SUIVI / FOURNISSEUR ; « En stock » / « Indisponible » pour PROPRE | **PARTIEL** | `ProductDetailPage.tsx` : `Disponibilite` n'affiche que « Indisponible à la commande » si `stockQuantity < 1` ; aucun badge positif | Conforme par accident : comme rien n'est compté, ne rien afficher est juste. Mais la règle est « stock = 0 », pas « produit suivi » : le jour où un comptage donnera 0, le produit s'affichera indisponible — ce qui sera vrai. Le DTO public **n'expose aucun mode** |
| 6 | Tests du chemin réel (6 cas) | **PARTIEL** | `StockServiceTest` : 12 tests (vente, plancher, inventaire, alertes, retour d'annulation, variation nulle) | Absents : **deux paiements simultanés**, produit NON_SUIVI non décrémenté, import d'inventaire, immuabilité du journal, import fournisseur qui n'écrit pas PROPRE. Aucun test ne traverse `OrderService` |
| 7 | Vérification en production après déploiement | **NON FAIT** | Aucune trace | Le module est en ligne sans qu'un parcours d'achat réel ait été vérifié |

### Points c) et d) demandés explicitement

**c) Annulation et remboursement.** `StockService.retourAnnulation()` existe et est testé —
mais **il n'est appelé nulle part** (`grep` sur tout `src/main` : zéro appel hors de sa propre
déclaration). `OrderService` ne contient aucune logique d'annulation, et le passage en
`REFUNDED` (`OrderService:411`) ne touche pas au stock. **Aucune remise en stock n'a lieu, et
la question de l'idempotence ne se pose donc pas encore.**

**d) Journal et RGPD.** `auteur_id uuid REFERENCES users(id)` sans `ON DELETE` : une
suppression physique d'utilisateur serait refusée par la clé étrangère. **Le risque est
toutefois théorique** : la gouvernance RGPD procède par **anonymisation**, pas par
suppression (`GovernanceService:40`), et l'auteur d'un mouvement est un administrateur, pas un
client. À traiter par propreté, pas en urgence.

---

## B. Conformité au prompt S2 (espace Stock complet)

S2 est une **demande d'analyse**, pas une commande de réalisation : « ABSENT » y est
l'état normal et non un défaut. Ce tableau sert à mesurer la distance entre le module livré et
la cible, lot par lot.

| № S2 | Domaine | Statut | Preuve / ce qui existe déjà |
|---|---|---|---|
| 0 | Réseau logistique (FR-01, fournisseurs, Congo) | **ABSENT** | Aucune notion d'entrepôt |
| 1 | Prix d'achat par produit | **CONFORME** | `Product.purchasePrice`, renseigné par l'import (`EcrivainCatalogue:221`) |
| 1 | `StockReservationService` : durées et verrous | **CONFORME** | 30 min achat / 24 h devis, verrou pessimiste |
| 1 | Statuts de commande et d'expédition | **PARTIEL** | `OrderStatus` (6 valeurs) et `payment_status` existent ; **ni `paid_at` ni `shipped_at`** |
| 1 | Devises | **PARTIEL** | FCFA affiché à parité fixe ; aucune grille de prix propre |
| 2 | Stock par entrepôt et par lot | **ABSENT** | `stock_quantity` est un entier unique par produit |
| 2 | Quantités réservée / quarantaine / transit | **PARTIEL** | Réservé existe via `stock_reservations` ; quarantaine et transit absents |
| 2 | Mouvement obligatoire dans la même transaction | **PARTIEL** | Vrai pour `StockService` ; **faux pour l'import fournisseur**, qui écrit sans mouvement |
| 2 | 13 types de mouvements | **PARTIEL** | 4 motifs : `VENTE`, `REAPPROVISIONNEMENT`, `INVENTAIRE`, `CORRECTION` |
| 3 | Achat et réapprovisionnement | **ABSENT** | — |
| 4 | Réception, bon de réception, écarts | **ABSENT** | — |
| 5 | Emplacements, lots, péremption, quarantaine | **ABSENT** | — |
| 6 | Transferts, transit, coût de revient rendu, documents douaniers | **ABSENT** | — |
| 7 | Allocation par entrepôt, retrait atomique, idempotence | **PARTIEL** | Retrait atomique et verrou présents ; **idempotence non traitée**, allocation sans objet |
| 7 | Affichage par zone de livraison | **ABSENT** | Un seul affichage, sans zone |
| 8 | Préparation, bon de préparation | **ABSENT** | — |
| 9 | Expédition, suivi, bons fournisseurs, commandes mixtes | **ABSENT** | — |
| 10 | Retours, quarantaine, rappels de lot | **ABSENT** | — |
| 11 | Inventaire tournant et de fin d'exercice | **ABSENT** | Seul l'ajustement unitaire existe |
| 12 | Valorisation, export comptable | **ABSENT** | — |
| 13 | Stock fournisseur (fraîcheur, délai) | **ABSENT** | L'import écrase une quantité, sans date ni validité |
| 14 | Écran Stock | **PARTIEL** | `AdminStockPage.tsx` (476 lignes) : résumé (suivis, rupture, sous seuil, jamais inventoriés, unités en stock, unités réservées), tableau filtrable, historique, ajustement, seuil. **Pas d'export, pas de sélecteur d'entrepôt, pas de pagination serveur** |
| 15 | Alerte quotidienne par e-mail | **PARTIEL** | Alerte **immédiate** au franchissement (`signalerSiCritique`) + sonde horaire `STOCK_CRITIQUE`, **désactivée** par `V79` |
| 16 | Rôle MAGASINIER | **ABSENT** | 8 rôles, aucun magasinier |
| 17 | Décisions | **SANS OBJET** | À trancher par le commanditaire |

---

## C. Écarts classés par gravité

### 1 — Fausse le stock, et efface un comptage *(bloquant avant La Centrale Médicale)*

**L'import fournisseur écrase `stock_quantity` sans passer par `StockService`.**
`EcrivainCatalogue:248` écrit directement et sauvegarde. Conséquences : aucun mouvement au
journal, aucun verrou (course avec une vente simultanée), et surtout **un inventaire compté
est effacé sans trace par l'import suivant**. C'est le défaut le plus grave : il annule le
travail du lot 1 au premier import.

### 2 — Fait perdre des ventes, sur des chiffres inventés

**Tout produit est traité comme si son stock était compté.** Il n'existe pas de mode. Les
1 464 produits à 50 sont vérifiés, réservés 30 minutes (24 heures sur un devis) et
décrémentés. Aujourd'hui sans dommage visible, mais : au 50ᵉ exemplaire vendu d'une référence
jamais comptée, elle devient invendable ; et une commande de gros de 60 unités est refusée par
une limite qui n'existe pas. Le défaut est **latent, et il mûrit à chaque vente**.

### 3 — Fausse le stock à chaque remboursement

**Aucune remise en stock à l'annulation ou au remboursement.** La méthode existe, personne ne
l'appelle. Dès qu'un stock sera réellement compté, chaque remboursement fera diverger le
compte, sans alerte. Et l'idempotence demandée n'a aucun support : rien n'empêche un double
appel une fois le branchement fait.

### 4 — Problème légal et de preuve

**Le journal n'est pas immuable.** Il est protégé par l'absence de route, pas par la base. Un
journal de mouvements de stock est une pièce comptable : sa valeur tient à ce qu'il ne puisse
pas être réécrit. Un `save()` ou un `delete()` ajouté par inadvertance suffirait.

**Les ajustements manuels ne sont pas au journal d'audit.** `AdminStockResource` n'appelle
jamais `auditService`. Un administrateur peut corriger un stock sans que l'action apparaisse
dans l'audit de la plateforme — alors que c'est exactement le genre de geste qu'on veut
pouvoir retracer.

### 5 — Empêche la suite

**Pas d'entrepôt.** Ni table, ni colonne sur le journal. L'entrepôt Congo, les transferts et
la valorisation par site supposent de reprendre `stock_mouvements` et toute lecture de stock.
Plus on écrit de mouvements sans entrepôt, plus la reprise coûte cher.

**Pas de `paid_at` ni `shipped_at`.** Sans elles, la déduction des ventes pendant un comptage
est impossible, et l'inventaire initial ne peut pas être exact.

### 6 — Couverture de test

Aucun test ne traverse `OrderService` : les 12 tests portent sur `StockService` isolé. Le cas
le plus dangereux — **deux paiements simultanés du dernier exemplaire** — n'est pas couvert, et
c'est précisément celui que le verrou pessimiste est censé tenir.

---

## D. Corrections proposées, en lots livrables

Aucune migration appliquée n'est modifiée : tout part de **V80**.

| Lot | Contenu | Migrations | Charge estimée |
|---|---|---|---|
| **1b — Intégrité** *(avant LCM)* | L'import fournisseur passe par `StockService` ou ne touche pas au stock d'un produit compté ; trigger d'immuabilité sur `stock_mouvements` ; audit sur les gestes manuels de `AdminStockResource` ; `auteur_id` en `ON DELETE SET NULL` | V80 | 4–6 j |
| **1c — Mode de stock** | `mode_stock` (NON_SUIVI / PROPRE / FOURNISSEUR) + CHECK ; `OrderService` et `StockReservationService` conditionnés au mode ; `modeStock` exposé dans les DTO ; import fournisseur → FOURNISSEUR ; activation et désactivation du suivi depuis la fiche produit | V81 | 6–8 j |
| **1d — Remboursement** | Branchement de `retourAnnulation` sur l'annulation et le remboursement, idempotent par ligne de commande et par identifiant de paiement | V82 (table d'idempotence) | 3–4 j |
| **1e — Entrepôt et dates** | Table `entrepots` + ligne FR-01 ; `entrepot_id` sur `stock_mouvements` (rétro-rempli à FR-01) ; `quantite_avant` ; `orders.paid_at` et `shipped_at` renseignés désormais | V83 | 4–5 j |
| **1f — Inventaire initial** | Export CSV et XLSX (cellules forcées en texte sauf la quantité comptée), import avec date et heure de comptage, aperçu, déduction des ventes par le journal des mouvements, lignes rejetées numérotées | — | 8–10 j |
| **1g — Tests du chemin réel** | Deux paiements simultanés, produit non suivi non décrémenté, double remboursement, refus d'écriture sur le journal, import qui n'écrit pas PROPRE | — | 4–5 j |

**Total : 29 à 38 jours** pour amener le module au niveau du prompt S1.

---

## E. Ce qui doit être corrigé AVANT le premier import de La Centrale Médicale

Par ordre de nécessité :

1. **L'écrasement du stock par l'import** (lot 1b). Sans cela, l'import de 7 400 références
   écrira 7 400 quantités fournisseur dans `stock_quantity` sans aucun mouvement, et effacera
   tout comptage déjà fait. C'est le seul point réellement bloquant.
2. **Le mode de stock** (lot 1c). Sans lui, les 7 400 produits de La Centrale Médicale seront
   traités comme du stock propre : réservés, décrémentés, et refusés en rupture alors que
   c'est le fournisseur qui expédie. Le catalogue se bloquerait tout seul au fil des ventes.
3. **La validation ligne à ligne de l'import** (déjà cadrée le 23 octobre, non faite) : une
   ligne au slug invalide arrête aujourd'hui l'import entier et perd les suivantes.

Le reste — entrepôts, inventaire initial, remboursement, immuabilité — peut suivre l'import
sans l'empêcher, à condition de ne pas lancer d'inventaire avant que le point 1 soit corrigé.

---

## F. Questions ouvertes pour le commanditaire

1. **Que faire des 1 464 stocks à 50 ?** Les passer en NON_SUIVI à la migration (recommandé :
   c'est l'état réel), ou les laisser tels quels jusqu'à l'inventaire.
2. **L'alerte `STOCK_CRITIQUE` reste-t-elle désactivée** jusqu'au premier inventaire ? C'est
   ce que prévoit S1, et c'est cohérent : alerter sur 1 464 chiffres fictifs noierait le
   signal.
3. **Le module en production peut-il rester en l'état pendant les lots 1b à 1g ?** Aucun
   produit n'étant à 0 et aucun inventaire n'ayant été fait, le risque immédiat est faible —
   mais il croît à chaque vente.
