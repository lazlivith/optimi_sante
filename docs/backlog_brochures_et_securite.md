# Backlog — brochures de formation et sécurité des routes publiques

Écrit pour : Mounir et les sessions suivantes. Points relevés pendant BR-1 (29 octobre 2026),
à traiter plus tard. Aucun n'est bloquant pour BR-1, qui est déployé.

## À faire avant toute campagne marketing vers les formations

- [ ] **Limite de débit sur les routes publiques POST.** Visé : `POST /api/v1/trainings/*/lead-capture`
      et la future route publique de journal client du catalogue
      (`/api/v1/journal-client`, cf. `plan_integration_catalogue.md`). Aujourd'hui aucune de ces
      routes n'a de limite. L'anti-doublon 24 h de `captureLead` ne protège que d'une répétition
      **exacte** du couple (adresse, formation) : un robot à adresses variables remplit
      `prospect_leads` et envoie autant d'alertes aux administrateurs. À traiter ensemble, une
      seule mécanique de limitation par IP.

## Utile rapidement

- [ ] **Écran d'administration listant `prospect_leads`.** Aujourd'hui l'alerte e-mail est le
      **seul** outil de suivi des demandes de brochure. Tant qu'il n'existe pas, les coordonnées
      du prospect voyagent dans le corps du message (faute d'un endroit où les consulter). Une
      fois l'écran en place, l'alerte se réduira à un lien, comme l'alerte d'accompagnement.

## Décision en suspens, à trancher avant BR-2

- [ ] **Stockage du fichier d'origine de la brochure.** Deux options, à départager :
      1. une table en base (une ligne par dépôt, binaire isolé) ;
      2. le passage des documents en mode **`authenticated`** chez Cloudinary.
      La correction du § 0.3 de `analyse_brochures_formation.md` a établi que `authenticated`
      par URL signée **fonctionne sur le compte actuel** (seules les authentifications par jeton
      et par cookie exigent l'offre Advanced). Le test Cloudinary de l'étape 0 — une URL brute
      `raw/upload/<public_id>` qui répond 200 — confirme que les documents sont aujourd'hui
      **publics**. S'il répond 200, passer en `authenticated` règle le problème pour **tous** les
      documents du projet (reçus, conventions, pièces de candidats), pas seulement les brochures,
      et peut rendre inutile un stockage parallèle.

## Dette technique à surveiller

- [ ] **Type d'e-mail dédié `DEMANDE_BROCHURE_ALERTE_ADMIN`.** L'alerte de brochure part
      aujourd'hui sous le type générique `NOTIFICATION`, parce que la contrainte `CHECK` de
      `email_logs` n'autorise que les types déjà migrés et que BR-1 s'interdit une migration.
      À ajouter **avec la migration de BR-2**, pour pouvoir filtrer ces alertes à part.
- [ ] **Vérification de signature sur les 13 autres routes de dépôt.** `SignatureFichier` est
      branché sur la seule route de brochure. Les autres (route `/partnership/requests` et 13
      autres) ne vérifient aucune signature. À généraliser dans un lot dédié, après avoir mesuré
      quels fichiers passaient hier et qui seraient désormais refusés.
- [ ] **`ControleBrochureTest` lit `../docs/brochures_test/…`, absent du miroir backend.** Sans
      effet aujourd'hui : le `Dockerfile` backend construit avec `-Dmaven.test.skip=true`, donc
      le miroir ne lance jamais ce test. À corriger si les tests venaient un jour à tourner
      depuis le miroir — en embarquant le fixture dans `backend/src/test/resources/` plutôt qu'en
      le lisant à la racine du monorepo.
- [ ] **Instabilité des workers vitest** (« Failed to start forks worker for test files »),
      observée une fois juste après une suite backend complète. Non reproductible à volonté. À
      traiter si elle réapparaît en intégration continue.
- [ ] **Commentaire trompeur corrigé, à propager.** `DossierStockage.TypeRessource.DOCUMENT`
      disait « servis par lien signé, jamais publiquement » : c'était faux. Corrigé. Si d'autres
      endroits du code ou de la documentation répètent cette promesse, les corriger de même.
