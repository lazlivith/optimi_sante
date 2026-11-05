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
> **Rédigé le 5 novembre 2026**, sur la branche `docs/cahier-des-charges-v1` depuis `main`
> (`734a928`). Aucun code, aucune migration, aucune écriture en base, aucun déploiement.

**Convention de preuve.** Chaque état affirmé ci-dessous porte son fichier et sa ligne. Ce qui
n'était pas vérifiable depuis le dépôt est écrit **« non vérifié »**, sans exception et sans
formule d'atténuation. Les estimations sont en jours de développement et portent la mention
*estimation* quand aucun lot comparable n'a déjà été livré.

---

## 1. Ce que « V1 commercialisable » veut dire

**Proposition à valider.** V1 est atteinte le jour où l'on peut encaisser un vrai paiement sans
qu'aucune des six phrases suivantes soit fausse.

1. **Boutique.** Un client voit un prix juste dans sa devise, pour une destination que nous
   savons servir ; il paie ; il reçoit une **facture conforme** ; et nous ne lui avons jamais
   promis un stock que nous n'avons pas compté.
2. **Formations en CHU.** Un dossier va de la candidature au kit d'arrivée sans qu'une pièce
   personnelle — passeport, diplôme, inscription à l'ordre — soit hébergée hors de l'Union
   européenne, et sans qu'une adresse de fichier atteigne un navigateur.
3. **Exercer en France.** Une demande d'accompagnement arrive, est traitée, et **rien** sur le
   site ne laisse croire que nous sommes une autorité sanitaire.
4. **Espace partenaire.** Un CHU publie une formation, relit un dossier, et voit ses
   reversements.
5. **Administration.** Les écrans d'administration répondent, chaque geste est journalisé, et
   l'accès à des pièces d'identité est protégé à la hauteur de ce qu'il ouvre.
6. **Exploitation.** La base est sauvegardée et une restauration a été **essayée** ; un
   paiement perdu se voit ; et chaque phrase des documents légaux est vraie au moment où elle
   est publiée.

**Ce que V1 n'est pas.** La refonte visuelle du catalogue (les dix écrans des maquettes), le
second entrepôt, la structuration des brochures par l'IA, et la double authentification des
comptes d'administration. Chacun est argumenté au § 8.

**Une distinction à ne pas perdre.** « Condition de V1 » et « place dans le calendrier » sont
deux choses différentes. Le **chantier brochures** (`BR‑2` à `BR‑5`) n'est **pas** une condition
de V1 — rien dedans n'empêche d'encaisser — mais il est placé **avant** `ST` par ton arbitrage
du 5 novembre (scénario A). Le § 6 respecte cet ordre ; le § 3 dit la vérité sur sa nature.

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

Un seul tableau. `V1` = condition de mise en vente ; `AV1` = après V1. La colonne **jours** est
en jours de développement ; **JS** est le poids ajouté au JavaScript servi.

### 3.1 Sécurité et données personnelles

| ID | Module | Objet | Pourquoi (risque si non fait) | Dépend de | V1 | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `DP‑4` | documents | Plus aucune clé de stockage au navigateur ; jeton chiffré ; alias de transition ; `409 REFERENCE_PERIMEE` ; reprise du dossier | La clé d'un passeport est lisible dans la réponse d'upload **et** dans la charge de tout jeton de téléchargement | rien | **V1** | 1,5 | 0 | **écrit, poussé, non déployé** (`fix/dp4-cles-hors-navigateur`, `9c06759`) | toi (fenêtre) |
| `SP‑1` | documents | `StockageService` à deux adaptateurs ; `StockagePriveS3` Scaleway ; lecture avec repli Cloudinary | Les pièces d'identité restent hors UE tant que ce lot n'existe pas | comptes + clés Scaleway | **V1** | 3 | 0 | à faire | toi (comptes) |
| `SP‑2` | documents | Correction du commentaire de `DossierStockage` et de l'inventaire ; **retrait de l'alias `publicId`** | Un alias qui survit devient une interface | `SP‑1` | **V1** | 0,5 | 0 | à faire | — |
| `SP‑3` | documents | Script de copie : essai à blanc, lots, journal, reprise, taille + SHA‑256 | Une copie non vérifiée est une perte de document qu'on découvre six mois après | `SP‑1` | **V1** | 2,5 | 0 | à faire | — |
| `SP‑4` | documents | Exécution de la copie, pièces des candidats d'abord | — | `SP‑3` + sauvegarde + ton accord | **V1** | 1 | 0 | à faire | toi |
| `SP‑5` | documents | Retrait du repli ; suppression chez Cloudinary ; **demande écrite au support** pour leurs sauvegardes | Sans la demande au support, la copie de sauvegarde du passeport reste chez eux après la suppression | `SP‑4` vérifié | **V1** | 1 | 0 | à faire (courriel rédigé, § A.9) | toi (envoi) |
| `SP‑6` | documents | `SignatureFichier` sur **toutes** les routes de dépôt | Aujourd'hui branché à un seul endroit : un `.exe` renommé `.pdf` entre | rien | **V1** | 1 | 0 | à faire | — |
| `SP‑7` | documents | Durées de conservation : cycle de vie, clôture par parcours, purge de l'objet **et de ses versions non courantes**, avertissement de réouverture | Sur un bucket versionné, un `DeleteObject` pose un marqueur et laisse la version lisible : la purge ne purgerait rien | `SP‑5` + validation comptable | **V1** | 2 | 0 | à faire | toi + comptable |
| `SEC‑1` | sécurité | **Limiteur de débit** sur les sept routes `permitAll`, avec un plafond par adresse et par route | Inscription en masse, énumération de mots de passe, et surtout `/api/v1/ai/chat` ouvert : une dépense d'IA sans plafond, par des anonymes | rien | **V1** | 1,5 | 0 | à faire | — |
| `SEC‑2` | sécurité | Double authentification des comptes d'administration | Un compte d'administration ouvre des pièces d'identité ; un mot de passe seul ne suffit pas | `SEC‑1` | AV1 | 2,5 | ~8 Ko | à faire | toi |
| `SEC‑3` | sécurité | **Fermer le dépôt anonyme de `/api/v1/partnership/**`** : contrôle de signature, plafond de taille propre à la route, et jeton de formulaire | Écriture anonyme et non contrôlée dans notre stockage, 55 Mo par appel, sans limite de fréquence. Le plus exploitable des manques trouvés | rien (peut rouler avec `SP‑6`) | **V1** | 1 | 0 | à faire | — |
| `SEC‑4` | sécurité | Versionner le registre d'incidents (`.md`, sous git), y fixer ce qu'il doit contenir, et le relier à `SP‑5` | Un registre qui n'existe que sur un poste n'est pas un registre | rien | **V1** | 0,5 | 0 | **rédigé, non suivi par git** | — |
| `SEC‑5` | sécurité | Jeton hors `localStorage` (cookie `HttpOnly` + `SameSite`) | Une injection de script vole la session | `SEC‑1` | AV1 | 1,5 | 0 | à faire | — |
| `SEC‑6` | sécurité | Les huit points du backlog sécurité / cloisonnement, dont la faille du formulaire médecins | Mis de côté par toi pendant « Exercer en France », à reprendre en premier après la mise en ligne | — | AV1 | *non estimé* | — | voir `docs/backlog_brochures_et_securite.md` | toi |

