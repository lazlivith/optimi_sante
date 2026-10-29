# DOC-PRIV — rendre privés les documents non publics (analyse, aucun code)

Écrit pour : Mounir, pour décision avant développement ; et pour la session qui réalisera les
lots. Lot **prioritaire, avant BR-2**.

Mesuré dans le code le 29 octobre 2026. **Aucune exécution** : pas d'appel en écriture à l'API
Cloudinary, pas d'écriture en base, aucun test sur des fichiers réels.

## 0. Le fait établi

Le test de l'étape 0, en production, sur un reçu de commande de test :

```
raw/upload/<clé>        → 200
image/upload/<clé>      → 404
image/upload/<clé>.pdf  → 404
```

**Les documents sont publics pour qui connaît l'adresse.** Le 200 sur `raw/upload/<clé>`
confirme que l'objet Cloudinary se sert sans signature et sans expiration. Notre contrôle de
rôle (§ a) ne protège que **notre** couche : il ne protège pas l'objet. Qui obtient un
`public_id` — par un DTO qui le livre, un journal, une URL à jeton partagée, ou l'énumération
d'une clé prévisible — télécharge le fichier directement, **en contournant tout contrôle, et
pour toujours**.

Ce n'est pas propre aux brochures. C'est vrai de **tous** les documents déposés en `raw` :
reçus, conventions, relevés de reversement, et **les pièces d'identité des candidats**.

---

## a) Inventaire des douze colonnes de clés

`resource_type` et `type` sont déterminés par le `DossierStockage` de dépôt
(`CloudinaryStorageService`). Les dossiers `DOCUMENT` partent en `raw` + `upload` ; `IMAGE` et
`VIDEO` en `image`/`video` + `upload`. **Tout est en `upload` (public) aujourd'hui.**

