# Mise en ligne d'« Exercer en France »

Écrit pour : Mounir, qui exécutera ces étapes lui-même sur Render et Vercel.

Rien de ce document n'a été exécuté. Chaque commande est à lancer par vous, dans
l'ordre. La durée totale, hors vérifications, est d'environ 40 minutes.

**Ce qui part en ligne** : les migrations V72 à V77, la page publique « Exercer en
France », le formulaire de demande, les deux e-mails, l'écran « Demandes
d'accompagnement » et l'écran d'administration de la page, plus l'article 3.8 des
CGV.

---

## 0. Avant tout : la sauvegarde de la base de production

**Ne lancez aucune autre étape avant d'avoir cette sauvegarde et de l'avoir
vérifiée.** Cinq migrations vont s'appliquer ; une base PostgreSQL ne se remonte
pas d'un clic.

### Si votre base Render est un plan payant

Render prend des sauvegardes automatiques quotidiennes, mais **ne comptez pas
dessus seul** : vous ne savez pas à quelle heure elle date.

1. Tableau de bord Render → votre base PostgreSQL → onglet **Recovery**.
2. Notez la date et l'heure du dernier point de restauration disponible.
3. Faites quand même la sauvegarde manuelle ci-dessous : elle vous appartient, et
   elle ne dépend pas de l'abonnement.

### Sauvegarde manuelle (à faire dans tous les cas)

1. Tableau de bord Render → votre base → **Connect** → copiez l'**External
   Database URL** (elle commence par `postgresql://`).
2. Depuis votre poste, avec `pg_dump` en version 16 ou plus :

   ```bash
   pg_dump "<EXTERNAL_DATABASE_URL>" \
     --format=custom --no-owner --no-privileges \
     --file="optimisante-avant-evc-$(date +%Y%m%d-%H%M).dump"
   ```

3. **Vérifiez le fichier avant de continuer.** Un fichier de quelques kilo-octets
   est une sauvegarde vide :

   ```bash
   ls -lh optimisante-avant-evc-*.dump
   pg_restore --list optimisante-avant-evc-*.dump | head -30
   ```

   La seconde commande doit lister des tables (`products`, `users`,
   `accompaniment_requests`…). Si elle rend une erreur, **arrêtez-vous ici** : la
   sauvegarde n'est pas exploitable.

4. Copiez le fichier ailleurs que sur votre poste de travail.

5. Notez la version de schéma actuelle, vous en aurez besoin en cas de retour
   arrière :

   ```bash
   psql "<EXTERNAL_DATABASE_URL>" -c \
     "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
   ```

   Notez le numéro de la dernière ligne : ce sera votre **point de retour**.

---

## 1. Vérifier la configuration Render AVANT de déployer

### 1.1 La variable `app.tenant`, pas à pas

Le rattachement des demandes déposées par un visiteur non connecté vient de la
configuration du serveur, jamais de la requête. Si cette valeur est absente ou
fausse, les demandes se rattacheront au mauvais tenant.

**Étape 1 — vérifier ce qui existe en base.**

```bash
psql "<EXTERNAL_DATABASE_URL>" -c "SELECT id, code, nom FROM tenants ORDER BY code;"
```

Notez le `code` attendu. Il doit valoir **`FR_MAIN`**. S'il n'apparaît pas dans
cette liste, **arrêtez-vous** : la variable ne peut pas désigner un tenant
inexistant, et le backend refusera de démarrer.

**Étape 2 — ouvrir la configuration Render.**

1. <https://dashboard.render.com> → connectez-vous.
2. Dans la liste des services, cliquez sur le service **backend** (celui de type
   *Web Service*, pas la base de données).
3. Dans le menu de gauche du service, cliquez sur **Environment**.
4. Vous voyez la liste **Environment Variables**.

**Étape 3 — vérifier ou créer la variable.**

Cherchez une ligne dont la clé est **`APP_TENANT_CODE_PAR_DEFAUT`**.

- **Si elle existe** : vérifiez que sa valeur est exactement `FR_MAIN`, sans
  espace avant ni après, majuscules comprises. Cliquez sur la valeur pour la
  révéler si elle est masquée.
- **Si elle n'existe pas** : cliquez sur **Add Environment Variable**, puis
  saisissez :

  | Key | Value |
  |---|---|
  | `APP_TENANT_CODE_PAR_DEFAUT` | `FR_MAIN` |

**Étape 4 — enregistrer.**