### 3.2 Brochures partenaires

Nature : **pas une condition de V1** — rien dedans n'empêche d'encaisser. Place arbitrée au
scénario A (juste après `SP`). Soupape conservée : si la trésorerie l'exige, `BR‑3` à `BR‑5`
repassent après `ST` sans retravail ; seul `BR‑2` est lié à `SP‑1`.

| ID | Module | Objet | Pourquoi | Dépend de | V1 | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `BR‑2` | partenaires | Modèle : brochure, statuts, autorisation du partenaire, accès journalisé, suppression à 90 jours ; **original en S3 privé**. Porte la migration du type `DEMANDE_BROCHURE_ALERTE_ADMIN` | `BR‑1` a retiré une fonction utilisée, en promettant son retour | **`SP‑1`** | AV1 *(planifié avant `ST`)* | 2 | 0 | à faire | — |
| `BR‑3` | partenaires | Extraction PDFBox, garde-fous mémoire / temps / pages, **purge déterministe des coordonnées avant tout enregistrement** | Enregistrer puis purger laisse la trace dans les sauvegardes | `BR‑2` | AV1 *(idem)* | 3 | 0 | à faire | — |
| `BR‑4` | partenaires | Écran de dépôt partenaire, écran de relecture, éléments retirés mis en évidence | Un partenaire doit voir ce qu'on a retiré de son texte avant publication | `BR‑3` | AV1 *(idem)* | 2,5 | 22 Ko | à faire | — |
| `BR‑5` | partenaires | Validation `ADMIN_MOBILITE` / `SUPER_ADMIN`, motif de refus, gabarit Optimi Santé (11ᵉ), pied de page validé, **prix du partenaire jamais publié**, en public l'établissement et la ville seulement — adresse précise réservée à la convocation | L'ancienne version reste en ligne jusqu'à la validation : c'est ce qui rend la modification sans risque | `BR‑4` | AV1 *(idem)* | 2 | 6 Ko | à faire | — |
| `BR‑6` | partenaires | Structuration par l'IA | Hors feuille de route : dépend d'une décision non prise, et ne bloque rien | `BR‑5` | AV1 | *non estimé* | — | **décision ouverte n° 7** | toi |

### 3.3 Catalogue et import