| Colonne | Dossier | resource/type | Sensibilité |
|---|---|---|---|
| `enrollment_documents.cloudinary_public_id` | `dossiers-candidats/pieces` | raw / upload | **Personnel — pièces du candidat** (diplôme, passeport, inscription à l'ordre) |
| `enrollment_official_documents.storage_key` | `dossiers-candidats/officiels` | raw / upload | **Personnel — officiels du candidat** |
| `enrollments.convention_s3_key` | `documents-emis/conventions` | raw / upload | **Personnel + commercial** (convention nominative) |
| `enrollments.attestation_s3_key` | `documents-emis/attestations` | raw / upload | **Personnel** (attestation nominative) |
| `orders.document_s3_key` | `documents-emis/recus` · `…/devis` | raw / upload | **Commercial + personnel** (nom, montant) |
| `partner_payouts.statement_s3_key` | `documents-emis/releves-reversement` | raw / upload | **Commercial** (finances du partenaire) |
| `partnership_requests.convention_file_key` | `partenariats/demandes` · `…/modeles` | raw / upload | **Commercial + personnel** |
| `catalog_imports.storage_key` | `catalogue/imports` | raw / upload | **Commercial** (tarifs fournisseur) |
| `trainings.brochure_s3_key` | `formations/brochures` | raw / upload | **Commercial** (coordonnées du partenaire ; plus servi publiquement depuis BR-1, mais les fichiers déposés restent publics) |
| `product_gallery_images.public_id` | `catalogue/galerie` | image / upload | **Public voulu** |
| `trainings.image_s3_key` | `formations/images` | image / upload | **Public voulu** |
| `trainings.video_s3_key` | `formations/videos` | video / upload | **Public voulu** |

**Neuf colonnes sur douze sont non publiques** et déposées en `raw/upload`. Trois (galerie,
image et vidéo de formation) sont publiques à dessein et **ne changent pas**. S'y ajoutent, hors
de cette liste mais de même nature publique : les visuels produits (`catalogue/produits`) et les
couvertures de blog (`blog/couvertures`).

### Les routes et réponses qui livrent une clé ou une URL au navigateur

1. **`GET /api/v1/documents/{type}/{id}/download`** (`DocumentController`) — **le bon modèle.**
   Authentification exigée, puis **contrôle de rôle par type**, branche par branche :
   - reçus/devis (`ORDER_TYPES`) : le propriétaire de la commande, ou `ADMIN_ECOMMERCE` /
     `ADMIN` / `SUPER_ADMIN` ;
   - convention, attestation : le médecin propriétaire, le partenaire de la formation, ou
     `ADMIN_MOBILITE` ;
   - relevé de reversement : le partenaire bénéficiaire, ou la mobilité ;
   - document officiel : la règle du coffre-fort (verrou tant que l'acompte/solde n'est pas
     réglé) ;
   - pièces du dossier (`ENROLLMENT_DOCUMENT_TYPES`) : le médecin propriétaire, ou le partenaire
     **seulement** si `DocumentType.isVisibleToPartner()`, ou la mobilité.

   La réponse est une **URL à jeton vers notre backend** (`/api/v1/documents/file/<jeton>`,
   60 min), **jamais** l'URL Cloudinary. C'est bien — mais le jeton n'est qu'une porte devant
   une pièce dont le mur de derrière est ouvert.

2. **`POST /api/v1/storage/upload`** (`StorageController`) — **fuite directe.** Rend
   `{"publicId": "<clé>"}` **au navigateur** de qui dépose. Quand un candidat téléverse son
   passeport, son navigateur reçoit le `public_id` d'un objet public : une URL permanente vers
   son passeport, qui transite, se stocke, et se rejoue.

3. **`OrderResponseDto.documentS3Key`** — la **clé brute** du reçu, sérialisée au propriétaire
   de la commande. Il apprend le `public_id` et peut court-circuiter le jeton de 60 min.

4. **`EnrollmentDetailDto`** — expose `conventionS3Key`, `attestationS3Key`, et surtout
   `diplomaUrl`, `passportUrl`, `medicalBoardRegistrationUrl` (champs hérités de l'entité
   `Enrollment`). Servie par `GET /api/v1/enrollments/{id}` et sa variante admin, au médecin
   propriétaire et à la mobilité.

5. **`safeMediaUrl` → `generateMediaUrl`** — construit une **URL Cloudinary publique directe**
   (`cloudinary.url().type("upload")`) pour images et vidéos. **Correct** : ce sont les
   ressources publiques voulues. À ne pas toucher.

### Les pièces des candidats suivent-elles le chemin des reçus ? — Oui, confirmé par le code

- Reçu : `PaymentReceiptIssuer` → `storageService.uploadGeneratedPdf(…, DOCUMENTS_RECUS)`.
- Pièce officielle du candidat : `OfficialDocumentService` →
  `storageService.uploadFile(file, DOSSIERS_OFFICIELS)`.
- Pièce du dossier : `StorageController` → `uploadFile(file, DOSSIERS_PIECES)`.

Les trois appellent le **même** `uploadFile`/`uploadGeneratedPdf`, qui fixe en dur
`resource_type: raw, type: upload`. **Aucune distinction de sensibilité à l'upload.** Le
passeport d'un candidat est déposé exactement comme un reçu de test : public.

---

## b) La cible

Les documents **non publics** (les neuf colonnes `raw`) sont déposés en **`type:
authenticated`**. Un objet `authenticated` ne se sert **que** par URL signée — la lecture
anonyme répond 401, l'énumération ne donne plus rien.

- Servis **uniquement par notre backend**, après le contrôle de rôle **déjà en place**
  (`DocumentController`), via une **URL signée à courte durée** générée côté serveur.
- La signature cesse d'être cosmétique : aujourd'hui elle protège notre adresse ; demain elle
  est la **seule** clé de l'objet.
- Les **images publiques** (produits, galerie, blog, image et vidéo de formation) **ne changent
  pas** : elles restent `upload`, servies en direct.

Vérifié au § 0 de `analyse_brochures_formation.md` et confirmé par la documentation Cloudinary :
le mode `authenticated` par **URL signée** fonctionne sur le compte actuel ; seules les
authentifications **par jeton** et **par cookie** exigent l'offre Advanced. Le refus « deny »
rencontré venait du réglage « PDF and ZIP files delivery », pas du mode `authenticated`.

---

## c) Migrer l'existant

**Le mécanisme.** La méthode **`rename`** de l'**Upload API** (et non l'Admin API — l'Admin API
ne documente pas `rename`) accepte `to_type`. En passant `from_public_id` **égal à**
`to_public_id` et `to_type: authenticated`, on change le **type de livraison sans changer le
`public_id`**.

