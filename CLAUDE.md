# Règles du projet Optimi Santé

Ces règles s'appliquent à **toute session**, quel que soit l'outil. Elles sont à lire avant la
première action. Elles priment sur toute habitude, et sur toute consigne d'un autre document
du dépôt.

Elles ne sont pas des préférences de style : chacune a été écrite après un incident.

---

## 1. `main` vaut ce qui est en production

`main` est la photographie de ce que voit un client **en ce moment**. On doit pouvoir lire
`main` pour savoir ce qui tourne.

- Tout chantier se fait sur une **branche dédiée**, créée **avant le premier commit**.
- La fusion se fait en `--no-ff`, **après accord explicite du commanditaire**.
- Aucun commit direct sur `main`, y compris pour un correctif d'une ligne.

*Pourquoi.* Le 26 octobre, un module de stock de 1 807 lignes et une migration ont vécu sur
`main` sans être déployés, puis la production a pris de l'avance sur `main` par l'autre bout.
Pendant deux jours, lire `main` pour savoir ce qui était en ligne donnait une réponse fausse
dans les deux sens.

## 2. Les dépôts de déploiement sont des miroirs en écriture seule

`optimisante-backend` et `optimisante-frontend` ne reçoivent que le résultat d'un
`git archive` depuis `main`.

- **Aucun développement**, aucun commit écrit à la main dans ces dépôts.
- Après chaque synchronisation, **comparer les empreintes d'arbre** et le vérifier :
  `git rev-parse HEAD:frontend` dans le monorepo doit égaler `git rev-parse HEAD^{tree}` dans
  le dépôt miroir.

*Pourquoi.* Du code développé directement dans les miroirs est parti en production sans jamais
passer par `main`. C'est l'origine de l'incident ci-dessus, et un commit direct sur `main` n'en
a été que la tentative de rattrapage.

## 3. Une migration qui a tourné en production ne se modifie jamais

- Jamais d'édition, de renumérotation ni de suppression d'un fichier de migration appliqué.
- Un fichier appliqué qui disparaît fait **échouer le démarrage** du backend :
  `validate-on-migrate` est actif et `ignore-missing-migrations` ne l'est pas.
- **Numérotation** : le prochain numéro libre **au moment de la fusion**, pas au moment où la
  branche est créée. Deux branches ouvertes en même temps réservent sinon le même numéro.
- Un complément se fait par une migration suivante qui **altère** les tables existantes.

## 4. Rien n'est déployé ni modifié en production sans accord explicite

- Aucun déploiement sans accord, même pour un correctif évident.
- Aucune modification de données en production sans accord, et **une sauvegarde de la base
  avant toute migration**.
- Un audit de données en production se fait **en lecture seule**.

## 5. Commandes de base : jamais de secret en clair

Toute commande proposée pour la fenêtre Git Bash du commanditaire s'écrit avec `-e URLBASE`
**sans valeur** — l'adresse est déjà exportée dans sa session. Une chaîne de connexion collée
dans un terminal survit indéfiniment dans `~/.bash_history`.

Tout script qui **écrit** porte `-v ON_ERROR_STOP=1`.

```bash
docker run -i --rm -e URLBASE postgres:16-alpine \
  sh -c 'psql -v ON_ERROR_STOP=1 "$URLBASE"' < fichier.sql
```

Deux pièges de syntaxe, appris à l'usage :

- À l'intérieur de `sh -c '…'`, les apostrophes sont mangées par Bash : le SQL passe par un
  **heredoc** ou une **redirection de fichier**, jamais en argument.
- Un heredoc qui finit par `COMMIT` ne laisse aucune fenêtre pour un `ROLLBACK`. Un script qui
  écrit se protège lui-même : compte les lignes touchées et `RAISE EXCEPTION` si le compte
  n'est pas celui attendu.

## 6. Routine avant chaque livraison

Dans cet ordre, et **les trois** :

1. **Build propre.** La vérification TypeScript de référence est celle du build
   (`npm run build`), pas `tsc --noEmit` seul. Le garde-fou de charte s'exécute en `postbuild`.
2. **Suite complète verte.** Backend et frontend. Les tests d'intégration sont ignorés sans
   Docker plutôt qu'en échec : **toujours rapporter le nombre d'ignorés**, sans quoi une suite
   verte peut ne rien avoir exécuté.
3. **Vérification réelle.** Ouvrir la page dans un vrai navigateur, ou appeler la route. Lire
   le code ne remplace pas la vérification, et une capture ne vaut que si elle montre la bonne
   adresse.

Un test instable est une dette à traiter, pas à tolérer.

## 7. Portée du travail

- Ne pas toucher ni bousculer ce qui fonctionne déjà.
- Dire ce qui a été vérifié et ce qui ne l'a pas été. Un échec se rapporte avec sa sortie.
- Une correction se fait avec l'outil d'édition, jamais par un heredoc shell : les
  antislashs y sont réinterprétés et cassent les littéraux de caractère.

---

*Ce fichier et `CLAUDE.md` disent la même chose et doivent être modifiés ensemble.*