| ID | Module | Objet | Pourquoi | Dépend de | V1 | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `M0` | catalogue | Cadrage, dont la **requête de contrôle des visuels factices** et la source unique côté serveur | Quatre listes divergentes de visuels factices ont déjà existé | rien | **V1** | 1 | 0 | à faire | — |
| `CAT` | catalogue | Arbre cible à 3 niveaux, filtrage **par descendance**, **redirections 301**, route d'arbre en cache, administration de l'arbre | Sans descendance, zéro produit sur toute page famille ; sans 301, 211 adresses indexées cassent | **ton arbitrage** (`docs/arborescence_catalogue_v2.xlsx`) | **V1** | 6–8 | incluses au budget | à faire | toi |
| `PIL` | catalogue | Import pilote LCM (50 lignes) | — | `CAT` + marge fixée | **V1** | 0,5 | 0 | à faire | toi (marge) |
| `IMP` | catalogue | Import complet, **7 344 lignes** | Durée de transaction, images manquantes, produits à désactiver : d'autres questions que le pilote | `PIL` vert + décisions 7, 8, 9 | **V1** | 1,5 | 0 | à faire | toi |
| `STK‑2` | stock | **Second entrepôt** (Congo, propriété d'Optimi SAS) : notion de site de stock, grille de prix en FCFA couvrant transport et douane | Rien n'existe : le stock est un scalaire sans site. C'est un chantier, pas un réglage | `ST‑1`, `IMP` | AV1 | 5–7 *(estimation, aucun lot comparable)* | ~10 Ko | à faire | toi |
| `STK‑3` | commande | Commande minimum et délais pour les zones non couvertes | Une zone non desservie refuse déjà la commande et invite au devis : le manque est le **délai**, pas le refus | `L` | AV1 | 0,5 | 0 | **décisions ouvertes n° 8 et 9** | toi |

### 3.4 Maquettes — les dix écrans

Aucun de ces lots n'est une condition de V1 : ils refont des écrans qui **fonctionnent**.
Budget JS arrêté : **183 Ko catalogue + 28 Ko brochures = 211 Ko**, sur une marge mesurée de
211 Ko (1 289 Ko servis, plafond 1 500). La marge est consommée **exactement**, sans réserve.

| ID | Module | Objet | Écrans livrés | Dépend de | V1 | Jours | JS |
|---|---|---|---|---|---|---|---|
| `M1` | catalogue | En-tête et barre de recherche | **aucun** (présentes sur les 6 écrans de `M3a`/`M3b`) | `M0` | AV1 | 1,5 | 10 Ko |
| `M2` | catalogue | Fiche produit | 9, 10 | `M1` | AV1 | 4 | 40 Ko |
| `M3a` | catalogue | Méga-menu, page Catalogue, Catalogue mobile | 1, 2, 3 | `CAT` | AV1 | 3 | 25 Ko |
| `M3b` | catalogue | Famille, rayon, versions mobiles, panneau Filtrer | 4 à 8 | `CAT` | AV1 | 4 | 30 Ko |
| `M4` | catalogue | Marque (filtre et libellé) | **aucun** | `M3b`, `CAT` | AV1 | 2,5 | 12 Ko |
| `M2‑bis` | catalogue | Caractéristiques, documents, garantie | **aucun** (onglets *dans* la fiche) | `M2`, **`SP‑1`** | AV1 | 3 | 25 Ko |
| `L` | commande | Délais de livraison par zone | **aucun** | `CAT` | AV1 | 1,5 | 0 |

**Aucun écran orphelin** : les dix sont couverts par `M2`, `M3a` et `M3b`. Les quatre autres lots
ne livrent aucun écran et se vérifient donc sur les captures de ceux-là — `M1` sur les six écrans
de `M3a`/`M3b`, `M4` sur Famille, Rayon et la fiche, `M2‑bis` dans la fiche, `L` sur le panier et
la commande. Le **numéro WhatsApp** est un paramètre : sans lui, `M2` se livre, bouton masqué,
sans dette. Le **texte DAP** est à centraliser au premier lot qui y touche — le code dit « hors
de France », pas « hors UE », et le serveur porte `droitsALArrivee`.

### 3.5 Paiements et facturation

| ID | Module | Objet | Pourquoi | Dépend de | V1 | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `ST‑0` | paiement | Neutralisation des commandes de test et purge des ventes fictives : inventaire en lecture seule, puis script `ON_ERROR_STOP=1` qui compte et `RAISE EXCEPTION` si le compte diffère | Le premier relevé de chiffre d'affaires réel serait illisible | ton accord + sauvegarde | **V1** | 1 | 0 | à faire | toi |
| `ST‑1` | commande | **Cycle de vie** : `PAID` et `DELIVERED`, règles de transition, `paid_at` / `shipped_at` / `delivered_at`, statut rendu au client, écran d'administration. Migration | Une commande payée par carte reste `PENDING`, et le client ne voit jamais son statut | rien | **V1** | 3 | ~5 Ko | à faire | — |
| `ST‑2` | stock | **Lot B** : remise en stock à l'annulation et au remboursement, journal des ajustements manuels | Un remboursement qui ne rend pas le stock fait vendre deux fois le même article | `ST‑1` | **V1** | 2,5 | 0 | à faire | — |
| `ST‑3` | paiement | **Test de bout en bout de `charge.refunded`** en mode test, remboursement déclenché depuis le tableau de bord Stripe | Le code est complet et n'a jamais tourné. Un webhook jamais essayé est un webhook inconnu | `ST‑1`, `ST‑2` | **V1** | 0,5 | 0 | à faire | — |
| `ST‑4` | facturation | **Factures `FA-`** à numérotation sans trou, mentions obligatoires, **avoirs `AV-`** adossés à une facture, PDF en stockage **privé** | Un hôpital ne récupère pas la TVA sur un reçu. Aucune facture n'existe aujourd'hui | `ST‑1`, **`SP‑1`** | **V1** | 4 | ~5 Ko | à faire | toi + comptable |
| `ST‑5` | facturation | **TVA** : taux vérifiés rayon par rayon, booléen export déduit de la zone, mention d'exonération (art. 262‑I CGI), justificatif de sortie rangé | Aucun pays de l'UE hors France n'est livré : pas de guichet unique, pas de 27 taux | `ST‑4` | **V1** | 2 | 0 | à faire | toi + comptable |
| `ST‑6` | formations | **Acompte d'inscription** : correction du blocage, puis confirmation manuelle par l'administration **vérifiée auprès de Stripe** — l'admin demande au serveur de relire le `PaymentIntent`, il ne déclare pas « payé ». Journal de qui a confirmé quoi | Une confirmation déclarative est une porte ouverte à l'erreur et à la fraude interne | le diagnostic (SQL + *Events*) | **V1** | 2 *(peut tomber à 0,5 selon le diagnostic — non revu à la baisse par prudence)* | 0 | **bloqué** | toi |
| `ST‑7` | paiement | **Passage en live** : clés live sur Render et Vercel, **nouveau** point d'entrée de webhook live avec son propre secret, paiement réel de faible montant puis remboursement | — | tous les `ST`, et `ST‑0` | **V1** | 0,5 | 0 | à faire | toi |
| `CDE‑1` | commande | **E-mail et notification d'expédition**, avec le suivi s'il existe | Un client qui a payé n'apprend jamais que son colis est parti. Zéro événement d'expédition sur vingt-quatre | `ST‑1` | **V1** | 1,5 | 0 | à faire | — |
| `CDE‑2` | administration | **Écran de suivi des prospects** (`prospect_leads`) | Les prospects sont capturés et invisibles de tous | rien | **V1** | 1 | ~6 Ko | à faire | — |
| `FIN‑1` | partenaires | Exécution réelle d'un reversement partenaire, de bout en bout | Le circuit existe et n'a jamais tourné en conditions réelles | `ST‑1` | **V1** | 0,5 | 0 | à faire | toi |

### 3.6 Formations et Exercer en France

| ID | Module | Objet | Pourquoi | Dépend de | V1 | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `FOR‑1` | formations | **Cohérence du badge de visa** : `isLongStay` déduit ou contraint par `durationDays`, avec refus à la saisie et badge calculé | Une formation d'un mois annonce « VLS-TS > 3 MOIS ». C'est une information de droit au séjour, fausse, publiée | rien | **V1** | 0,5 | 0 | à faire | — |
| `FOR‑2` | formations | **Images verticales** : image entière sur fond flouté dans la bannière, format conseillé indiqué au dépôt | Les affiches de CHU sont en portrait et sont recadrées | rien | **V1** | 1 | ~3 Ko | à faire | — |
| `FOR‑3` | formations | Relecture mot à mot des textes publics : aucun « officiel », aucun « certifié », aucun lien vers une autorité | Le test ne couvre que les **liens**. Un adjectif passe | rien | **V1** | 0,5 | 0 | à faire (garde-fou partiel : `AucunLienSortant.test.tsx`) | toi (texte) |
| `FOR‑4` | formations | Parcours candidat joué **en entier** sur un dossier de test en production, les huit étapes | Chaque étape a été vérifiée seule ; l'enchaînement complet, jamais | `DP‑4` déployé | **V1** | 1 | 0 | à faire | toi |

### 3.7 Légal et contenu

| ID | Module | Objet | Pourquoi | Dépend de | V1 | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `LEG‑1` | légal | Politique de confidentialité : **corriger le § 9** (hébergement UE), **écrire le § 5** (coffre-fort, aujourd'hui vide), **nommer** les sous-traitants (Scaleway, Cloudinary, Stripe, Mailtrap, Render, Vercel, service d'IA s'il est activé), et dater | Un document publié affirme aujourd'hui quelque chose de faux, et expose une section vide | `SP‑5` pour le § 9 | **V1** | 1 | 0 | à faire | toi (validation) |
| `LEG‑2` | légal | **Adhésion à un médiateur de la consommation**, puis `VITE_LEGAL_MEDIATEUR_NOM` et `…_SITE` | Obligation légale pour un commerçant en ligne B2C. Les CGV y renvoient déjà et les deux champs sont `null` | — | **V1** | 0 (dev) | 0 | **démarche à engager** | toi |
| `LEG‑3` | légal | **Conditions partenaires** avec clause de non-contournement | Aucune occurrence dans le dépôt. Sans elle, rien n'empêche un CHU de reprendre un candidat en direct | rien | **V1** | 1 | ~2 Ko | à faire | toi (texte) |
| `LEG‑4` | légal | **Registre des traitements** : où il vit, ce qu'il contient, qui le tient à jour | Introuvable. Obligatoire dès qu'on traite des données de santé et d'identité | `LEG‑1` | **V1** | 0,5 | 0 | à faire | toi |

### 3.8 Qualité et exploitation

| ID | Module | Objet | Pourquoi | Dépend de | V1 | Jours | JS | Statut | Qui décide |
|---|---|---|---|---|---|---|---|---|---|
| `EXP‑1` | exploitation | **Sauvegardes** : fréquence constatée et écrite, puis **restauration essayée** sur une base jetable, avec sa date | Une sauvegarde jamais restaurée n'est pas une sauvegarde | rien | **V1** | 1 | 0 | **non vérifié** | toi |
| `EXP‑2` | exploitation | Supervision des erreurs, **alerte sur échec de webhook**, journaux conservés | Un paiement encaissé dont le webhook échoue est aujourd'hui silencieux | rien | **V1** | 1,5 | 0 | à faire | — |
| `EXP‑3` | exploitation | **Intégration continue** : build, suite complète, garde-fou de charte à chaque `push`, avec le **nombre d'ignorés** rapporté | `.github/workflows` est vide. La routine de `CLAUDE.md` est tenue de mémoire | rien | **V1** | 1 | 0 | à faire | — |
| `QUA‑1` | qualité | Dette d'outillage en un lot : fixture de `ControleBrochureTest` déplacée **dans** `backend/src/test/resources`, suppression de la configuration morte `app.aws.*`, correction de `_plafondJsKo`, correction du « onze statuts » de `parcoursDossier.ts` (il y en a douze), scission du prompt `M3`, correction de l'affirmation fausse de `PIL` | Chacun est un piège pour la prochaine session, et aucun ne coûte cher | rien | **V1** | 1 | 0 | à faire | — |
| `QUA‑2` | qualité | Cause racine de l'instabilité des tests frontend, ou décision écrite de vivre avec | Contournement en place (`testTimeout: 20_000`), cause inconnue. Un test instable est une dette | `EXP‑3` | AV1 | 1 | 0 | contourné | — |
| `QUA‑3` | qualité | Accessibilité : parcours clavier complet et contrastes sur les écrans de vente | Le garde-fou de charte mesure les contrastes ; il ne mesure **pas** le clavier. **Non vérifié** | rien | AV1 | 2 *(estimation)* | 0 | à faire | — |
| `QUA‑4` | catalogue | Référencement : `sitemap.xml`, `robots.txt`, titres et métadonnées par page | `frontend/public/` ne contient que le favicon, les icônes et la marque. Aucun des deux fichiers n'existe | `CAT` (pour les 301) | AV1 | 1,5 | 0 | à faire | — |
| `QUA‑5` | catalogue | Bandeau promotionnel mobile (`PromoHeroSlider`) | Signalé dans ton inventaire ; **non vérifié** faute d'une description du défaut | rien | AV1 | *non estimé* | — | **à préciser** | toi |

### 3.9 Totaux

| | Jours | dont acquis |
|---|---|---|
| **V1** | **60 à 62** | 1 (`DP‑4` écrit) |
| dont stockage privé (`DP‑4` + `SP`) | 12,5 | 1 |
| dont sécurité ajoutée (`SEC‑1`, `SEC‑3`, `SEC‑4`) | 3 | 0 |
| dont catalogue (`M0`, `CAT`, `PIL`, `IMP`) | 9 à 11 | 0 |
| dont paiements (`ST‑0` à `ST‑7`) | 15,5 | 0 |
| dont commande et administration (`CDE`, `FIN`) | 3 | 0 |
| dont formations (`FOR‑1` à `FOR‑4`) | 3 | 0 |
| dont légal (`LEG‑1`, `LEG‑3`, `LEG‑4`) | 2,5 | 0 |
| dont exploitation et qualité (`EXP`, `QUA‑1`) | 4,5 | 0 |
| dont brochures (`BR‑2` à `BR‑5`) — *planifiées en V1, non conditionnelles* | 9,5 | 0 |
| **Après V1** | **28,5 à 30,5** | — |
| dont maquettes (`M1` → `L`) | 19,5 | — |
| dont sécurité (`SEC‑2`, `SEC‑5`) | 4 | — |
| dont second entrepôt (`STK‑2`) | 5 à 7 | — |
| dont qualité (`QUA‑2`, `QUA‑4`, et `STK‑3`) | 4 | — |
| **Non estimé** | `SEC‑6` (8 points du backlog), `BR‑6`, `QUA‑5` | — |

**Si l'on retire les brochures du périmètre V1** (soupape du § B.6 bis), V1 tombe à
**50,5 à 52,5 jours**.

---

## 4. Décisions en attente

Dans l'ordre de ce qu'elles bloquent.

| # | Décision | Bloque | Ma recommandation |
|---|---|---|---|
| **1** | **La définition de V1 du § 1**, et donc la frontière V1 / Après V1 | tout le calendrier | Valider les six phrases telles quelles. Si tu veux les maquettes en V1, le total passe de 60‑62 à 80‑82 jours et l'ouverture des paiements recule d'un mois : c'est un arbitrage de trésorerie, pas de qualité. |
| **2** | **Les comptes et clés Scaleway**, et les sept variables sur Render | `SP‑1`, donc `SP‑2` à `SP‑7`, `ST‑4`, `BR‑2`, `M2‑bis` | **Deux projets séparés** (production, développement), un bucket et une application chacun : la portée d'une politique IAM Scaleway est le **projet**, jamais le bucket. Tu me colleras la liste des jeux de permissions affichée dans la console ; **je ne te demanderai jamais la valeur d'une clé.** |
| **3** | **L'arbre cible** à trois niveaux, et le rattachement des 211 catégories | `CAT`, donc `IMP`, `M3a`, `M3b`, `M4`, `L`, `QUA‑4` | Je produis le fichier d'arbitrage ; tu tranches ligne par ligne. Rien ne part en base avant. C'est la plus longue attente du projet : à lancer **en parallèle** de `SP`. |
| **4** | **Le diagnostic de l'acompte** : la requête SQL sur le dossier `0BFDD667`, et la page *Events* de Stripe | `ST‑6`, donc `ST‑7`, donc l'ouverture des paiements | Deux copies d'écran suffisent. Elles tranchent entre « la session ne se termine pas » et « le webhook échoue à l'arrivée », et peuvent faire tomber `ST‑6` de 2 jours à 0,5. |
| **5** | **La validation comptable** des durées de conservation par type de document, et des taux de TVA rayon par rayon | `SP‑7`, `ST‑4`, `ST‑5` | Un seul rendez-vous couvre les trois. Y apporter : le tableau du § A.6, la liste des rayons, et la question de l'obligation de conservation comptable face à la minimisation RGPD — ce sont deux durées différentes, et c'est la plus longue qui s'applique au document, pas aux données. |
| **6** | **L'adhésion à un médiateur de la consommation** | le go-live B2C, légalement | À engager tout de suite : c'est une démarche administrative dont le délai ne dépend pas de nous, et les CGV y renvoient déjà. |
| **7** | **L'envoi de texte de brochure au service d'IA** | `BR‑6` seulement | **Non.** Et si un jour oui, seulement du texte **déjà purgé** par `BR‑3`, jamais le PDF d'origine. Ne bloque rien aujourd'hui. |
| **8** | **La commande minimum**, et les délais des zones non couvertes | `STK‑3`, `L` | Deux bornes en jours ouvrés par zone, jamais une valeur unique. Une zone non desservie refuse déjà la commande : ne pas inventer un délai pour elle. |
| **9** | **Le second entrepôt : V1 ou après ?** | `STK‑2` | **Après V1.** Rien n'existe — ni site de stock, ni zone sur le stock : c'est un chantier de 5 à 7 jours, pas un réglage. Les devises `XAF` / `XOF` et la correspondance pays → devise existent déjà (`PaysDevise`), et c'est tout ce qui est acquis. |
| **10** | **Le numéro WhatsApp** | `M2` (bouton masqué sans lui) | Un numéro professionnel dédié, en paramètre. `M2` se livre sans, sans dette. |
| **11** | **La marge fournisseur** | `PIL`, `IMP` | Par catégorie de l'arbre cible, pas par fournisseur : `Category.marginRate` existe déjà et prime. |
| **12** | **Les décisions 5 à 10 de `plan_integration_catalogue.md` § 8** (57 catégories vides, 10 groupes de doublons, 111 « ARRET », 43 « Port en sus », 4 398 lignes sans rayon, garantie) | `CAT`, `IMP`, `M2‑bis` | Inchangées, et toujours valables mot pour mot. À trancher avec la décision 3, au même moment. |

---

## 5. Tes tâches, hors code

Dans l'ordre où elles débloquent le travail.

**Tout de suite, parce que leur délai ne dépend pas de nous**

1. **Scaleway** : deux projets, deux buckets, deux applications, les clés, et les sept variables sur Render. *(débloque `SP‑1`)*
2. **Le médiateur de la consommation** : adhésion. *(débloque le go-live)*
3. **Le rendez-vous comptable** : durées de conservation, taux de TVA par rayon, mentions de facture. *(débloque `SP‑7`, `ST‑4`, `ST‑5`)*
4. **L'arbitrage de l'arbre** dès que je te livre le fichier. *(débloque `CAT` et cinq lots derrière)*

**Deux copies d'écran, cinq minutes**

5. La **requête SQL** sur le dossier `0BFDD667` et la page **Events** de Stripe. *(débloque `ST‑6`)*
6. La **région** de ton compte Cloudinary, lue dans la console. *(confirme le § A.1)*

**Envois et vérifications**

7. Le **courriel au support Cloudinary** pour la suppression de leurs sauvegardes — rédigé, § A.9. *(étape de `SP‑5`)*
8. Chez **Render** : la fréquence réelle des sauvegardes de la base. *(`EXP‑1`)*
9. Chez **Stripe** : le nouveau point d'entrée de webhook **live** et son secret. *(`ST‑7`)*

**Contenus et validations de texte**

10. Les **conditions partenaires** et la clause de non-contournement : les règles commerciales, je rédige. *(`LEG‑3`)*
11. La **définition de la clôture d'un dossier** par parcours (formation courte, Exercer en France), et ce qui se passe à une réouverture. Proposée au § A.6. *(`SP‑7`)*
12. La **relecture du positionnement** : aucun « officiel », aucun « certifié ». *(`FOR‑3`)*
13. La validation de la **politique de confidentialité** réécrite. *(`LEG‑1`)*
14. Le **numéro WhatsApp**, s'il existe. *(`M2`)*

**Accords ponctuels, chacun à son moment**

15. Déploiement de `DP‑4`. Sauvegarde avant `SP‑4`. Accord avant `ST‑0`. Accord avant `ST‑7`.
    Chaque fusion dans `main`, en `--no-ff`.

---

## 6. Chemin critique et calendrier

### 6.1 L'ordre

```
  ┌─ maintenant ─────────────────────────────────────────────────────────────┐
  │ DP-4 (déploiement, ce soir)                                             │
  └──────────────────────────────────────────────────────────────────────────┘
        │
        ├── EN PARALLÈLE, sans dépendance : SEC-1 · SEC-3 · SEC-4 · QUA-1 ·
        │   EXP-3 · FOR-1 · FOR-2 · CDE-2 · LEG-3 · M0
        │
        ▼
  1. STOCKAGE PRIVÉ ····· SP-1 → SP-2 → SP-3 → SP-4 → SP-5 → SP-7      12,5 j
                          SP-6 hors chaîne (avec SEC-3)
        │                                   └─ LEG-1 (§ 9) suit SP-5
        ▼
  2. BROCHURES ·········· BR-2 → BR-3 → BR-4 → BR-5                     9,5 j
        │                 (planifié ici, non conditionnel : soupape § 3.2)
        ▼
  3. CATALOGUE ·········· CAT → PIL                                     7,5–9,5 j
        │                 ↑ ton arbitrage : à lancer dès maintenant
        ▼
  4. PAIEMENTS ·········· ST-0 → ST-1 → ST-2 → ST-3                    15,5 j
                                   └─ ST-4 → ST-5     (ST-4 a besoin de SP-1)
                                   └─ CDE-1 · FIN-1
                          ST-6 (dès le diagnostic) ──┐
                                                     ▼
                                                   ST-7  ← dernier verrou
        ▼
  5. IMP (1,5 j) · FOR-4 · EXP-1 · EXP-2 · LEG-4 · LEG-2 (ta démarche)
        ▼
  ══ V1 ══  puis : maquettes (M1 → L) · SEC-2 · SEC-5 · STK-2 · QUA-2/4 · SEC-6
```

### 6.2 Le chemin critique, en une phrase

`SP‑1` → `SP‑3` → `SP‑4` → `SP‑5` → `ST‑4` → `ST‑5` → `ST‑7` : **16 jours de développement
enchaînés**, et chacun attend le précédent. Tout le reste se parallélise. Ce chemin ne démarre
pas avant que **les comptes Scaleway existent** — c'est la décision 2, et c'est elle qui tient
le calendrier, pas l'arbre.

### 6.3 Trois parallélismes à ne pas gâcher

- **Ton arbitrage de l'arbre** doit partir **maintenant**, pas au début de `CAT`. C'est la seule
  attente de plusieurs jours du projet, et elle peut se superposer entièrement à `SP` et à `BR`.
- **`SP‑4`** (la copie) tourne en tâche de fond : `BR‑2` peut démarrer dès `SP‑1` vert.
- **Les onze lots sans dépendance** (`SEC‑1`, `SEC‑3`, `SEC‑4`, `QUA‑1`, `EXP‑3`, `FOR‑1`,
  `FOR‑2`, `FOR‑3`, `CDE‑2`, `LEG‑3`, `M0` — 10 jours au total) comblent toutes les attentes
  d'arbitrage. Ils ne devraient jamais être la raison pour laquelle on attend.

### 6.4 Une date

**Si tu tranches les décisions 1 à 5 sous une semaine** — soit d'ici au **12 novembre 2026** :

| | Jours ouvrés | À raison de 5 jours par semaine |
|---|---|---|
| Chemin V1 complet | 60 à 62 | 12 à 13 semaines |
| **Date de lancement V1** | | **fin février 2027** (semaine du 22 février) |
| Sans les brochures (soupape) | 50,5 à 52,5 | **début février 2027** |

**Trois réserves, et je préfère les écrire que les sous-entendre.**

1. Ce calcul suppose **cinq jours de développement effectif par semaine**, tenus pendant trois
   mois. Le dépôt ne me permet pas de vérifier que c'est le rythme réel : **non vérifié**.
2. Il ne contient **aucune marge pour l'imprévu**. Les trois derniers mois en ont produit
   plusieurs — la fuite du jeton de document n'était dans aucun plan, et elle a occupé une
   journée. Une marge de 15 % porterait la date à **mi-mars 2027**.
3. `LEG‑2` (le médiateur) est une démarche administrative dont **le délai ne nous appartient
   pas**. Si elle n'est pas engagée cette semaine, elle peut devenir le dernier verrou — et ce
   serait le plus bête de tous.

---

## 7. Critères de lancement

À cocher avant le passage en live. Chacun est vérifiable par quelqu'un d'autre que son auteur.

**Données personnelles**

- [ ] Aucune réponse de l'API ne contient de clé de stockage. *Vérification : onglet Réseau sur un dépôt et un téléchargement, aucun chemin `optimisante/` ni `dossiers-candidats`, et la charge d'un jeton décodée ne rend que du chiffre.*
- [ ] Les neuf dossiers privés sont servis depuis le bucket Scaleway `fr-par`, et le repli Cloudinary est retiré.
- [ ] Un objet supprimé par la purge ne se relit **par aucune version**. *Vérification : `ListObjectVersions` revient vide.*
- [ ] Le support Cloudinary a confirmé **par écrit** la suppression de ses sauvegardes.
- [ ] Toutes les routes de dépôt refusent un fichier dont la signature ne correspond pas à son extension.
- [ ] Le registre d'incidents est sous git, et l'incident n° 1 porte sa date de fin de migration.

**Argent**

- [ ] `ST‑0` a tourné : plus aucune vente fictive en base, et le compte de lignes supprimées est celui qui était attendu.
- [ ] Une commande payée passe en `PAID`, puis `SHIPPED`, puis `DELIVERED`, et le client voit chacun de ces états.
- [ ] Une facture `FA-` est émise, numérotée sans trou, porte les mentions obligatoires, et son PDF est en stockage privé.
- [ ] Un avoir `AV-` est adossé à sa facture.
- [ ] Une commande hors France porte la mention d'exonération à l'export, et son justificatif de sortie est rangé.
- [ ] Un remboursement déclenché depuis Stripe fait sortir la commande du chiffre d'affaires **et** remet le stock.
- [ ] Un acompte d'inscription encaissé fait avancer le dossier **sans** geste manuel ; et une confirmation manuelle est refusée si Stripe ne confirme pas le `PaymentIntent`.
- [ ] Un paiement réel de faible montant est passé en **live**, puis remboursé.
- [ ] Un reversement partenaire a été exécuté de bout en bout.

**Parcours**

- [ ] Le parcours candidat a été joué **en entier**, les huit étapes, sur un dossier de test en production.
- [ ] Aucune fiche formation n'annonce un visa que sa durée n'ouvre pas.
- [ ] Une affiche en portrait s'affiche entière dans la bannière.

**Légal**

- [ ] La politique de confidentialité ne contient **aucune phrase fausse**, ses sous-traitants sont nommés, et sa section « coffre-fort » n'est plus vide.
- [ ] Un médiateur de la consommation est nommé, et les CGV pointent vers lui.
- [ ] Les conditions partenaires sont publiées, clause de non-contournement comprise.
- [ ] Le registre des traitements existe, et quelqu'un est nommé pour le tenir.
- [ ] Aucun texte public ne dit « officiel » ni « certifié », et aucun lien ne sort vers une autorité.

**Exploitation**

- [ ] Une sauvegarde a été **restaurée** sur une base jetable, et la date de l'essai est écrite.
- [ ] Un échec de webhook déclenche une alerte qui arrive à quelqu'un.
- [ ] Le build, la suite complète et le garde-fou de charte tournent à chaque `push`, **et le nombre de tests ignorés est rapporté** — sans quoi une suite verte peut n'avoir rien exécuté.
- [ ] Les sept routes publiques portent un plafond de débit, et le dépôt anonyme de `/api/v1/partnership/**` est fermé.
- [ ] Les empreintes d'arbre des deux miroirs égalent celles de `main`.

---

## 8. Après V1, par ordre de priorité

| Rang | Lots | Pourquoi ce rang |
|---|---|---|
| 1 | **`SEC‑6`** — les huit points du backlog sécurité, la faille du formulaire médecins d'abord | C'est ton propre arbitrage : mis de côté pendant « Exercer en France », à reprendre **en premier** après la mise en ligne. Une faille connue qui attend est la pire dette qu'on puisse porter. |
| 2 | **`SEC‑2`** 2FA des administrateurs, **`SEC‑5`** jeton hors `localStorage` | Le trafic arrive avec le lancement, et avec lui les tentatives. Avant V1 ils retardent une mise en vente ; juste après, ils protègent des comptes qui ouvrent des pièces d'identité. |
| 3 | **`QUA‑4`** référencement (`sitemap.xml`, `robots.txt`, métadonnées) | Sans effet sur une vente ponctuelle, décisif sur le flux à trois mois. Dépend des 301 de `CAT`, donc impossible plus tôt de toute façon. |
| 4 | **`M1` → `M2` → `M3a` → `M3b` → `M4` → `M2‑bis` → `L`** — les dix écrans | 19,5 jours et 142 Ko pour refaire des écrans qui **fonctionnent**. C'est de la conversion, pas de la capacité : à mesurer sur du trafic réel, pas à deviner avant. `M1` et `M2` peuvent démarrer pendant V1 s'il reste du temps mort. |
| 5 | **`STK‑2`** second entrepôt, et **`STK‑3`** commande minimum et délais | Un entrepôt au Congo n'a de sens qu'avec un volume qui le remplit. Le mesurer d'abord. |
| 6 | **`QUA‑2`** cause de l'instabilité des tests, **`QUA‑3`** accessibilité clavier | Deux dettes réelles, aucune ne saigne. `QUA‑2` devient urgent le jour où l'intégration continue de `EXP‑3` rougit sans raison : c'est là qu'on cesse de lire les résultats. |
| 7 | **`BR‑6`** structuration des brochures par l'IA | Dépend d'une décision non prise, ne bloque rien, et ne sert que si les partenaires déposent beaucoup. |

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
| 8 | « Seconde zone de stock : dis ce qui existe déjà (`mode_stock`, zones, devises) » | **`ModeStock` et les devises existent ; les « zones » que tu mentionnes n'ont aucun rapport avec le stock.** `ZoneLivraison` regroupe des **destinations de livraison**, pas des sites de stockage. Le stock est un scalaire sur `products` : il n'y a **aucune** notion d'entrepôt. Ce qui est acquis se limite à `XAF`/`XOF` et à `PaysDevise` (`CG → XAF`). D'où 5 à 7 jours, et le rang 5 en Après V1. |
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

---

*Fin du cahier des charges. Les lots se livrent selon la routine de `CLAUDE.md` § 6 — build
propre, suite complète verte **avec le nombre d'ignorés**, vérification réelle dans un
navigateur — et aucun ne part en production sans ton accord explicite.*
