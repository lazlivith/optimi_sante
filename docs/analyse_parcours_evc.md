# « Parcours EVC » dans l'espace médecin — analyse préalable

Écrit pour : Mounir, pour décision avant tout développement.

**Aucune ligne de code n'a été écrite.** Ce document établit l'existant, propose
un modèle, et découpe le chantier. Numérotation retenue : **V78 est prise**,
**V79 = règles de statut**, **Parcours EVC à partir de V80**.

---

## 1. L'existant

### 1.1 Une correction à la prémisse

Vous demandez comment l'espace médecin gère « les profils *médecin en procédure
de visa* et *médecin déjà en France ou dans l'UE* ».

**Ces profils n'existent pas au niveau du compte.** Ce qui existe est un champ
`RegistrationType` — `INTERNATIONAL_VISA` ou `FRANCE` — porté par
**l'inscription à une formation** (`Enrollment`), pas par l'utilisateur. Un même
médecin peut donc avoir deux inscriptions de parcours différents ; son *compte*
n'a pas de parcours.

C'est une bonne nouvelle pour ce chantier : **le modèle « parcours cumulables »
que vous proposez est déjà la logique du système**, et il n'y a aucun profil
d'utilisateur à démêler. Il reste seulement à le remonter d'un cran — du dossier
de formation vers le compte.

### 1.2 Ce que l'espace médecin contient aujourd'hui

| Élément | État |
|---|---|
| Rôle | `MEDECIN`, un seul, dans la colonne `users.role` |
| Routes | `/doctor` (mes inscriptions), `/doctor/vault`, `/doctor/profile`, `/doctor/enrollments/:id` |
| Garde | `@PreAuthorize("hasRole('MEDECIN')")` côté serveur, `ProtectedRoute` côté écran |
| Coffre documents | `DoctorVaultResource` — alimenté par les commandes et les inscriptions |
| Jetons de téléchargement | `DocumentAccessTokenService` : jeton signé, expiration en minutes |
| Transitions de statut | `EnrollmentTransitions` — **un graphe déjà écrit**, avec une divergence par parcours |

**`EnrollmentTransitions` est le modèle à reprendre pour la V79.** Il fait déjà
ce qu'on veut : un graphe déclaratif, une vérification côté serveur
(`assertAllowed`), et la liste des destinations possibles depuis un état donné —
de quoi griser les boutons sans dupliquer la règle.

### 1.3 Le stockage : le point bloquant

**Cloudinary ne peut pas héberger les annales ni les pièces justificatives.** Ce
n'est pas une précaution, c'est établi par le code lui-même :

> `"type", "upload"` — *« authenticated » nécessite une fonctionnalité de contrôle
> d'accès non activée sur ce compte Cloudinary (erreur « deny or ACL failure »).*
> — `CloudinaryStorageService.java`

En mode `upload`, **l'objet est publiquement atteignable par son adresse**.
L'URL signée n'ajoute qu'une expiration et de l'obscurité : qui connaît le
`public_id` lit le fichier, indéfiniment, sans être authentifié. Pour un
passeport ou un diplôme, c'est rédhibitoire.

**Il n'existe aujourd'hui qu'une seule implémentation de `StorageService`**, et
c'est Cloudinary. Un second magasin est donc à écrire : c'est le travail
structurant du lot 3, et il conditionne aussi le lot 1 (les annales).

| Option | Pour | Contre |
|---|---|---|
| **Cloudinary `authenticated`** | aucun code à écrire | fonctionnalité **non activée** sur le compte ; à négocier, coût inconnu |
| **S3 privé (ou compatible : Scaleway, OVH, Backblaze)** | URL présignées vraies, chiffrement au repos, standard | un fournisseur de plus ; le SDK AWS n'est pas au projet |
| **Disque persistant Render** | simple, pas de tiers | **interdit la mise à l'échelle** et impose une coupure à chaque changement de plan |
| **En base, colonne `bytea` chiffrée** | sauvegardé avec la base, accès naturellement contrôlé | alourdit les sauvegardes ; mauvais au-delà de quelques Go |

