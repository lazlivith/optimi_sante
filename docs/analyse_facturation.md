# Facturation dans la plateforme — analyse préalable

Écrit pour : Mounir, et pour son comptable (chapitres 1 et 3 en particulier).

**Aucun code n'a été écrit.** Ce document établit ce qui existe, propose un
parcours, liste les contraintes légales à respecter dès la conception, et découpe
le chantier. Il ne bloque pas la mise en ligne d'« Exercer en France ».

---

## 1. Inventaire de l'existant

### 1.1 La réponse courte

**Non, la plateforme ne génère aucune facture aujourd'hui.** Elle émet des
**reçus**, ce qui n'est pas la même chose : un reçu atteste qu'on a encaissé, une
facture est la pièce qui fonde la créance et porte les mentions légales. Les
seules tables de documents en base sont `document_sequences` et
`payment_receipts`. Il n'existe ni table `invoices`, ni table `quotes`.

### 1.2 Ce qui est directement réutilisable — et c'est beaucoup

| Composant | État | Verdict |
|---|---|---|
| `DocumentNumberService` | Numérotation continue, par exercice, verrou `UPDATE … RETURNING`, `Propagation.MANDATORY` | **Réutilisable tel quel.** C'est déjà la règle légale. |
| `CompanyIdentity` + `CompanyIdentityGuard` | Dénomination, SIRET, adresse, TVA intracom ; le serveur **refuse de démarrer** si l'un manque | **Réutilisable tel quel.** |
| `DocumentRenderer` / `PdfGeneratorService` | Thymeleaf → HTML → PDF (flying-saucer + openpdf), identité légale estampillée au pied | **Réutilisable**, à faire évoluer vers Factur-X (§ 3.5). |
| `ServiceTva` | `decomposer` TTC→HT+TVA, `ventiler`, `repartirRemise`, taux normal 20 % | **Réutilisable pour le calcul**, à étendre au régime par pays (§ 3.3). |
| `PaymentReceiptIssuer` | Idempotent par `reference` **unique en base**, émission dans la transaction du paiement | **Modèle à copier** pour la facture. |
| Stripe | Intégré : `checkout.session.completed` → reçu | **Réutilisable.** |
| Gabarits PDF | `recu-paiement.html`, `devis-b2b.html`, conventions… | Base de départ. |

Le point le plus important : **`DocumentNumberService` fait déjà exactement ce
que la loi demande d'une facture.** Il n'utilise pas de séquence PostgreSQL —
qui avance même sur transaction annulée et laisserait des trous — mais une ligne
de compteur verrouillée, incrémentée dans la **même transaction** que l'émission.
Si l'émission échoue, le numéro n'est pas consommé. Ce travail est fait.

### 1.3 Ce qui n'existe pas, contrairement à ce qu'on pourrait croire

**pawaPay / Mobile Money : aucune ligne de code.** Zéro occurrence dans tout le
dépôt. Le fichier `docs/guide_paiements_production_et_mobile_money.md` est un
**plan de travail** rédigé le 4 octobre, pas une intégration. Tout reste à
écrire : c'est le poste le plus lourd du chantier.

**Le « Devis B2B » n'est pas un devis au sens comptable.** C'est une `Order`
portant `isQuote = true` et un `quoteDiscountRate`. Le PDF est produit **à la
demande** et n'est **pas archivé** ; il n'a ni numéro, ni date de validité, ni
cycle de vie (envoyé / accepté / refusé / expiré). Un devis accepté ne se
transforme en rien : il n'y a pas de passage devis → facture.

**« Fiscalité & prix » ne fait pas de fiscalité de facturation.** C'est
`AdminFiscaliteResource` → réglages d'affichage de la TVA et campagnes de prix.

**La TVA n'est pas imprimée aujourd'hui.** `ServiceTva.estPubliable()` est
**fermé par défaut** (paramètre `TVA_ACTIVE`), délibérément : le taux par article
vient d'une grille que seul le comptable peut établir, et annoncer « dont TVA
20 % » sur un dispositif médical qui relève peut-être du taux réduit serait une
mention fausse sur une pièce comptable. **Pour facturer, cet interrupteur devra
être ouvert**, ce qui suppose la grille.