Cliquez sur **Save Changes**. Render redéploie automatiquement le service. C'est
normal, et c'est même souhaitable : vous voulez que la variable soit en place
AVANT le déploiement du nouveau code.

**Pourquoi ce nom de clé.** Spring lit la propriété `app.tenant.code-par-defaut`
dans les variables d'environnement en remplaçant les points et les tirets par des
tirets bas, et en passant tout en majuscules. `APP_TENANT_CODE_PAR_DEFAUT` est
donc la même chose, vue depuis Render.

**Étape 5 — vérifier après le déploiement.** Dans les journaux Render, au
démarrage, le tenant retenu est tracé. Et après la mise en ligne, déposez une
demande d'essai puis vérifiez :

```bash
psql "<EXTERNAL_DATABASE_URL>" -c   "SELECT a.nom, t.code FROM accompaniment_requests a JOIN tenants t ON t.id = a.tenant_id ORDER BY a.cree_le DESC LIMIT 3;"
```

La colonne `code` doit afficher `FR_MAIN`.

### 1.2 La mémoire de la JVM, pas à pas

Mesuré en local sur ce jar : **506 Mio au repos à froid**, **524 Mio au maximum
pendant un parcours complet**. La JVM tourne avec `MaxRAMPercentage` à sa valeur
par défaut de **25 %** — sur une instance de 512 Mo, cela ne laisse que 128 Mo de
tas, et un import de catalogue tombe en `OutOfMemoryError`.

Vous passez au palier supérieur à 512 Mo : réglez aussi le pourcentage, sans quoi
l'instance plus grande ne servirait presque à rien.

**Où exactement :**

1. <https://dashboard.render.com> → service **backend**.
2. Menu de gauche → **Settings** → section **Instance Type** : choisissez le
   palier retenu (**Standard**, 2 Go, est le premier palier confortable).
3. Menu de gauche → **Environment** → **Add Environment Variable** :

   | Key | Value |
   |---|---|
   | `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=70` |

4. **Save Changes**.

**Vérification après redémarrage.** Dans les journaux Render, au tout début du
démarrage, la JVM affiche la ligne :

```
Picked up JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=70
```

Si cette ligne n'apparaît pas, la variable n'est pas prise en compte : vérifiez
son orthographe, elle est sensible à la casse.

**Pourquoi 70 et non 100.** Une JVM a besoin de mémoire HORS du tas — métadonnées
de classes, piles de threads, tampons réseau. Monter à 100 % ferait tuer le
conteneur par le système avant que le ramasse-miettes n'ait une chance d'agir.

### 1.3 Les autres variables

Vérifiez que ces variables existent et ne sont pas vides, sans les afficher dans
un terminal partagé : identifiants PostgreSQL, Cloudinary, Mailtrap, Stripe,
pawaPay, et la clé de signature JWT.

---

## 2. Fusionner `feature/exercer-en-france` dans `main`

Rappel de la règle : **`main` est ce qui est en ligne.** La fusion ne se fait donc
qu'au moment où vous déployez.

```bash
cd /d/OptimiSanté_Projet/optimi-sante
git checkout main
git pull origin main
git merge --no-ff feature/exercer-en-france
```

En cas de conflit, **n'improvisez pas** : `git merge --abort`, et reprenons-le
ensemble.

Avant de pousser, relancez la vérification complète :

```bash
cd backend  && ./mvnw clean test      # 258 tests attendus, 0 ignoré
cd ../frontend && npm run build       # charte respectée, 39 tests vitest
```

Puis :

```bash
cd ..
git push origin main
```

---

## 3. Déployer le backend, puis le frontend

**Le backend d'abord, toujours.** Le frontend appelle des routes qui n'existent
pas encore dans l'ancien backend : déployé en premier, il afficherait des erreurs
le temps que le backend suive.

### 3.1 Backend

```bash
cd /d/OptimiSanté_Projet
git -C optimisante-backend rm -r --cached . -q
git -C optimi-sante archive HEAD:backend | tar -x -C optimisante-backend
cd optimisante-backend
git add -A && git commit -m "Exercer en France : page publique, demandes et administration"
git push origin main
```

Render déclenche la construction automatiquement. Suivez les journaux.

### 3.2 Les migrations V72 à V77

Elles s'appliquent **au démarrage**, avant que l'application n'accepte une
requête. Dans les journaux Render, vous devez voir :