**Ma recommandation : S3 privé compatible.** C'est le seul choix qui donne de
vraies URL présignées, un chiffrement au repos, et qui ne contraint pas
l'hébergement. **C'est une décision qui vous revient**, et elle précède le lot 1.

### 1.4 Ce qui existe déjà et qu'on réutilise

- `ControleFichier` : plafond de **10 Mo**, et une table extension → types MIME
  acceptés. À étendre, pas à réécrire.
- `DocumentNumberService`, `PdfGeneratorService` (openpdf + flying-saucer) :
  l'export PDF du dossier et le **filigrane** des annales s'appuient dessus.
- Les deux régimes de conservation RGPD, déjà paramétrés :
  `rgpd.accompagnement.mois_avant_anonymisation` = **24 mois** (prospects) et
  `rgpd.accompagnement.mois_conservation_apres_paiement` = **60 mois** (contrats,
  comptés depuis `prestation_fin_le`).
- **Aucun antivirus** n'existe dans la plateforme. Voir § 5.4.

---

## 2. Le modèle d'accès

### 2.1 Des parcours cumulables, pas de nouveaux rôles

Ajouter un rôle `MEDECIN_EVC` serait une erreur : les rôles sont **exclusifs**
dans ce système (`users.role` est une colonne unique, contrainte par un CHECK).
Un médecin qui suit une formation *et* un parcours EVC devrait en perdre un.

**Table `parcours_medecin`** — une ligne par parcours suivi :

| Colonne | Rôle |
|---|---|
| `user_id` | le compte |
| `type` | `EVC` aujourd'hui, d'autres demain |
| `etape` | `ANNALES`, `DOSSIER`, `SUIVI` |
| `ouvert_le`, `ouvert_par` | traçabilité |
| `demande_id` | la demande d'accompagnement d'origine |

L'étape est **vérifiée côté serveur** à chaque appel, par une annotation dédiée
(`@EtapeEvc(ANNALES)`), sur le modèle des `@MobilityAdmin` existantes. L'écran
masque ce qui n'est pas ouvert ; le serveur refuse. **Les deux, comme ailleurs :
masquer seul laisse la route ouverte à qui la connaît.**

### 2.2 Les trois étapes

| Étape | Ce qu'elle ouvre | Déclencheur |
|---|---|---|
| 1 — Annales | consultation et téléchargement | création du compte à la suite d'une demande d'accompagnement |
| 2 — Dossier | formulaire, pièces, mandat | une offre **Dossier** ou **Pack** passe au statut **Payé** |
| 3 — Suivi | état du dépôt, échanges | validation finale du dossier par le candidat |

L'ouverture de l'étape 2 est **une conséquence du statut**, pas une action
manuelle : c'est le passage à `paye` (règles V79) qui l'ouvre. Un administrateur
peut l'ouvrir à la main, mais cela s'inscrit au journal comme une dérogation.

### 2.3 Relier la demande au compte — invitation à usage unique

**Jamais par correspondance d'adresse e-mail.** Une demande est déposée par un
formulaire public : l'adresse n'est pas vérifiée, et un tiers peut déposer pour
autrui. Rattacher sur cette base donnerait à n'importe qui le dossier d'un
homonyme — ou d'une adresse qu'il contrôle.

**Le circuit :**

1. L'administration marque la demande « Payé ».
2. La plateforme crée une **invitation** : jeton aléatoire, **usage unique**,
   expiration courte (72 h), lié à `demande_id`.
3. Un e-mail part vers l'adresse de la demande, avec le lien.
4. Le praticien ouvre le lien : s'il n'a pas de compte, il le crée ; s'il en a
   un, il se connecte. **Dans les deux cas, c'est l'ouverture du lien qui lie le
   compte à la demande**, et le jeton est consommé.
