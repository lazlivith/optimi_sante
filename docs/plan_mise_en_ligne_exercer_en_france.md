# Mise en ligne d'« Exercer en France »

Écrit pour : Mounir, qui exécutera ces étapes lui-même sur Render et Vercel.

Rien de ce document n'a été exécuté. Chaque commande est à lancer par vous, dans
l'ordre. La durée totale, hors vérifications, est d'environ 40 minutes.

**Ce qui part en ligne** : les migrations V72 à V76, la page publique « Exercer en
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

### 1.1 La variable `app.tenant`

Le rattachement des demandes déposées par un visiteur non connecté vient de la
configuration du serveur, jamais de la requête. Si cette valeur est absente ou
fausse, **les demandes se rattacheront au mauvais tenant, ou le démarrage
échouera**.

1. Render → service backend → **Environment**.
2. Cherchez `APP_TENANT_CODE_PAR_DEFAUT` (Spring lit les variables en majuscules
   avec tirets bas pour `app.tenant.code-par-defaut`).
3. Si elle est absente, ajoutez-la avec la valeur **`FR_MAIN`**.
4. Vérifiez que ce code existe réellement en base :

   ```bash
   psql "<EXTERNAL_DATABASE_URL>" -c "SELECT id, code FROM tenants;"
   ```

   Le code de la variable doit figurer dans cette liste, à l'identique, casse
   comprise.

### 1.2 La mémoire de la JVM — à décider maintenant

Mesuré en local sur ce jar : **506 Mio au repos à froid**, **524 Mio au maximum
pendant un parcours complet**. La JVM tourne avec `MaxRAMPercentage` à sa valeur
par défaut de **25 %**.

Sur une instance Render de 512 Mo, 25 % donne un tas de 128 Mo : l'application
démarre mais tombera en `OutOfMemoryError` sur un import ou un export un peu
gros. Deux possibilités, à choisir avant de déployer :

- **Instance 1 Go minimum**, et ajouter la variable d'environnement
  `JAVA_TOOL_OPTIONS` = `-XX:MaxRAMPercentage=70`. C'est la recommandation.
- Rester à 512 Mo en acceptant que les imports de catalogue échouent.

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

### 3.2 Les migrations V72 à V76

Elles s'appliquent **au démarrage**, avant que l'application n'accepte une
requête. Dans les journaux Render, vous devez voir :

```
Migrating schema "public" to version "72 - exercer en france"
Migrating schema "public" to version "73 - exercer en france reglages"
Migrating schema "public" to version "74 - offre technique"
Migrating schema "public" to version "75 - accompagnement delai et contact"
Migrating schema "public" to version "76 - offre prix barre et badge"
Successfully applied 5 migrations
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
7. **Supprimer vos demandes d'essai** depuis la base, avant d'oublier.
8. **Surveiller la mémoire** sur Render pendant 24 h. Au-delà de 80 % de
   l'instance de façon durable, dites-le-moi.

---

## 6. Ce qu'il ne faut PAS faire

- **Ne cliquez pas sur « Restaurer »** dans l'onglet Historique sans avoir lu
  l'aperçu de la version visée. La restauration **remet cette version en ligne
  immédiatement** — elle ne touche pas au brouillon. Une version ancienne porte
  les textes de son époque, y compris ceux retirés depuis.
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
   normalement sans effet — ces cinq migrations n'ajoutent que des tables et des
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
