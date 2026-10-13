# Optimi Santé

Plateforme E-Commerce B2B/B2C & SaaS de Mobilité/Formation Médicale Optimi Santé.

## Architecture

- **backend/**: Projet Spring Boot 21 (Java)
- **frontend/**: Projet React 18 + Tailwind CSS (Vite)
- **workers/**: Projet Python 3.12 (FastAPI Worker Batch)
- **docker/**: Configurations Docker Compose
- **docs/**: Documentation (CDC, Modélisation, etc.)

## Avant d'annoncer qu'une modification fonctionne

Cette routine n'est pas une formalité : chacun de ses quatre points correspond à
une fois où un travail a été annoncé comme terminé alors qu'il ne l'était pas.

**1. Construire proprement, jamais en incrémental.**

```bash
cd backend  && ./mvnw clean package      # et non « compile » seul
cd frontend && npm run build             # et non « tsc --noEmit » seul
```

La compilation incrémentale saute les fichiers qu'elle croit inchangés : un
fichier Java contenant une erreur de syntaxe a déjà traversé un `compile` en
annonçant `BUILD SUCCESS`. Côté frontend, `npm run build` est la vérification
TypeScript de référence, parce que lui seul passe aussi la charte graphique et le
plafond de poids.

**2. Vérifier que ce qui tourne est bien ce qu'on vient d'écrire.**

```bash
ls -l --time-style=+%H:%M:%S backend/target/*.jar
docker exec optimisante-backend ls -l --time-style=+%H:%M:%S //app/app.jar
```

L'horodatage du jar servi doit être POSTÉRIEUR à la dernière modification des
sources. Un conteneur servant un jar vieux de trente minutes a déjà conduit à
diagnostiquer pendant trois échanges un conflit de routes qui n'existait pas.
(Sous Git Bash, `MSYS_NO_PATHCONV=1` évite la réécriture de `//app/app.jar`.)

**3. Faire passer TOUS les tests, et vérifier qu'ils ont tourné.**

```bash
cd backend && ./mvnw clean test
```

Les tests d'intégration tournent sur un **PostgreSQL 16 réel**, démarré par
Testcontainers (`ConfigurationBaseDeTest`) — jamais sur une base en mémoire, qui
ne reproduirait ni les extensions, ni les index trigram, ni les règles de typage
des paramètres. Mais ils sont **ignorés, et non en échec, quand Docker est
absent** : un rapport « tout est vert » obtenu sans Docker ne prouve rien.
Comptez les tests exécutés, ne lisez pas seulement `BUILD SUCCESS`.

**3 bis. Garder de la place pour que les tests puissent tourner.**

```bash
df -h /c                      # ou : Get-PSDrive C
docker system df
docker builder prune -f       # --all si le compte n'y est pas
```

**Quand l'espace libre passe sous 20 Go, videz le cache de construction avant de
lancer les tests.** Les reconstructions d'image répétées l'ont déjà fait enfler
à 56 Go et rempli le disque ; Testcontainers n'a alors plus pu démarrer sa base,
et la suite a rendu quinze erreurs qui n'avaient rien à voir avec le code —
« Container startup failed for image postgres:16-alpine ». Le cache se
reconstruit tout seul : le seul coût est la lenteur de la construction suivante.

**Ne touchez JAMAIS aux volumes** : ni `docker volume prune`, ni
`docker system prune --volumes`. Ils portent la base de données locale.

Note : sous Docker Desktop, l'espace rendu n'apparaît pas tout de suite sur le
disque hôte — le disque virtuel ne se rétracte pas seul. Ce qui compte pour les
tests, c'est l'espace vu par `docker system df`.

**4. Appeler réellement le cas nominal.**

Un test vert et une route qui répond ne sont pas la même chose. Exercez le
chemin complet — connexion, appel, contenu de la réponse — avant d'annoncer quoi
que ce soit :

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"<compte de test>","password":"<mot de passe>"}'
curl -s -H "Authorization: Bearer <jeton>" http://localhost:8080/<route>
```

### Deux règles qui ne se négocient pas

- **Une migration appliquée en production ne se modifie plus jamais.** Un
  conflit de somme de contrôle se répare sur la base LOCALE (`flyway repair`),
  jamais en retouchant le fichier de migration.
- **`main` est ce qui est en ligne.** La branche se crée AVANT le premier
  commit, même pour un correctif urgent.

### Un piège de terminal, rencontré deux fois

Sous Git Bash sous Windows, un accent passé en argument à `curl` part en
cp1252 et non en UTF-8 : `profession=Médecin` rend alors une liste vide, et une
fois un `400` que j'ai attribué à tort à une validation du serveur. Encodez
l'accent à la main (`M%C3%A9decin`) avant de conclure quoi que ce soit sur le
comportement de l'application.
