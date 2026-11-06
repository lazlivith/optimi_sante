# Cahier des charges de finalisation — Optimi Santé V1

> **Préséance.** Ce document vient **après `CLAUDE.md` et D0**
> (`docs/maquette_catalogue_optimisante/prompt_dette_technique.md`), et **avant** tous les autres
> documents du dépôt, pour **l'ordre des travaux et le périmètre**.
>
> Il ne remplace aucune analyse existante pour le **contenu** d'un lot : il y renvoie.
> `docs/analyse_stockage_prive_et_feuille_de_route.md` fait foi pour `SP`, `BR` et `ST` ;
> `docs/plan_integration_catalogue.md` pour `CAT`, `PIL`, `IMP` et les maquettes ;
> `docs/backlog_brochures_et_securite.md` pour les critères d'acceptation des brochures.
>
> **Rédigé le 5 novembre 2026** (branche `docs/cahier-des-charges-v1`, fusionnée dans `main`),
> **révisé le 6 novembre 2026** après ta relecture : découpage en **deux lancements**,
> reclassement de `SEC‑2`, tri chiffré des huit points de dette sécurité, ajout de `LEG‑0`,
> `SEC‑7`, `LEG‑5` et `EXP‑4`, extension de `ST‑0`, scission de `ST‑4`, et calendrier refait avec
> une marge de 25 % et l'attente de relecture. Les écarts de cette révision sont au § 9 bis.
>
> Aucun code, aucune migration, aucune écriture en base, aucun déploiement.

**Convention de preuve.** Chaque état affirmé ci-dessous porte son fichier et sa ligne. Ce qui
n'était pas vérifiable depuis le dépôt est écrit **« non vérifié »**, sans exception et sans
formule d'atténuation. Les estimations sont en jours de développement et portent la mention
*estimation* quand aucun lot comparable n'a déjà été livré.

---

## 1. Le lancement, en deux temps

**Arbitré le 6 novembre 2026.** La mise en vente ne se fait plus en un bloc. Deux lancements,
dans cet ordre, et un troisième temps qui n'est pas un lancement.

| | Périmètre | Ce qu'on encaisse |
|---|---|---|
| **Lancement 1** | **Formations et Exercer en France** | Acomptes et soldes de frais de formation, options de service, accompagnement « Exercer en France » |
| **Lancement 2** | **Boutique** | Commandes de produits, devis B2B |
| **Après** | Refonte visuelle, second entrepôt, dette résiduelle | — |

**Pourquoi cet ordre tient, et il tient très bien.** Les deux activités ne partagent presque
rien : un acompte de formation ne passe par aucun panier, aucun stock, aucune zone de livraison,
aucune TVA à l'export, aucune facture produit. Et l'arborescence du catalogue — qui est le
chemin critique du lancement 2 et commence par **ton arbitrage** — ne bloque **rien** du
lancement 1. Découper ici, c'est encaisser trois mois plus tôt sur l'activité qui est déjà la
plus avancée, pendant que l'arbre s'arbitre.

### Lancement 1 — les six phrases qui doivent être vraies

1. **Dossiers.** Un dossier va de la candidature au kit d'arrivée sans qu'une pièce
   personnelle — passeport, diplôme, inscription à l'ordre — soit hébergée hors de l'Union
   européenne, et sans qu'une adresse de fichier atteigne un navigateur.
2. **Encaissement.** Un acompte encaissé fait avancer le dossier **sans geste manuel** ; le
   payeur reçoit **le document que la loi exige pour cet encaissement-là** — et ce document est
   celui que le comptable aura désigné, pas celui que le code émet aujourd'hui (§ 4, décision 5).
3. **Exercer en France.** Une demande arrive, est traitée, et **rien** sur le site ne laisse
   croire que nous sommes une autorité sanitaire.
4. **Partenaires.** Un CHU publie une formation, relit un dossier, retrouve sa brochure, et
   voit ses reversements — dont un au moins a réellement été exécuté.
5. **Administration.** Chaque geste est journalisé, et l'accès à des pièces d'identité est
   protégé **à la hauteur de ce qu'il ouvre** — donc par plus qu'un mot de passe.
6. **Exploitation.** La base est sauvegardée et une restauration a été **essayée** ; un paiement
   perdu se voit ; les e-mails arrivent ; et chaque phrase des documents légaux est vraie au
   moment où elle est publiée.

### Lancement 2 — les trois phrases qui s'ajoutent

7. **Boutique.** Un client voit un prix juste dans sa devise, pour une destination que nous
   savons servir ; il paie ; il reçoit une **facture conforme** ; et nous ne lui avons jamais
   promis un stock que nous n'avons pas compté.
8. **Catalogue.** L'arbre à trois niveaux est en place, les 211 adresses indexées redirigent, et
   les 7 344 lignes sont importées.
9. **Après-vente.** Une commande annulée ou remboursée rend son stock, et le client sait où en
   est son colis.

### Une correction à ma propre phrase 5

Ma rédaction du 5 novembre exigeait « un accès protégé à la hauteur des pièces d'identité » **et**
classait la double authentification après V1. Les deux ne tiennent pas ensemble : un mot de passe
seul pour ouvrir un passeport n'est pas à la hauteur. `SEC‑2` passe en **lancement 1**. De même,
une faille connue ne reste pas « non estimée » : les huit points du backlog sécurité sont triés
un par un au § 3.1, avec leur état **revérifié le 6 novembre** — et deux d'entre eux étaient déjà
corrigés sans que la note de suivi le dise.

### Les brochures

`BR‑2` à `BR‑5` sont rattachées au **lancement 1**, après `SP‑1`. Non par calendrier, mais par
périmètre : elles servent les **partenaires CHU**, qui sont exactement les clients du lancement 1,
et `BR‑1` leur a retiré une fonction en promettant son retour. La soupape du § B.6 bis tombe : il
n'y a plus de « après `ST` » où les repousser, puisque `ST` produit du catalogue appartient
maintenant au lancement 2.

---

## 2. L'état actuel, module par module

### 2.1 Catalogue

| | |
|---|---|
| **Fonctionne** | Fiche produit, panier, filtres, recherche, galerie et vidéo produit, marges par catégorie, TVA par produit avec héritage de la catégorie, import fournisseur avec rejeu (`V58`), devises et taux de change. |
| **Partiel** | L'arborescence est **plate** et le filtrage par catégorie se fait en **égalité stricte** — une page famille rendra zéro produit dès que l'arbre existera (`plan_integration_catalogue.md` § 1). La colonne marque est reconnue à l'import mais **sa valeur est jetée**. |
| **Manque** | L'arbre cible à trois niveaux, les **redirections 301** des 211 adresses indexées (aucun mécanisme de redirection n'existe côté serveur), l'import des 7 344 lignes LCM, le champ garantie, les délais de livraison par zone. |

### 2.2 Commande et expédition

| | |
|---|---|
| **Fonctionne** | Panier, commande, devis B2B, codes promotionnels, zones de livraison (`ZoneLivraison` : France métropolitaine, Afrique du Nord, Afrique de l'Ouest et centrale, Afrique de l'Est et australe — un pays absent n'est **pas** livré), estimation des frais, incoterm DAP. |
| **Partiel** | Le statut existe mais ne dit rien du paiement : `OrderStatus` = `PENDING, PROCESSING, SHIPPED, VALIDATED, REJECTED, CANCELLED` — **ni `PAID` ni `DELIVERED`**. L'axe paiement, lui, existe séparément : `PaymentStatus` porte `UNPAID, PAID, PENDING_APPROVAL, QUOTE_SENT, QUOTE_REJECTED, REFUNDED`. Les deux axes ne sont pas reliés par des règles de transition. |
| **Manque** | `paid_at` / `shipped_at` / `delivered_at` ; **aucun e-mail ni aucune notification d'expédition** — le répartiteur de notifications porte vingt événements (`NotificationDispatcher`) et **aucun** ne concerne un colis parti ; aucun suivi transporteur. |

### 2.3 Paiement

| | |
|---|---|
| **Fonctionne** | Stripe Checkout pour les cinq encaissements de la plateforme, répartition par `payment_type` (`StripePaymentDispatcher`), reçus `REC-` à numérotation sans trou avec PDF joint à l'e-mail (`PaymentReceiptIssuer`), échéancier 60 / 40 des frais de formation (`TuitionPaymentService`, depuis `V45`), remboursements (`V64`, `V65`). Le webhook `charge.refunded` **existe et est complet** : `PaymentWebhookResource:54` → `StripeRefundHandler`. |
| **Partiel** | L'acompte d'inscription est **correct dans le code** et bloqué en pratique ; le diagnostic attend la requête SQL sur le dossier `0BFDD667` et la page *Events* de Stripe. **Non vérifié** : lequel des deux maillons casse. |
| **Manque** | **Aucune facture.** Pas de série `FA-`, pas d'avoir `AV-`, aucune table, aucun numérotage : la seule occurrence du mot est un titre de PDF, `DoctorVaultResource:44` (« Facture acquittée » — c'est un reçu). Un reçu ne porte pas les mentions obligatoires et ne permet pas à un hôpital de récupérer la TVA. Aucune prise en compte de la destination dans la TVA. Clés de test sur Render et Vercel ; aucun point d'entrée de webhook live déclaré. Le test de bout en bout de `charge.refunded` n'a **jamais** été fait. |

### 2.4 Stock

| | |
|---|---|
| **Fonctionne** | `ModeStock` distingue `NON_SUIVI` (personne ne compte), `PROPRE` (nous comptons, opposable) et `FOURNISSEUR` (photographie de son entrepôt, jamais opposable) — `estOpposable()` est le seul test que le reste du code fait. Journal signé des mouvements (`stock_mouvements`, `V79`), `stock_inventorie_le`, sonde de veille désactivée tant qu'aucun inventaire n'existe. |
| **Partiel** | Mesure du 27 octobre inscrite dans le code : **1 464 produits sur 1 497** portaient le 50 posé par défaut à l'import. `NON_SUIVI` rend ce chiffre inoffensif, il ne le rend pas vrai. |
| **Manque** | Remise en stock à l'annulation et au remboursement, journal des ajustements manuels (lot B). **Aucune notion d'entrepôt** : le stock est un scalaire sur `products`, sans zone ni site. Le second entrepôt n'existe donc nulle part. |

### 2.5 Formations

| | |
|---|---|
| **Fonctionne** | Publication par le CHU avec circuit d'approbation (`TrainingApprovalStatus`), sessions, options de service, entretiens (`InterviewScheduleStatus`), documents officiels (`V57`), capture de prospects (`prospect_leads`). |
| **Partiel** | Le badge **« VLS-TS > 3 MOIS » est écrit en dur** (`frontend/src/pages/training/TrainingsPage.tsx:107-110`) et piloté par le seul booléen `training.isLongStay`, que `TrainingService` recopie du formulaire sans jamais le confronter à `durationDays` (lignes 276‑277 à la création, 311‑312 à la modification). Une formation d'un mois cochée « long séjour » affiche donc un visa qu'elle n'ouvre pas. |
| **Manque** | Le traitement des **images verticales** : la bannière de la fiche est un `objectFit="cover"` sur une hauteur fixe `h-64` (`TrainingDetailPage.tsx:98-103`), la vignette de liste un `h-40` (`TrainingsPage.tsx:95-97`) — une affiche en portrait est recadrée, sans image entière ni fond flouté, et aucun format conseillé n'est indiqué au dépôt. |

### 2.6 Inscriptions et parcours candidat

| | |
|---|---|
| **Fonctionne** | Les **douze** statuts serveur (`EnrollmentStatus`) — le commentaire de `parcoursDossier.ts` en annonce onze, écart d'un à reprendre dans `QUA‑1` —, leur lecture en **huit étapes** côté client (`frontend/src/lib/parcoursDossier.ts`, `ETAPES_PARCOURS`), un second parcours à six étapes pour « Exercer en France » (`ETAPES_FRANCE`, les statuts de visa fermés par l'automate), dépôt de pièces, demandes de pièces complémentaires, convention, programme, convocation, coffre-fort du médecin, appel du solde à la délivrance du visa (`TuitionBalanceCard`), kit d'arrivée. |
| **Partiel** | La **reprise d'un dossier en cours** vient d'être ajoutée par `DP‑4` et n'est pas déployée. Avant elle, fermer l'onglet entre l'étape 1 et l'étape 2 bloquait le candidat sur « Vous êtes déjà inscrit ». |
| **Manque** | Rien de structurel. **Non vérifié** : le parcours de bout en bout en production — il n'a jamais été joué en entier sur un dossier réel. |

