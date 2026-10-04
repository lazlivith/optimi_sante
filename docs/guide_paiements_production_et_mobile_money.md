# Paiements : passage de Stripe en production, et ajout du mobile money (pawaPay)

**Rédigé le 4 octobre 2026.** Deux chantiers distincts, à mener dans cet ordre : Stripe en
production d'abord, parce qu'il est déjà intégré et qu'il ne reste qu'à le basculer ; le mobile
money ensuite, parce qu'il faut l'écrire.

> **Ce guide s'appuie sur l'état réel du code au 4 octobre 2026.** Les chemins de fichiers, noms
> de classes et noms de variables qui y figurent ont été vérifiés dans le dépôt. Les éléments
> venant des documentations Stripe et pawaPay sont sourcés en fin de document ; ces API bougent,
> vérifiez les points marqués **[à confirmer]** au moment où vous les traiterez.

---

## Sommaire

- [Partie A — Stripe en production](#partie-a--stripe-en-production)
- [Partie B — Mobile money via pawaPay](#partie-b--mobile-money-via-pawapay)
- [Partie C — Les pièges transverses](#partie-c--les-pièges-transverses)
- [Partie D — Les listes à cocher](#partie-d--les-listes-à-cocher)

---

# Partie A — Stripe en production

## A.0 — Ce qui existe déjà dans le code

| Élément | Emplacement |
|---|---|
| Création de la session de paiement | `backend/.../domain/orders/service/StripePaymentService.java` |
| Réception des événements | `backend/.../domain/orders/controller/PaymentWebhookResource.java` |
| Aiguillage par type de paiement | `backend/.../domain/orders/webhook/StripePaymentDispatcher.java` |
| Formulaire de paiement | `frontend/src/components/payment/StripeEmbeddedCheckout.tsx` |
| Clés serveur | `stripe.api-key`, `stripe.webhook-secret` dans `application.yml` |
| Clé navigateur | `VITE_STRIPE_PUBLISHABLE_KEY` |

Le profil `prod` de `application.yml` **n'a volontairement aucun repli** sur ces clés : le serveur
refuse de démarrer si elles manquent, plutôt que de tourner sur une clé de test en laissant croire
que les paiements passent. C'est un comportement voulu — ne le contournez pas.

## A.1 — Activer le compte Stripe (à faire avant tout le reste)

Un compte Stripe en mode test ne peut pas encaisser. Il faut d'abord l'activer, et cela prend
du temps côté Stripe (vérification d'identité). Préparez :

- **Les informations de la société** : HOLDING GUIDON, SAS, SIRET 937 848 869 00023, adresse du
  siège, code NAF, numéro de TVA FR24937848869.
- **L'identité du représentant légal** : Andoche Guide Varin Tchiloemba, pièce d'identité, et
  justificatif de domicile si demandé.
- **Les bénéficiaires effectifs** (toute personne détenant plus de 25 % du capital) : Stripe les
  demande au titre de la lutte anti-blanchiment, et c'est le point qui bloque le plus souvent.
- **Le RIB du compte d'encaissement**, celui sur lequel Stripe virera les fonds.
- **La description de l'activité** et l'URL du site. Une description vague — « services » — fait
  partir le dossier en revue manuelle ; décrivez précisément : vente d'équipements médicaux,
  frais de dossier de formation, accompagnement administratif.
- **Le libellé qui apparaîtra sur le relevé bancaire du client** (*statement descriptor*).
  Choisissez-le : par défaut, Stripe prend le nom du compte, et un client qui lit « HOLDING
  GUIDON » sur son relevé ne reconnaît pas son achat et conteste. Mettez `OPTIMI SANTE`.

> **Délai à prévoir.** Comptez de quelques heures à plusieurs jours selon les pièces. Ne planifiez
> pas la bascule le jour où vous demandez l'activation.

## A.2 — Les clés : lesquelles, et où

Quatre valeurs, trois endroits. **Ne mélangez jamais une clé de test et une clé live** : une clé
live avec un secret de webhook de test fait échouer tous les événements en 400, silencieusement
du point de vue du client.

| Valeur | Où la prendre | Où la mettre |
|---|---|---|
| `STRIPE_SECRET_KEY` | Stripe → Developers → API keys → **Live** → Secret key (`sk_live_…`) | Render, service `optimisante-backend` |
| `STRIPE_WEBHOOK_SECRET` | Créé à l'étape A.3 (`whsec_…`) | Render, service `optimisante-backend` |
| `VITE_STRIPE_PUBLISHABLE_KEY` | Stripe → API keys → **Live** → Publishable key (`pk_live_…`) | Vercel, projet frontend |
| `STRIPE_CURRENCY` | — | Facultative. Vaut `eur` par défaut ; validée au démarrage |

> ### ⚠️ Le piège Vercel
> `VITE_STRIPE_PUBLISHABLE_KEY` est **figée à la construction du paquet**, pas lue au démarrage.
> Changer la variable sur Vercel ne suffit pas : il faut **reconstruire et redéployer** le
> frontend. Un *Redeploy* avec l'option « Use existing Build Cache » **ne refait pas le build** et
> laisse la clé de test dans le paquet servi. Décochez-la, ou poussez un commit.

> ### ⚠️ Le piège Render
> Spring lit la configuration **une seule fois, au démarrage**. Modifier une variable sans
> redéployer laisse l'instance servir les anciennes valeurs — c'est exactement ce qui s'est
> produit avec les mentions légales le 3 octobre. Après chaque modification : *Manual Deploy*, ou
> au minimum *Restart service*, puis vérifiez.

## A.3 — Créer le webhook live

Le webhook de test ne reçoit pas les événements live, et son secret ne valide pas leurs
signatures. Il en faut un nouveau.

1. Stripe → **Developers → Webhooks**, en **mode Live** (l'interrupteur en haut à droite).
2. **Add endpoint** → URL : `https://optimisante-backend.onrender.com/api/v1/payments/webhook`
3. Événements à écouter : **`checkout.session.completed`** et **`charge.refunded`**. Ce sont
   les deux que `PaymentWebhookResource` traite ; tout autre événement souscrit sera reçu et
   ignoré, sans dommage. **Oublier `charge.refunded` rend le traitement des remboursements
   inopérant** : le code est là, mais rien ne lui parvient.
4. Copiez le **Signing secret** (`whsec_…`) affiché à la création et mettez-le dans
   `STRIPE_WEBHOOK_SECRET` sur Render.
5. Redéployez le backend.
6. Dans Stripe, utilisez **Send test webhook** sur l'endpoint et vérifiez une réponse **200**.
   Un **400** signifie que le secret ne correspond pas.

> **Le point de non-retour.** Tant que ce webhook ne répond pas 200, un client peut payer sans que
> sa commande soit confirmée : l'argent est encaissé, le reçu n'est pas émis, la commande reste en
> attente. Vérifiez ce point **avant** d'ouvrir les paiements.

## A.4 — Purger les données de test

Toutes les ventes et inscriptions en base sont des données de test. Elles doivent partir avant
l'ouverture, pour trois raisons : elles faussent les statistiques du tableau de bord, elles
portent des références Stripe de test qui ne correspondent à rien en live, et elles rendent
impossible de distinguer la première vraie commande.

À purger, dans cet ordre (les dépendances d'abord) :

```
order_items → orders
enrollment_payments → enrollments
payment_receipts
doctor_applications (celles de test)
stock_reservations
```

> **Faites une sauvegarde de la base avant.** Render propose des sauvegardes sur les plans payants ;
> sinon `pg_dump` depuis votre poste. Une purge ne se rejoue pas.

Je peux produire le script SQL de purge avec son script de retour arrière — demandez-le-moi au
moment voulu plutôt que de l'écrire à la main.

## A.5 — La recette, en production

Un paiement réel de petit montant, suivi de bout en bout. C'est la seule vérification qui vaut.

1. Créez un produit à **1 €** dans le catalogue, non publié ou dans une catégorie de test.
2. Achetez-le avec une **vraie carte**, depuis le site en production.
3. Vérifiez, dans l'ordre :
   - [ ] Le paiement apparaît dans Stripe → Payments, en mode Live.
   - [ ] L'événement `checkout.session.completed` est marqué **Succeeded** dans Developers →
         Webhooks → votre endpoint.
   - [ ] La commande passe au statut payé dans l'espace d'administration.
   - [ ] Le reçu PDF est généré et porte la bonne identité légale (HOLDING GUIDON, SIRET réel).
   - [ ] L'e-mail de confirmation arrive.
   - [ ] Le stock du produit a été décrémenté.
4. **Remboursez le paiement** depuis Stripe, et vérifiez que cela ne casse rien côté plateforme.
   ⚠️ Le remboursement n'est **pas** traité par le code aujourd'hui : l'événement
   `charge.refunded` n'est pas écouté, donc la commande restera marquée payée. Voir A.6.
5. Supprimez le produit de test.

## A.6 — Quatre défauts, corrigés le 4 octobre 2026

Ces quatre points ont été relevés en écrivant ce guide, puis corrigés et éprouvés. Ils sont
conservés ici parce qu'ils expliquent ce qui a changé, et ce qu'il reste à surveiller.

### 1. `STRIPE_CURRENCY` ne servait à rien — corrigé

La propriété existait et était exposée en production, mais la devise était écrite en dur dans
`StripePaymentService`. La définir donnait l'illusion d'agir. Elle est désormais lue, et
**validée au démarrage** : une devise mal orthographiée arrête le serveur plutôt que de faire
échouer le paiement d'un client.

### 2. La conversion en centimes supposait deux décimales — corrigé

`montant.multiply(100)` est juste pour l'euro et faux pour le franc CFA, qui n'a pas de
subdivision : 328 000 XAF seraient partis chez Stripe pour 32 800 000. La règle vit maintenant
dans `MontantStripe`, avec ses tests. Elle **refuse** les devises à trois décimales plutôt que
de les approximer. L'arrondi y remplace aussi une troncature qui offrait un centime par vente.

> **Ce qui reste à votre charge.** Pour une devise sans décimales, l'arrondi à l'unité est
> silencieux. C'est à l'appelant d'arrondir à un palier lisible — 328 000 XAF, pas 327 978,5 —
> comme expliqué en B.0.1.

### 3. Les remboursements ne redescendaient pas — corrigé

Une commande remboursée restait marquée payée et pesait au chiffre d'affaires. L'événement
`charge.refunded` est désormais traité : un remboursement **intégral** passe la commande à
`REFUNDED`, qui la fait sortir des agrégats financiers.

Deux limites assumées, documentées dans le code :

- **Le stock n'est pas réapprovisionné.** Un remboursement ne dit pas que la marchandise est
  revenue. Le retour physique se constate à la réception.
- **Un remboursement partiel ne change pas le statut.** Il est journalisé en avertissement, avec
  le numéro de commande, et se traite à la main.

### 4. Les webhooks abandonnaient les événements en cas d'écart de version — corrigé

Le plus grave des quatre, et il n'a été trouvé qu'en envoyant de vrais événements signés. Les
deux webhooks lisaient l'objet en mode strict : dès que la version d'API du compte Stripe diffère
de celle de la bibliothèque Java, la lecture rend un résultat vide et **l'événement était
abandonné**. Pour un remboursement, il passait inaperçu ; pour une confirmation, un paiement
était encaissé sans que la commande soit confirmée. Les deux chemins retombent désormais sur la
lecture permissive que Stripe documente pour ce cas.

### 5. L'identifiant de paiement n'était jamais enregistré — corrigé

Découvert en éprouvant le point 3, et il le rendait inopérant. La commande enregistrait
`session.getPaymentIntent()` **à la création de la session**, où il est toujours nul : Stripe ne
l'attribue qu'une fois le client engagé dans le règlement. Onze commandes réglées par carte
portaient leur identifiant de session, et **aucune** son identifiant de paiement — or c'est le
seul lien que l'événement `charge.refunded` porte vers nos données. Il est désormais capturé à
la confirmation.

> **Les commandes antérieures restent sans identifiant de paiement.** Un remboursement sur l'une
> d'elles ne trouvera pas sa commande et sera journalisé en avertissement. Ce sont toutes des
> données de test, purgées avant l'ouverture : le point se referme de lui-même.

---

# Partie B — Mobile money via pawaPay

## B.0 — Les décisions à prendre AVANT d'écrire une ligne

Ce sont des choix d'entreprise, pas des choix techniques. Chacun change ce qu'il faut coder.

### B.0.1 — En quelle devise affiche-t-on les prix à un praticien congolais ?

Deux options, et elles ne se valent pas :

| Option | Ce que voit le client | Conséquence |
|---|---|---|
| **Prix en XAF** | « 327 979 FCFA » | Il sait exactement ce qu'il paiera. Il faut afficher un prix XAF à côté du prix EUR. |
| **Prix en EUR converti au paiement** | « 500 € », puis un montant XAF à l'écran de paiement | Plus simple à coder, mais le client découvre le montant tard. |

> **Un point qui simplifie tout : le franc CFA est à parité fixe avec l'euro.**
> **1 EUR = 655,957 XAF**, parité garantie par le Trésor français. Il n'y a donc **aucun risque de
> change** et aucun taux à aller chercher : la conversion est une multiplication par une constante.
> C'est un avantage rare, qui rend l'option « prix en XAF » tout à fait praticable.

**Deux contraintes à respecter dans le calcul :**
- Le XAF **n'a pas de décimales** chez MTN et Airtel Congo. Le montant transmis doit être un
  entier.
- Arrondissez **vers le haut**, et à un palier lisible (5 ou 100 XAF). 500 € = 327 978,5 XAF →
  annoncez 328 000 XAF, pas 327 978,5.

### B.0.2 — Qui paie les frais pawaPay ?

Les frais de collecte annoncés pour le Congo-Brazzaville sont de l'ordre de **4 %**
(≈ 3 % opérateur + ≈ 1 % pawaPay) **[à confirmer sur votre contrat : ces taux se négocient et
varient par opérateur]**. Sur une formation à 500 €, cela fait environ 20 € par transaction.

Trois réponses possibles : vous les absorbez (marge réduite), vous les répercutez (prix mobile
money plus élevé — à annoncer clairement, c'est une obligation d'information), ou vous les
intégrez au prix affiché pour toutes les zones. **Tranchez avant de coder** : la réponse décide
s'il faut un prix par moyen de paiement dans le modèle de données.

### B.0.3 — Que fait-on quand le client n'approuve pas ?

Un paiement mobile money est **asynchrone** : vous l'initiez, le client reçoit une demande sur son
téléphone, et il saisit son code PIN — ou ne le fait pas. Entre les deux, votre commande est dans
un état d'attente qui n'existe pas aujourd'hui dans la plateforme.

Décidez : au bout de combien de temps abandonne-t-on ? Que devient le panier ? La réservation de
stock est-elle maintenue ? (Il existe déjà une table `stock_reservations` — c'est là que cela se
joue.)

### B.0.4 — Et les remboursements ?

pawaPay propose des remboursements, mais ils ne sont pas instantanés et peuvent échouer (compte
fermé, plafond atteint). Décidez si vous les ouvrez, ou si un remboursement se traite à la main.

### B.0.5 — Et la TVA ?

**Ne tranchez pas seul.** Une vente à un client hors Union européenne pose exactement la question
posée au comptable dans le document « Questions TVA et livraison » : exonération à l'export avec
justificatif, ou TVA française facturée. Le moyen de paiement ne change pas la règle fiscale, mais
il rend la question concrète. Attendez la réponse.

## B.1 — Le contrat pawaPay

Avant tout accès à l'API de production :

- [ ] Créer un compte sur le tableau de bord pawaPay et obtenir l'accès **sandbox** (immédiat).
- [ ] Monter le dossier KYB (*Know Your Business*) : statuts, immatriculation, bénéficiaires
      effectifs, pièce d'identité du représentant. Mêmes pièces que pour Stripe.
- [ ] **Négocier et faire écrire les frais** par pays et par opérateur.
- [ ] Déclarer le **compte de règlement** : sur quel compte bancaire pawaPay reverse les sommes
      collectées, dans quelle devise, et à quelle fréquence. Point souvent oublié, et c'est lui
      qui détermine votre trésorerie réelle.
- [ ] Faire préciser le **délai de règlement** (J+1 ? J+7 ?) et le **seuil minimum** de virement.
- [ ] Obtenir les **jetons d'API** sandbox et production — ce sont deux jetons distincts.

## B.2 — Les pays et opérateurs couverts

Pour le Congo-Brazzaville, pawaPay expose deux opérateurs, en **XAF**, **sans décimales** :

| Pays | Code opérateur pawaPay | Devise | Décimales |
|---|---|---|---|
| Congo-Brazzaville | `MTN_MOMO_COG` | XAF | non |
| Congo-Brazzaville | `AIRTEL_COG` | XAF | non |
| Cameroun | `MTN_MOMO_CMR` | XAF | non |
| Cameroun | `ORANGE_CMR` | XAF | non |
| Gabon | `AIRTEL_GAB` | XAF | 2 décimales |

Limites de montant annoncées pour le Congo : MTN de 500 à 2 000 000 XAF, Airtel de 500 à
1 500 000 XAF **[à confirmer]**. À 655,957 XAF pour un euro, le plafond MTN représente environ
3 050 € et celui d'Airtel environ 2 290 €.

> **Conséquence directe sur votre catalogue.** Une formation à 3 500 € **ne peut pas** être réglée
> en une fois par mobile money au Congo. Il faut soit prévoir un paiement en plusieurs fois, soit
> réserver le mobile money aux montants inférieurs au plafond, soit le limiter aux frais de
> dossier. **C'est une décision produit, à prendre maintenant.**

> **Ne codez pas ces listes en dur.** pawaPay expose un point d'accès de configuration active
> (`GET /v2/active-conf?country=COG&operationType=DEPOSIT`) qui rend les opérateurs disponibles,
> leurs devises et leurs limites. Le lire au démarrage et le mettre en cache évite qu'un opérateur
> ajouté ou retiré chez pawaPay vous oblige à redéployer. La zone CEMAC ne se limite d'ailleurs
> pas au Congo : Tchad, Centrafrique et Guinée équatoriale ne figurent pas dans la liste
> ci-dessus — vérifiez leur présence si vous visez ces pays.

## B.3 — Le flux, dans l'ordre

```
  Client                 Plateforme                pawaPay              Opérateur
    │                        │                        │                     │
    │ choisit Mobile Money   │                        │                     │
    │ saisit son numéro      │                        │                     │
    ├───────────────────────>│                        │                     │
    │                        │ crée la commande       │                     │
    │                        │ statut = EN_ATTENTE    │                     │
    │                        │ génère un depositId    │                     │
    │                        │ (UUIDv4)               │                     │
    │                        ├───────────────────────>│                     │
    │                        │  POST /v2/deposits     │                     │
    │                        │<───────────────────────┤                     │
    │                        │  ACCEPTED / REJECTED   │                     │
    │<───────────────────────┤                        ├────────────────────>│
    │ « Validez sur votre    │                        │   demande de PIN    │
    │   téléphone »          │                        │                     │
    │                        │                        │                     │
    │ saisit son code PIN    │                        │                     │
    ├────────────────────────┼────────────────────────┼────────────────────>│
    │                        │                        │<────────────────────┤
    │                        │<───────────────────────┤      confirmé       │
    │                        │  callback COMPLETED    │                     │
    │                        │  commande payée        │                     │
    │                        │  reçu émis             │                     │
```

**Trois états à retenir.** La réponse immédiate à l'initiation (`ACCEPTED`, `REJECTED`,
`DUPLICATE_IGNORED`) ne dit **pas** que le client a payé : elle dit que pawaPay a accepté de
traiter. Le statut final arrive par callback : `COMPLETED`, `FAILED`, `PROCESSING`, ou
`IN_RECONCILIATION`.

## B.4 — Ce qu'il faut écrire, fichier par fichier

L'architecture existante se prête bien à l'ajout : le webhook Stripe aiguille déjà par type de
paiement via `StripePaymentDispatcher`. Suivez le même découpage.

### Migration `V64__mobile_money.sql`

La valeur `MOBILE_MONEY` doit être ajoutée **à la contrainte CHECK** de la table `orders`, posée
dans `V1__init_schema_v6_1.sql` :

```sql
payment_method VARCHAR(50) CHECK (payment_method IN ('STRIPE_CARD', 'BANK_TRANSFER', 'QUOTE_REQUEST'))
```

Sans cette migration, toute commande en mobile money sera **rejetée par la base**, pas par le
code — avec un message illisible. La migration doit :

1. Supprimer et recréer la contrainte avec `'MOBILE_MONEY'`.
2. Créer la table des dépôts :

```sql
CREATE TABLE mobile_money_deposits (
    id               uuid PRIMARY KEY,
    deposit_id       uuid NOT NULL UNIQUE,   -- l'identifiant envoyé à pawaPay
    order_id         uuid REFERENCES orders(id),
    enrollment_id    uuid REFERENCES enrollments(id),
    provider         varchar(40) NOT NULL,   -- MTN_MOMO_COG, AIRTEL_COG…
    phone_number     varchar(20) NOT NULL,
    amount           numeric(14,2) NOT NULL,
    currency         varchar(3)  NOT NULL,
    status           varchar(24) NOT NULL,
    failure_code     varchar(60),
    failure_message  text,
    provider_tx_id   varchar(80),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_mm_deposits_status ON mobile_money_deposits(status);
```

> **Pourquoi une table dédiée, et pas deux colonnes sur `orders`.** Un dépôt peut échouer et être
> rejoué : le client se trompe de numéro, n'a pas assez de solde, laisse expirer la demande. Avec
> des colonnes sur la commande, chaque tentative écrase la précédente et vous perdez la trace de
> ce qui s'est passé — exactement ce qu'il faut pour instruire une réclamation. Une ligne par
> tentative conserve l'historique.

### Côté Java

| Fichier à créer | Rôle |
|---|---|
| `domain/payments/mobilemoney/PawaPayClient.java` | Les appels sortants : initier un dépôt, lire son statut, lire la configuration active. |
| `domain/payments/mobilemoney/MobileMoneyService.java` | La logique : convertir EUR→XAF, arrondir, choisir l'opérateur, enregistrer le dépôt, appliquer le statut final à la commande. |
| `domain/payments/mobilemoney/PawaPayCallbackResource.java` | `POST /api/v1/payments/mobile-money/callback`. |
| `domain/payments/mobilemoney/PawaPaySignatureVerifier.java` | Vérification de la signature des callbacks. |
| `domain/payments/mobilemoney/MobileMoneyDeposit.java` | L'entité. |
| `domain/payments/mobilemoney/ReconciliationMobileMoney.java` | Le filet de sécurité (§ B.6). |

| Fichier à modifier | Modification |
|---|---|
| `domain/orders/entity/PaymentMethod.java` | Ajouter `MOBILE_MONEY("Mobile money")`. Le libellé s'imprime sur le reçu. |
| `config/security/SecurityConfig.java` | Ouvrir le callback : `.requestMatchers("/api/v1/payments/mobile-money/callback").permitAll()`. |
| `application.yml` | Bloc `pawapay:` — `base-url`, `api-token`, `callback-secret`, dans **les deux** documents YAML (dev et prod). Sans repli en prod, comme Stripe. |

Le reçu ne demande **aucune modification** : `Encaissement.moyenPaiement` est une chaîne libre.
Passez-y `"Mobile money (MTN)"` et le document est correct.

### Côté frontend

- Une option « Mobile money » dans `CheckoutPage.tsx`, **affichée seulement si le pays de
  livraison ou le pays de résidence est desservi**.
- Un champ numéro de téléphone avec l'indicatif, et le choix de l'opérateur — ou la détection
  automatique via le point d'accès `predict-provider` de pawaPay, qui devine l'opérateur à partir
  du numéro. Plus agréable, mais prévoyez le repli manuel : la prédiction se trompe sur les
  numéros portés.
- **Un écran d'attente**, qui est la vraie difficulté d'interface : « Validez le paiement sur
  votre téléphone », avec interrogation du statut toutes les 3 à 5 secondes, un compte à rebours,
  et une sortie claire au bout de 2 à 3 minutes. Sans cet écran, le client ferme la page en
  croyant que rien ne s'est passé — alors que le paiement peut aboutir après.

## B.5 — Le callback : les quatre règles à ne pas rater

### 1. Vérifier la signature

pawaPay signe ses callbacks selon la **RFC 9421**, par défaut en **ECDSA P-256 / SHA-256**. Les
en-têtes utiles sont `Signature`, `Signature-Input`, `Signature-Date`, `Content-Digest` et
`Content-Type`. La clé publique se récupère sur leur point d'accès dédié.

**La vérification est optionnelle chez pawaPay. Activez-la quand même.** Le point d'accès est
public par construction — il faut bien que pawaPay puisse l'appeler — donc sans signature,
n'importe qui connaissant l'URL peut déclarer une commande payée. C'est exactement ce que le
webhook Stripe vérifie déjà, et pour la même raison.

En complément : restreignez par **liste d'adresses IP** si pawaPay en publie une **[à confirmer]**.

### 2. Être idempotent

Un callback peut arriver **plusieurs fois** : pawaPay réessaie pendant 15 minutes, et peut rejouer
un callback jusqu'à 90 jours après. Traiter deux fois un `COMPLETED`, c'est émettre deux reçus,
décrémenter deux fois le stock, envoyer deux e-mails.

La protection : `deposit_id` est **UNIQUE** en base, et le traitement commence par vérifier si ce
dépôt est déjà au statut final. S'il l'est, répondez **200 sans rien faire**. Répondre autre chose
que 200 fait réessayer pawaPay.

### 3. Répondre vite

Traitez en base, répondez 200, et faites le reste — e-mail, PDF, notification — **après**. Un
callback qui met dix secondes à répondre sera considéré en échec et rejoué.

### 4. Ne jamais faire confiance au montant reçu

Vérifiez que le montant et la devise du callback **correspondent à ceux du dépôt enregistré**.
S'ils diffèrent, c'est une anomalie : journalisez, alertez, et ne validez pas la commande.

## B.6 — Le filet de sécurité : la réconciliation

**C'est le point que l'on oublie, et c'est celui qui coûte le plus cher.** Un callback peut se
perdre : votre serveur redémarrait, Render dormait (le plan gratuit met le service en veille),
le réseau a coupé. Le client a payé, et votre plateforme l'ignore.

Prévoyez une tâche planifiée, toutes les 5 minutes :

1. Sélectionner les dépôts non finalisés créés il y a plus de 2 minutes et moins de 48 heures.
2. Pour chacun, appeler `GET /v2/deposits/{depositId}`.
3. Appliquer le statut obtenu **par le même chemin de code que le callback** — pas une copie.
4. Au-delà de 48 heures sans statut final, marquer comme abandonné et alerter.

> Il existe déjà un précédent dans votre code : `DoctorApplicationPaymentReconciler`. Regardez
> comment il est écrit et suivez le même modèle.

## B.7 — La recette

**En sandbox d'abord.** pawaPay fournit des numéros de test qui déclenchent chaque cas.

- [ ] Paiement accepté et confirmé → commande payée, reçu émis.
- [ ] Client qui refuse ou laisse expirer → commande non payée, stock libéré, message clair.
- [ ] Solde insuffisant → message compréhensible, pas un code d'erreur brut.
- [ ] Numéro invalide → refus à l'initiation, avant tout appel.
- [ ] **Callback reçu deux fois** → un seul reçu, un seul e-mail.
- [ ] **Callback jamais reçu** → la réconciliation rattrape dans les 5 minutes.
- [ ] Callback avec une signature invalide → rejeté en 401, journalisé.
- [ ] Montant supérieur au plafond opérateur → refusé avant l'appel, avec le plafond annoncé.
- [ ] Montant avec décimales → arrondi correctement, jamais tronqué.

**Puis en production**, avec un paiement réel de petit montant, sur un vrai téléphone, par
quelqu'un au Congo. Un numéro de test ne révèle pas les problèmes de portabilité ni les
particularités d'un opérateur.

---

# Partie C — Les pièges transverses

### C.1 — Le franc CFA n'a pas de décimales

Déjà dit, mais c'est l'erreur la plus probable. `327 978,50 XAF` n'existe pas. Arrondissez **avant**
d'appeler pawaPay, et stockez le montant arrondi — pas le montant théorique. Sinon, le reçu et le
relevé de l'opérateur ne diront pas la même chose.

### C.2 — Le numéro de téléphone est une donnée personnelle

Il relève du RGPD, au même titre que l'adresse. Trois conséquences concrètes :

- Il doit apparaître dans le **registre des traitements** et dans la politique de confidentialité
  (le fichier `RgpdService.java` et la page `PrivacyPolicyPage.tsx` existent déjà).
- Il doit être inclus dans l'**export de données personnelles** et dans la **suppression de
  compte** — vérifiez `PersonalDataService.java`.
- pawaPay devient un **sous-traitant** au sens du RGPD : il faut un accord de traitement, et
  mentionner un transfert hors Union européenne le cas échéant.

### C.3 — Le plan gratuit de Render met le service en veille

Un service endormi ne répond pas au premier appel, qui est alors perdu. **Pour Stripe comme pour
pawaPay, c'est un paiement non confirmé.** Stripe réessaie, pawaPay aussi — mais pendant une durée
limitée. Si vous ouvrez les paiements en production, passez le backend sur un plan qui ne dort
pas. Ce n'est pas une optimisation : c'est une condition de fonctionnement.

### C.4 — Deux moyens de paiement, un seul parcours

Résistez à la tentation de dupliquer le tunnel de commande. Le mobile money change **le mode de
règlement**, pas la commande, ni le panier, ni le reçu, ni les frais de port. `StripePaymentDispatcher`
montre le découpage à suivre : un point d'entrée, et des traitements interchangeables derrière.

### C.5 — Les secrets ne passent jamais par le dépôt Git

Jeton pawaPay, clé secrète Stripe, secret de webhook : **uniquement** dans les variables
d'environnement de Render. Jamais dans `application.yml`, jamais dans un fichier commité, jamais
dans un message. Si un secret est exposé une fois, il est compromis : régénérez-le.

---

# Partie D — Les listes à cocher

## D.1 — Stripe en production

**Avant**
- [ ] Compte Stripe activé, pièces validées
- [ ] *Statement descriptor* défini sur `OPTIMI SANTE`
- [ ] RIB d'encaissement déclaré
- [ ] Sauvegarde de la base effectuée
- [ ] Données de vente de test purgées

**Bascule**
- [ ] `STRIPE_SECRET_KEY` (`sk_live_…`) sur Render
- [ ] Webhook live créé, événement `checkout.session.completed` souscrit
- [ ] `STRIPE_WEBHOOK_SECRET` (`whsec_…`) sur Render
- [ ] Backend redéployé
- [ ] `VITE_STRIPE_PUBLISHABLE_KEY` (`pk_live_…`) sur Vercel
- [ ] Frontend **reconstruit** (sans cache de build)

**Vérification**
- [ ] *Send test webhook* → 200
- [ ] Paiement réel de 1 € → commande payée, reçu correct, e-mail reçu, stock décrémenté
- [ ] Remboursement effectué et conséquences constatées
- [ ] Produit de test supprimé

## D.2 — Mobile money

**Décisions**
- [ ] Devise d'affichage pour l'Afrique tranchée
- [ ] Prise en charge des frais tranchée
- [ ] Règle de plafond tranchée (que fait-on des montants au-dessus de la limite opérateur ?)
- [ ] Délai d'abandon tranché
- [ ] Politique de remboursement tranchée
- [ ] Traitement TVA confirmé par le comptable

**Contrat**
- [ ] Compte pawaPay créé, sandbox accessible
- [ ] KYB déposé et validé
- [ ] Frais négociés et écrits
- [ ] Compte et délai de règlement confirmés
- [ ] Jetons sandbox et production obtenus
- [ ] Accord de sous-traitance RGPD signé

**Développement**
- [ ] Migration `V64` : contrainte CHECK + table `mobile_money_deposits`
- [ ] `PaymentMethod.MOBILE_MONEY`
- [ ] Client pawaPay (initier, lire le statut, configuration active)
- [ ] Callback, signature vérifiée, idempotent
- [ ] Réconciliation planifiée
- [ ] Interface : choix, saisie du numéro, écran d'attente, messages d'erreur en français
- [ ] Politique de confidentialité et export de données mis à jour

**Recette**
- [ ] Les neuf cas de la section B.7 passés en sandbox
- [ ] Paiement réel sur un vrai téléphone au Congo
- [ ] Backend sur un plan Render qui ne dort pas

---

## Ce que je peux prendre en charge

Sur demande, et dans cet ordre de dépendance :

1. Le script SQL de purge des données de test, avec son retour arrière.
2. Le branchement de `STRIPE_CURRENCY` et la conversion en centimes dépendante de la devise
   (défaut A.6 n° 1 et 2) — une heure, sans risque pour l'existant.
3. L'écoute de `charge.refunded` (défaut A.6 n° 3).
4. L'intégration pawaPay complète, une fois les décisions de B.0 prises et le compte sandbox
   ouvert.

Ce que je ne peux pas faire : créer les comptes, déposer les pièces KYB, négocier les frais, et
saisir les variables d'environnement sur Render et Vercel. Ces quatre points sont à vous.

---

## Sources

- [pawaPay — Deposits](https://docs.pawapay.io/v2/docs/deposits) · [Signatures](https://docs.pawapay.io/v2/docs/signatures) · [What to know](https://docs.pawapay.io/v2/docs/what_to_know) · [Providers](https://docs.pawapay.io/v2/docs/providers) · [Fees](https://pawapay.io/fees)
- [Stripe — Go-live checklist](https://docs.stripe.com/development/checklist) · [Supported currencies](https://docs.stripe.com/currencies)
