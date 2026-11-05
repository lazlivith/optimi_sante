# Stockage privé et feuille de route — analyse et plan

Écrit pour : Mounir, pour arbitrage avant tout développement ; et pour la session qui réalisera
les lots.

Mesuré dans le code le 5 novembre 2026, sur `main` (`40d7ef3`). **Aucune exécution** : aucun
appel en écriture à Cloudinary, aucune écriture en base, aucun déploiement, aucune migration.
Les prix et les capacités des fournisseurs sont cités avec leur source ; ce que je n'ai pas pu
vérifier est dit comme tel.

---

# A. Stockage

## A.1 Dans quelle région Cloudinary héberge notre compte

**Ce que dit la configuration : rien.** `application.yml` ne porte que cinq paramètres —
`cloud-name`, `api-key`, `api-secret`, `root-folder`, `environment` (lignes 175‑185 et 298‑305).
**Il n'y a aucun paramètre de région**, et ce n'est pas un oubli de notre part : le SDK Java de
Cloudinary n'en expose pas. L'emplacement de stockage est une propriété **du compte**, fixée à
sa création, que seul Cloudinary peut changer.

**Ce que dit la documentation.** La FAQ officielle de Cloudinary sur le stockage est explicite :
par défaut, Cloudinary utilise des centres de données **aux États-Unis** ; un emplacement
différent (UE, Asie-Pacifique) se demande, et c'est une capacité des comptes **Enterprise**. La
même page précise que les fichiers vivent sur Amazon S3 ou Google Cloud, avec sauvegardes
multiples et redondance multi-région — et que sur l'offre **gratuite**, les sauvegardes sont
elles aussi conservées par Cloudinary.