5. Le parcours EVC s'ouvre à l'étape 1 ou 2 selon l'offre payée.

Table `invitation_parcours` : `jeton_hache`, `demande_id`, `expire_le`,
`consomme_le`, `consomme_par`. **Le jeton est stocké haché**, comme un mot de
passe : une fuite de la base ne doit pas livrer les invitations en cours.

---

## 3. Les annales — lot 1

### 3.1 Modèle

`annale` : `session` (année), `specialite`, `epreuve`, `titre`, `cle_fichier`,
`source` (« Publié par le Centre national de gestion »), `recupere_le`,
`publiee`, `depose_par`, `depose_le`.

La mention affichée reprend la règle de la page publique :

> Source : Centre national de gestion — constaté le [date]

**Non cliquable, et aucune adresse.** Le composant `MentionSource` écrit pour la
page publique se réutilise tel quel.

### 3.2 Filigrane et journal

Chaque PDF téléchargé porte **le nom et l'adresse du candidat**, plus la date.
C'est un dissuasif, pas une protection : un filigrane se retire. Sa vraie
fonction est de rendre une fuite **attribuable**, et de le faire savoir.

Le filigrane est appliqué **à la volée, à chaque téléchargement** — jamais stocké
par candidat, ce qui multiplierait les fichiers. openpdf, déjà au projet, le fait.

`telechargement_annale` : `annale_id`, `user_id`, `telecharge_le`, `ip`. Une
ligne par téléchargement, conservée comme une pièce de sécurité.

**Le coût à annoncer** : filigraner à la volée consomme du processeur, et vous
avez **1 CPU**. Un PDF de 50 pages prend de l'ordre de la seconde. À dix
téléchargements simultanés, le site ralentit. Le lot 1 devra mesurer cela.

### 3.3 Livrable seul

L'étape 1 ne dépend ni du formulaire, ni des pièces, ni de la facturation. **Elle
peut partir en premier**, à condition que la décision de stockage (§ 1.3) soit
prise.

---

## 4. Le formulaire — lot 2

### 4.1 Une structure versionnée

Vous remplacerez ce formulaire par le formulaire officiel. Une table à colonnes
figées obligerait alors à une migration de schéma **et** à une reprise des
dossiers déjà saisis.

**Deux tables :**

- `formulaire_modele` : `version`, `schema` (JSON : champs, libellés, types,
  obligatoires), `actif_du`, `actif_au`.
- `dossier_evc` : `user_id`, `modele_version`, `reponses` (JSON clé → valeur),
  `statut` (`brouillon`, `soumis`, `a_corriger`, `valide`, `depose`).

Un dossier **garde le numéro de modèle avec lequel il a été rempli**. Un nouveau
modèle ne touche pas aux dossiers existants ; l'administration décide, dossier par
dossier, de demander une mise à jour. **C'est ce qui permet le remplacement sans
perte.**

### 4.2 Annotations

`annotation_champ` : `dossier_id`, `champ`, `etat` (`valide` / `a_corriger`),
`commentaire`, `auteur`, `le`. Affichées en face de chaque champ côté candidat.

### 4.3 Le mandat

`mandat_signe` : `dossier_id`, `texte_version`, `accepte_le`, `ip`,
`agent_utilisateur`. Le **texte** du mandat est versionné comme le formulaire :
on doit pouvoir prouver *ce qui a été accepté*, pas seulement *qu'on a accepté*.

> **Question ouverte, déjà signalée pour les CGV.** Une case cochée a une valeur
> probante faible. Un document signé et téléversé est plus solide. Le chantier
> facturation produira des devis signés : **mutualiser les deux** est la voie que
> je recommande, mais c'est votre décision — et celle de votre conseil.

### 4.4 Export PDF

