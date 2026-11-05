# M2-bis — caractéristiques, documents, garantie

**Appliquer D0**, puis `docs/plan_integration_catalogue.md` § 4 « M2-bis ». Contradiction :
arrête-toi et dis-le.

Branche `feature/fiche-caracteristiques-documents`. Prérequis : M2 livré.

## LE CADRE, MESURÉ AVANT D'ÊTRE SPÉCIFIÉ

Le fichier de La Centrale Médicale a été lu en lecture seule le 29 octobre : **neuf colonnes**,
et **aucune** ne porte de caractéristique technique, de garantie ni de lien de notice.
`OBSERVATIONS` est du texte libre commercial (« NOUVEAUTE » 653 fois, « ARRET » 111,
« Port en sus sur devis » 43) — ce n'est pas une fiche technique.

**Ces trois champs seront donc remplis à la main, produit par produit.** L'écran de saisie
compte autant que l'affichage, et le lot reste **petit** : on ne construit pas une cathédrale
pour des champs qu'aucun import ne remplira. Si tu constates une autre source avant de coder,
dis-le — cela changerait le dimensionnement.

## 1. CARACTÉRISTIQUES

Liste **clé-valeur ordonnée** par produit. Onglet « Caractéristiques » **masqué si vide** — il
l'est depuis M2 : il s'allume ici. Sur téléphone, section dépliable **ouverte par défaut**,
comme la maquette le prévoit.

Pas de schéma par catégorie à ce stade : une liste libre ordonnée se remplit tout de suite,
un référentiel de caractéristiques par famille est un chantier en soi. Note-le, ne le code pas.

## 2. DOCUMENTS

Notice et déclaration de conformité. **Fichiers publics**, servis sans authentification.

**À distinguer explicitement du stockage privé des pièces des candidats « Exercer en
France ».** Les deux ne partagent ni chemin, ni compartiment, ni règle d'accès. Écris-le dans
le code, à l'endroit où le chemin est construit : la confusion coûterait une fuite de pièces
d'identité.

**Vérification de la signature du fichier**, pas seulement de l'extension. C'est la quinzième
route d'envoi du projet, et la sécurisation par signature est une dette déjà identifiée (route
`/partnership/requests` et 13 autres). M2-bis **n'ajoute pas** une route non sécurisée : la
vérification est dans le périmètre, pas reportée.

Onglet « Documents » masqué si aucun document.

## 3. GARANTIE

Une **durée en mois**, affichée « Garantie fabricant : 24 mois ». Masquée si vide.

Pas de texte libre : il deviendrait un fourre-tout commercial non comparable, et c'est la
comparaison qui intéresse un acheteur d'hôpital.

## 4. ADMINISTRATION

Un écran de saisie pour les trois, sur la fiche produit de l'administration : ajouter,
réordonner et supprimer des caractéristiques ; déposer et retirer des documents ; saisir la
durée de garantie.

## EXCLU

L'import en masse de ces trois champs — aucune source ne les fournit. Les documents privés ou
rattachés à un client. Toute réutilisation du stockage des pièces EVC. Tout référentiel de
caractéristiques par famille.

## TESTS

Un produit sans aucun des trois champs affiche **exactement** la fiche de M2 — c'est le test de
non-régression le plus important du lot. Onglet masqué quand vide, visible quand rempli.
Document déposé **joignable publiquement**. Fichier dont le contenu ne correspond pas à
l'extension : **refusé**, avec un test par type accepté. Garantie à 0 ou nulle : masquée, et
non « 0 mois ».

## CRITÈRES D'ACCEPTATION

Un fichier renommé en `.pdf` mais qui n'en est pas un est refusé — vérifié réellement, pas en
test seul. Les documents publics ne sont **pas** servis depuis le même chemin que les pièces
EVC, vérifié en lisant les deux configurations côte à côte. Poids JS **≤ 20 Ko**.

## RAPPORT

Section 7 de D0. Plus : les types de fichiers acceptés et refusés, avec la sortie du refus ;
et la confirmation écrite que le chemin public est distinct du chemin privé EVC.