> Source : [Where are my Cloudinary assets stored?](https://cloudinary.com/documentation/developer_onboarding_faq_storage) ·
> [Billing and Plans](https://cloudinary.com/documentation/billing_and_plans)

**La conclusion, et elle compte.** Notre compte est sur l'offre gratuite. Rien dans la
configuration ne demande l'UE, et l'offre gratuite ne permet pas de le demander. **Nos
documents sont donc hébergés aux États-Unis, et leurs sauvegardes aussi.**

Ce qui veut dire que, depuis le début, les passeports et diplômes de praticiens — pour
l'essentiel des ressortissants hors UE, traités dans le cadre d'un parcours de formation en
France — sont transférés hors de l'Union européenne, sans que ce transfert soit documenté
nulle part chez nous. **C'est une raison de changer de stockage qui est indépendante de la
fuite d'accès public**, et elle ne se corrige pas en passant les objets en `authenticated` :
un objet privé hébergé aux États-Unis reste hébergé aux États-Unis.

**À vérifier de ton côté, en deux minutes et sans aucun secret** : console Cloudinary →
*Settings* → *Account*, la ligne qui nomme le centre de données ou la région, et le nom d'hôte
de livraison (`res.cloudinary.com` ou une variante régionale). Je ne peux pas le lire depuis le
code, et la documentation en ligne a plus d'un an : si la ligne dit autre chose que « US », tu
me le dis et je corrige ce paragraphe. Le reste du plan ne change pas pour autant — les six
autres raisons de migrer tiennent.

---

## A.2 Scaleway Object Storage (fr-par) contre AWS S3 (eu-west-3)

Notre usage, pour cadrer la comparaison : **quelques gigaoctets**, **accès par le backend
seulement** (aucune URL remise au navigateur, voir A.3), quelques centaines d'écritures par
mois, des lectures rares, et une exigence de journal d'accès.

| Critère | Scaleway Object Storage (fr-par) | AWS S3 (eu-west-3, Paris) |
|---|---|---|
| **SDK Java** | AWS SDK for Java v2, avec `endpointOverride` sur `https://s3.fr-par.scw.cloud` et région `fr-par`. Montages documentés par la communauté depuis 2021. | AWS SDK for Java v2, nativement. Aucune surcharge. |
| **URL présignées** | Prises en charge (GET et PUT), via le même `S3Presigner`. Exemples publics en Node ; **aucun exemple Java officiel de Scaleway**. Durée maximale annoncée à 7 jours par une source non officielle, **à vérifier**. | `S3Presigner`, durée maximale 7 jours avec des identifiants IAM, documentée par AWS. |
| **Journal des accès** | **Inclus et actif par défaut.** Object Storage envoie un journal **par requête** à Cockpit, avec le nom du bucket parmi les champs ; onglet *Logs* du bucket dans la console, et tableau de bord Grafana « Object Storage Logs Overview ». Audit Trail couvre en plus les changements de configuration au niveau du compte. | **À activer, et jamais gratuit.** Soit *server access logging* (écrit dans un second bucket, livraison « au mieux », pas de garantie d'exhaustivité), soit CloudTrail **data events**, facturé à l'événement. |
| **Chiffrement au repos** | SSE-ONE géré par Scaleway (`AES256`), SSE-KMS avec une clé Key Manager, ou SSE-C avec notre propre clé. Chiffrement par défaut au niveau du bucket (`PutBucketEncryption`). **Attention** : les objets importés **avant** l'activation ne sont pas chiffrés. | SSE-S3 (`AES256`) actif par défaut sur tout bucket, SSE-KMS avec politique de clé fine. |
| **Gestion des versions** | Prise en charge. | Prise en charge, plus **Object Lock** (WORM) mature. |
| **Règles de conservation** | Cycle de vie S3 standard : expiration, expiration des versions, transition de classe, abandon des téléversements incomplets. 1 000 règles par bucket maximum ; exécution quotidienne à minuit UTC, effet entre une minute et 24 h. | Cycle de vie S3, plus les classes Glacier. |
| **Résidence** | Société française, région Paris, offre certifiée HDS disponible si des données de santé entraient un jour dans le périmètre. | Région Paris, mais **région sur option** (à activer explicitement), et société soumise au CLOUD Act. |
| **Prix à notre volume** | Moins cher au gigaoctet que S3 selon les comparatifs. | Référence du marché. |

> Sources : [Scaleway Object Storage — Concepts](https://www.scaleway.com/en/docs/object-storage/concepts/) ·
> [Logs and metrics collected and sent to Cockpit](https://www.scaleway.com/en/docs/object-storage/reference-content/logs-metrics-collection-cockpit/) ·
> [Managing the lifecycle of objects](https://www.scaleway.com/en/docs/object-storage/api-cli/lifecycle-rules-api/) ·
> [Understanding bucket operations](https://www.scaleway.com/en/docs/object-storage/api-cli/bucket-operations/) ·
> [Backup strategies (SSE-C)](https://www.scaleway.com/en/docs/tutorials/backup-strategies/) ·
> [Host healthcare data (bucket conforme)](https://www.scaleway.com/en/docs/object-storage/how-to/host-healthcare-data/) ·
> [Audit Trail — Concepts](https://www.scaleway.com/en/docs/audit-trail/concepts/) ·
> [Object Storage en Java avec le SDK Amazon S3](https://pellegrino.link/2021/05/31/scaleway-object-storage-s3-java-library.html) ·
> [AWS SDK for Java 2.x — URL présignées](https://docs.aws.amazon.com/pt_br/sdk-for-java/latest/developer-guide/examples-s3-presign.html) ·
> [Comparatif de prix UE (source non officielle, à recouper)](https://www.iloveblogs.blog/post/scaleway-object-storage-vs-aws-s3-eu-pricing)

### Ma recommandation : **Scaleway Object Storage, région `fr-par`, classe Standard Multi-AZ**

Trois raisons, dans l'ordre d'importance.

1. **Le journal d'accès est inclus et déjà actif.** C'est le critère qui tranche, et il tranche
   à cause de ce qui vient de nous arriver : au § d de `analyse_doc_priv.md`, nous avons dû
   écrire que nous ne pouvions **pas affirmer** qu'aucun document n'avait été consulté, faute de
   journal sur l'offre Cloudinary. Chez Scaleway, le journal par requête existe sans qu'on le
   demande et sans facture supplémentaire. Chez AWS, c'est une case à cocher qu'on peut oublier,
   ou un service facturé à l'événement. Ne pas reproduire ce trou vaut plus que n'importe quelle
   fonctionnalité de la liste.
2. **Le code est le même dans les deux cas.** On écrit contre l'AWS SDK for Java v2 ; seule
   l'adresse du point d'accès change. Si Scaleway déçoit, le passage à `eu-west-3` est un
   changement de trois variables d'environnement, pas une réécriture. Choisir Scaleway ne nous
   enferme pas ; c'est précisément ce qui rend le choix peu risqué.
3. **Résidence et souveraineté.** Société française, région Paris, et une offre HDS existante
   si des données de santé entrent un jour dans le périmètre — ce qui n'est pas absurde pour une
   plateforme qui vend du matériel médical et accompagne des praticiens.

Le prix n'est **pas** un argument à notre échelle : à quelques gigaoctets, l'écart entre les
deux se compte en euros par an. Je ne le mets pas dans la balance.

**Ce que je sais ne pas savoir, et qui se vérifie au premier lot (SP‑1), pas maintenant :**

- Scaleway n'implémente qu'un **sous-ensemble** de S3. Les quatre capacités dont nous dépendons
  — `GetObject`/`PutObject`, URL présignée GET, chiffrement par défaut du bucket, règles de
  cycle de vie — sont documentées, mais **un test réel sur notre bucket est la seule preuve**.
  SP‑1 commence par là : un aller-retour dépôt / présignature / lecture / suppression sur le
  bucket de développement.
- La durée maximale d'une URL présignée chez Scaleway n'est pas documentée officiellement. Sans
  importance pour nous : nos présignatures durent **une minute** (le backend lit l'objet
  lui-même, le navigateur ne voit jamais l'URL — voir A.3).
- **Un piège à connaître** : le guide Scaleway du bucket conforme HDS interdit les règles de
  cycle de vie sur un bucket conforme. Si l'on vise HDS un jour, les durées de conservation du
  § A.6 devront être appliquées par notre propre tâche planifiée et non par le cycle de vie du
  bucket. Aujourd'hui nous ne visons pas HDS : les règles de cycle de vie sont le bon outil.

---

## A.3 Réévaluation de DP‑1 et DP‑4

La branche `fix/dp1-dp4-documents-prives` porte **un seul commit**, `1834a49`, qui mêle les deux
lots. Le partage est net : **deux fichiers relèvent de DP‑1**, les dix-sept autres de DP‑4.

### Ce qui reste valable : **tout DP‑4**, sans retouche

DP‑4 ne parle pas de Cloudinary. Il parle de ce que notre API livre au navigateur, et la réponse
reste la même quel que soit le stockage derrière :

| Fichier | Ce qu'il fait | Sort |
|---|---|---|
| `StorageController.java` | rend une **référence signée** et un `previewUrl` vers notre backend, plus jamais le `public_id` | **garder** |
| `OrderResponseDto.java` (+ `OrderService`, `AdminOrderResource`) | `String documentS3Key` → `boolean hasDocument` | **garder** |
| `EnrollmentDetailDto.java`, `EnrollmentResponseDto.java` | cinq champs de clé retirés, `hasConvention` / `hasAttestation` ajoutés | **garder** |
| `EnrollmentService.java` | `resoudreReference()` résout la référence signée en clé de stockage, et **refuse une clé fabriquée par le client** | **garder** |
| `AucuneCleCloudinaryExposeeTest.java` | verrou par réflexion sur les noms de champs : une clé qui revient casse la suite | **garder** — à renommer `AucuneCleDeStockageExposeeTest` |
| 9 fichiers frontend | les écrans n'attendent plus de clé | **garder** |

Le relais par notre backend, les jetons de 60 min et le contrôle de rôle par type du
`DocumentController` sont le **bon modèle**, et ils deviennent le modèle **unique** : avec S3, le
navigateur ne reçoit jamais la moindre adresse de stockage, et l'URL présignée ne vit qu'une
minute à l'intérieur de la JVM, le temps d'une lecture.

### Ce qui change : **DP‑1, DP‑2, DP‑3, DP‑5 sont abandonnés**

| Fichier | Ce qu'il fait | Sort |
|---|---|---|
| `CloudinaryStorageService.java` | dépôt des documents en `type: authenticated`, lecture et suppression essayant `authenticated` puis `upload`, `genererUrlSignee(publicId, type)` | **abandonner** |
| `CloudinaryAuthenticatedRoundTripTest.java` | aller-retour `authenticated` sur un compte réel | **abandonner** |

Pourquoi abandonner, alors que c'est écrit, testé et vert :

1. **Cela ne règle pas le problème de région.** Un document `authenticated` chez Cloudinary reste
   un document hébergé aux États-Unis (§ A.1). On fermerait l'accès public en laissant le
   transfert hors UE intact.
2. **Cela impose deux migrations au lieu d'une.** D'abord `upload` → `authenticated` chez
   Cloudinary (DP‑2 + DP‑3, 3 jours), puis Cloudinary → S3 (SP‑3 + SP‑4). La première serait
   entièrement jetée. Avec DP‑5, c'est **5,5 jours** de travail dont il ne resterait rien.
3. **Le risque qu'elle couvrait est faible, et DP‑4 le couvre déjà.** J'ai vérifié comment les
   clés sont fabriquées (`CloudinaryStorageService`, lignes 58, 109, 234) : `UUID.randomUUID()`
   complet pour les pièces déposées, et `<nom> + 12 caractères hexadécimaux` pour les documents
   générés — soit 48 bits d'aléa. **Ces clés ne s'énumèrent pas.** La fuite réaliste n'était donc
   pas la devinette d'adresse mais la **sortie de la clé par nos propres réponses**, et c'est
   exactement ce que DP‑4 ferme. Fermer DP‑4 maintenant, puis migrer vers S3, laisse une fenêtre
   dont le vecteur d'exploitation a disparu.

**Une exception, et elle compte : le modèle vierge de convention de partenariat.** La méthode
`uploadPublicTemplate` (ligne 134) dépose avec une clé **volontairement stable**, sans aléa —
`public_id` = nom du fichier. Le commentaire dit pourquoi, et il a raison : ce document est
régénéré à chaque demande d'un prospect et **ne désigne personne**. Celui-là est **réellement
public** et doit **rester chez Cloudinary en `upload`**. Conséquence pour l'inventaire du
§ a de `analyse_doc_priv.md` : la colonne `partnership_requests.convention_file_key` porte
**deux sensibilités** selon le dossier — `partenariats/demandes` est privé, `partenariats/modeles`
est public. L'aiguillage doit se faire sur le `DossierStockage`, pas sur la colonne. C'est le
seul point de l'inventaire à corriger.

### Ce qui survit de DP‑1, mais ailleurs

L'**idée** de DP‑1 est juste et se réemploie telle quelle, deux étages plus haut :

- `typeLivraison(DossierStockage)`, qui choisissait `authenticated` ou `upload` selon
  `TypeRessource`, devient le **choix d'adaptateur** : `DOCUMENT` → S3, `IMAGE` / `VIDEO` →
  Cloudinary.
- La lecture qui essayait deux types devient la lecture qui essaie **deux adaptateurs** : S3
  d'abord, Cloudinary en repli pendant la transition. Même garantie, même absence de fenêtre
  d'illisibilité.
- La correction du commentaire mensonger de `DossierStockage.TypeRessource.DOCUMENT` est à
  reprendre, avec le bon texte cette fois : non plus « `authenticated` est à notre portée »,
  mais « les documents ne sont pas chez Cloudinary ».

### Le sort du commit, concrètement

Ne pas fusionner `1834a49` tel quel : il embarquerait `CloudinaryStorageService.java`.
**Rejouer DP‑4 seul** sur une branche `fix/dp4-cles-hors-navigateur` partie de `main`, en
reprenant les dix-sept fichiers du tableau « garder » et en laissant les deux autres. Comme tout
est dans un commit unique, c'est un `git checkout fix/dp1-dp4-documents-prives -- <les 17
fichiers>`, pas un `cherry-pick`.

**Et l'essai qui te bloquait n'est plus dû.** La commande
`./mvnw -o test -Dtest=CloudinaryAuthenticatedRoundTripTest`, que je te demandais de lancer en
développement avant toute fusion, **devient sans objet** : nous ne déposerons rien en
`authenticated`. Tu peux l'oublier.

### Le module de stockage, en interface

```
                       ┌──────────────────────────────┐
   dépôt / lecture ───►│  StockageService (interface) │
                       └──────────────┬───────────────┘
                                      │ aiguillage sur DossierStockage.TypeRessource
                      ┌───────────────┴────────────────┐
                      ▼                                ▼
       ┌───────────────────────────┐      ┌───────────────────────────────┐
       │ StockagePublicCloudinary  │      │ StockagePriveS3               │
       │ images, vidéos, modèle    │      │ les 9 colonnes de documents   │
       │ vierge de convention      │      │ bucket privé, fr-par, chiffré │
       │ URL publique directe      │      │ présignature 1 min, backend   │
       └───────────────────────────┘      └───────────────────────────────┘
```

`StorageService` existe déjà comme interface (`common/storage/StorageService.java`) et
`CloudinaryStorageService` en est l'unique implémentation. Le travail n'est donc pas d'inventer
l'interface mais de **l'ouvrir à deux implémentations** et d'y ajouter l'aiguillage. C'est ce qui
rend SP‑1 raisonnable à 3 jours : la couture est déjà là.

---

## A.4 Migrer l'existant

**Le principe, inchangé depuis `analyse_doc_priv.md` : à aucun instant un document ne devient
illisible.** Seul le mécanisme change — une copie entre deux stockages au lieu d'un changement
de type sur place.

1. **Le code apprend à lire les deux.** SP‑1 déploie `StockageService` avec aiguillage : les
   **nouveaux** dépôts privés partent chez S3, et la lecture tente **S3 d'abord, Cloudinary en
   repli**. À cet instant rien n'est migré : tout l'existant se lit encore par le repli.
2. **La copie tourne**, par lots (SP‑4). Chaque objet copié et vérifié devient lisible par la
   voie S3 ; le repli couvre ceux qui ne le sont pas encore. **Aucune fenêtre d'illisibilité.**
3. **Le repli est retiré** après une passe de vérification complète (SP‑5). Cloudinary cesse
   d'être une voie de lecture pour les documents.
4. **Et seulement alors, la suppression chez Cloudinary.** Jamais avant : tant que le repli
   existe, Cloudinary est notre filet.

### Le mécanisme de copie, et comment on sait qu'elle est bonne

Pour chaque objet : lecture du flux Cloudinary par URL signée (ce que `download()` fait déjà),
calcul de la **taille** et de l'**empreinte SHA‑256 au vol** pendant l'écriture vers S3, puis
relecture de l'objet S3 et comparaison des deux. Taille **et** empreinte : la taille seule
laisse passer une troncature compensée, l'empreinte seule ne dit rien si le flux a été lu zéro
octet.

La clé de l'objet dans S3 est **le `public_id` Cloudinary, inchangé**. Conséquence : **aucune
écriture en base, aucune migration Flyway.** Les neuf colonnes conservent leurs valeurs, comme
dans l'analyse initiale, et pour la même raison.

### Le journal, la reprise, l'essai à blanc, le retour arrière

- **Essai à blanc** (`dryRun=true`, **valeur par défaut**) : énumère les objets à copier par
  dossier, avec leur nombre et leur volume, **sans rien écrire**. Premier passage obligatoire.
- **Journal** : un fichier `_migration/journal-<date>.jsonl` écrit **dans le bucket S3
  lui-même**, une ligne par objet (dossier, clé **tronquée**, taille, empreinte, issue,
  horodatage). Dans le bucket et non en mémoire : Render redémarre quand il veut, et un journal
  perdu est une reprise impossible. Aucune donnée personnelle, aucune clé complète.
- **Reprise** : le script relit son journal, saute ce qui est déjà copié **et vérifié**, et
  reprend au premier objet qui ne l'est pas. Une copie interrompue en cours d'écriture se refait
  — S3 remplace l'objet, l'opération est donc naturellement rejouable.
- **Retour arrière** : le repli Cloudinary **est** le retour arrière, tant que SP‑5 n'a pas eu
  lieu. Il n'y a rien à défaire : l'original n'a pas bougé. Après SP‑5, le retour arrière est le
  même script en sens inverse, et il faut le vouloir.

### Le modèle d'exécution, sans que le secret sorte de Render

Une **tâche d'administration réservée au `SUPER_ADMIN`**, exposée par le backend lui-même :

```
POST /api/v1/admin/stockage/migration?dryRun=true&dossier=DOSSIERS_PIECES&lot=50
```

Elle tourne **dans le processus Render**. Les identifiants Cloudinary et les clés S3 sont lus
depuis l'environnement Render, qu'ils ne quittent jamais ; tu ne manipules aucun secret. Tu
déclenches la tâche depuis l'écran d'administration, ou par un appel qui ne porte que **ton
propre jeton de session** — jamais une clé de stockage. La réponse rend le compte des objets
traités, copiés, vérifiés, et en échec, avec les clés tronquées des échecs.

C'est la réponse à ta question du 29 octobre, et elle est la même que celle que j'avais donnée
pour DP‑3 : le secret ne sort pas parce que **le code qui en a besoin est déjà là où il est**.

### L'ordre de bascule, par priorité de sensibilité

1. `dossiers-candidats/pieces` et `dossiers-candidats/officiels` — **données personnelles,
   d'abord** : passeport, diplôme, inscription à l'ordre.
2. `dossiers-candidats/convocations`, `dossiers-candidats/souscriptions`.
3. `documents-emis/conventions`, `…/attestations` — nominatifs.
4. `documents-emis/recus`, `…/devis`, `…/releves-reversement` — commercial.
5. `catalogue/imports`, `partenariats/demandes`, `formations/brochures`.

`partenariats/modeles`, `catalogue/produits`, `catalogue/galerie`, `catalogue/videos`,
`formations/images`, `formations/videos`, `blog/couvertures` : **ne bougent pas.**

---

## A.5 Ce que tu as à créer toi-même

Je ne te demanderai la valeur d'aucune clé. Ce qui suit est la liste des objets à créer et le
**nom** des variables à poser sur Render ; les valeurs, tu les colles directement dans
l'interface de Render, et elles ne passent ni par cette conversation ni par un terminal.

### Chez Scaleway

1. **Un compte**, et dans ce compte un **projet** dédié — pas le projet par défaut : un projet
   séparé rend la politique d'accès et la facture lisibles.
2. **Deux buckets**, en région **`fr-par`**, classe **Standard Multi-AZ** :
   - `optimisante-documents` — la production ;
   - `optimisante-documents-dev` — le développement. Deux buckets et **deux clés distinctes** :
     une session de développement ne doit jamais pouvoir écrire dans le bucket de production.
3. Sur **chacun** des deux buckets :
   - visibilité **privée** (c'est le défaut ; à vérifier explicitement) ;
   - **chiffrement par défaut activé** — SSE-ONE / `AES256` suffit, SSE-KMS est inutilement
     complexe à notre échelle. **À activer avant le premier dépôt** : les objets importés avant
     l'activation ne sont pas chiffrés ;
   - **gestion des versions activée** — c'est notre protection contre une suppression
     accidentelle pendant la migration, et elle ne coûte rien tant qu'on ne supprime rien.
4. **Une « application » (identité) par environnement**, avec une **politique minimale** :
   - production : `ObjectRead` **et** `ObjectWrite` sur le bucket `optimisante-documents`
     **seulement**, dans le projet dédié, et **rien d'autre** — ni `BucketDelete`, ni accès aux
     autres services Scaleway ;
   - développement : les mêmes droits sur `optimisante-documents-dev` **seulement**.
5. **Une paire de clés API par application** (clé d'accès + clé secrète). La clé secrète ne
   s'affiche qu'une fois ; range-la dans ton gestionnaire de mots de passe avant de fermer la
   page.

### Sur Render (service backend), en variables d'environnement

| Variable | Valeur | Secret ? |
|---|---|---|
| `STOCKAGE_PRIVE_FOURNISSEUR` | `scaleway` | non |
| `STOCKAGE_PRIVE_ENDPOINT` | `https://s3.fr-par.scw.cloud` | non |
| `STOCKAGE_PRIVE_REGION` | `fr-par` | non |
| `STOCKAGE_PRIVE_BUCKET` | `optimisante-documents` | non |
| `STOCKAGE_PRIVE_PREFIXE` | `optimisante/prod` | non |
| `STOCKAGE_PRIVE_ACCES_CLE` | la clé d'accès de l'application de production | **oui** |
| `STOCKAGE_PRIVE_ACCES_SECRET` | la clé secrète de l'application de production | **oui** |

Rien à changer sur **Vercel** : le frontend ne parle jamais au stockage, et après DP‑4 il n'en
connaît même plus l'existence.

En local, les mêmes noms avec le bucket et la clé de développement, dans ton environnement de
session — jamais dans un fichier du dépôt.

### Un point de vigilance sur le préfixe

`STOCKAGE_PRIVE_PREFIXE` joue le rôle que `root-folder` + `environment` jouent chez Cloudinary.
Il reste **distinct du bucket** parce que deux buckets séparés nous protègent déjà ; le préfixe
sert surtout à ce qu'un objet migré conserve un chemin lisible dans le journal d'accès.

---

## A.6 Durées de conservation

Deux logiques s'opposent, et c'est leur arbitrage qui donne les durées.

- **L'obligation comptable et commerciale** pousse à **garder** : le code de commerce impose la
  conservation des pièces justificatives de comptabilité **dix ans** (art. L123‑22), et le droit
  commun des contrats prescrit à **cinq ans** (art. 2224 du code civil).
- **Le principe de minimisation du RGPD** (art. 5.1.c et 5.1.e) pousse à **jeter** : une donnée
  personnelle ne se garde que le temps nécessaire à la finalité pour laquelle elle a été
  collectée. Une copie de passeport n'a **aucune** finalité comptable.

Ces deux logiques ne portent pas sur les mêmes documents, et c'est ce qui permet de les
satisfaire toutes les deux.

| Document | Dossier | Durée proposée | Fondement |
|---|---|---|---|
| **Reçus, factures, avoirs** | `documents-emis/recus`, `…/devis` | **10 ans** après l'exercice | Pièce comptable — L123‑22. Aucune donnée sensible : nom, montant. |
| **Relevés de reversement** | `documents-emis/releves-reversement` | **10 ans** | Pièce comptable, et preuve de notre dette envers le partenaire. |
| **Conventions de formation** | `documents-emis/conventions` | **10 ans** | Alignée sur le comptable plutôt que sur les 5 ans du civil : la convention **justifie** le produit encaissé, c'est donc une pièce comptable. |
| **Attestations de formation** | `documents-emis/attestations` | **10 ans** | Le médecin peut la redemander des années plus tard, et nous sommes le seul émetteur. |
| **Pièces des candidats** — passeport, diplôme, inscription à l'ordre | `dossiers-candidats/pieces`, `…/officiels` | **3 mois après la clôture du dossier** (fin de parcours, refus, ou annulation) | **Minimisation.** C'est la donnée la plus sensible du projet et elle n'a aucune valeur probante après la clôture : en cas de litige, la convention et le reçu — gardés dix ans — prouvent la transaction ; la copie du passeport ne prouve rien de plus. Les 3 mois couvrent une réclamation immédiate. |
| **Convocations** | `dossiers-candidats/convocations` | **fin de session + 1 an** | Opérationnel. Contient l'adresse précise de l'établissement (décision BR‑5). |
| **Souscriptions** | `dossiers-candidats/souscriptions` | **10 ans** | Attestation de souscription : pièce contractuelle et comptable. |
| **Imports catalogue fournisseur** | `catalogue/imports` | **2 ans** | Rejouer un import, retracer un prix d'achat. Aucune donnée personnelle. |
| **Brochures d'origine des partenaires** | `formations/brochures` | **tant que la formation est publiée, + 1 an** | Document commercial du partenaire. |
| **Demandes de partenariat signées** | `partenariats/demandes` | **5 ans** après la fin de la relation | Contrat — prescription civile. |

**La règle de cycle de vie correspondante**, une par préfixe. Exemple pour les pièces des
candidats, qui est le seul cas délicat :

```
Règle « pieces-candidats-90j »
  Préfixe   : optimisante/prod/dossiers-candidats/pieces/
  Expiration: 90 jours après la date de l'objet
  Versions non courantes : expiration à 7 jours
```

**Et le piège qu'il faut nommer, parce qu'il casserait la plateforme en silence.** Une règle de
cycle de vie supprime **l'objet**, pas la **ligne en base**. Le jour où une pièce expire,
`enrollment_documents.cloudinary_public_id` pointe encore vers un objet qui n'existe plus, et
`DocumentController` répondra une erreur de lecture au médecin sans qu'aucun écran ne l'explique.
Deux conséquences, à tenir dans le même lot :

1. La règle ne se cale **pas sur la date de l'objet** mais sur la **clôture du dossier**, que S3
   ne connaît pas. Le cycle de vie seul ne peut donc pas l'appliquer correctement.
2. C'est **notre tâche planifiée** qui doit décider, et faire les deux gestes ensemble :
   supprimer l'objet **et** vider la colonne, dans la même transaction, avec un marqueur
   `piece_purgee_le` pour que l'écran dise « pièce purgée après la clôture du dossier » au lieu
   d'afficher une erreur.

Le cycle de vie S3 reste utile pour ce qui se date par l'objet : l'expiration des versions non
courantes, l'abandon des téléversements incomplets, et les durées longues des pièces comptables.
Pour les pièces des candidats, c'est notre code qui décide. **C'est un lot à part** (SP‑7), et je
ne le mets pas dans SP‑1.

**À faire valider par ton comptable** : les dix ans sur les conventions (plutôt que cinq), le
point de départ de l'exercice, et le fait que les reçus `REC-` d'aujourd'hui ne sont pas des
factures — ce qui est le sujet du lot ST‑4.

---

## A.7 Brancher `SignatureFichier` sur les routes de dépôt privé

**L'état réel, mesuré.** `SignatureFichier.exigerPdf` n'est appelé qu'**à un seul endroit** :
`ControleFichier.verifierBrochure` (ligne 67), donc par la seule route
`PartnerTrainingResource` (ligne 103). `ControleFichier.verifierDocument` — celle qu'utilisent
`StorageController` (ligne 44) et `OfficialDocumentService` (ligne 288) — vérifie l'extension,
la cohérence du type MIME annoncé et la taille, **mais pas la signature du fichier**. Et neuf
autres routes qui acceptent un `MultipartFile` n'appellent **ni l'une ni l'autre** :
`PartnershipResource`, `CatalogImportService`/`SupplierResource`, `AdminBlogResource`,
`AdminProductMediaResource`, `AiResource`, `EnrollmentDocumentRequestResource`,
`EnrollmentResource`, `OfficialDocumentResource` (par un autre chemin).

Un poste peut donc déposer un exécutable nommé `diplome.pdf` avec un type MIME
`application/pdf`, et il est accepté.

**La cible, par nature de route.** `SignatureFichier` apprend trois signatures de plus et une
méthode qui en accepte une liste :

| Signature | Octets de tête |
|---|---|
| PDF | `25 50 44 46 2D` (`%PDF-`) — déjà connue |
| JPEG | `FF D8 FF` |
| PNG | `89 50 4E 47 0D 0A 1A 0A` |
| XLSX (conteneur ZIP) | `50 4B 03 04` |

Et l'aiguillage se fait sur le `DossierStockage`, pas sur la route — pour la même raison qui a
fait naître l'énumération : un chemin inconnu ne doit plus compiler.

| Dossier | Accepté |
|---|---|
| `dossiers-candidats/pieces`, `…/officiels` | PDF, JPG, PNG |
| `documents-emis/*`, `partenariats/demandes`, `formations/brochures` | PDF seulement |
| `catalogue/imports` | XLSX — et **CSV à part** (voir ci-dessous) |
| `catalogue/*`, `formations/images`, `blog/couvertures` | JPG, PNG (et WEBP si la route l'accepte déjà) |

**Le cas honnête du CSV** : un CSV n'a aucune signature. Pour lui, le contrôle reste
l'extension, la taille, et la **première ligne d'en-tête attendue** — et le code doit le dire en
commentaire plutôt que de laisser croire à une vérification qui n'existe pas.

C'est le lot **SP‑6**, 1 jour. Il est petit, sans migration, et il ferme le point « généraliser
`SignatureFichier` aux 13 autres routes de dépôt » du backlog de sécurité.

---

# B. Feuille de route

## B.1 L'ordre, et pourquoi il tient

```
1. Stockage privé  ──►  2. Catégories + import LCM  ──►  3. Stripe en live  ──►  4. Maquettes
   (SP, 10,5 j)            (CAT, PIL, IMP : 9 j)          (ST, 15,5 j)           (M1→L : 18 j)
```

L'ordre que tu donnes est le bon, et chaque flèche a une raison :

- **Le stockage d'abord** parce que c'est le seul des quatre qui corrige une fuite de données
  personnelles **en cours**, et parce qu'il est le seul dont le coût augmente avec le temps :
  chaque jour ajoute des documents à migrer.
- **Les catégories avant Stripe** parce que l'arborescence est le chemin critique de tout le
  reste (elle bloque M3, M4 et l'import), et parce qu'elle commence par **ton arbitrage** : la
  session te livre le fichier, puis c'est toi qui tiens le planning. Autant que cette attente se
  déroule pendant que le développement avance ailleurs.
- **Stripe avant les maquettes** parce qu'une belle fiche produit sur un catalogue qui n'émet
  pas de facture ne vend rien, et qu'une maquette qui change les écrans de commande pendant
  qu'on réécrit le cycle de vie de la commande fait collision.
- **Les maquettes en dernier** parce qu'elles sont le seul des quatre dont rien ne dépend.

**Deux chevauchements voulus.** `M1` (en-tête et recherche) et `M2` (fiche produit) ne dépendent
ni du stockage ni de Stripe : ils peuvent avancer pendant l'attente de ton arbitrage sur l'arbre.
Et `SP‑4` (la copie) tourne en tâche de fond pendant que `CAT` démarre.

**Total : 53 jours de développement**, hors attentes d'arbitrage et hors les deux chantiers déjà
ouverts qu'il reste à refermer (`docs/doc-priv`, `docs/dette-catalogue`).

---

## B.2 Objectif 1 — stockage privé (lots SP)

| Lot | Objet | Dépend de | Jours |
|---|---|---|---|
| **SP‑0** | Cette analyse | — | fait |
| **DP‑4** | **Rejeu de DP‑4 seul** (§ A.3) : plus aucune clé au navigateur. Branche `fix/dp4-cles-hors-navigateur` depuis `main`. Fermable **tout de suite**, indépendamment du reste. | rien | 0,5 (acquis) |
| **SP‑1** | `StockageService` ouvert à deux adaptateurs ; `StockagePriveS3` (AWS SDK v2, endpoint Scaleway) ; dépôt des neuf dossiers privés vers S3 ; lecture S3 **avec repli Cloudinary**. Aller-retour réel sur le bucket de développement. Tests. | ton compte + bucket + clés (§ A.5) | 3 |
| **SP‑2** | Correction du commentaire de `DossierStockage`, et de l'inventaire : `partenariats/modeles` reste public (§ A.3). | SP‑1 | 0,5 |
| **SP‑3** | Script de copie : essai à blanc par défaut, par lots, journal dans le bucket, reprise, vérification taille + SHA‑256. Tâche `SUPER_ADMIN`. | SP‑1 | 2,5 |
| **SP‑4** | **Exécution**, pièces des candidats d'abord (§ A.4). Sauvegarde de la base avant, et ton accord explicite. | SP‑3 | 1 |
| **SP‑5** | Retrait du repli Cloudinary, puis suppression des objets documents chez Cloudinary. | SP‑4 vérifié | 1 |
| **SP‑6** | `SignatureFichier` sur toutes les routes de dépôt (§ A.7). | rien | 1 |
| **SP‑7** | Durées de conservation : règles de cycle de vie **et** tâche de purge qui vide la colonne (§ A.6). | SP‑5, validation comptable | 1 |

**Total SP : 10,5 jours**, dont 0,5 déjà écrit.

**Ce que SP ne fait pas** : il ne touche pas aux images publiques, ni au calcul des prix, ni au
paiement, ni aux circuits de validation. Aucune migration Flyway — les neuf colonnes gardent
leurs valeurs (§ A.4). **Une seule exception possible** : SP‑7 a besoin d'une colonne
`piece_purgee_le` ; c'est la seule migration de tout l'objectif 1, et elle est argumentée par le
piège du § A.6.

---

## B.3 Objectif 2 — arborescence puis import LCM

Inchangé par rapport à `plan_integration_catalogue.md`, dont les lots `CAT` et `PIL` restent
valables mot pour mot. Rappel du périmètre et des dépendances :

| Lot | Objet | Dépend de | Jours |
|---|---|---|---|
| **M0** | Cadrage — dont la requête de contrôle des visuels factices | rien | 1 |
| **CAT** | Arborescence cible, filtrage par descendance, redirections 301, route d'arbre en cache | **ton arbitrage** (fichier `docs/arborescence_catalogue_v2.xlsx`) | 6–8 |
| **PIL** | Import pilote LCM | CAT + marge fixée | 0,5 |
| **IMP** | **Import complet, 7 344 lignes** — nouveau lot, qui n'existait pas dans le plan | PIL vert + décisions 7, 8, 9 | 1,5 |

**Total : 9 à 11 jours.** `IMP` était fondu dans PIL ; il en sort, parce qu'un pilote de
50 lignes et un import de 7 344 ne posent pas les mêmes questions — le second se mesure en durée
de transaction, en volume d'images manquantes et en produits à désactiver.

---

## B.4 Objectif 3 — Stripe en mode live (lots ST)

### Ce qui manque avant d'encaisser un vrai paiement

Mesuré dans le code, pas supposé.

| Manque | Ce que dit le code | Gravité |
|---|---|---|
| **Cycle de vie des commandes** | `OrderStatus` = `PENDING, PROCESSING, SHIPPED, VALIDATED, REJECTED, CANCELLED`. **Il n'y a ni `PAID` ni `DELIVERED`.** Une commande payée par carte reste `PENDING` ; aucune règle de transition n'existe ; le client ne voit jamais `status`. | **bloquant** |
| **Facturation** | Aucune facture. Des reçus `REC-` à numérotation sans trou, et **aucun avoir**. Un reçu n'est pas une facture : il ne porte pas les mentions obligatoires et ne permet pas à un hôpital de récupérer la TVA. | **bloquant** |
| **TVA** | `vatRate` existe par catégorie **et** par produit, avec héritage, et un interrupteur `TVA_ACTIVE` qui n'ouvre que la **ventilation sur les documents**. **Mais rien ne tient compte de la destination.** Voir ci-dessous : le besoin n'est pas celui que tu décris. | **bloquant** |
| **Lot B du stock** | Pas de remise en stock à l'annulation ni au remboursement ; `paid_at` / `shipped_at` absents ; aucun journal des ajustements manuels. | **bloquant avant l'inventaire** |
| **Webhook `charge.refunded`** | Le code **existe et est complet** (`PaymentWebhookResource.traiterRemboursement` → `StripeRefundHandler`). Il n'a **jamais été testé de bout en bout**. | **à tester, pas à écrire** |
| **Acompte d'inscription** | Le code est correct de bout en bout ; le blocage est opérationnel, et le diagnostic **attend encore** ta requête SQL et la page *Events* de Stripe. | **bloquant** |
| **Commandes de test** | Des ventes fictives sont en base, et aucune n'est réelle. Elles faussent le chiffre d'affaires du jour où les vraies arrivent. | **bloquant** |
| **Clés live** | Clés de test sur Render et Vercel ; aucun point d'entrée de webhook live déclaré chez Stripe. | **dernière étape** |

### Une correction sur la TVA, et elle simplifie le travail

Tu demandes « la TVA par pays de livraison ». **Ce n'est pas le besoin**, et c'est une bonne
nouvelle. `ZoneLivraison` dit où nous livrons : **France métropolitaine**, Afrique du Nord,
Afrique de l'Ouest et centrale, Afrique de l'Est et australe. Un pays absent n'est pas livré.

Il n'y a donc **aucun autre pays de l'Union européenne** dans nos destinations. Le régime du
guichet unique de TVA (OSS) et ses vingt-sept taux ne nous concernent pas du tout. La règle
réelle est beaucoup plus courte :

- **France** : le taux du produit, tel que `vatRate` le donne déjà — 20 % en général, 5,5 % pour
  les dispositifs médicaux qui y ouvrent droit. **Le mécanisme existe** ; il reste à vérifier les
  taux rayon par rayon.
- **Toute autre zone** : **exportation hors UE, exonérée** (art. 262‑I du CGI), avec la mention
  obligatoire sur la facture et la **conservation du justificatif de sortie**.

Ce qui manque n'est donc pas un moteur de TVA multi-pays, mais : un **booléen « exportation »**
déduit de la zone, la **mention d'exonération** sur la facture, et le **rangement du justificatif
de sortie** — qui est précisément un document privé, et donc un client de l'objectif 1.
`ST‑5` passe de « 5 jours de moteur fiscal » à **2 jours**.

### Les lots

| Lot | Objet | Dépend de | Jours |
|---|---|---|---|
| **ST‑0** | **Neutralisation des commandes de test** et purge des ventes fictives. Requête d'inventaire en lecture seule d'abord, puis script `ON_ERROR_STOP=1` qui compte les lignes et `RAISE EXCEPTION` si le compte n'est pas celui attendu. Sauvegarde avant. | ton accord | 1 |
| **ST‑1** | **Cycle de vie des commandes** : `PAID` et `DELIVERED` ajoutés, règles de transition explicites, `paid_at` / `shipped_at` / `delivered_at`, statut rendu au client, écran d'administration. Migration Flyway. | rien | 3 |
| **ST‑2** | **Lot B du stock** : remise en stock à l'annulation et au remboursement, journal des ajustements manuels. | ST‑1 | 2,5 |
| **ST‑3** | **Test du webhook `charge.refunded`** de bout en bout en mode test, remboursement déclenché depuis le tableau de bord Stripe, et vérification que la commande change d'état et sort du chiffre d'affaires. | ST‑1, ST‑2 | 0,5 |
| **ST‑4** | **Factures et avoirs** : série `FA-` à numérotation sans trou, mentions obligatoires, avoirs `AV-` adossés à une facture, PDF rangé en stockage **privé**. | ST‑1, **SP‑1** | 4 |
| **ST‑5** | **TVA** : taux vérifiés rayon par rayon, exonération à l'export avec mention, justificatif de sortie rangé. | ST‑4 | 2 |
| **ST‑6** | **Acompte d'inscription** : correction du blocage, puis **confirmation manuelle par l'administration**, vérifiée auprès de Stripe avant d'être acceptée — l'admin ne déclare pas « payé », il demande au serveur de **relire le `PaymentIntent` chez Stripe** et n'avance le dossier que si Stripe le confirme. Journal de qui a confirmé quoi. | le diagnostic (SQL + *Events*) | 2 |
| **ST‑7** | **Passage en live** : clés live sur Render et Vercel, **nouveau** point d'entrée de webhook live chez Stripe avec son propre secret, vérification par un paiement réel de faible montant, puis remboursement. | tous les précédents | 0,5 |

**Total ST : 15,5 jours.**

**Deux dépendances croisées à ne pas manquer** : `ST‑4` a besoin de `SP‑1`, parce qu'une facture
nominative déposée dans un stockage public serait exactement l'erreur qu'on vient de corriger.
Et `ST‑7` a besoin de `ST‑0` : passer en live sur une base qui contient des ventes fictives rend
le premier relevé de chiffre d'affaires illisible.

**Sur `ST‑6`, un rappel** : je n'ai pas encore le résultat de la requête SQL sur le dossier
`0BFDD667` ni ce que montre la page *Events* de Stripe. Le code est correct de bout en bout —
le dispatcher reconnaît bien une session d'acompte par `payment_type=TUITION_FEE`, et les deux
événements abonnés suffisent. Les deux éléments que j'attends tranchent entre « la session ne se
termine pas » et « le webhook échoue à l'arrivée », et l'estimation de 2 jours tombera peut-être
à une demi-journée. Je ne l'ai pas revue à la baisse par prudence.

---

## B.5 Objectif 4 — les dix écrans des maquettes

**Précision retenue, et elle resserre utilement le périmètre** : « maquettes » désigne
exactement les **dix fichiers** de `docs/maquette_catalogue_optimisante/maquette_catalogue/`.
Aucune autre page n'est concernée — ni l'accueil, ni le panier, ni le tableau de bord.

### Quel lot livre quel écran

| # | Écran | Fichier | Lot qui le livre |
|---|---|---|---|
| 1 | Page Catalogue — ordinateur | `Main.dc.html` | **M3a** |
| 2 | Méga-menu « Catalogue » | `MegaMenu.dc.html` | **M3a** |
| 3 | Catalogue — mobile | `CatalogueMobile.dc.html` | **M3a** |
| 4 | Page famille — ordinateur | `Famille.dc.html` | **M3b** |
| 5 | Page rayon — ordinateur | `Rayon.dc.html` | **M3b** |
| 6 | Famille — mobile | `FamilleMobile.dc.html` | **M3b** |
| 7 | Rayon — mobile | `RayonMobile.dc.html` | **M3b** |
| 8 | Panneau « Filtrer » — mobile | `FiltresMobile.dc.html` | **M3b** |
| 9 | Fiche produit — ordinateur | `Produit.dc.html` | **M2** |
| 10 | Fiche produit — mobile | `ProduitMobile.dc.html` | **M2** |

**Aucun écran n'est orphelin.** Les dix sont attribués, et chacun à un seul lot.

### Mais la vérification en révèle deux choses, et il faut les dire

**1. `M3` portait sept écrans sur dix. C'est trop pour un lot, et je le coupe en deux.**

Sept écrans, 7 jours et 55 Ko dans un seul lot, c'est un lot qu'on ne peut ni livrer à moitié ni
vérifier proprement : la première capture arrive au septième jour. La coupure suit une frontière
réelle — **l'entrée dans le catalogue** d'un côté, **la navigation dans l'arbre** de l'autre :

| Lot | Écrans | Jours | JS |
|---|---|---|---|
| **M3a** — entrée du catalogue | Méga-menu, page Catalogue, Catalogue mobile | 3 | 25 Ko |
| **M3b** — navigation dans l'arbre | Famille, Rayon, leurs versions mobiles, panneau Filtrer | 4 | 30 Ko |

M3a se vérifie seul : le méga-menu et la page Catalogue n'ont besoin que de la route d'arbre que
`CAT` livre. M3b a besoin en plus du filtrage par descendance — c'est-à-dire du **cœur** de
`CAT`. La coupure sépare donc aussi deux niveaux de dépendance, ce qui est le signe qu'elle est
au bon endroit.

**2. Quatre lots ne livrent aucun écran, et le plan ne le disait pas.**

`M1`, `M4`, `M2-bis` et `L` ne correspondent à **aucun** des dix fichiers : ce sont des bandes et
des blocs **à l'intérieur** d'écrans livrés par `M2` et `M3`.

| Lot | Ce qu'il livre, et sur quels écrans |
|---|---|
| **M1** | l'en-tête et la barre de recherche — présentes sur les **six** écrans de M3a et M3b |
| **M4** | le filtre marque sur Famille et Rayon, le libellé de marque sur la fiche produit |
| **M2-bis** | les onglets caractéristiques, documents et garantie **dans** la fiche produit |
| **L** | la ligne de délai de livraison **dans** la fiche produit |

Ce n'est pas un défaut du découpage — un en-tête partagé est bien un lot à part. Mais cela a une
conséquence pratique qu'aucun prompt ne disait : **la vérification de `M1`, `M4`, `M2-bis` et `L`
se fait sur les captures des écrans de `M2` et `M3`**, pas sur une page à eux. Un rapport de fin
de lot pour `M1` qui montrerait une page isolée ne prouverait rien. À ajouter aux quatre prompts.

### Les lots de l'objectif 4

| Lot | Objet | Dépend de | Jours | JS |
|---|---|---|---|---|
| **M1** | En-tête et barre de recherche | M0 | 1,5 | 10 Ko |
| **M2** | Fiche produit (écrans 9 et 10) | M1 | 4 | 40 Ko |
| **M3a** | Méga-menu, page Catalogue (écrans 1 à 3) | CAT | 3 | 25 Ko |
| **M3b** | Famille, rayon, filtres (écrans 4 à 8) | CAT | 4 | 30 Ko |
| **M4** | Marque | M3b, CAT | 2,5 | 12 Ko |
| **M2-bis** | Caractéristiques, documents, garantie | M2, **SP‑1** | 3 | 25 Ko |
| **L** | Délais de livraison par zone | tes délais | 1,5 | 6 Ko |

**Total : 19,5 jours, 148 Ko.**

**Une dépendance nouvelle** : `M2-bis` dépose des **documents produit** (notices, certificats de
conformité). Ils ne sont pas tous publics — un certificat porte parfois des coordonnées
fournisseur. `M2-bis` dépose donc par `StockageService` et dépend de `SP‑1`. Le plan initial ne
le voyait pas.

---

## B.6 Le budget de poids, et un écart d'arithmétique à trancher

Mesuré à l'instant sur `main` (`40d7ef3`), après la fusion du retrait du bandeau :
**le JavaScript produit pèse 1 289 Ko**, pour un plafond de charte de **1 500 Ko**. Le marge
disponible est donc de **211 Ko**.

Tu donnes **183 Ko**. Je ne sais pas d'où vient l'écart de 28 Ko — peut-être une mesure prise
avant une fusion, peut-être une réserve que tu gardes volontairement. **Je planifie à 183 Ko**,
comme tu le demandes, et les 28 Ko restants deviennent une réserve que **aucun lot ne peut
demander**. Si l'écart était une erreur de ma part, dis-le-moi : le plan a de la marge, il n'en
manque pas.

| Lot | Plafond | Ce qu'il paie |
|---|---|---|
| CAT | 10 Ko | écran d'administration de l'arbre |
| M1 | 10 Ko | sélecteur de catégorie, formulaire de recherche |
| M2 | 40 Ko | galerie à balayer, onglets accessibles, barre d'achat fixe, sélecteur de pays |
| M3a | 25 Ko | méga-menu chargé à la demande, page Catalogue |
| M3b | 30 Ko | pages famille et rayon, panneau de filtres mobile |
| M4 | 12 Ko | filtre marque, logos |
| M2-bis | 25 Ko | éditeur clé-valeur, dépôt de documents |
| L | 6 Ko | affichage du délai, saisie dans l'écran des tarifs |
| **Réserve attribuable** | **25 Ko** | imprévus, et de quoi refuser un dépassement sans bloquer un lot |
| **Réserve intouchable** | **28 Ko** | l'écart entre ma mesure et ton chiffre, mis de côté |

**Total attribué : 158 Ko + 25 Ko de réserve = 183 Ko.** Les lots SP et ST ne consomment rien
côté navigateur, sauf `ST‑1` (affichage du statut de commande au client) et `ST‑4` (bouton de
téléchargement de facture) : **4 Ko**, pris sur la réserve attribuable, qui tombe à 21 Ko.

Les règles de tenue du budget ne changent pas : un lot qui dépasse s'arrête et le signale, un lot
qui finit sous son plafond rend la différence, le poids se **mesure** et ne s'estime jamais. Et
augmenter `plafondJsKo` n'est pas une option — c'est le garde-fou, pas un paramètre.

---

## B.7 Ce qu'il te reste à faire, dans l'ordre

1. **Vérifier la région Cloudinary** dans la console (§ A.1) — deux minutes, et cela confirme ou
   corrige le premier argument de tout ce document.
2. **Arbitrer le choix du fournisseur** (§ A.2). Je recommande Scaleway `fr-par`.
3. **Créer le compte, les deux buckets, les deux applications et les clés** (§ A.5), puis poser
   les sept variables sur Render. `SP‑1` ne peut pas commencer avant.
4. **Trancher le sort de `fix/dp1-dp4-documents-prives`** (§ A.3) : je recommande de rejouer
   DP‑4 seul et d'abandonner DP‑1. L'essai `CloudinaryAuthenticatedRoundTripTest` que je te
   demandais n'est plus dû.
5. **Me donner le résultat de la requête SQL et de la page *Events* de Stripe** pour clore le
   diagnostic de l'acompte (`ST‑6`).
6. **Faire valider les durées de conservation** par ton comptable (§ A.6), en particulier les dix
   ans sur les conventions.
7. **Confirmer ou corriger les 183 Ko** (§ B.6).

Et trois branches attendent toujours une décision de fusion : `docs/doc-priv` (l'analyse
DOC-PRIV, désormais à lire avec le correctif du § A.3), `docs/dette-catalogue` (D0, le plan et
les prompts), `docs/backlog-securite`.

---

## B.8 Journal des écarts avec ce que tu demandais

Pour mémoire, et pour qu'aucune session ne les redécouvre.

| Ce que demandait la consigne | Ce que dit le code ou la documentation |
|---|---|
| « les 9 colonnes de l'analyse DOC-PRIV » | Neuf **sur douze**, oui — mais l'une d'elles, `partnership_requests.convention_file_key`, porte **deux sensibilités** : `partenariats/demandes` est privé, `partenariats/modeles` est un modèle vierge volontairement public, à clé stable, qui doit **rester** chez Cloudinary (§ A.3). |
| « vérifie la région Cloudinary dans la configuration » | **Aucun paramètre de région n'existe**, ni chez nous ni dans le SDK. La région est une propriété du compte, et l'offre gratuite ne permet pas de la choisir (§ A.1). |
| « le sort des commits déjà faits » | Le commit est **unique** et mêle les deux lots. Le partage se fait **par fichier** : deux fichiers pour DP‑1, dix-sept pour DP‑4 (§ A.3). |
| « le module de stockage doit être une interface » | `StorageService` **est déjà** une interface ; il n'y a qu'une implémentation. Le travail est d'en ouvrir une seconde et d'ajouter l'aiguillage, pas de créer l'abstraction. |
| « TVA par pays de livraison » | **Aucun pays de l'UE hors France n'est livré** (`ZoneLivraison`). Pas de guichet unique, pas de vingt-sept taux : France au taux du produit, reste **exonéré à l'export**. `ST‑5` passe de 5 jours à 2 (§ B.4). |
| « test du webhook `charge.refunded` » | Le code **existe et est complet**. Ce qui manque est le **test**, pas le développement (§ B.4). |
| « budget JS réel (183 Ko) » | Mesuré : **1 289 Ko sur 1 500**, soit **211 Ko** disponibles. Je planifie à 183 et mets 28 Ko de côté (§ B.6). |
| « les 10 écrans, vérifie qu'aucun n'est orphelin » | Aucun orphelin. Mais **`M3` en portait sept** — coupé en `M3a` / `M3b` — et **quatre lots n'en livrent aucun** : ils se vérifient sur les captures des écrans d'autres lots, ce qu'aucun prompt ne disait (§ B.5). |
| « plan d'implémentation à mettre à jour » | `docs/plan_integration_catalogue.md` n'est **pas sur `main`** : il vit sur `docs/dette-catalogue`, jamais fusionnée. Cette branche en part, pour pouvoir le modifier. |
| Lot d'import complet LCM | Il était **fondu dans PIL**. Il en sort : `IMP`, 1,5 jour — 7 344 lignes ne posent pas les questions d'un pilote de 50 (§ B.3). |
| Documents produit de `M2-bis` | Ils ne sont **pas tous publics** : `M2-bis` dépose par `StockageService` et dépend donc de `SP‑1`. Dépendance nouvelle (§ B.5). |
| Durées de conservation « par type » | La règle de cycle de vie S3 **ne suffit pas** pour les pièces des candidats : elle se cale sur la date de l'objet, pas sur la clôture du dossier, et supprimer l'objet sans vider la colonne casse l'écran en silence. D'où `SP‑7` à part (§ A.6). |
| `SignatureFichier` « sur les routes de dépôt » | Il n'est appelé **qu'à un seul endroit**. `verifierDocument` ne vérifie **aucune** signature, et **neuf** routes de dépôt n'appellent ni `verifierDocument` ni `verifierBrochure` (§ A.7). |

---

*Aucune ligne de code, aucune migration et aucun déploiement n'accompagnent ce document. Les
contraintes de `CLAUDE.md` puis de D0 s'appliqueront à chaque lot : branche dédiée avant le
premier commit, numéro de migration libre **au moment de la fusion**, accord explicite avant tout
déploiement, sauvegarde de la base avant toute opération de masse, `-e URLBASE` sans valeur et
`-v ON_ERROR_STOP=1` sur tout script qui écrit, et rapport de fin de lot **avec le nombre de
tests ignorés**.*