**Les demandes d'accompagnement sont isolées.** `AccompanimentRequest` n'est
référencé nulle part hors de son module : aucun lien vers une commande, un
paiement ou un document. Tout le raccordement est à construire.

---

## 2. Parcours proposé

```
Demande déposée (existe)
      │
      ▼
[1] DEVIS  ──────────────────────────────────────────────────┐
    numéroté DEV-2026-0001, lignes, validité 30 j            │
    déduction automatique des 89 € si l'Orientation EVC      │
    a déjà été FACTURÉE et PAYÉE par ce praticien            │
      │                                                      │
      ▼                                                      │
    PDF envoyé par e-mail depuis la fiche détail             │
      │                                                      │
      ▼                                                      │
[2] LIEN DE PAIEMENT (carte Stripe / Mobile Money)           │
    émis depuis la fiche, rattaché au devis                  │
      │                                                      │
      ▼                                                      │
[3] PAIEMENT CONFIRMÉ (webhook)                              │
      │                                                      │
      ├──► FACTURE émise automatiquement, immuable           │
      │    FAC-2026-0001, PDF archivé                        │
      ├──► REÇU (existant) ◄────────────────────────────────┘
      └──► statut de la demande → « Payé »
      │
      ▼
[4] Prestation réalisée → date de fin saisie à la main
    (c'est elle qui démarre le délai de conservation RGPD)
```

### 2.1 Points de conception à retenir

**Le devis est émis depuis la fiche détail**, où l'administrateur a déjà le
dossier sous les yeux. Il ne se construit pas dans un écran séparé : le praticien
a déclaré son offre, son pays et son étape, et ces trois éléments suffisent à
pré-remplir le devis. Ce qui reste à saisir, c'est l'écart au tarif.

**La déduction des 89 € doit être vérifiable, pas déclarative.** Elle ne
s'applique que si une facture d'Orientation EVC **payée** existe pour ce
praticien. Elle apparaît en ligne négative explicite :

> `Déduction — Orientation EVC déjà réglée (facture FAC-2026-0042) … −89,00 €`

Référencer la facture d'origine est ce qui rend la remise justifiable au contrôle.
Une remise sans origine est une remise à expliquer de mémoire, trois ans plus tard.

**Le régime de TVA est figé au moment de l'émission**, d'après le pays déclaré,
et recopié dans la facture — jamais recalculé à la lecture. Un praticien qui
déménage ne doit pas changer rétroactivement une facture émise.

**Le lien de paiement rattache le paiement au devis**, et non à une commande
anonyme. C'est ce rattachement qui permet d'émettre la bonne facture
automatiquement au webhook.

---

## 3. Exigences légales à respecter dès la conception

> Ce chapitre est celui à faire relire par le comptable. Les points marqués
> **[à confirmer]** appellent une décision qui n'est pas technique.

### 3.1 Numérotation

Continue, chronologique, **sans trou**, par série et par exercice. Le mécanisme
existant répond déjà à l'exigence ; trois séries distinctes sont à ouvrir :

| Série | Préfixe | Format |
|---|---|---|
| Devis | `DEV` | `DEV-2026-0001` |
| Factures | `FAC` | `FAC-2026-0001` |
| Avoirs | `AV` | `AV-2026-0001` |

Des séries séparées sont admises à condition que **chacune** soit continue.

**Un piège à éviter** : le numéro ne doit être attribué qu'à l'**émission**
définitive, jamais à la création d'un brouillon. Un brouillon supprimé emporterait
sinon un numéro, et le trou serait constitué.

### 3.2 Immuabilité et avoirs

Une facture émise ne se modifie plus : ni le montant, ni le destinataire, ni la
date, ni le PDF. Toute correction passe par un **avoir** qui référence la facture
d'origine.

Conséquences techniques à prévoir :

- la table `invoices` n'expose **aucune** opération de modification ni de
  suppression — seulement l'insertion et la lecture ;
- un déclencheur PostgreSQL `BEFORE UPDATE OR DELETE` qui lève une exception est
  la ceinture qui tient quand la bretelle applicative cède ;
- le PDF est archivé à l'émission et **son empreinte (SHA-256) est stockée** :
  c'est ce qui permet de démontrer, plus tard, que le fichier n'a pas bougé.