```
Migrating schema "public" to version "72 - exercer en france"
Migrating schema "public" to version "73 - exercer en france reglages"
Migrating schema "public" to version "74 - offre technique"
Migrating schema "public" to version "75 - accompagnement delai et contact"
Migrating schema "public" to version "76 - offre prix barre et badge"
Migrating schema "public" to version "77 - statut suspect"
Successfully applied 6 migrations
```

Puis `Started BackendApplication`.

**Si une migration échoue**, l'application ne démarre pas et Flyway laisse la
base dans l'état de la dernière migration réussie. Allez directement au
chapitre 7.

### 3.3 Frontend

Seulement une fois le backend démarré et répondant.

```bash
cd /d/OptimiSanté_Projet
git -C optimisante-frontend rm -r --cached . -q
git -C optimi-sante archive HEAD:frontend | tar -x -C optimisante-frontend
cd optimisante-frontend
git add -A && git commit -m "Exercer en France : page publique et administration"
git push origin main
```

---

## 4. Premier démarrage : le seed, puis « Publier »

Le seed s'exécute automatiquement au premier démarrage et crée les sections, les
périodes, les six liens officiels, les spécialités et les cinq offres. Il est
**idempotent par clé** : relancé, il ne crée rien en double et **ne réécrit
aucune ligne existante**.

### 4.1 Vérifier que le seed a bien tourné

```bash
psql "<EXTERNAL_DATABASE_URL>" -c \
  "SELECT cle, titre, prix, prix_barre, badge FROM evc_offers ORDER BY ordre;"
```

Vous devez lire exactement :

| cle | titre | prix | prix_barre | badge |
|---|---|---|---|---|
| preparation_evc | Orientation EVC | 89.00 | | |
| dossier_demarches | Dossier & démarches | 450.00 | | |
| mobilite_installation | Mobilité & installation | 650.00 | | |
| pack_dossier_mobilite | Pack Dossier + Mobilité | 990.00 | 1100.00 | Le plus complet |
| alerte_aep | Alerte d'ouverture AEP | | | |

**Si les titres ou les prix diffèrent**, c'est que des offres existaient déjà :
le seed ne les a pas touchées. Dites-le-moi avant d'aller plus loin — il faudra
les corriger à la main depuis l'écran d'administration.

### 4.2 Publier

**C'est l'étape qu'on oublie.** La page publique sert la version PUBLIÉE, pas le
contenu en base : tant que personne n'a publié, le visiteur ne voit rien.

1. Connectez-vous en **super administrateur**.
2. Administration → **Exercer en France**.
3. Bouton **Aperçu** : relisez le contenu, en particulier les quatre offres et
   leurs prix.
4. Bouton **Publier**, puis confirmez.
5. La carte « Version en ligne » doit afficher **1** et la date du jour.

### 4.3 Vérifier la page publique

Ouvrez `https://optimisante.com/exercer-en-france` **dans une fenêtre de
navigation privée** (sans session administrateur) et vérifiez :

- les quatre offres s'affichent, avec « 89 € TTC » et « ≈ 58 500 FCFA » ;
- le pack porte le badge « Le plus complet » et « 1 100 € » barré ;
- la phrase de déduction des 89 € apparaît sous les offres ;
- **aucune mention de QCM, d'examens blancs, de classes virtuelles ou de
  traductions** ;
- les six liens officiels s'ouvrent ;
- la page s'affiche correctement sur votre téléphone.

---

## 5. Vérifications après mise en ligne

À faire dans l'heure qui suit, dans cet ordre.

1. **Déposer une vraie demande** depuis la page publique, avec votre propre
   adresse, sur l'offre « Orientation EVC ».
2. **Vérifier les deux e-mails** : l'accusé de réception arrive sur votre
   adresse, l'alerte arrive aux administrateurs. L'accusé doit annoncer « sous
   48 h ouvrées » et inviter à répondre à **infos@optimisante.com**.
3. **Cliquer le lien de l'alerte** : il doit ouvrir directement la fiche du
   dossier, pas la liste.
4. **Traiter la demande** : changer le statut, ajouter une note, vérifier que le
   fil d'événements s'alimente.
5. **Exporter** les deux CSV, les ouvrir dans Excel : accents corrects, colonnes
   séparées, numéros WhatsApp collables tels quels.
6. **Vérifier les droits** avec un compte ADMIN_MOBILITE : le bouton de
   modification des prix est absent, et la restauration est refusée.
