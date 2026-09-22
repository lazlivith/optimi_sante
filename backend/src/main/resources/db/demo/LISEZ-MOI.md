# Données de démonstration — jamais en production

Ce dossier contient des migrations Flyway qui n'insèrent **que des données** : des formations
fictives et cinq comptes de test. Aucune ne touche au schéma.

## Pourquoi elles ne sont plus dans `db/migration`

Tant qu'elles s'y trouvaient, toute base neuve les recevait — y compris la base de production.
Concrètement, `V11` y aurait créé un compte `admin@optimi.com` en **SUPER_ADMIN**, actif, portant
le mot de passe de test partagé par les cinq comptes. Désactiver ce compte dans une base ne change
rien : c'est la migration qui fait foi, et elle se rejoue sur chaque base vierge.

## Comment elles sont appliquées

Par `spring.flyway.locations`, réglé profil par profil dans `application.yml` :

| Profil | Emplacements | Effet |
|---|---|---|
| `dev` | `db/migration` **et** `db/demo` | les comptes de test existent, rien ne change pour vous |
| `test` | `db/migration` | la suite de tests valide un schéma tel qu'il sera en production |
| `prod` | `db/migration` | aucune donnée de démonstration, aucun compte de test |

## Ce qu'il ne faut pas faire

**Ne renommez pas et ne modifiez pas ces fichiers.** Ils ont déjà été appliqués sur les bases de
développement, qui en ont enregistré la version et l'empreinte. Les changer ferait échouer Flyway
au démarrage. Les numéros 5 et 11 manquent désormais à la suite des migrations de production :
c'est voulu, et Flyway accepte les trous.

## Et en production ?

Une base de production neuve démarre donc **sans catalogue, sans sessions et sans compte**. Le
premier administrateur et les données réelles se chargent par un autre moyen, hors migrations.