Le dossier complet, mis en page pour le dépôt. `PdfGeneratorService` + un gabarit
Thymeleaf. L'export porte le numéro de modèle et la date, pour qu'on sache
toujours quelle version a été déposée.

---

## 5. Les pièces justificatives — lot 3

### 5.1 Stockage

**Privé et chiffré au repos**, selon la décision du § 1.3. Aucune adresse
publique, jamais. Téléchargement par jeton à expiration courte, comme les
documents existants.

### 5.2 Accès et traçabilité

Lecture réservée au **candidat propriétaire**, à **SUPER_ADMIN** et à
**ADMIN_MOBILITE**. `consultation_piece` : qui, quoi, quand — **y compris les
consultations administratives**. Un journal qui n'enregistre que les accès du
candidat ne sert à rien : c'est le regard interne qu'il faut pouvoir justifier.

### 5.3 Liste attendue

`piece_attendue` : `cle`, `libelle`, `obligatoire`, `ordre`, `aide`. Gérée depuis
l'administration, comme les sections de la page publique.

### 5.4 Types, taille et antivirus

`ControleFichier` plafonne à **10 Mo** et vérifie la cohérence extension / type
MIME. À étendre aux pièces : PDF, JPG, PNG.

**L'antivirus : réponse honnête — rien n'existe, et ce n'est pas simple ici.**
Render n'offre pas de service d'analyse managé, et faire tourner ClamAV dans le
conteneur applicatif demanderait de la mémoire (environ 1 Go pour la base de
signatures) et un rafraîchissement régulier — sur une instance de 2 Go qui sert
déjà le site. Trois voies :

1. **Un service d'analyse par API** (VirusTotal, Cloudmersive) : simple, mais une
   dépendance externe de plus, et des pièces d'identité envoyées à un tiers — ce
   qui pose sa propre question RGPD.
2. **Un conteneur ClamAV séparé** sur Render : propre, mais c'est une instance de
   plus à payer.
3. **Différer**, en s'appuyant sur le contrôle de type, le plafond de taille, et
   le fait que ces fichiers ne sont **jamais exécutés ni servis publiquement**.

**Je recommande la voie 3 pour le lot 3, et la voie 2 quand le volume le
justifiera.** Le risque réel ici n'est pas l'infection de notre serveur — c'est
qu'un administrateur ouvre une pièce vérolée sur son poste.

### 5.5 Conservation

Les pièces suivent le dossier, donc les deux régimes existants : **24 mois** sans
paiement, **60 mois** après la fin de prestation. **Les pièces sont supprimées,
pas anonymisées** — un passeport ne s'anonymise pas. La ligne reste, le fichier
part. **Les factures, elles, resteront 10 ans** : c'est une troisième durée, déjà
identifiée dans l'analyse facturation.

---

## 6. Sessions et calendrier — lot 4

### 6.1 Ce qui existe

`evc_periodes` : libellé, dates de début et de fin, spécialités non ouvertes,
adresse de source (non affichée), visibilité, ordre. Plus le réglage
**« Calendrier des commissions, constaté le »**.

### 6.2 Ce qu'il faut ajouter

| Information | Où |
|---|---|
| Dates d'inscription | nouvelles colonnes sur `evc_periodes` |
| Dates des épreuves | idem |
| Date des résultats | idem |
| Spécialités et postes ouverts | table `evc_postes_ouverts` : session, spécialité, nombre |
| Date de constat **par source** | colonne sur chaque entité, et exposée par l'API publique |

**Le dernier point corrige une approximation d'aujourd'hui** : toutes les
mentions de source affichent la même date, celle du calendrier. Chaque lien porte
pourtant déjà sa propre date de vérification — l'API publique ne l'expose pas.
Petit ajout, vraie amélioration de justesse.

---

## 7. Les statuts

### 7.1 Articulation avec la V79

Les règles déjà planifiées restent, et le parcours s'y greffe :