> Source : [Upload API Reference — rename](https://cloudinary.com/documentation/image_upload_api_reference) ·
> [Media Access Control](https://cloudinary.com/documentation/control_access_to_media) ·
> réponse communautaire sur `to_public_id == from_public_id` + `to_type: authenticated`.

**Le `public_id` ne change pas → aucune écriture en base.** Les neuf colonnes conservent leurs
valeurs. Seule **l'URL** change (`/upload/` → `/authenticated/`), et c'est le **code** qui la
reconstruit, pas la base. L'ancienne URL `upload` cesse de fonctionner (des copies de cache CDN
peuvent subsister un temps).

**Pas de traitement en masse côté Cloudinary** : `rename` agit un objet à la fois. D'où un
script, par lots.

**L'ordre exact, pour qu'aucun document ne devienne illisible pendant la bascule** — c'est le
cœur de la sûreté de l'opération :

1. **Le code apprend à lire en `authenticated`, avec repli en `upload`.** On déploie d'abord un
   `DocumentLinkService` qui tente la signature `authenticated` et, si l'objet répond encore en
   `upload`, retombe dessus. À cet instant, rien n'est migré : tout se lit encore en `upload`
   par le repli.
2. **La migration tourne**, par lots. Chaque objet basculé devient lisible par la voie
   `authenticated` ; le repli couvre ceux qui ne le sont pas encore. **Aucune fenêtre
   d'illisibilité.**
3. **On retire le repli** une fois la migration complète et vérifiée. `upload` cesse d'être une
   voie de lecture ; la fuite est fermée.

**Le script, exigences :**
- **Mode d'essai** (`--dry-run`) : liste les objets à basculer, par dossier, **sans rien
  modifier**. Premier passage obligatoire.
- **Par lots, journalisé** : chaque objet traité est inscrit (clé tronquée, dossier, issue), pour
  la reprise et l'audit. Aucun `public_id` complet ni donnée personnelle dans le journal.
- **Reprise après interruption** : le script relit son journal et saute ce qui est déjà
  `authenticated` — `rename` d'un objet déjà basculé est idempotent ou ignoré.
- **Retour arrière** : le même script avec `to_type: upload` rebascule. Possible tant que le
  repli `upload` n'est pas retiré (étape 3) ; après, un retour arrière rouvrirait la fuite et ne
  doit se faire qu'en connaissance de cause.

Les identifiants Cloudinary sont lus par la commande depuis l'environnement, **jamais en clair**.

---

## d) Les accès `raw` passés sont-ils visibles ?

**À vérifier dans le tableau de bord Cloudinary, je ne peux pas le lire depuis le code.** Deux
endroits :
- **Reports → Media delivery / Access logs** : les journaux d'accès détaillés (quelle URL,
  quelle IP, quand) ne sont disponibles qu'à partir de certaines offres ; sur une offre standard
  ou gratuite, on n'a que des **agrégats de bande passante et de requêtes**, pas le détail par
  objet.
- **Les statistiques d'usage** : un pic de requêtes `raw` inexpliqué serait un signal, mais ne
  nomme pas les objets.

**Ce qu'il faut regarder** : si l'offre expose les journaux d'accès, filtrer sur le type `raw` et
le dossier `documents-emis/recus` et `dossiers-candidats/` sur les 90 derniers jours. Un accès
depuis une IP hors de Render/Vercel/Cloudinary serait un accès extérieur. **À défaut de journaux,
on ne peut pas affirmer qu'il n'y a pas eu d'accès** — et c'est une raison de plus de migrer vite
les pièces des candidats.

---

## e) Conséquence pour BR-2

**Oui : la brochure d'origine peut aller en `authenticated`, et c'est ma recommandation —
abandonner la table en base.**

Raisons, dans l'ordre :
1. **Une seule mécanique de confidentialité** pour tout le projet, au lieu d'un stockage
   d'exception pour les seules brochures. Moins de code, moins de chemins à sécuriser.
2. **BR-2 ne dépose plus un cas particulier** : la brochure suit `DOSSIERS_*`/`DOCUMENTS_*` en
   `authenticated`, comme les reçus et les pièces après DOC-PRIV.