### 3.3 Mentions obligatoires et régime de TVA

Les mentions françaises sont nombreuses ; les variables d'identité existent déjà
(`CompanyIdentity`). À prévoir sur le gabarit : identité et adresse du vendeur,
SIRET, RCS, TVA intracommunautaire, identité et adresse du client, numéro et date
de facture, date de la prestation, désignation, quantité, prix unitaire HT, taux
et montant de TVA, totaux HT / TVA / TTC, conditions et date de règlement,
pénalités de retard, indemnité forfaitaire de recouvrement de 40 € (clients
professionnels), et le cas échéant l'escompte.

Le régime suit la décision de votre comptable, déjà inscrite à l'article 3.9 des
CGV :

| Résidence déclarée | Régime | Mention |
|---|---|---|
| France ou UE | TTC, TVA 20 % incluse | ventilation HT / TVA / TTC |
| Hors UE | Hors taxe, **même montant** | « TVA non applicable – article 259 B du CGI » |

**[à confirmer] Trois questions pour le comptable :**

1. **Le client professionnel établi dans l'UE.** Un établissement de santé
   belge qui fournit un numéro de TVA intracommunautaire relève en principe de
   l'autoliquidation, avec une mention différente. Faut-il prévoir ce cas, et
   donc **collecter le numéro de TVA** du client ? Aujourd'hui le formulaire ne
   demande que le pays de résidence.
2. **La qualité du client.** L'article 259 B vise des prestations rendues à des
   preneurs qui ne sont pas assujettis, ou établis hors de l'UE. Nos clients sont
   des praticiens, le plus souvent des particuliers : la mention retenue
   convient-elle à tous les cas, ou faut-il distinguer ?
3. **La grille de taux.** L'interrupteur `TVA_ACTIVE` est fermé faute de grille.
   Pour les quatre offres d'accompagnement, le taux normal de 20 % s'applique-t-il
   sans réserve ? (La question se pose séparément pour le catalogue de matériel.)

### 3.4 Archivage et RGPD

- **Conservation 10 ans.** Les pièces comptables relèvent de l'article L123-22
  du code de commerce. Le PDF est archivé sur Cloudinary comme les reçus ; sa clé
  et son empreinte sont stockées en base.
- **Les factures échappent à l'anonymisation.** La tâche d'anonymisation efface
  aujourd'hui les données nominatives d'une demande à l'échéance de conservation.
  Une facture **ne doit pas** être touchée : l'obligation légale de conservation
  est un motif qui prime (article 17.3.b du RGPD). **La tâche devra exclure
  explicitement les factures et les avoirs**, et la politique de confidentialité
  devra énoncer ces deux durées distinctes — celle de la demande, celle de la
  pièce comptable.
- Une demande anonymisée dont la facture subsiste doit rester **lisible** : la
  facture porte sa propre copie du nom et de l'adresse, figée à l'émission. C'est
  voulu, et c'est ce qui permet d'anonymiser la demande sans détruire la pièce.

### 3.5 Facture électronique

La réforme française impose un format structuré et une transmission par
plateforme agréée, avec un calendrier par taille d'entreprise.

> **[à confirmer] — point de vigilance honnête.** Ce calendrier a été plusieurs
> fois décalé, et mes informations s'arrêtent à mai 2026. Ne vous fiez pas aux
> dates que je donnerais : **faites confirmer par votre comptable** l'échéance qui
> s'applique à Optimi Santé et la plateforme de dématérialisation à retenir.

Ce qui, en revanche, ne dépend pas du calendrier et doit être décidé **maintenant**,
parce que le reprendre après coup coûte dix fois plus cher :

1. **Produire du Factur-X dès le premier jour.** Un PDF/A-3 avec le XML (CII)
   embarqué est lisible par un humain *et* par une machine. Le surcoût à la
   conception est faible ; la reprise ultérieure de toutes les factures émises
   ne l'est pas.
2. **Isoler la transmission derrière une interface.** Une `PasserelleFacturation`
   avec une implémentation « archivage local » aujourd'hui, et une implémentation
   « plateforme agréée » le jour venu. Le reste du code ne doit pas savoir par où
   la facture part.