### 2.7 Exercer en France

| | |
|---|---|
| **Fonctionne** | Page publique, modale de demande, anonymisation (`ServiceAnonymisationTest`), réglages d'administration, périodes, offre technique, délais et contact, accusé de réception et alerte interne séparés (`ACCOMPAGNEMENT_RECU`, `ACCOMPAGNEMENT_ALERTE_ADMIN`). |
| **Fonctionne, et c'est un garde-fou** | `frontend/src/pages/AucunLienSortant.test.tsx` **interdit par test** un lien sortant vers une autorité. Le positionnement « agence privée » est donc tenu par l'outillage, pas par la vigilance. |
| **Manque** | Rien. **Non vérifié** : la relecture mot à mot de tous les textes publics à la recherche de « officiel » ou « certifié ». Le test ne couvre que les **liens**, pas les **adjectifs**. |

### 2.8 Partenaires

| | |
|---|---|
| **Fonctionne** | Demande de partenariat, convention, dossier Excel, tableau de bord partenaire, sessions, relecture des dossiers, finances, reversements : `PartnerPayout`, `PartnerPayoutService`, `SepaVirementService`, `ReferencesReversement`, relevé PDF (`V29`). Le module est substantiellement bâti. |
| **Partiel** | **Non vérifié** : aucun reversement réel n'a été exécuté. Le circuit existe, son passage en conditions réelles n'a pas eu lieu. |
| **Manque** | Les **conditions partenaires** et la clause de non-contournement : aucune occurrence dans le dépôt, ni page, ni document, ni gabarit. Et la brochure du partenaire, retirée du public par `BR‑1` le 29 octobre avec la promesse qu'elle revienne à `BR‑5`. |

### 2.9 Documents