7. **Supprimer vos demandes d'essai**, avant d'oublier. Commencez TOUJOURS par la
   lecture : une suppression se décide sur ce qu'on a vu, pas sur ce qu'on croit.

   ```sql
   -- 1. LECTURE SEULE : ce que la suppression emporterait.
   SELECT id, nom, email, statut, offre_cle, cree_le
   FROM accompaniment_requests
   WHERE email IN ('<votre-adresse>', '<autre-adresse-d-essai>')
   ORDER BY cree_le DESC;
   ```

   Relisez cette liste. **Si une seule ligne ne vous appartient pas, arrêtez-vous**
   et dites-le-moi : ce serait une demande d'un vrai praticien.

   ```sql
   -- 2. Suppression, seulement après avoir relu la liste ci-dessus.
   BEGIN;

   DELETE FROM accompaniment_request_events
    WHERE request_id IN (SELECT id FROM accompaniment_requests
                          WHERE email IN ('<votre-adresse>', '<autre-adresse-d-essai>'));

   DELETE FROM accompaniment_requests
    WHERE email IN ('<votre-adresse>', '<autre-adresse-d-essai>');

   -- Vérifiez le nombre de lignes annoncé. S'il ne correspond pas à votre liste :
   --   ROLLBACK;
   -- Sinon :
   COMMIT;
   ```

   Les événements partent **avant** les demandes : ils les référencent, et
   l'ordre inverse se heurterait à la clé étrangère.

8. **Supprimer les demandes marquées « Suspect »** si elles sont manifestement
   des robots — mais relisez-les d'abord dans l'écran « Demandes », filtre
   « Suspect ». Un praticien pressé peut s'y trouver.
9. **Surveiller la mémoire** sur Render pendant 24 h. Au-delà de 80 % de
   l'instance de façon durable, dites-le-moi.

---

## 6. Ce qu'il ne faut PAS faire

- **« Restaurer » écrase votre brouillon** avec le contenu de la version choisie,
  et ne publie rien. Relisez l'aperçu, puis publiez si cela vous convient. Une
  version ancienne porte les textes et les prix de son époque, y compris ceux
  retirés depuis : c'est l'aperçu qui vous le dira.
- **Ne modifiez jamais une migration déjà appliquée en production.** Un conflit
  de somme de contrôle se répare sur la base locale, jamais en retouchant le
  fichier.
- **Ne purgez pas les volumes Docker** sur votre poste : ils portent la base
  locale.

---

## 7. Retour arrière

### 7.1 Si le backend ne démarre pas

1. Render → service backend → **Events** → repérez le déploiement précédent →
   **Rollback**. Le code revient en arrière en quelques minutes.
2. **Attention** : les migrations déjà appliquées, elles, ne reviennent pas. Si
   V72 à V76 sont passées, l'ancien code tourne sur un schéma plus récent. C'est
   normalement sans effet — ces six migrations n'ajoutent que des tables et des
   colonnes, sans rien supprimer ni renommer — mais vérifiez le démarrage.
3. Si l'ancien code refuse de démarrer à cause du schéma, passez au 7.3.

### 7.2 Si seule la page est fautive (contenu, prix, texte)

Pas besoin de toucher au code. Dans l'administration, onglet **Historique**,
restaurez la version précédente — en ayant lu son aperçu d'abord. Si rien n'a
jamais été publié, masquez les sections concernées et republiez.

### 7.3 Si la base doit revenir en arrière

C'est l'opération la plus lourde : **tout ce qui a été écrit depuis la sauvegarde
est perdu**, commandes et inscriptions comprises. Ne la lancez que si la base est
réellement corrompue.

1. Mettez le service backend **en pause** sur Render, pour que plus rien n'écrive.
2. Restaurez :

   ```bash
   pg_restore --clean --if-exists --no-owner --no-privileges \
     --dbname="<EXTERNAL_DATABASE_URL>" \
     optimisante-avant-evc-AAAAMMJJ-HHMM.dump
   ```

3. Vérifiez que la version de schéma est revenue à votre **point de retour** :

   ```bash
   psql "<EXTERNAL_DATABASE_URL>" -c \
     "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
   ```

4. Faites le **Rollback** du code sur Render, pour que le code corresponde au
   schéma.
5. Relancez le service.

Si vous en arrivez là, prévenez-moi avant l'étape 2 : une restauration ratée est
pire que le problème qu'elle corrige.