3. **Distinguer B2B et B2C dès le modèle.** Les ventes à un praticien particulier
   et celles à un établissement ne suivent pas le même régime déclaratif. Un
   champ `client_est_professionnel` posé maintenant évite une migration de
   requalification sur l'historique.

---

## 4. Découpage proposé

Branche dédiée : **`feature/facturation`**, sans aucune interaction avec
`feature/exercer-en-france` ni avec la mise en ligne du 22 octobre.

| Étape | Contenu | Migrations | Estimation |
|---|---|---|---|
| **0** | Validation du modèle de facture par le comptable. **Rien ne commence avant.** | — | à votre main |
| **1** | Socle : tables `quotes`, `quote_lines`, `invoices`, `invoice_lines`, `credit_notes`. Séries DEV/FAC/AV. Immuabilité par déclencheur. Aucun écran. | V78–V79 | 2 à 3 j |
| **2** | Service de TVA par pays : régime figé à l'émission, mention hors UE, ventilation. Tests sur les deux régimes. | — | 1 à 2 j |
| **3** | Devis : création depuis la fiche détail, lignes, validité, déduction vérifiée des 89 €, PDF, envoi par e-mail, cycle de vie. | V80 | 3 à 4 j |
| **4** | Facture : émission automatique au webhook Stripe, PDF archivé + empreinte, exclusion de l'anonymisation, avoirs. | V81 | 3 à 4 j |
| **5** | Factur-X : PDF/A-3 + XML CII, interface de transmission. | — | 2 à 3 j |
| **6** | Mobile Money (pawaPay) : intégration complète, webhook, rapprochement. | V82 | 4 à 6 j |
| **7** | Recette comptable : jeu d'essai des deux régimes, contrôle de continuité des numéros, relecture par le comptable. | — | 2 j |

**Total : 17 à 24 jours de développement**, hors délais de validation et hors
contractualisation avec le fournisseur Mobile Money.

**Ordre recommandé** : 1 → 2 → 3 → 4 → 7, puis 5 et 6. Les étapes 1 à 4 rendent
la vente possible par carte ; 5 et 6 l'élargissent.

---

## 5. Risques

| Risque | Conséquence | Parade |
|---|---|---|
| **Le pays est auto-déclaré** | TVA appliquée à tort ou à raison selon une déclaration non vérifiée | La clause de facturation rectificative existe déjà (CGV 3.9). Figer le pays déclaré **sur la facture**. Croiser avec le pays de la carte bancaire et **signaler les écarts**, sans bloquer. |
| **Trou de numérotation** | Irrégularité comptable, difficile à corriger après coup | Numéro attribué à l'émission seulement, jamais au brouillon. Contrôle de continuité en recette, et alerte périodique. |
| **Facture modifiée** | Pièce comptable invalide | Déclencheur PostgreSQL en plus de la règle applicative. Empreinte SHA-256 du PDF. |
| **Grille de TVA non établie** | Blocage de l'étape 2 | C'est l'objet de l'étape 0. Rien ne doit commencer avant. |
| **Mobile Money : dépendance externe** | Étape 6 non maîtrisée en durée | La sortir du chemin critique. Vendre par carte d'abord. |
| **Calendrier de la facture électronique** | Obligation manquée, ou travail fait trop tôt | Produire du Factur-X dès le début, isoler la transmission. La date devient alors une question de configuration, pas de refonte. |
| **Anonymisation RGPD et facture** | Effacement d'une pièce à conserver 10 ans | Exclusion explicite dans la tâche, **et un test qui le prouve**. |
| **Devis B2B existant** | Deux notions de « devis » cohabitent | Décider à l'étape 1 : soit le devis B2B boutique rejoint le nouveau modèle, soit les deux restent séparés et portent des noms distincts à l'écran. **À trancher avec vous.** |

---

## 6. Ce que j'attends de vous pour démarrer

1. La validation du **modèle de facture** par votre comptable.
2. Les réponses aux **trois questions [à confirmer]** du § 3.3.
3. Le calendrier de **facture électronique** qui s'applique à Optimi Santé.
4. Votre arbitrage sur le **devis B2B boutique** (§ 5, dernière ligne).

Rien de tout cela ne retarde la mise en ligne d'« Exercer en France ».