| | |
|---|---|
| **Fonctionne** | Arborescence fermée (`DossierStockage` : une énumération, un chemin inconnu ne compile plus), neuf dossiers privés, neuf gabarits PDF (`templates/pdf`), coffre-fort du médecin, jeton d'accès aux documents **chiffré** (AES‑GCM) depuis `DP‑4`. |
| **Partiel** | `SignatureFichier` n'est branché **qu'à un seul endroit** : `ControleFichier.java:67`, et seulement pour exiger un PDF. `ControleFichier` lui-même n'est appelé que trois fois — `StorageController:44`, `PartnerTrainingResource:103`, `OfficialDocumentService:288`. |
| **Manque** | Le stockage privé en France : tout est sur Cloudinary, dont le compte est hébergé **aux États-Unis** (§ A.1 de l'analyse de stockage). Aucune facture ni aucun justificatif d'export à ranger, puisqu'ils n'existent pas. |

### 2.10 E-mails

| | |
|---|---|
| **Fonctionne** | Huit types (`EmailType`), un envoyeur par type, et une trace en base. Le reçu part en pièce jointe pour les cinq encaissements. Les e-mails de candidature sont volontairement **séparés** (accusé et identifiants) et cette logique est gelée. |
| **Partiel** | L'alerte de brochure part sous le type générique `NOTIFICATION` faute de pouvoir étendre la contrainte SQL sans migration — le choix est documenté dans `EmailType` lui-même, et `BR‑2` porte la migration qui le corrigera. |
| **Manque** | **Aucun e-mail d'expédition.** C'est le seul manque de ce module, et il est net : un client qui a payé n'apprend jamais que son colis est parti. La confirmation de commande, elle, **existe** — portée par le reçu. |

### 2.11 Administration

| | |
|---|---|
| **Fonctionne** | Vingt-neuf écrans d'administration, journal d'audit (`AuditTrailInterceptor`), gouvernance, RGPD, analytique, alertes, devises, fiscalité, stock, fournisseurs. |
| **Manque** | L'**écran de suivi des prospects** : `ProspectLead` et son dépôt existent côté serveur, et **aucun fichier du frontend ne les mentionne**. Les prospects capturés ne sont visibles de personne. |

### 2.12 Sécurité

| | |
|---|---|
| **Fonctionne** | JWT, rôles, cloisonnement par locataire, garde-fou d'identité légale au démarrage (`ValeurDAttente` — il refuse « à renseigner » autant que le vide, après que les reçus ont annoncé « SIRET : IMMATRICULATION EN COURS » pendant des mois), jeton de document chiffré, liste fermée des dossiers de dépôt. |
| **Manque, et c'est le point le plus sérieux de cet audit** | **Aucun limiteur de débit, nulle part.** Aucune dépendance (ni `bucket4j`, ni `resilience4j` dans `backend/pom.xml`), aucun code. Or `SecurityConfig` ouvre en `permitAll` : `/api/v1/auth/**` (ligne 75), `/api/v1/partnership/**` (96), `/api/v1/doctor-applications/**` (97), `/api/v1/ai/chat` (102), `/api/v1/newsletter/subscribe` (95), `/api/v1/trainings/*/lead-capture` (92), `/api/v1/public/demandes-accompagnement` (110). |
| | **Et `/api/v1/partnership/**` porte deux dépôts de fichier** (`PartnershipResource:81` et `:100`), donc **anonymes**, **sans contrôle de signature** — `ControleFichier` n'y est pas appelé — avec `max-file-size: 55MB` et `max-request-size: 120MB` (`application.yml:18,21`). N'importe qui peut écrire dans notre stockage, autant de fois qu'il veut, 55 Mo à la fois. |
| | Pas de double authentification. Le JWT est en `localStorage` (`AuthContext.tsx`, `LoginPage.tsx`) : une faille d'injection de script le vole. |
| **Registre d'incidents** | Il **existe** — `docs/incident_security/registre_incidents_securite`, un incident, celui du 29 octobre, correctement rédigé — mais il est **non suivi par git**, sans extension de fichier, et n'existe donc que sur ce poste. Il porte une ligne « date de fin de migration à compléter » que `SP‑5` devra renseigner. |

### 2.13 Légal

| | |
|---|---|
| **Fonctionne** | Identité légale **complète et réelle** (`frontend/src/config/legal.ts` : SIREN 937 848 869, SIRET, RCS Bordeaux, TVA FR24937848869, capital, directeur de publication, hébergeur nommé). CGV de 363 lignes couvrant les délais, l'incoterm DAP et les droits à l'arrivée, la rétractation des consommateurs et son exclusion pour les professionnels, les remboursements, les litiges. |
| **Fonctionne, contre toute attente** | Le **consentement aux cookies n'est pas requis** : la politique déclare n'utiliser que le strictement nécessaire, et c'est **vrai** — aucune mesure d'audience tierce dans le dépôt (ni `gtag`, ni Matomo, ni Plausible, ni pixel), et le stockage local ne contient que le jeton, le panier et le choix de devise. Voir le § 9, écart n° 5. |
| **Faux, aujourd'hui, dans un document publié** | `PrivacyPolicyPage.tsx:180` : « **Les données sont hébergées dans l'Union européenne.** » Les pièces des candidats sont sur un compte Cloudinary hébergé aux États-Unis. La phrase deviendra vraie à `SP‑5`, et pas avant. |
| **Vide, dans un document publié** | `PrivacyPolicyPage.tsx:176-177` : la section **« 5. Coffre-fort documentaire » est un paragraphe vide**. Elle a un titre, une entrée dans le sommaire, et aucun contenu. |
| **Manque** | La liste **nommée** des sous-traitants (la politique parle du « prestataire de paiement » et de « l'hébergeur » sans les nommer) ; le **registre des traitements** — introuvable dans le dépôt ; l'**adhésion à un médiateur de la consommation** : `mediateurNom` et `mediateurSite` sont `null` dans `legal.ts:92-93`, alors que les CGV renvoient le consommateur à un médiateur et que l'adhésion est **obligatoire** pour un commerçant en ligne B2C. `dateMaj: '9 septembre 2026'`. |

### 2.14 Exploitation

| | |
|---|---|
| **Manque, en totalité** | **Aucune intégration continue** : `.github/workflows` est vide. Rien ne tourne à aucun `push` ; la routine de livraison de `CLAUDE.md` est tenue à la main, à chaque fois, par celui qui s'en souvient. |
| | **Aucune description d'infrastructure** : ni `render.yaml`, ni `vercel.json`. Les deux hébergements ne sont décrits que dans leurs consoles. |
| | **Non vérifié, et non vérifiable depuis le dépôt** : la fréquence des sauvegardes de la base Render, et le fait qu'une restauration ait déjà été essayée. Tant que la seconde n'a pas eu lieu, nous n'avons pas de sauvegarde — nous avons l'espoir d'en avoir une. |
| | Aucune supervision des erreurs, aucune alerte sur échec de webhook. Un paiement encaissé dont le webhook échoue est aujourd'hui **silencieux**. |
| **Dette d'outillage mesurée** | `ControleBrochureTest.java:29` lit `Path.of("..", "docs", "brochures_test", …)` — **hors du module `backend`**. Le miroir ne reçoit que `git archive HEAD:backend` : ce test ne peut pas s'exécuter dans le dépôt de déploiement. Le PDF est bien suivi par git dans le monorepo, lui. |
| | `application.yml:161-170` déclare `app.aws.s3` (endpoint, région, clés, bucket) **et** `app.aws.kms`. **Aucun code ne les lit, et il n'y a aucune dépendance AWS SDK dans `pom.xml`.** C'est de la configuration morte, avec des valeurs d'apparence secrète (`access-key: test`), et un futur lot `SP‑1` pourrait croire qu'un client S3 existe déjà. |
| | `frontend/scripts/charte-reference.json:34` : `"_plafondJsKo": "1 333 Ko produits aujourd'hui."` — périmé, la mesure donne 1 289 Ko. |
| | `docs/prompts_catalogue/M3_navigation_catalogue.md` couvre deux lots depuis la coupe en `M3a`/`M3b` et doit être scindé ; `PIL_import_pilote_lcm.md` affirme à tort que `import/pilote-lcm` est fusionné dans `main`. |
| **Instabilité des tests** | Traitée par contournement assumé : `vitest.config.ts` porte `testTimeout: 20_000` au lieu de 5 000, avec son raisonnement écrit. L'épisode « 6 échecs, 33 réussites » n'a jamais été reproduit en dix-huit exécutions. La cause n'est pas identifiée. |
| **Volume de tests** | 45 fichiers `*Test.java` au backend, dont 17 portent `@SpringBootTest` ou Testcontainers et sont donc **ignorés sans Docker** ; 13 fichiers de test au frontend, dont la couverture est volontairement limitée à « Exercer en France » et à quelques garde-fous. |

---

## 3. Inventaire des travaux

Colonne de rattachement : **`L0`** = tout de suite, avant tout le reste · **`L1`** = lancement 1
(formations et Exercer en France) · **`L2`** = lancement 2 (boutique) · **`Ap`** = après.
La colonne **jours** est en jours de développement ; **JS** est le poids ajouté au JavaScript
servi.

### 3.0 `L0` — tout de suite, avant `SP`

Trois lots, **3 jours**, aucune dépendance. Ils sont à part parce qu'ils ne corrigent pas un
manque : ils ferment une porte qui est ouverte **cette nuit**.

| ID | Module | Objet | Pourquoi maintenant et pas en février | Dépend de | L | Jours | JS | Statut |
|---|---|---|---|---|---|---|---|---|
| `LEG‑0` | légal | **Rendre vraie** la phrase de `PrivacyPolicyPage.tsx:180` : hébergement des documents chez un prestataire **hors UE**, migration **en cours** vers un hébergement en France. Et **remplir la section 5**, aujourd'hui un paragraphe vide | Laisser publiée trois mois de plus une affirmation qu'on sait fausse transforme un défaut technique en **déclaration inexacte**. En cas de contrôle, la date à laquelle nous l'avons su compte plus que la date à laquelle nous l'avons corrigé | rien | **L0** | 0,5 | 0 | **texte à soumettre d'abord** |
| `SEC‑3` | sécurité | **Fermer le dépôt anonyme** de `/api/v1/partnership/**` : contrôle de signature, plafond de taille propre à la route, jeton de formulaire | Deux `POST` multipart en `permitAll` (`PartnershipResource:81` et `:100`), sans contrôle de signature, à **55 Mo par appel** et sans limite de fréquence. N'importe qui peut remplir notre stockage ce soir | rien | **L0** | 1 | 0 | à faire |
| `SEC‑1` | sécurité | **Plafonner les sept routes `permitAll`**, par adresse et par route. **`/api/v1/ai/chat` en premier** | Un assistant d'IA **que nous payons**, ouvert aux anonymes, sans plafond. La facture ne se découvre qu'après | rien | **L0** | 1,5 | 0 | à faire |

`LEG‑1` réécrit la politique **après `SP‑5`**, quand la phrase exacte devient « hébergées en
France ». `LEG‑0` n'est donc pas un brouillon de `LEG‑1` : c'est l'état vrai de l'étape
intermédiaire, et il doit l'être pendant toute la migration.

### 3.1 Sécurité et données personnelles

| ID | Module | Objet | Pourquoi (risque si non fait) | Dépend de | L | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `DP‑4` | documents | Plus aucune clé de stockage au navigateur ; jeton chiffré ; alias de transition ; `409 REFERENCE_PERIMEE` ; reprise du dossier | La clé d'un passeport est lisible dans la réponse d'upload **et** dans la charge de tout jeton de téléchargement | rien | **L1** | 1,5 | 0 | **écrit, poussé, non déployé** (`fix/dp4-cles-hors-navigateur`, `9c06759`) | toi (fenêtre) |
| `SP‑1` | documents | `StockageService` à deux adaptateurs ; `StockagePriveS3` Scaleway ; lecture avec repli Cloudinary | Les pièces d'identité restent hors UE tant que ce lot n'existe pas | comptes + clés Scaleway | **L1** | 3 | 0 | à faire | toi (comptes) |
| `SP‑2` | documents | Correction du commentaire de `DossierStockage` et de l'inventaire ; **retrait de l'alias `publicId`** | Un alias qui survit devient une interface | `SP‑1` | **L1** | 0,5 | 0 | à faire | — |
| `SP‑3` | documents | Script de copie : essai à blanc, lots, journal, reprise, taille + SHA‑256 | Une copie non vérifiée est une perte de document qu'on découvre six mois après | `SP‑1` | **L1** | 2,5 | 0 | à faire | — |
| `SP‑4` | documents | Exécution de la copie, pièces des candidats d'abord | — | `SP‑3` + sauvegarde + ton accord | **L1** | 1 | 0 | à faire | toi |
| `SP‑5` | documents | Retrait du repli ; suppression chez Cloudinary ; **demande écrite au support** pour leurs sauvegardes | Sans la demande au support, la copie de sauvegarde du passeport reste chez eux après la suppression | `SP‑4` vérifié | **L1** | 1 | 0 | à faire (courriel rédigé, § A.9) | toi (envoi) |
| `SP‑6` | documents | `SignatureFichier` sur **toutes** les routes de dépôt | Aujourd'hui branché à un seul endroit : un `.exe` renommé `.pdf` entre | rien | **L1** | 1 | 0 | à faire | — |
| `SP‑7` | documents | Durées de conservation : cycle de vie, clôture par parcours, purge de l'objet **et de ses versions non courantes**, avertissement de réouverture | Sur un bucket versionné, un `DeleteObject` pose un marqueur et laisse la version lisible : la purge ne purgerait rien | `SP‑5` + `SEC‑6c` + validation comptable | **L1** | 2 | 0 | à faire | toi + comptable |
| `SEC‑2` | sécurité | **Double authentification des comptes d'administration** | Un compte d'administration ouvre des pièces d'identité ; un mot de passe seul n'est pas « à la hauteur de ce qu'il ouvre ». Reclassé en `L1` le 6 novembre : ma propre phrase 5 l'exigeait déjà | `SEC‑1` | **L1** | 2,5 | ~8 Ko | à faire | toi |
| `SEC‑4` | sécurité | Versionner le registre d'incidents (`.md`, sous git), y fixer ce qu'il doit contenir, et le relier à `SP‑5` | Un registre qui n'existe que sur un poste n'est pas un registre | rien | **L1** | 0,5 | 0 | **rédigé, non suivi par git** | — |
| `SEC‑7` | sécurité | **En-tête `Content-Security-Policy`** : `script-src` en liste blanche, `object-src 'none'`, `base-uri 'self'`, `frame-ancestors 'none'` — posé au serveur et vérifié par un test | **Atténuation tant que `SEC‑5` n'est pas fait** : le jeton est en `localStorage`, donc à portée de tout script injecté. Une CSP ne supprime pas le risque, elle supprime la **voie** la plus courante d'y arriver. Peu coûteux, immédiat | rien | **L1** | 1 | 0 | à faire | — |
| `SEC‑5` | sécurité | Jeton hors `localStorage` (cookie `HttpOnly` + `SameSite`) | Une injection de script vole la session. `SEC‑7` atténue, ne corrige pas | `SEC‑7` | **Ap** | 1,5 | 0 | à faire | — |

**`SEC‑6` — les huit points du backlog, triés un par un.** État **revérifié dans le code le
6 novembre 2026**, et non repris de la note de suivi du 11 octobre : **deux points étaient déjà
corrigés sans qu'elle le dise**, et un autre change de rang parce que `SP‑7` va le rendre
dangereux.

| | Point | État vérifié le 6/11 | L | Jours |
|---|---|---|---|---|
| `SEC‑6a` | **Le tenant vient du client.** `doctor-applications` le lit dans le **corps** de la requête (`DoctorApplicationService:99`, `tenantRepository.findByCode(dto.getTenantCode())`), et l'en-tête `X-Tenant-Id` sert de source pour les visiteurs anonymes (`JwtAuthenticationFilter:62`) | **toujours présents** — points 1 et 2 | **L1** | 1 |
| `SEC‑6b` | **Le rôle `ADMIN` figure encore** dans les trois annotations : `PlatformAdmin:28`, `MobilityAdmin:21`, `EcommerceAdmin:23` | **toujours présent** ; la condition de sortie est remplie depuis le 11/10 (un `SUPER_ADMIN` actif existe, le seul compte `ADMIN` est désactivé) — c'est donc un retrait, pas une migration | **L1** | 0,5 |
| `SEC‑6c` | **L'extension est portée par la clé** des médias de formation (`Training.imageS3Key`, `V19`) | **toujours présent**, et **sûr aujourd'hui** : aucun `deleteFile` ne lit cette colonne (les deux appels existants portent sur `getPublicId()`). Mais `SP‑7` introduit précisément une **suppression** : ce point doit être corrigé **avant** `SP‑7`, pas après | **L1** | 0,5 |
| `SEC‑6d` | **Jackson 2 par transitivité**, sans `jackson-datatype-jsr310`, alors que Spring Boot 4 embarque Jackson 3 | **toujours présent** : aucune dépendance Jackson directe dans `pom.xml`, seul `jjwt-jackson` l'amène | **Ap** | 1 |
| `SEC‑6e` | **`@Scheduled` sans verrou partagé** : 4 fichiers concernés, dont l'anonymisation RGPD et la libération des réservations de stock | **toujours présent**, aucun ShedLock dans `pom.xml`. Sans effet tant que le backend tourne sur **une** instance : le jour où il en aura deux, chaque tâche s'exécutera deux fois | **Ap** | 1 |
| ~~point 5~~ | **Adresses `javascript:` rendues en `<a href>`** — dont `products.video_url`, marqué « PRIORITAIRE » | **CORRIGÉ, des deux côtés, et la note ne le disait pas.** Au serveur, `AdresseWeb.exiger` filtre `products.video_url` (`ProductMediaService:132`), `interview_schedules.meeting_link` (`InterviewSchedulingService:86`) et les liens EVC (`ServiceContenuEvc:304`). Au client, les deux `<a href>` de `ProductVideo.tsx` sont gardés par `adresseSure` | — | 0 |
| ~~point 7~~ | **Authentification des e-mails** | **CORRIGÉ**, et vérifié de nouveau en DNS le 6/11. Ce qui reste est ailleurs et bien plus petit : voir `EXP‑4` | — | 0 |

**Total `SEC‑6` : 2 jours en `L1`, 2 jours après.** Et **zéro** reste « non estimé » : la faille
du formulaire médecins que tu nommais est `SEC‑6a`, elle coûte **un jour**, et elle part en
lancement 1.

### 3.2 Brochures partenaires

**Rattachées au lancement 1**, après `SP‑1`, par ton arbitrage du 6 novembre. Le motif n'est
plus calendaire mais de périmètre : les brochures servent les **partenaires CHU**, qui sont les
clients du lancement 1. La soupape « les repousser après `ST` » n'a plus de sens, puisque le `ST`
de la boutique part au lancement 2.

| ID | Module | Objet | Pourquoi | Dépend de | L | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `BR‑2` | partenaires | Modèle : brochure, statuts, autorisation du partenaire, accès journalisé, suppression à 90 jours ; **original en S3 privé**. Porte la migration du type `DEMANDE_BROCHURE_ALERTE_ADMIN` | `BR‑1` a retiré une fonction utilisée, en promettant son retour | **`SP‑1`** | **L1** | 2 | 0 | à faire | — |
| `BR‑3` | partenaires | Extraction PDFBox, garde-fous mémoire / temps / pages, **purge déterministe des coordonnées avant tout enregistrement** | Enregistrer puis purger laisse la trace dans les sauvegardes | `BR‑2` | **L1** | 3 | 0 | à faire | — |
| `BR‑4` | partenaires | Écran de dépôt partenaire, écran de relecture, éléments retirés mis en évidence | Un partenaire doit voir ce qu'on a retiré de son texte avant publication | `BR‑3` | **L1** | 2,5 | 22 Ko | à faire | — |
| `BR‑5` | partenaires | Validation `ADMIN_MOBILITE` / `SUPER_ADMIN`, motif de refus, gabarit Optimi Santé (11ᵉ), pied de page validé, **prix du partenaire jamais publié**, en public l'établissement et la ville seulement — adresse précise réservée à la convocation | L'ancienne version reste en ligne jusqu'à la validation : c'est ce qui rend la modification sans risque | `BR‑4` | **L1** | 2 | 6 Ko | à faire | — |
| `BR‑6` | partenaires | Structuration par l'IA | Hors feuille de route : dépend d'une décision non prise, et ne bloque rien | `BR‑5` | **Ap** | *non estimé* | — | **décision ouverte n° 7** | toi |

### 3.3 Catalogue et import

**Tout ce bloc part au lancement 2**, et c'est le gain principal du découpage : l'arbre est le
chemin critique de la boutique, il commence par **ton arbitrage**, et il ne bloque **rien** du
lancement 1. Son attente se superpose entièrement au travail sur les formations.

| ID | Module | Objet | Pourquoi | Dépend de | L | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `M0` | catalogue | Cadrage, dont la **requête de contrôle des visuels factices** et la source unique côté serveur | Quatre listes divergentes de visuels factices ont déjà existé | rien | **L2** | 1 | 0 | à faire | — |
| `CAT` | catalogue | Arbre cible à 3 niveaux, filtrage **par descendance**, **redirections 301**, route d'arbre en cache, administration de l'arbre | Sans descendance, zéro produit sur toute page famille ; sans 301, 211 adresses indexées cassent | **ton arbitrage** (`docs/arborescence_catalogue_v2.xlsx`) | **L2** | 6–8 | incluses au budget | à faire | toi |
| `PIL` | catalogue | Import pilote LCM (50 lignes) | — | `CAT` + marge fixée | **L2** | 0,5 | 0 | à faire | toi (marge) |
| `IMP` | catalogue | Import complet, **7 344 lignes** | Durée de transaction, images manquantes, produits à désactiver : d'autres questions que le pilote | `PIL` vert + décisions 7, 8, 9 | **L2** | 1,5 | 0 | à faire | toi |
| `STK‑2` | stock | **Second entrepôt** (Congo, propriété d'Optimi SAS) : notion de site de stock, grille de prix en FCFA couvrant transport et douane | Rien n'existe : le stock est un scalaire sans site. C'est un chantier, pas un réglage | `ST‑1`, `IMP` | **Ap** | 5–7 *(estimation, aucun lot comparable)* | ~10 Ko | à faire | toi |
| `STK‑3` | commande | Commande minimum et délais pour les zones non couvertes | Une zone non desservie refuse déjà la commande et invite au devis : le manque est le **délai**, pas le refus | `L` | **L2** | 0,5 | 0 | **décisions ouvertes n° 8 et 9** | toi |

### 3.4 Maquettes — les dix écrans

Aucun de ces lots n'est une condition de lancement : ils refont des écrans qui **fonctionnent**.
Tous en **`Ap`**, sauf `L`, qui n'est pas une maquette mais une donnée manquante du panier et
part donc au lancement 2 avec le reste de la boutique.
Budget JS arrêté : **183 Ko catalogue + 28 Ko brochures = 211 Ko**, sur une marge mesurée de
211 Ko (1 289 Ko servis, plafond 1 500). La marge est consommée **exactement**, sans réserve —
et les 28 Ko des brochures sont désormais consommés au lancement 1.

| ID | Module | Objet | Écrans livrés | Dépend de | L | Jours | JS |
|---|---|---|---|---|---|---|---|
| `M1` | catalogue | En-tête et barre de recherche | **aucun** (présentes sur les 6 écrans de `M3a`/`M3b`) | `M0` | **Ap** | 1,5 | 10 Ko |
| `M2` | catalogue | Fiche produit | 9, 10 | `M1` | **Ap** | 4 | 40 Ko |
| `M3a` | catalogue | Méga-menu, page Catalogue, Catalogue mobile | 1, 2, 3 | `CAT` | **Ap** | 3 | 25 Ko |
| `M3b` | catalogue | Famille, rayon, versions mobiles, panneau Filtrer | 4 à 8 | `CAT` | **Ap** | 4 | 30 Ko |
| `M4` | catalogue | Marque (filtre et libellé) | **aucun** | `M3b`, `CAT` | **Ap** | 2,5 | 12 Ko |
| `M2‑bis` | catalogue | Caractéristiques, documents, garantie | **aucun** (onglets *dans* la fiche) | `M2`, **`SP‑1`** | **Ap** | 3 | 25 Ko |
| `L` | commande | Délais de livraison par zone | **aucun** | `CAT` | **L2** | 1,5 | 0 |

**Aucun écran orphelin** : les dix sont couverts par `M2`, `M3a` et `M3b`. Les quatre autres lots
ne livrent aucun écran et se vérifient donc sur les captures de ceux-là — `M1` sur les six écrans
de `M3a`/`M3b`, `M4` sur Famille, Rayon et la fiche, `M2‑bis` dans la fiche, `L` sur le panier et
la commande. Le **numéro WhatsApp** est un paramètre : sans lui, `M2` se livre, bouton masqué,
sans dette. Le **texte DAP** est à centraliser au premier lot qui y touche — le code dit « hors
de France », pas « hors UE », et le serveur porte `droitsALArrivee`.

### 3.5 Paiements et facturation

Le découpage passe exactement ici. **`ST‑7` ne se fait qu'une fois** : le compte Stripe bascule
en live au lancement 1, et le lancement 2 n'a plus à y revenir.

| ID | Module | Objet | Pourquoi | Dépend de | L | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `ST‑0` | exploitation | **Neutralisation de toutes les données de test**, pas seulement des commandes : ventes fictives, **dossiers d'inscription** (dont `0BFDD667`), **paiements d'inscription**, **prospects** et **comptes de test**. Inventaire **en lecture seule d'abord**, puis script `ON_ERROR_STOP=1` qui compte les lignes et `RAISE EXCEPTION` si le compte n'est pas celui attendu | Étendu le 6 novembre. Il ne neutralisait que les commandes : un dossier de test qui survit au lancement entre dans les statistiques de conversion, déclenche des relances, et `0BFDD667` est précisément celui qui a servi à tous les essais de dépôt | ton accord + sauvegarde | **L1** | 1,5 | 0 | à faire | toi |
| `ST‑6` | formations | **Acompte d'inscription** : correction du blocage, puis confirmation manuelle par l'administration **vérifiée auprès de Stripe** — l'admin demande au serveur de relire le `PaymentIntent`, il ne déclare pas « payé ». Journal de qui a confirmé quoi | Une confirmation déclarative est une porte ouverte à l'erreur et à la fraude interne. **C'est le lot le plus critique du lancement 1** : sans lui, rien ne s'encaisse | le diagnostic (SQL + *Events*) | **L1** | 2 *(peut tomber à 0,5 selon le diagnostic — non revu à la baisse par prudence)* | 0 | **bloqué** | toi |
| `ST‑4a` | facturation | **Le document d'encaissement des frais de formation** : ce que le comptable désignera — reçu actuel conservé, note au sens de l'arrêté du 3 octobre 1983, ou facture complète. Et la mention de TVA correspondante | Le `REC-` actuel a été conçu comme un reçu, pas comme une pièce comptable opposable. Encaisser 60 % d'une formation chez un particulier avec le mauvais document est une irrégularité qui se répète à chaque vente | **décision 5**, `SP‑1` | **L1** | 2 *(estimation, dépend de la réponse)* | 0 | **bloqué par le comptable** | comptable |
| `ST‑3` | paiement | **Test de bout en bout de `charge.refunded`** en mode test, remboursement déclenché depuis le tableau de bord Stripe, sur un **paiement de formation** | Le code est complet et n'a jamais tourné. Un webhook jamais essayé est un webhook inconnu. `RemboursementFormation` existe et n'a jamais servi | `ST‑6` | **L1** | 0,5 | 0 | à faire | — |
| `ST‑7` | paiement | **Passage en live** : clés live sur Render et Vercel, **nouveau** point d'entrée de webhook live avec son propre secret, paiement réel de faible montant puis remboursement | — | `ST‑0`, `ST‑6`, `ST‑4a`, **activation du compte live** | **L1** | 0,5 | 0 | à faire | toi |
| `FIN‑1` | partenaires | Exécution réelle d'un reversement partenaire, de bout en bout | Le circuit existe (`PartnerPayoutService`, `SepaVirementService`) et n'a jamais tourné en conditions réelles. Un CHU qui attend son premier virement est un partenaire qu'on perd ou qu'on garde | `ST‑6`, `ST‑7` | **L1** | 0,5 | 0 | à faire | toi |
| `CDE‑2` | administration | **Écran de suivi des prospects** (`prospect_leads`) | Les prospects sont capturés et invisibles de tous — et ce sont les prospects **des formations** | rien | **L1** | 1 | ~6 Ko | à faire | — |
| `ST‑1` | commande | **Cycle de vie des commandes** : `PAID` et `DELIVERED`, règles de transition, `paid_at` / `shipped_at` / `delivered_at`, statut rendu au client, écran d'administration. Migration | Une commande payée par carte reste `PENDING`, et le client ne voit jamais son statut | rien | **L2** | 3 | ~5 Ko | à faire | — |
| `ST‑2` | stock | **Lot B** : remise en stock à l'annulation et au remboursement, journal des ajustements manuels | Un remboursement qui ne rend pas le stock fait vendre deux fois le même article | `ST‑1` | **L2** | 2,5 | 0 | à faire | — |
| `ST‑4b` | facturation | **Factures produit `FA-`** à numérotation sans trou, mentions obligatoires, **avoirs `AV-`** adossés à une facture, PDF en stockage **privé** | Un hôpital ne récupère pas la TVA sur un reçu. Aucune facture n'existe aujourd'hui | `ST‑1`, `ST‑4a`, `SP‑1` | **L2** | 4 | ~5 Ko | à faire | toi + comptable |
| `ST‑5` | facturation | **TVA produits** : taux vérifiés rayon par rayon, booléen export déduit de la zone, mention d'exonération (art. 262‑I CGI), justificatif de sortie rangé | Aucun pays de l'UE hors France n'est livré : pas de guichet unique, pas de 27 taux | `ST‑4b` | **L2** | 2 | 0 | à faire | toi + comptable |
| `CDE‑1` | commande | **E-mail et notification d'expédition**, avec le suivi s'il existe | Un client qui a payé n'apprend jamais que son colis est parti. Zéro événement d'expédition sur vingt | `ST‑1` | **L2** | 1,5 | 0 | à faire | — |

### 3.6 Formations et Exercer en France

**Tout ce bloc est au lancement 1** : c'est son activité.

| ID | Module | Objet | Pourquoi | Dépend de | L | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `FOR‑1` | formations | **Cohérence du badge de visa** : `isLongStay` déduit ou contraint par `durationDays`, avec refus à la saisie et badge calculé | Une formation d'un mois annonce « VLS-TS > 3 MOIS ». C'est une information de droit au séjour, fausse, publiée | rien | **L1** | 0,5 | 0 | à faire | — |
| `FOR‑2` | formations | **Images verticales** : image entière sur fond flouté dans la bannière, format conseillé indiqué au dépôt | Les affiches de CHU sont en portrait et sont recadrées | rien | **L1** | 1 | ~3 Ko | à faire | — |
| `FOR‑3` | formations | Relecture mot à mot des textes publics : aucun « officiel », aucun « certifié », aucun lien vers une autorité | Le test ne couvre que les **liens**. Un adjectif passe | rien | **L1** | 0,5 | 0 | à faire (garde-fou partiel : `AucunLienSortant.test.tsx`) | toi (texte) |
| `FOR‑4` | formations | Parcours candidat joué **en entier** sur un dossier de test en production, les huit étapes | Chaque étape a été vérifiée seule ; l'enchaînement complet, jamais | `DP‑4` déployé, `ST‑6` | **L1** | 1 | 0 | à faire | toi |

### 3.7 Légal et contenu

| ID | Module | Objet | Pourquoi | Dépend de | L | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `LEG‑0` | légal | **Voir § 3.0.** Rendre vraie la phrase de `PrivacyPolicyPage.tsx:180` et remplir la section 5 vide | Une affirmation fausse qu'on sait fausse | rien | **L0** | 0,5 | 0 | **texte à soumettre** | toi |
| `LEG‑1` | légal | Politique de confidentialité, **réécriture complète** : § 9 passé à « hébergées en France », **sous-traitants nommés** (Scaleway, Stripe, Mailtrap, Render, Vercel, service d'IA s'il est activé), section 5 développée, date de mise à jour | `LEG‑0` dit la vérité de l'étape intermédiaire ; `LEG‑1` dit celle de l'état final | `SP‑5` | **L1** | 1 | 0 | à faire | toi (validation) |
| `LEG‑2` | légal | **Adhésion à un médiateur de la consommation**, puis `VITE_LEGAL_MEDIATEUR_NOM` et `…_SITE` | Obligation légale pour un commerçant en ligne B2C. Les CGV y renvoient déjà et les deux champs sont `null`. **Le délai ne nous appartient pas** | — | **L1** | 0 (dev) | 0 | **démarche à engager cette semaine** | toi |
| `LEG‑3` | légal | **Conditions partenaires** avec clause de non-contournement | Aucune occurrence dans le dépôt. Sans elle, rien n'empêche un CHU de reprendre un candidat en direct — et les partenaires CHU sont les clients du lancement 1 | rien | **L1** | 1 | ~2 Ko | à faire | toi (texte) |
| `LEG‑4` | légal | **Registre des traitements** : où il vit, ce qu'il contient, qui le tient à jour | Introuvable. Obligatoire dès qu'on traite des données de santé et d'identité | `LEG‑1` | **L1** | 0,5 | 0 | à faire | toi |
| `LEG‑5` | légal | **CGV : le volet formation** — ce que la réponse du comptable impose (document d'encaissement, mention de TVA), et la cohérence avec l'échéancier 60 / 40 déjà appliqué | Les CGV décrivent déjà l'échéancier ; elles ne décrivent pas la pièce remise ni le régime de TVA | **décision 5**, `ST‑4a` | **L1** | 0,5 | 0 | à faire | toi + comptable |

### 3.8 Qualité et exploitation

| ID | Module | Objet | Pourquoi | Dépend de | L | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `EXP‑1` | exploitation | **Sauvegardes** : fréquence constatée et écrite, puis **restauration essayée** sur une base jetable, avec sa date | Une sauvegarde jamais restaurée n'est pas une sauvegarde | rien | **L1** | 1 | 0 | **non vérifié** | toi |
| `EXP‑2` | exploitation | Supervision des erreurs, **alerte sur échec de webhook**, journaux conservés | Un paiement encaissé dont le webhook échoue est aujourd'hui silencieux | rien | **L1** | 1,5 | 0 | à faire | — |
| `EXP‑3` | exploitation | **Intégration continue** : build, suite complète, garde-fou de charte à chaque `push`, avec le **nombre d'ignorés** rapporté | `.github/workflows` est vide. La routine de `CLAUDE.md` est tenue de mémoire | rien | **L1** | 1 | 0 | à faire | — |
| `EXP‑4` | exploitation | **Délivrabilité des e-mails** — et le périmètre est bien plus petit que prévu, voir l'encadré ci-dessous : poser un `rua=` sur le DMARC, resserrer le SPF du domaine apex, poser un `Reply-To` sur les types qui n'en ont pas, et **revérifier un en-tête réel** une fois Mailtrap en mode live | Sans `rua=`, le jour où l'authentification casse, **personne ne l'apprend** : les reçus partent en indésirable et aucun rapport ne remonte | rien | **L1** | 0,5 | 0 | **SPF, DKIM et DMARC déjà en place** | toi (registrar) |
| `QUA‑1` | qualité | Dette d'outillage en un lot : fixture de `ControleBrochureTest` déplacée **dans** `backend/src/test/resources`, suppression de la configuration morte `app.aws.*`, correction de `_plafondJsKo`, correction du « onze statuts » de `parcoursDossier.ts` (il y en a douze), scission du prompt `M3`, correction de l'affirmation fausse de `PIL` | Chacun est un piège pour la prochaine session, et aucun ne coûte cher | rien | **L1** | 1 | 0 | à faire | — |
| `QUA‑4` | catalogue | Référencement : `sitemap.xml`, `robots.txt`, titres et métadonnées par page | `frontend/public/` ne contient que le favicon, les icônes et la marque. Aucun des deux fichiers n'existe | `CAT` (pour les 301) | **L2** | 1,5 | 0 | à faire | — |
| `QUA‑2` | qualité | Cause racine de l'instabilité des tests frontend, ou décision écrite de vivre avec | Contournement en place (`testTimeout: 20_000`), cause inconnue. Un test instable est une dette | `EXP‑3` | **Ap** | 1 | 0 | contourné | — |
| `QUA‑3` | qualité | Accessibilité : parcours clavier complet et contrastes sur les écrans de vente | Le garde-fou de charte mesure les contrastes ; il ne mesure **pas** le clavier. **Non vérifié** | rien | **Ap** | 2 *(estimation)* | 0 | à faire | — |
| `QUA‑5` | catalogue | Bandeau promotionnel mobile (`PromoHeroSlider`) | Signalé dans ton inventaire ; **non vérifié** faute d'une description du défaut | rien | **Ap** | *non estimé* | — | **à préciser** | toi |

> **`EXP‑4` — ce que le DNS dit réellement, relevé le 6 novembre 2026**
>
> Ton inventaire annonçait « sans SPF, DKIM et DMARC, les reçus finissent dans les spams ».
> **Les trois sont en place**, et j'ai failli conclure le contraire : interroger le domaine
> apex ne montre rien, parce que Mailtrap utilise un **sous-domaine de retour** et des
> **sélecteurs** qui lui sont propres. C'est exactement l'erreur que j'avais déjà commise le
> 11 octobre, et que la vérification sur un en-tête réel du 12 octobre avait corrigée
> (`spf=pass`, `dkim=pass`, `dmarc=pass`).
>
> | Enregistrement | Valeur relevée | Verdict |
> |---|---|---|
> | `mt47.optimisante.com` | `CNAME → smtp.mailtrap.live`, dont le SPF autorise les plages d'envoi de Mailtrap | **en place** — chemin de retour propre, c'est lui qui aligne le SPF |
> | `rwmt1._domainkey` et `rwmt2._domainkey` | `CNAME → rwmtN.dkim.smtp.mailtrap.live`, clés RSA publiées | **en place**, deux sélecteurs — la rotation est possible sans coupure |
> | `_dmarc.optimisante.com` | `v=DMARC1; p=quarantine;` | **en place**, mais **sans `rua=`** |
> | `optimisante.com` (apex) | `v=spf1 ip4:91.234.195.113 ip4:91.234.195.114 include:premiumsmtp.dnshostservices.com +a +mx ~all` | à resserrer : `+a +mx` autorise tout hôte du domaine |
>
> **Le domaine pointe sur `mailtrap.live`, l'infrastructure d'envoi de production** — pas sur
> le bac à sable. Le domaine est donc déjà prêt pour le mode live ; ce qui reste à vérifier est
> que le **backend** utilise bien les identifiants live, et cela appartient à `ST‑7`.
>
> **Ce que tu as à faire chez ton registrar : une seule ligne.** Ajouter l'adresse de rapport au
> DMARC, par exemple `v=DMARC1; p=quarantine; rua=mailto:infos@optimisante.com; fo=1;`. Sans
> elle, rien ne nous préviendra le jour où une signature cassera. Le resserrement du SPF apex
> (retrait de `+a +mx`) est un second geste, utile mais moins pressant.
>
> **Ce qui reste côté code**, et qui vient du point 7 du backlog : les messages partent de
> `no-reply@optimisante.com` et seuls les accusés « Exercer en France » portent un `Reply-To`.
> Une réponse à un reçu de paiement, à un envoi d'identifiants ou à une convocation **part dans
> le vide**. Les boîtes réelles du domaine sont `infos@`, `comptabilite@`, `Tbrittany@` et
> `Tandoche@` : toute autre adresse écrite dans un message n'existe pas.

### 3.9 Totaux

| | Jours de développement | dont acquis |
|---|---|---|
| **`L0` — tout de suite** | **3** | 0 |
| `LEG‑0`, `SEC‑3`, `SEC‑1` | 3 | |
| **`L1` — Formations et Exercer en France** | **47** | 1 (`DP‑4` écrit) |
| dont stockage privé (`DP‑4` + `SP‑1` à `SP‑7`) | 12,5 | 1 |
| dont brochures (`BR‑2` à `BR‑5`) | 9,5 | 0 |
| dont paiements (`ST‑0`, `ST‑3`, `ST‑4a`, `ST‑6`, `ST‑7`) | 6,5 | 0 |
| dont sécurité (`SEC‑2`, `SEC‑4`, `SEC‑7`) | 4 | 0 |
| dont dette sécurité triée (`SEC‑6a`, `SEC‑6b`, `SEC‑6c`) | 2 | 0 |
| dont exploitation (`EXP‑1` à `EXP‑4`) | 4 | 0 |
| dont formations (`FOR‑1` à `FOR‑4`) | 3 | 0 |
| dont légal (`LEG‑1` à `LEG‑5`) | 3 | 0 |
| dont administration et finances (`CDE‑2`, `FIN‑1`) | 1,5 | 0 |
| dont qualité (`QUA‑1`) | 1 | 0 |
| **`L2` — Boutique** | **25,5 à 27,5** | 0 |
| dont catalogue (`M0`, `CAT`, `PIL`, `IMP`) | 9 à 11 | |
| dont paiements et facturation (`ST‑1`, `ST‑2`, `ST‑4b`, `ST‑5`) | 11,5 | |
| dont commande (`CDE‑1`, `STK‑3`, `L`) | 3,5 | |
| dont référencement (`QUA‑4`) | 1,5 | |
| **`Ap` — après les deux lancements** | **29,5 à 31,5** | — |
| dont maquettes (`M1`, `M2`, `M3a`, `M3b`, `M4`, `M2‑bis`) | 18 | |
| dont second entrepôt (`STK‑2`) | 5 à 7 | |
| dont qualité (`QUA‑2`, `QUA‑3`) | 3 | |
| dont dette sécurité restante (`SEC‑5`, `SEC‑6d`, `SEC‑6e`) | 3,5 | |
| **Total des trois** | **105 à 109** | 1 |
| **Reste non estimé** | `BR‑6` et `QUA‑5` seulement | — |

**Ce qui a bougé depuis le 5 novembre**, et pourquoi le total monte de 88,5‑92,5 à 105‑109 :
`SEC‑2` reclassé (+2,5), les huit points du backlog triés et chiffrés au lieu de « non estimé »
(+5), `SEC‑7` ajouté (+1), `LEG‑0` ajouté (+0,5), `LEG‑5` ajouté (+0,5), `EXP‑4` ajouté (+0,5),
`ST‑0` étendu (+0,5), `ST‑4` scindé en `ST‑4a` et `ST‑4b` (+2), `QUA‑3` et `QUA‑4` chiffrés.
**Aucun lot n'a été retiré.** Le total n'augmente pas parce que le travail grandit : il augmente
parce qu'il était sous-estimé, et que « non estimé » valait zéro dans la somme.

---

## 4. Décisions en attente

Dans l'ordre de ce qu'elles bloquent.

| # | Décision | Bloque | Ma recommandation |
|---|---|---|---|
| **1** | **Les deux lancements du § 1** : leur périmètre, et les phrases qui les conditionnent | tout le calendrier | Valider le découpage. Le gain est net : le lancement 1 encaisse **deux mois plus tôt** qu'un lancement unique, parce qu'il ne dépend ni de l'arbre du catalogue, ni du cycle de vie des commandes, ni de la TVA à l'export. |
| **2** | **Les comptes et clés Scaleway**, et les sept variables sur Render | `SP‑1`, donc `SP‑2` à `SP‑7`, `ST‑4a`, `ST‑4b`, `BR‑2`, `M2‑bis` | **Deux projets séparés** (production, développement), un bucket et une application chacun : la portée d'une politique IAM Scaleway est le **projet**, jamais le bucket. Tu me colleras la liste des jeux de permissions affichée dans la console ; **je ne te demanderai jamais la valeur d'une clé.** C'est la décision qui tient le lancement 1. |
| **3** | **Le diagnostic de l'acompte** : la requête SQL sur le dossier `0BFDD667`, et la page *Events* de Stripe | `ST‑6`, donc `ST‑7`, donc **tout** l'encaissement du lancement 1 | Deux copies d'écran suffisent. Elles tranchent entre « la session ne se termine pas » et « le webhook échoue à l'arrivée », et peuvent faire tomber `ST‑6` de 2 jours à 0,5. |
| **4** | **L'activation du compte Stripe en mode live** : identité de la SAS, IBAN, informations d'entreprise | `ST‑7`, donc le lancement 1 | **À engager cette semaine.** Stripe vérifie l'identité du bénéficiaire et cela prend plusieurs jours ouvrés, parfois avec un aller-retour de pièces. C'est une attente externe de plus, et elle est invisible tant qu'on ne l'a pas commencée. |
| **5** | **Le rendez-vous comptable** — quatre questions, formulées au § 4 bis | `SP‑7`, `ST‑4a`, `ST‑4b`, `ST‑5`, `LEG‑5` | Un seul rendez-vous couvre les quatre. **Les deux premières conditionnent le lancement 1** et sont à poser en priorité : je n'ai pas tranché à sa place, et je ne le ferai pas. |
| **6** | **L'arbre cible** à trois niveaux, et le rattachement des 211 catégories | `CAT`, donc `IMP`, `M3a`, `M3b`, `M4`, `L`, `QUA‑4` — soit **tout le lancement 2** | Je produis le fichier d'arbitrage ; tu tranches ligne par ligne. Rien ne part en base avant. Plus bloquant qu'il n'y paraît, mais **plus rien du lancement 1 ne l'attend** : son arbitrage se déroule entièrement pendant les travaux sur les formations. |
| **6 bis** | **L'adhésion à un médiateur de la consommation** | le lancement 1, légalement | À engager cette semaine, avec la décision 4 : c'est une démarche administrative dont le délai ne dépend pas de nous, et les CGV y renvoient déjà. |
| **7** | **L'envoi de texte de brochure au service d'IA** | `BR‑6` seulement | **Non.** Et si un jour oui, seulement du texte **déjà purgé** par `BR‑3`, jamais le PDF d'origine. Ne bloque rien aujourd'hui. |
| **8** | **La commande minimum**, et les délais des zones non couvertes | `STK‑3`, `L` | Deux bornes en jours ouvrés par zone, jamais une valeur unique. Une zone non desservie refuse déjà la commande : ne pas inventer un délai pour elle. |
| **9** | **Le second entrepôt : dans un lancement, ou après ?** | `STK‑2` | **Après les deux.** Rien n'existe — ni site de stock, ni zone sur le stock : c'est un chantier de 5 à 7 jours, pas un réglage. Les devises `XAF` / `XOF` et la correspondance pays → devise existent déjà (`PaysDevise`), et c'est tout ce qui est acquis. |
| **10** | **Le numéro WhatsApp** | `M2` (bouton masqué sans lui) | Un numéro professionnel dédié, en paramètre. `M2` se livre sans, sans dette. |
| **11** | **La marge fournisseur** | `PIL`, `IMP` | Par catégorie de l'arbre cible, pas par fournisseur : `Category.marginRate` existe déjà et prime. |
| **12** | **Les décisions 5 à 10 de `plan_integration_catalogue.md` § 8** (57 catégories vides, 10 groupes de doublons, 111 « ARRET », 43 « Port en sus », 4 398 lignes sans rayon, garantie) | `CAT`, `IMP`, `M2‑bis` | Inchangées, et toujours valables mot pour mot. À trancher avec la décision 6, au même moment. |

### 4 bis. Les questions au comptable, formulées

**Je ne tranche aucune des quatre, et je ne proposerai pas de réponse.** Ce sont des questions de
droit fiscal et de droit de la formation professionnelle : une recommandation de ma part n'aurait
pas plus de valeur qu'une supposition, et elle risquerait d'être retenue comme un avis. Les
articles cités sont ceux qui me paraissent concernés, **à confirmer par lui** — c'est lui qui dit
lesquels s'appliquent.

Ce que je peux affirmer, en revanche, c'est **ce que le code fait aujourd'hui**, et c'est la
première colonne.

---

**Question 1 — la pièce remise au payeur d'une formation.** *Bloque `ST‑4a`, donc le lancement 1.*

> **Ce que le code fait aujourd'hui.** À chaque encaissement, la plateforme émet un **reçu** de
> série `REC-`, à numérotation sans trou, en PDF joint à l'e-mail (`PaymentReceiptIssuer`). Ce
> document a été conçu comme un **justificatif de règlement** : il porte le montant, la date, le
> motif et l'identité de l'émetteur, mais il n'a jamais été conçu comme une pièce comptable
> opposable et il **ne porte pas** les mentions d'une facture.
>
> **La question.** Pour l'encaissement de frais de formation auprès d'un **particulier qui paie
> pour lui-même** (et non d'un employeur ou d'un OPCO), quelle pièce devons-nous remettre :
> le reçu `REC-` tel quel suffit-il, faut-il une **note** au sens de l'arrêté du 3 octobre 1983
> — qui, sauf erreur, impose une note pour les prestations de services au-delà de 25 € —, ou
> faut-il une **facture complète** au sens de l'article 289 du CGI ?
>
> **Et, selon la réponse** : quelles mentions exactement, et faut-il une **série de numérotation
> distincte** de celle des factures produits de la boutique ?

---

**Question 2 — la TVA sur la formation, et notre qualité.** *Bloque `ST‑4a` et `LEG‑5`, donc le lancement 1.*

> **Ce que le code fait aujourd'hui.** Rien de spécifique : les frais de formation sont encaissés
> **sans mention de TVA** ni ventilation. L'interrupteur `TVA_ACTIVE` n'ouvre que la ventilation
> sur les documents de la boutique. L'objet social déclaré dans `legal.ts` est
> « **intermédiaire à valeur ajoutée** : ingénierie de formation médicale continue, négoce… ».
>
> **La question, en deux temps, et le second dépend du premier.**
>
> **a)** Dans l'opération telle qu'elle est montée — un CHU dispense la formation, Optimi Santé
> recrute, accompagne, encaisse et reverse au partenaire —, sommes-nous **organisme de
> formation** au sens du code du travail, ou **intermédiaire** qui facture une prestation
> d'accompagnement ? De cette qualification dépend tout le reste, et je ne sais pas y répondre.
>
> **b)** Selon la réponse : l'**exonération de l'article 261‑4‑4° du CGI** s'applique-t-elle, et
> à quelle part — au prix de la formation, à notre commission, aux deux, à aucune ? Si elle
> s'applique, elle suppose, sauf erreur, une **attestation** et un **numéro de déclaration
> d'activité** : les avons-nous, et sinon, les demandons-nous ou facturons-nous à **20 %** ?
>
> **c)** Même question pour l'**accompagnement « Exercer en France »** et pour les **options de
> service**, qui ne sont pas de la formation et suivent peut-être un régime différent du reste.

---

**Question 3 — un point que je soulève sans pouvoir le trancher, et qui peut coûter cher.**
*Peut affecter `ST‑6`, les CGV et l'échéancier déjà en production.*

> **Ce que le code fait aujourd'hui.** L'échéancier **60 / 40** est appliqué depuis la `V45`
> (`TuitionPaymentService`) : 60 % à l'admission, le solde à la délivrance du visa. Les CGV le
> décrivent. C'est en place et cela fonctionne.
>
> **Ce qui m'inquiète.** Lorsqu'un **particulier** finance lui-même une formation
> professionnelle, les articles L6353‑3 et suivants du code du travail imposent, sauf erreur, un
> **contrat de formation professionnelle** — distinct de nos CGV — assorti d'un **délai de
> rétractation** et d'une **limite à ce qui peut être encaissé avant l'expiration de ce délai**.
>
> **La question.** Ce régime s'applique-t-il à nous ? Si oui, l'encaissement de 60 % à
> l'admission est-il conforme, et faut-il un contrat de formation professionnelle en plus des
> CGV ?
>
> **Pourquoi je le pose malgré le risque de me tromper.** Si la réponse est oui, elle touche le
> modèle de trésorerie, pas seulement un gabarit PDF — et il vaut infiniment mieux l'apprendre
> avant le premier encaissement réel qu'après le centième. Si la réponse est non, la question
> aura coûté trois minutes de rendez-vous.

---

**Question 4 — conservation et taux, pour les deux lancements.** *Bloque `SP‑7` (L1), `ST‑5` (L2).*

> **a) Les durées de conservation**, par type de document : le tableau du § A.6 de
> `analyse_stockage_prive_et_feuille_de_route.md` propose une durée pour chacun, en distinguant
> l'**obligation de conservation comptable** de la **minimisation RGPD**. Ce sont deux durées
> différentes, et notre lecture est que c'est la plus longue qui s'applique **au document**, pas
> aux données qui l'entourent. À confirmer, ligne par ligne.
>
> **b) Les taux de TVA rayon par rayon** pour la boutique : `vatRate` existe par catégorie et par
> produit, avec héritage. Quels rayons relèvent du taux réduit applicable à certains dispositifs
> médicaux, et lesquels restent à 20 % ? C'est la seule des quatre questions qui n'a aucun effet
> sur le lancement 1.