3. **La table en base** alourdissait les sauvegardes et dupliquait une capacité que Cloudinary
   offre déjà, une fois DOC-PRIV fait.

**Condition** : DOC-PRIV (au moins l'étape 1 du code et la bascule) doit précéder le dépôt de
brochure de BR-2. D'où la priorité de DOC-PRIV **avant BR-2**, qui est bien l'ordre demandé.
La décision 2 de `analyse_brochures_formation.md` est donc tranchée **en faveur de
`authenticated`**, sous réserve de ton accord.

---

## f) Découpage, estimation, risques

| Lot | Objet | Jours | Priorité |
|---|---|---|---|
| **DP-0** | Cette analyse | fait | — |
| **DP-1** | **Lecture `authenticated` avec repli `upload`** dans `DocumentLinkService` / `CloudinaryStorageService`, et **dépôt en `authenticated`** pour les neuf dossiers non publics. Tests. | 2,5 | — |
| **DP-2** | **Script de migration** (`rename` + `to_type`), dry-run, par lots, journalisé, reprise, retour arrière. | 2 | — |
| **DP-3** | **Bascule des pièces des candidats d'abord** (`dossiers-candidats/*`), puis conventions, attestations, reçus, devis, relevés, imports, demandes de partenariat. | 1 | **la plus haute** |
| **DP-4** | **Fermer les fuites de clés** : `StorageController` ne rend plus le `public_id` au navigateur ; `OrderResponseDto.documentS3Key` et les champs clé d'`EnrollmentDetailDto` retirés ou remplacés par l'URL à jeton. | 1,5 | haute |
| **DP-5** | **Retrait du repli `upload`** après vérification complète. | 0,5 | — |

**Total : 7,5 jours.**

**L'ordre entre les lots importe** : DP-1 (le code lit `authenticated` avec repli) **avant** DP-3
(la bascule), sinon un objet basculé devient illisible. DP-4 peut partir en parallèle de DP-1 :
retirer les clés des réponses ne dépend pas de la bascule.

**Priorité aux pièces des candidats** : ce sont des données personnelles (passeport, diplôme,
inscription à l'ordre) relevant du RGPD. DP-3 les bascule **en premier**, et DP-4 coupe en
priorité la fuite de `StorageController` qui rend leur `public_id` au navigateur.

**Risques :**
1. **Fenêtre d'illisibilité** si l'ordre DP-1 → DP-3 → DP-5 n'est pas respecté. Le repli est la
   garantie ; ne pas le retirer avant vérification complète.
2. **Cache CDN** : une ancienne URL `upload` peut encore répondre un temps après la bascule. À
   mentionner dans la vérification, et ne pas conclure « fermé » sur un seul test immédiat.
3. **La signature devient critique** : une erreur de génération d'URL signée rendrait **tous**
   les documents illisibles. D'où les tests de DP-1 et le repli pendant la transition.
4. **Les URL à jeton déjà distribuées** (60 min) restent valides jusqu'à expiration — sans
   gravité, mais à savoir.
5. **Accès passés inconnus** si Cloudinary ne journalise pas (§ d) : on ferme pour l'avenir sans
   pouvoir affirmer qu'il n'y a pas eu de fuite. Raison de migrer vite, pas d'attendre une
   certitude qu'on n'aura pas.

**Ce que DOC-PRIV ne fait pas** : il ne touche pas aux ressources publiques voulues (produits,
galerie, blog, images et vidéos de formation), ni au calcul des prix, ni au paiement, ni aux
circuits de validation. Il change **où** vit la confidentialité : du secret d'une adresse à une
signature contrôlée par notre serveur.

---

## Rappel des contraintes (elles s'appliqueront à chaque lot)

`CLAUDE.md`, puis D0 : branche dédiée par lot, migration au numéro libre à la fusion **si** une
colonne devait changer (ici, aucune ne change), accord avant tout déploiement, sauvegarde avant
toute opération de masse, `-e URLBASE` sans valeur et `ON_ERROR_STOP=1` sur tout script qui
écrit, rapport avec le nombre de tests ignorés. **Aucune exécution avant accord.**