```
Nouveau → Qualifié → Devis envoyé → Payé → En cours → Terminé
                                      │        │
                                      │        └─ étape 3 : suivi
                                      └─ ouvre l'étape 2 (dossier)
```

### 7.2 Le statut « Déposé »

Il s'intercale **entre « En cours » et « Terminé »** :

```
En cours → Déposé → Terminé
```

« Déposé » marque la fin du mandat — c'est la clause de l'article 3.8. Il **exige
la validation finale du candidat** : le serveur refuse le passage si
`mandat_signe` est absent, exactement comme « Payé » exige un mode de paiement.

« Terminé » reste ce qui déclenche `prestation_fin_le`, donc le délai de
conservation.

**Conséquence sur la V79 : prévoir « Déposé » dès maintenant dans le graphe**,
même inutilisé. L'ajouter après coup demanderait de retoucher la contrainte CHECK
de `accompaniment_requests.statut` — et une migration appliquée ne se modifie pas.

---

## 8. Découpage

| Lot | Contenu | Migrations | Estimation |
|---|---|---|---|
| **0** | Décision de stockage + magasin privé (§ 1.3) | — | **2 à 4 j** selon l'option |
| **1** | Parcours cumulables, invitation à usage unique, annales, filigrane, journal | V80–V81 | **4 à 6 j** |
| **2** | Formulaire versionné, annotations, mandat, export PDF | V82–V83 | **5 à 7 j** |
| **3** | Pièces justificatives, liste gérée, traçabilité des consultations | V84 | **4 à 5 j** |
| **4** | Sessions et calendrier enrichis, date de constat par source | V85 | **2 à 3 j** |

**Total : 17 à 25 jours**, hors décisions et hors antivirus.

**Le lot 0 n'était pas dans votre découpage, et il conditionne tout le reste** :
sans magasin privé, ni les annales ni les pièces ne peuvent être livrées.

---

## 9. Risques

| Risque | Conséquence | Parade |
|---|---|---|
| **Cloudinary public** | pièces d'identité lisibles par qui connaît l'adresse | lot 0, et **aucune pièce versée avant** |
| **Filigrane sur 1 CPU** | site ralenti aux heures de pointe | mesurer au lot 1 ; file d'attente si besoin |
| **Invitation par e-mail** | lien intercepté = accès au dossier | usage unique, 72 h, jeton haché, et le lien **ne donne pas** accès aux pièces sans connexion |
| **Remplacement du formulaire** | dossiers saisis perdus | versionnement du modèle, dès le lot 2 |
| **Mandat par case cochée** | valeur probante faible en litige | § 4.3 — décision à prendre avec votre conseil |
| **Antivirus absent** | poste administrateur exposé | § 5.4 |
| **Trois durées de conservation** (24 / 60 mois, 10 ans) | effacement trop tôt ou trop tard | une règle par entité, et **un test qui la prouve** |

---

## 10. Les décisions qui vous reviennent

1. **Le magasin privé** (§ 1.3) — S3 compatible, Cloudinary `authenticated`, ou
   autre. **Bloquant pour tout.**
2. **Le mandat** : case cochée ou document signé (§ 4.3), et mutualisation avec
   le chantier facturation.
3. **L'antivirus** : différer, API tierce, ou conteneur dédié (§ 5.4).
4. **L'ordre réel des lots.** Vous demandez annales d'abord ; c'est le bon choix
   si le lot 0 est tranché rapidement. Sinon, le lot 4 — sessions et calendrier —
   ne dépend d'aucun stockage et peut passer devant.
5. **Le périmètre de l'étape 1** : les annales sont-elles ouvertes à tout compte
   créé après une demande, ou seulement après un premier paiement ? Votre texte
   dit « dès la création du compte qui suit une demande d'accompagnement » — donc
   gratuitement. **Je le signale parce que c'est de la valeur donnée avant tout
   encaissement**, et que c'est un choix commercial, pas technique.