---

## 5. Tes tâches, hors code

Dans l'ordre où elles débloquent le travail.

**Cette semaine — quatre démarches dont le délai ne nous appartient pas.** Ce sont les seules qui
peuvent devenir le dernier verrou du lancement 1 sans qu'on les ait vues venir, parce qu'elles
attendent la réponse de quelqu'un d'autre.

1. **Stripe — activation du compte en mode live** : identité de la SAS, IBAN du compte
   bénéficiaire, informations d'entreprise. **Plusieurs jours ouvrés**, parfois un aller-retour
   de pièces. *(débloque `ST‑7`, donc tout l'encaissement)*
2. **Scaleway** : deux projets, deux buckets, deux applications, les clés, et les sept variables
   sur Render. *(débloque `SP‑1`, et avec lui douze lots)*
3. **Le médiateur de la consommation** : adhésion. *(condition légale du lancement 1)*
4. **Le rendez-vous comptable** : les quatre questions du § 4 bis, les deux premières en
   priorité. *(débloque `ST‑4a`, `LEG‑5`, `SP‑7`, et `ST‑5` au lancement 2)*

**Chez ton registrar — une ligne de DNS**

5. Ajouter l'adresse de rapport au DMARC : `v=DMARC1; p=quarantine; rua=mailto:infos@optimisante.com; fo=1;`.
   **SPF, DKIM et DMARC sont déjà en place et vérifiés** — c'est le rapport qui manque, donc la
   capacité d'apprendre qu'une signature a cassé. *(`EXP‑4`)*

**Deux copies d'écran, cinq minutes, et elles débloquent le lot le plus critique**

6. La **requête SQL** sur le dossier `0BFDD667` et la page **Events** de Stripe. *(débloque `ST‑6`)*
7. La **région** de ton compte Cloudinary, lue dans la console. *(confirme le § A.1)*

**Envois et vérifications**

8. Le **courriel au support Cloudinary** pour la suppression de leurs sauvegardes — rédigé, § A.9. *(étape de `SP‑5`)*
9. Chez **Render** : la fréquence réelle des sauvegardes de la base. *(`EXP‑1`)*
10. Chez **Stripe**, après l'activation : le nouveau point d'entrée de webhook **live** et son secret. *(`ST‑7`)*

**Contenus et validations de texte**

11. Le **texte de `LEG‑0`** : je te le soumets, tu valides, je l'écris. *(`LEG‑0`, maintenant)*
12. Les **conditions partenaires** et la clause de non-contournement : les règles commerciales, je rédige. *(`LEG‑3`)*
13. La **définition de la clôture d'un dossier** par parcours (formation courte, Exercer en France), et ce qui se passe à une réouverture. Proposée au § A.6. *(`SP‑7`)*
14. La **relecture du positionnement** : aucun « officiel », aucun « certifié ». *(`FOR‑3`)*
15. La validation de la **politique de confidentialité** réécrite. *(`LEG‑1`)*

**Lancement 2, à ton rythme pendant le lancement 1**

16. **L'arbitrage de l'arbre** dès que je te livre le fichier. *(débloque `CAT` et six lots derrière)*
17. La **marge fournisseur** par catégorie. *(`PIL`, `IMP`)*
18. Le **numéro WhatsApp**, s'il existe. *(`M2`)*

**Accords ponctuels, chacun à son moment**

19. Déploiement de `DP‑4`. Sauvegarde avant `SP‑4`. Accord avant `ST‑0` — qui touche désormais
    aussi des dossiers, des paiements et des comptes. Accord avant `ST‑7`. Et chaque fusion dans
    `main`, en `--no-ff`.

---

## 6. Chemin critique et calendrier

### 6.1 L'ordre

```
  ┌─ L0 — MAINTENANT ───────────────────────────────────────────── 3 j ──────┐
  │  LEG-0 (texte à valider) → SEC-3 → SEC-1                                │
  │  + DP-4 : déploiement, dès ta fenêtre                                   │
  └──────────────────────────────────────────────────────────────────────────┘
        │
        ▼
  ══ L1 — FORMATIONS ET EXERCER EN FRANCE ══════════════════════ 47 j ══════

   chaîne critique    SP-1 → SP-3 → SP-4 → SP-5 → SEC-6c → SP-7
   (le stockage)       │                      └→ LEG-1
                       │
   en parallèle dès    └─ BR-2 → BR-3 → BR-4 → BR-5          (9,5 j)
   SP-1 vert              SP-2 · SP-6 (avec SEC-6a, SEC-6b)

   chaîne de l'argent  ST-0 → ST-6 → ST-3 → ST-7 → FIN-1
                             ↑ attend ton diagnostic
                             ST-4a ↑ attend le comptable

   sans dépendance,    SEC-2 · SEC-4 · SEC-7 · QUA-1 · EXP-1 · EXP-2 · EXP-3
   à glisser dans      EXP-4 · FOR-1 · FOR-2 · FOR-3 · CDE-2 · LEG-3 · LEG-4
   toute attente       (≈ 14 j — ils ne doivent JAMAIS être la raison d'attendre)

   en dernier          FOR-4 (le parcours joué en entier) · LEG-5
        │
        ▼
  ══ LANCEMENT 1 ═══ on encaisse ═══════════════════════════════════════════
        │
        │  (ton arbitrage de l'arbre s'est déroulé pendant tout L1)
        ▼
  ══ L2 — BOUTIQUE ═════════════════════════════════════ 25,5 à 27,5 j ═════

   M0 → CAT → PIL → IMP          ST-1 → ST-2 → ST-4b → ST-5
                     │                  └→ CDE-1
   CAT → L → STK-3   └→ QUA-4
        │
        ▼
  ══ LANCEMENT 2 ═══════════════════════════════════════════════════════════
        │
        ▼
  Ap : M1 → M2 → M2-bis · M3a → M3b → M4 · SEC-5 · SEC-6d · SEC-6e
       STK-2 · QUA-2 · QUA-3                      29,5 à 31,5 j
```

### 6.2 Les deux chemins critiques du lancement 1

**Le stockage** : `SP‑1` → `SP‑3` → `SP‑4` → `SP‑5` → `SEC‑6c` → `SP‑7` = **10,5 jours
enchaînés**, chacun attendant le précédent. Il ne démarre pas avant que **les comptes Scaleway
existent**.

**L'argent** : `ST‑0` → `ST‑6` → `ST‑3` → `ST‑7` → `FIN‑1` = **5 jours enchaînés**, plus
`ST‑4a` en dérivation. Il ne démarre pas avant **ton diagnostic** et **l'activation du compte
live**.

Les deux chaînes sont **indépendantes** : elles peuvent avancer en même temps. Le lancement 1
n'est donc pas tenu par 47 jours en file, mais par la plus longue des deux chaînes **plus** ce
qui ne se parallélise pas. C'est ce que calcule le § 6.4.

### 6.3 Ce qui ne doit pas gâcher le parallélisme

- **L'arbitrage de l'arbre** part **maintenant**, bien qu'il serve le lancement 2. C'est la plus
  longue attente du projet, et le découpage en deux lancements lui offre enfin un endroit où
  tenir : tout `L1`.
- **`SP‑4`** (la copie) tourne en tâche de fond : `BR‑2` démarre dès `SP‑1` vert.
- **Les quatorze lots sans dépendance** (≈ 14 jours) comblent toutes les attentes. Tant qu'il en
  reste un, aucune attente ne devrait coûter un jour.

### 6.4 Les deux dates

**Le modèle est explicite, pour que tu puisses le contester.** Trois facteurs, et le deuxième
est celui que tu as demandé d'ajouter.

| Facteur | Ce que j'applique | Pourquoi |
|---|---|---|
| **Marge d'imprévu** | **+ 25 %** | Ton arbitrage. Les trois derniers mois l'ont justifié : la fuite du jeton de document n'était dans aucun plan et a coûté une journée. |
| **Attente de ta relecture** | **+ 0,5 jour ouvré par lot, mais seulement sur les chaînes critiques** | Un lot hors chaîne peut attendre ta relecture pendant que j'avance sur un autre : l'attente est alors gratuite. Sur une chaîne, elle ne l'est pas. `L1` compte **10 relectures bloquantes**, `L2` en compte **6**. |
| **Fêtes de fin d'année** | **2 semaines creuses**, du 21 décembre au 1er janvier | C'est du calendrier, pas de l'estimation. Les ignorer rendrait la date fausse d'un demi-mois. |

| | Développement | +25 % | + relecture bloquante | **Jours ouvrés** |
|---|---|---|---|---|
| `L0` | 3 | 3,75 | + 1,5 | **≈ 5** |
| `L1` | 47 | 58,75 | + 5 | **≈ 64** |
| `L2` | 25,5 à 27,5 | 32 à 34,5 | + 3 | **≈ 35 à 38** |

**Si tu engages les quatre démarches de la décision 1 à 5 cette semaine** — d'ici au
**13 novembre 2026** :

| Jalon | Date | Détail |
|---|---|---|
| `L0` terminé | **~13 novembre 2026** | 5 jours ouvrés, à partir de maintenant |
| **Lancement 1** | **début mars 2027** *(semaine du 1ᵉʳ mars)* | 64 jours ouvrés depuis le 16 novembre, **plus les deux semaines des fêtes** |
| **Lancement 2** | **fin avril 2027** *(semaine du 26 avril)* | 35 à 38 jours ouvrés après le lancement 1 |

**Trois réserves, et je préfère les écrire que les sous-entendre.**

1. Le calcul suppose **cinq jours de développement effectif par semaine**, tenus sans
   interruption. Le dépôt ne me permet pas de vérifier que c'est le rythme réel :
   **non vérifié**. Si la cadence réelle est de quatre jours, le lancement 1 glisse à
   **fin mars**.
2. Les **quatre démarches externes** (Stripe live, Scaleway, médiateur, comptable) ont un délai
   que nous ne maîtrisons pas. Chacune peut devenir le dernier verrou, et aucune n'est visible
   tant qu'elle n'est pas commencée. C'est la raison pour laquelle elles sont en tête du § 5 et
   non à leur place chronologique.
3. **Je ne compte pas `ST‑6` à 0,5 jour**, bien que le diagnostic puisse l'y ramener. Si c'est le
   cas, le lancement 1 gagne une semaine — mais ce gain n'est pas dans les dates ci-dessus, et il
   n'y sera pas avant que les deux copies d'écran existent.

---

## 7. Critères de lancement

Deux listes, une par lancement. Chaque case est vérifiable **par quelqu'un d'autre que son
auteur** — c'est la seule propriété qui rend une liste de contrôle utile.

### 7.0 Avant tout, dès `L0`

- [ ] `PrivacyPolicyPage.tsx` ne contient plus la phrase « les données sont hébergées dans l'Union européenne », et sa section 5 n'est plus vide.
- [ ] `/api/v1/partnership/**` refuse un dépôt anonyme, refuse un fichier dont la signature ne correspond pas, et plafonne la taille à la route.
- [ ] Les sept routes `permitAll` portent un plafond de débit. *Vérification : `/api/v1/ai/chat` appelé en boucle rend un `429` avant la dixième fois.*

### 7.1 Lancement 1 — Formations et Exercer en France

**Données personnelles**

- [ ] Aucune réponse de l'API ne contient de clé de stockage. *Vérification : onglet Réseau sur un dépôt et un téléchargement, aucun chemin `optimisante/` ni `dossiers-candidats`, et la charge d'un jeton décodée ne rend que du chiffre.*
- [ ] Les neuf dossiers privés sont servis depuis le bucket Scaleway `fr-par`, et le repli Cloudinary est retiré.
- [ ] Un objet supprimé par la purge ne se relit **par aucune version**. *Vérification : `ListObjectVersions` revient vide.*
- [ ] Le support Cloudinary a confirmé **par écrit** la suppression de ses sauvegardes.
- [ ] **Toutes** les routes de dépôt refusent un fichier dont la signature ne correspond pas à son extension — les dix-sept, pas la première.
- [ ] Le registre d'incidents est sous git, et l'incident n° 1 porte sa date de fin de migration.

**Sécurité**

- [ ] Un compte d'administration exige un second facteur.
- [ ] L'en-tête `Content-Security-Policy` est servi, et un test échoue s'il disparaît.
- [ ] Le tenant ne vient **plus** du client : ni du corps de la requête, ni de l'en-tête `X-Tenant-Id`.
- [ ] Le rôle `ADMIN` ne figure plus dans aucune annotation d'autorisation.

**Argent**

- [ ] `ST‑0` a tourné sur **les cinq catégories** : ventes, dossiers, paiements, prospects, comptes. Le compte de lignes touchées est celui qui était attendu, et `0BFDD667` n'existe plus.
- [ ] Un acompte d'inscription encaissé fait avancer le dossier **sans** geste manuel.
- [ ] Une confirmation manuelle par l'administration est **refusée** si Stripe ne confirme pas le `PaymentIntent`. *Vérification : tenter la confirmation d'un dossier non payé.*
- [ ] Le payeur reçoit **la pièce que le comptable a désignée**, avec les mentions qu'il a indiquées, et le régime de TVA qu'il a tranché.
- [ ] Un remboursement déclenché depuis le tableau de bord Stripe fait sortir le paiement de formation des agrégats.
- [ ] Un paiement réel de faible montant est passé en **live**, puis remboursé.
- [ ] Un reversement partenaire a été exécuté de bout en bout, et le CHU l'a reçu.

**Parcours et partenaires**

- [ ] Le parcours candidat a été joué **en entier**, les huit étapes, sur un dossier de test en production — puis ce dossier a été purgé.
- [ ] Aucune fiche formation n'annonce un visa que sa durée n'ouvre pas.
- [ ] Une affiche en portrait s'affiche entière dans la bannière.
- [ ] Un partenaire dépose une brochure, la relit, l'administration la valide, et la version publiée ne porte **ni son prix, ni son adresse précise**.
- [ ] L'écran des prospects existe et affiche les captures de `prospect_leads`.

**Légal**

- [ ] La politique de confidentialité ne contient **aucune phrase fausse**, ses sous-traitants sont **nommés**, et sa section « coffre-fort » est écrite.
- [ ] Un médiateur de la consommation est nommé, et les CGV pointent vers lui.
- [ ] Les conditions partenaires sont publiées, clause de non-contournement comprise.
- [ ] Les CGV décrivent la pièce remise et le régime de TVA des formations.
- [ ] Le registre des traitements existe, et quelqu'un est **nommé** pour le tenir.
- [ ] Aucun texte public ne dit « officiel » ni « certifié », et aucun lien ne sort vers une autorité.

**Exploitation**

- [ ] Une sauvegarde a été **restaurée** sur une base jetable, et la date de l'essai est écrite.
- [ ] Un échec de webhook déclenche une alerte qui **arrive à quelqu'un**. *Vérification : la provoquer.*
- [ ] Le build, la suite complète et le garde-fou de charte tournent à chaque `push`, **et le nombre de tests ignorés est rapporté** — sans quoi une suite verte peut n'avoir rien exécuté.
- [ ] Le DMARC porte un `rua=`, et un premier rapport a été reçu et lu.
- [ ] Un reçu, un envoi d'identifiants et une convocation portent un `Reply-To` vers une boîte **qui existe**.
- [ ] Les empreintes d'arbre des deux miroirs égalent celles de `main`.

### 7.2 Lancement 2 — Boutique

- [ ] L'arbre à trois niveaux est en place, et une page famille rend **les produits de ses descendants**.
- [ ] Les 211 adresses indexées répondent `301` vers leur nouvelle adresse. *Vérification : en prendre dix au hasard.*
- [ ] Les 7 344 lignes sont importées ; les « ARRET » ne sont pas commandables ; les « Port en sus » sont sur devis.
- [ ] Une commande payée passe en `PAID`, puis `SHIPPED`, puis `DELIVERED`, et le client voit chacun de ces états.
- [ ] Une facture `FA-` est émise, numérotée **sans trou**, porte les mentions obligatoires, et son PDF est en stockage privé.
- [ ] Un avoir `AV-` est adossé à sa facture.
- [ ] Une commande hors France porte la mention d'exonération à l'export, et son justificatif de sortie est rangé.
- [ ] Une commande annulée ou remboursée **remet son stock**, et le journal dit pourquoi.
- [ ] Un client expédié reçoit un e-mail qui le lui dit.
- [ ] Aucun produit en `PROPRE` n'est vendable au-delà de ce qui a été compté ; aucun produit en `NON_SUIVI` n'est bloqué par un chiffre que personne n'a compté.
- [ ] `sitemap.xml` et `robots.txt` existent et sont servis.

---

## 8. Après les deux lancements, par ordre de priorité

**Ce qui n'est plus dans cette liste, et c'est l'effet de tes arbitrages :** `SEC‑2` (2FA),
`SEC‑6a` (la faille du formulaire médecins), `SEC‑6b`, `SEC‑6c` et la CSP sont passés en
lancement 1. Le rang 1 de ma version du 5 novembre — « reprendre la dette sécurité en premier
après la mise en ligne » — n'existe plus : **elle est traitée avant.**

| Rang | Lots | Pourquoi ce rang |
|---|---|---|
| 1 | **`SEC‑5`** jeton hors `localStorage` (1,5 j) | La CSP de `SEC‑7` **atténue** le vol de jeton, elle ne le supprime pas. C'est ce qui reste du risque le mieux identifié du projet, une fois le trafic arrivé. |
| 2 | **`M1` → `M2` → `M2‑bis`**, puis **`M3a` → `M3b` → `M4`** — les dix écrans (18 j, 142 Ko) | Refaire des écrans qui **fonctionnent** : c'est de la conversion, pas de la capacité. À mesurer sur du trafic réel. `M1` et `M2` ne dépendent ni du stockage ni de Stripe : ils peuvent démarrer dès qu'il existe du temps mort, même pendant `L2`. |
| 3 | **`SEC‑6d`** Jackson, **`SEC‑6e`** verrou des tâches planifiées (2 j) | Aucun des deux ne saigne aujourd'hui, et chacun a un déclencheur net : `SEC‑6e` devient urgent **le jour où le backend tourne sur deux instances** — c'est-à-dire le jour où le trafic justifie de monter en charge, donc au pire moment pour le découvrir. |
| 4 | **`STK‑2`** second entrepôt (5 à 7 j) | Un entrepôt au Congo n'a de sens qu'avec un volume qui le remplit. Le mesurer d'abord. |
| 5 | **`QUA‑2`** cause de l'instabilité des tests, **`QUA‑3`** accessibilité clavier (3 j) | Deux dettes réelles, aucune ne saigne. `QUA‑2` devient urgent le jour où l'intégration continue de `EXP‑3` rougit sans raison : c'est là qu'on cesse de lire les résultats, et une intégration continue qu'on ne lit plus est pire que pas d'intégration continue. |
| 6 | **`BR‑6`** structuration des brochures par l'IA | Dépend d'une décision non prise, ne bloque rien, et ne sert que si les partenaires déposent beaucoup. |
| 7 | **`QUA‑5`** bandeau promotionnel mobile | **Non estimé** : j'attends que tu décrives le défaut. |

---

## 9. Journal des écarts entre ton inventaire et ce que dit le code

| # | Ce que ton inventaire dit | Ce que dit le code |
|---|---|---|
| 1 | « Webhook `charge.refunded` » parmi les manques | **Il existe et il est complet** (`PaymentWebhookResource:54` → `StripeRefundHandler`). Ce qui manque est le **test**, et il tient en une demi-journée. |
| 2 | « Cycle de vie des commandes (`PAID`, `DELIVERED`, dates) » | Exact pour `OrderStatus`, mais **`PaymentStatus` porte déjà `PAID` et `REFUNDED`**. L'axe paiement existe ; c'est l'axe **logistique** qui manque, et le **lien par des règles de transition** entre les deux. `ST‑1` est donc un peu moins vaste qu'il n'y paraît. |
| 3 | « E-mails transactionnels manquants : confirmation de commande, expédition, facture » | **La confirmation existe**, portée par le reçu en pièce jointe (`PaymentReceiptIssuer`, et le commentaire de `NotificationDispatcher.onOrderPaid` explique pourquoi l'e-mail a été retiré de là). La **facture** n'existe pas du tout, donc son e-mail non plus. **L'expédition est le seul manque réel**, et il est total : zéro événement sur vingt-quatre. |
| 4 | « Fixture `ControleBrochureTest` absente du miroir » | **Exact, et la cause est plus simple que prévu** : le test lit `Path.of("..", "docs", …)` (ligne 29), hors du module. Le PDF **est** suivi par git dans le monorepo. Le correctif n'est pas de copier le fichier dans le miroir, c'est de le déplacer dans `backend/src/test/resources`. |
| 5 | « Consentement aux cookies » parmi les travaux | **Il n'y a rien à faire**, et c'est vérifié : aucune mesure d'audience tierce dans le dépôt, et le stockage local ne porte que le jeton, le panier et le choix de devise. La politique (§ 10) décrit exactement cela. Ce travail **réapparaîtra** le jour où une mesure d'audience sera ajoutée — pas avant. |
| 6 | « Limite de débit sur les routes publiques (lead-capture, future route du journal client) » | **Le périmètre est bien plus large que ces deux-là.** Sept routes sont en `permitAll`, et les deux que tu nommes ne sont pas les plus exposées : `/api/v1/ai/chat` est une dépense d'IA ouverte aux anonymes, et `/api/v1/partnership/**` est un **dépôt de fichier anonyme et non contrôlé**, 55 Mo par appel. C'est la trouvaille la plus sérieuse de cet audit. |
| 7 | « Registre d'incidents de sécurité : où il vit » | **Il existe déjà**, bien rédigé, un incident. Mais il est **non suivi par git**, sans extension, et n'existe donc que sur ce poste. Il porte « date de fin de migration à compléter », que `SP‑5` devra renseigner. |
| 8 | « Seconde zone de stock : dis ce qui existe déjà (`mode_stock`, zones, devises) » | **`ModeStock` et les devises existent ; les « zones » que tu mentionnes n'ont aucun rapport avec le stock.** `ZoneLivraison` regroupe des **destinations de livraison**, pas des sites de stockage. Le stock est un scalaire sur `products` : il n'y a **aucune** notion d'entrepôt. Ce qui est acquis se limite à `XAF`/`XOF` et à `PaysDevise` (`CG → XAF`). D'où 5 à 7 jours, et le rang 4 après les deux lancements. |
| 9 | « Badge de visa cohérent avec la durée (pas de VLS-TS pour 1 mois) » | **Confirmé, et la cause est précise** : le badge est **écrit en dur** (`TrainingsPage.tsx:107-110`) et piloté par le seul booléen `isLongStay`, que `TrainingService` recopie du formulaire sans jamais le confronter à `durationDays` (lignes 276‑277 et 311‑312). Il n'y a donc rien à « rendre cohérent » : il faut **contraindre la saisie** et **calculer** le badge. |
| 10 | « Positionnement : vérifie qu'aucun texte ne dit officiel ou certifié et qu'aucun lien vers le CNG ne subsiste » | **Les liens sont tenus par un test** (`AucunLienSortant.test.tsx`) : c'est de l'outillage, pas de la vigilance, et c'est acquis. **Les adjectifs ne le sont pas** : aucun test ne les couvre. `FOR‑3` reste à faire. |
| 11 | « TODO, FIXME, textes entre crochets, données factices, fonctions désactivées » | **Aucun `TODO` ni `FIXME` dans `backend/src/main`.** Le dépôt est propre sur ce point, et ce n'est pas un hasard : `ValeurDAttente` **refuse le démarrage** sur une valeur d'attente, après que les reçus ont annoncé « SIRET : IMMATRICULATION EN COURS » pendant des mois. Ce que l'audit a trouvé à la place est plus subtil : de la **configuration morte** (`app.aws.s3` / `app.aws.kms`, `application.yml:161-170`, lue par aucun code, sans dépendance AWS SDK) et une **section légale vide** (`PrivacyPolicyPage.tsx:176-177`). |
| 12 | « Politique de confidentialité à mettre à jour (fin du transfert hors UE) » | **Plus grave que « à mettre à jour »** : la page **affirme aujourd'hui** « les données sont hébergées dans l'Union européenne » (ligne 180), ce qui est faux pour les pièces des candidats. Et sa section 5 « Coffre-fort documentaire » est un **paragraphe vide** avec un titre et une entrée au sommaire. |
| 13 | « CGV (délais, remboursements, export, DAP), mentions légales » | **Les CGV sont faites** : 363 lignes, délais, DAP et droits à l'arrivée, rétractation des consommateurs et son exclusion pour les professionnels, remboursements, médiation, litiges. Les mentions légales portent des identifiants **réels**. Le manque n'est pas là : il est dans l'**adhésion à un médiateur** (`legal.ts:92-93`, deux champs `null`, obligation B2C), dans les **conditions partenaires** (aucune occurrence dans tout le dépôt), et dans le **registre des traitements** (introuvable). |
| 14 | « Intégration continue : les tests tournent-ils automatiquement à chaque push ? » | **Non. `.github/workflows` est vide.** Et il n'y a ni `render.yaml` ni `vercel.json` : l'infrastructure n'est décrite nulle part dans le dépôt. La routine de livraison de `CLAUDE.md` est tenue de mémoire, par celui qui s'en souvient. |
| 15 | « Instabilité des workers vitest » | **Contournée et documentée**, pas ignorée : `testTimeout: 20_000` au lieu de 5 000, avec son raisonnement dans le fichier. L'épisode « 6 échecs, 33 réussites » n'a jamais été reproduit en dix-huit exécutions. La cause reste inconnue : c'est `QUA‑2`, et son urgence viendra de `EXP‑3`. |
| 16 | « Écrans inaccessibles ou morts, routes sans test » | Un seul écran manquant trouvé, et il est net : **`prospect_leads` n'a aucun écran**. L'entité et son dépôt existent au serveur, et aucun fichier du frontend ne les mentionne. Côté tests : 45 fichiers au backend dont **17 ignorés sans Docker**, 13 au frontend. |
| 17 | « Référencement (titres, sitemap, 301) » | **Ni `sitemap.xml` ni `robots.txt`** : `frontend/public/` ne contient que le favicon, les icônes et la marque. **Aucun mécanisme de redirection** côté serveur, pour 211 adresses indexées. Les deux dépendent de `CAT`. |
| 18 | « Bandeau promotionnel mobile » | **Non vérifié.** `PromoHeroSlider` existe ; je n'ai pas de description du défaut et je n'ai pas voulu en inventer un. `QUA‑5` attend que tu dises ce qui ne va pas. |
| 19 | « Accessibilité (contrastes, clavier) » | Les **contrastes** sont tenus par le garde-fou de charte (≥ 4,5:1), avec un cran sur les couleurs en dur à **247** qui ne peut que baisser. Le **clavier** n'est mesuré par rien : **non vérifié**. |
| 20 | Les 28 Ko et les 183 Ko | **Rappel de l'erreur du 5 novembre, pour qu'aucune session n'y retombe** : j'avais appelé les 28 Ko une « réserve intouchable » et les 183 Ko un mystère, après avoir silencieusement sorti le chantier brochures de la feuille de route. `211 = 183 + 28`, la marge mesurée est de 211 Ko, et elle est consommée **exactement**. Le commentaire `_plafondJsKo` du fichier de référence annonce encore « 1 333 Ko » : périmé, c'est `QUA‑1`. |

### 9 bis. Écarts relevés le 6 novembre 2026, à ta relecture

Les quatre premiers sont des corrections que **tu** as apportées à ma version du 5 novembre.
Les trois suivants sont ce que la vérification a révélé en les traitant.

| # | Point | Ce que la vérification dit |
|---|---|---|
| 21 | **Ma propre phrase 5 contredisait mon propre classement.** J'exigeais « un accès protégé à la hauteur des pièces d'identité » et je renvoyais la double authentification après V1 | **Tu as raison, et l'erreur est de raisonnement, pas d'information.** J'avais classé `SEC‑2` par son coût, puis rédigé la définition par son exigence, sans relire l'une à la lumière de l'autre. `SEC‑2` passe en lancement 1. |
| 22 | **« `SEC‑6` non estimé » était une échappatoire.** On n'ouvre pas les paiements avec une faille connue | **Tu as raison.** « Non estimé » valait zéro dans mon total et repoussait la décision indéfiniment. Les huit points sont triés au § 3.1 : **2 jours en lancement 1, 2 jours après, et deux déjà corrigés**. La « faille du formulaire médecins » est `SEC‑6a` : **un jour**. |
| 23 | **La phrase fausse de la politique de confidentialité ne pouvait pas attendre février** | **Tu as raison, et l'argument est plus fort que celui que j'avais donné.** Je l'avais classée comme un défaut à corriger ; c'est une **déclaration inexacte maintenue en connaissance de cause**, et c'est la connaissance qui change sa nature. D'où `LEG‑0`, aujourd'hui. |
| 24 | **Le calendrier : 5 jours par semaine pendant trois mois, sans marge** | **Tu as raison.** J'avais écrit la réserve sans l'appliquer au calcul, ce qui revient à ne pas la faire. +25 % appliqué, **plus** l'attente de ta relecture sur les chaînes critiques, **plus** les deux semaines des fêtes. Modèle explicite au § 6.4, pour que tu puisses le contester ligne par ligne. |
| 25 | **« SPF, DKIM et DMARC manquent »** | **Non — les trois sont en place**, vérifiés en DNS le 6 novembre. Et j'ai failli conclure comme toi : le domaine apex ne montre rien, parce que Mailtrap passe par le sous-domaine de retour `mt47` et les sélecteurs `rwmt1` / `rwmt2`. **C'est exactement l'erreur que j'avais déjà commise le 11 octobre**, corrigée le 12 sur un en-tête réel — ma propre note de suivi me l'a évitée cette fois. Ce qui manque vraiment est le `rua=` : sans lui, le jour où une signature casse, **personne ne l'apprend**. `EXP‑4` tombe de « tout configurer » à **une ligne de DNS et un demi-jour**. |
| 26 | **La dette de sécurité contenait deux points déjà corrigés** | Le point 5 — les adresses `javascript:`, celui qui était marqué « PRIORITAIRE » — est **corrigé des deux côtés** : `AdresseWeb.exiger` au serveur sur les trois colonnes, `adresseSure` au client. Le point 7 (authentification des e-mails) l'était aussi. **La note de suivi ne le disait pour aucun des deux.** Une dette qu'on croit porter coûte des décisions, même quand elle n'existe plus. |
| 27 | **Un point de la dette change de rang à cause de `SP‑7`** | Le point 6 — l'extension portée par `Training.imageS3Key` — est **sûr aujourd'hui** parce qu'aucune suppression ne lit cette colonne. Or `SP‑7` introduit précisément une **tâche de purge**. Il passe donc en lancement 1 sous le nom `SEC‑6c`, **avant** `SP‑7` et non après. C'est le genre de dépendance qu'on ne voit qu'en relisant la dette à la lumière du plan. |
| 28 | **Deux oublis de ma liste de tâches, tous deux à délai externe** | L'**activation du compte Stripe en mode live** (identité de la SAS, IBAN) : plusieurs jours ouvrés, et je l'avais fondue dans `ST‑7` comme si c'était un réglage. Et l'en-tête **`Content-Security-Policy`**, absent de tout l'inventaire : une atténuation à un jour pour le risque que `SEC‑5` met 1,5 jour à supprimer, et qui tient pendant tout l'intervalle. |

---

*Fin du cahier des charges. Les lots se livrent selon la routine de `CLAUDE.md` § 6 — build
propre, suite complète verte **avec le nombre d'ignorés**, vérification réelle dans un
navigateur — et aucun ne part en production sans ton accord explicite.*
