# « Exercer en France » — textes v2, positionnement agence

Écrit pour : Mounir, qui saisira ces textes dans l'administration.

Optimi Santé est une **agence d'accompagnement privée**. Elle prend en charge la
procédure EVC du candidat jusqu'au dépôt de son dossier, sur mandat du candidat.
La page ne renvoie plus vers aucun site extérieur.

---

## 1. Inventaire des liens, et qui peut les retirer

### 1.1 Les six liens de la table (onglet Liens)

| Clé | Libellé actuel | Destination | Qui peut le retirer |
|---|---|---|---|
| `site_cng` | Site officiel du CNG | cng.sante.fr | **code** (fait) |
| `session_evc` | Prochaine session EVC | cng.sante.fr | **code** (fait) |
| `calendrier_commissions` | Voir le calendrier officiel | cng.sante.fr | **code** (fait) |
| `fellowship` | En savoir plus (site officiel) | cng.sante.fr | **code** (fait) |
| `refugies` | Dispositions spécifiques | cng.sante.fr | **code** (fait) |
| `formulaire_renouvellement_aep` | Formulaire de renouvellement | demarche.numerique.gouv.fr | **code** (fait) |

> **Vous ne pouviez pas le faire vous-même ce soir.** L'onglet Liens n'a **pas de
> bouton Supprimer** — ni l'écran, ni l'API : il n'existe aucune route de
> suppression pour un lien. Vous pouviez seulement en vider l'adresse, ce qui
> aurait produit un `<a href="">` : un lien toujours cliquable, qui recharge la
> page. Pire que le problème.
>
> **Le correctif est donc entièrement dans le code** : la page ne rend plus aucun
> lien, quelle que soit la table. Les six entrées restent en base — elles servent
> à votre suivi interne des sources — mais rien ne les rend cliquables.

### 1.2 Les adresses « source » des périodes

Les trois périodes portent une `sourceUrl` vers cng.sante.fr. **Elles n'ont jamais
été affichées publiquement** : aucune action n'est nécessaire. Vous pouvez les
garder comme trace interne.

### 1.3 Ailleurs dans la plateforme

| Emplacement | Nature | Action |
|---|---|---|
| Courriels (accusé, alerte) | Aucun lien externe. L'accusé mentionne le CNG en TEXTE, dans la clause de non-garantie. | **Rien à faire** |
| CGV, articles 3.8 et 3.9 | Mentionnent le CNG et les ARS en texte, sans lien | **Rien ce soir** — voir § 4 |
| Assistant IA, FAQ, SEO | Aucune référence au CNG trouvée | **Rien à faire** |
| Code backend (seed, enums, commentaires) | Mentions en commentaires et libellés, pas des liens | **Rien à faire** |

---

## 2. Ce qui remplace les liens

Chaque information officielle porte désormais une mention **non cliquable** :

> Source : Centre national de gestion — constaté le 3 septembre 2026

La date vient du champ **« Calendrier des commissions, constaté le »**, onglet
Périodes. C'est vous qui la tenez à jour ; la mention disparaît si elle est vide,
parce que « constaté le » suivi d'un blanc vaut moins que le silence.

**Deux points à arbitrer :**

1. **Le formulaire de renouvellement AEP** ne vient pas du CNG mais d'un service
   de l'État (`demarche.numerique.gouv.fr`). Sa mention dit donc « Source :
   service public — demarche.numerique.gouv.fr ». Dites-moi si vous préférez une
   autre formulation.
2. **Une seule date pour toutes les sources.** Chaque lien porte déjà sa propre
   date de vérification (bouton « Marquer comme vérifié »), mais l'API publique ne
   l'expose pas. L'utiliser donnerait une mention juste par source — **petit ajout
   de code, à prévoir pour le lot 4**, pas ce soir.

---

## 3. Les textes, ancien et nouveau

### 3.1 Bandeau — `bandeau_non_officiel`

**INCHANGÉ, mot pour mot.** Ne le retouchez pas.

| Aujourd'hui | Demain |
|---|---|
| *Optimi Santé n'est pas un organisme officiel.* Les demandes se déposent auprès du Centre National de Gestion (CNG) ou des Agences Régionales de Santé (ARS). Nous vous aidons à comprendre les règles et à préparer un dossier complet. | **identique** |

Seul le lien « Site officiel du CNG » qui le suivait a disparu.

---

### 3.2 Les EVC — `evc`

| Aujourd'hui | Demain |
|---|---|
| Les EVC sont la première étape de la procédure d'autorisation d'exercice. Elles concernent les praticiens de toute nationalité dont le diplôme a été obtenu hors Union européenne et hors Espace économique européen, et qui leur permet d'exercer dans leur pays de délivrance. À l'issue de la procédure, les praticiens qui obtiennent l'autorisation de plein exercice peuvent exercer leur profession en France.<br><br>*(suivi du bouton « Prochaine session EVC » → cng.sante.fr)* | Les EVC sont la première étape de la procédure d'autorisation d'exercice. Elles concernent les praticiens de toute nationalité dont le diplôme a été obtenu hors Union européenne et hors Espace économique européen, et qui leur permet d'exercer dans leur pays de délivrance. À l'issue de la procédure, les praticiens qui obtiennent l'autorisation de plein exercice peuvent exercer leur profession en France.<br><br>**## Nous prenons en charge votre dossier**<br>Nous prenons en charge votre dossier, de sa préparation jusqu'à son dépôt. Vous nous transmettez vos pièces ; nous vérifions votre éligibilité, nous constituons le dossier et nous le déposons dans les délais. |

---

### 3.3 L'AEP — `aep`

Un seul passage change : le bloc des spécialités relevant des ARS.

| Aujourd'hui | Demain |
|---|---|
| **## Spécialités relevant des ARS**<br>Les autres spécialités relèvent des ARS : consultez les dates de l'ARS concerné[e]. | **## Spécialités relevant des ARS**<br>Les autres spécialités relèvent des ARS, dont les calendriers sont propres à chaque région. Indiquez-nous votre spécialité et la région visée : nous vérifions les dates applicables et nous vous disons ce qui est ouvert. |

> « Consultez les dates » envoie le candidat chercher seul ce que nous facturons
> de faire pour lui.

---

### 3.4 Le dossier — `dossier`

**INCHANGÉ.** Aucun lien, aucun renvoi. Ce texte est déjà le bon : il décrit ce
que nous vérifions.

Une phrase pourrait être ajoutée en fin d'introduction :

> Nous prenons en charge votre dossier, de sa préparation jusqu'à son dépôt.

---

### 3.5 La commission — `cae`

| Aujourd'hui | Demain |
|---|---|
| **## Calendrier des commissions**<br>Publié et mis à jour par le CNG. **Les dates ne sont communiquées que sur le site officiel.**<br><br>Odontologie : des commissions sont souvent programmées vers septembre-octobre. Le candidat est contacté lors de l'instruction de son dossier. | **## Calendrier des commissions**<br>Le calendrier est publié et mis à jour par le Centre national de gestion. **Nous le suivons pour vous et nous vous prévenons des dates qui concernent votre spécialité.**<br><br>Odontologie : des commissions sont souvent programmées vers septembre-octobre. Le candidat est contacté lors de l'instruction de son dossier. |

> La phrase supprimée est la plus contraire au nouveau positionnement : elle dit
> au candidat que l'information est ailleurs, et que nous n'y pouvons rien.

---

### 3.6 Autres voies — `autres_voies`

| Aujourd'hui | Demain |
|---|---|
| **## Fellowship / autorisation temporaire**<br>Pour parfaire sa formation médicale en France, dans le cadre du dispositif proposé par le ministère de la Santé.<br>*(suivi du lien « En savoir plus (site officiel) »)* | **## Fellowship / autorisation temporaire**<br>Pour parfaire sa formation médicale en France, dans le cadre du dispositif proposé par le ministère de la Santé. Ce dispositif obéit à ses propres conditions : dites-nous si vous l'envisagez, nous vous indiquons ce qui s'applique à votre situation. |
| **## Praticiens réfugiés**<br>*(texte inchangé)*<br>*(suivi du lien « Dispositions spécifiques (site officiel) »)* | **## Praticiens réfugiés**<br>*(texte inchangé, le lien en moins)* |

---

### 3.7 Nos accompagnements — `accompagnements`

| Aujourd'hui | Demain |
|---|---|
| Optimi Santé ne garantit pas l'issue des épreuves ni des décisions administratives, qui relèvent des autorités compétentes. | **Nous prenons en charge votre dossier, de sa préparation jusqu'à son dépôt.** Optimi Santé ne garantit pas l'issue des épreuves ni des décisions administratives, qui relèvent des autorités compétentes. |

> La clause de non-garantie **reste**. La phrase de prise en charge la précède :
> ce que nous faisons d'abord, ce que nous ne promettons pas ensuite.

---

## 4. Ce qui contredit le nouveau positionnement — à traiter vendredi

**Je ne touche à rien ce soir.** Voici ce que j'ai relevé.

### CGV, article 3.8

> « Optimi Santé **informe, oriente, vérifie et suit** le praticien dans ses
> démarches d'accès à l'exercice en France. »

Cette énumération décrit un rôle de conseil. Le nouveau positionnement va plus
loin : **prise en charge jusqu'au dépôt, sur mandat du candidat**. Déposer un
dossier au nom d'un tiers n'est pas « orienter » — c'est un mandat, et il mérite
d'être nommé comme tel dans les CGV, avec ce qu'il implique (procuration,
responsabilité en cas de pièce manquante, délais).

### Titres des offres

« Orientation EVC » annonce une orientation, pas une prise en charge. Si l'offre
à 89 € inclut désormais le dépôt, son nom et ses points doivent le dire. **À
trancher avec vous** : le périmètre de l'offre à 89 € change-t-il, ou reste-t-il
une orientation vendue avant la prise en charge ?

### Rien d'autre

Aucune mention de « agréé », « mandaté par l'État » ou de comparaison avec un
organisme public n'existe dans le code ou les textes. Le bandeau dit l'inverse,
et il est conservé.

---

## 5. Blocs à créer — lot 4 du chantier Parcours EVC

Informations aujourd'hui atteintes par un lien, et qui devront vivre comme blocs
de texte gérés par l'administration :

| Information | Où elle vivrait | État |
|---|---|---|
| Dates de la prochaine session EVC | section `evc`, bloc `##` | **saisissable dès maintenant** |
| Calendrier des commissions | section `cae`, bloc `##` | **saisissable dès maintenant** |
| Conditions du fellowship | section `autres_voies`, bloc `##` | **saisissable dès maintenant** |
| Liste des pièces à fournir | section `dossier` | **déjà présent** |
| Date de constat par source | mention automatique | **code — lot 4** |

Les quatre premiers ne demandent **aucun développement** : l'éditeur de sections
accepte déjà les blocs `##`. Vous pouvez les écrire ce soir si vous le souhaitez.

---

## 6. Textes des offres

À saisir dans l'administration, onglet **Offres & tarifs** (écriture réservée au
super administrateur). Un point par ligne.

### 6.1 Orientation EVC — 89 € *(périmètre inchangé : un diagnostic, sans dépôt)*

**Sur-titre** : `AVANT LES EVC` · **Bouton** : `Être orienté`

| Aujourd'hui | Demain |
|---|---|
| Vérification de votre éligibilité<br>Explication de la procédure et du calendrier<br>Aide à l'inscription à la session<br>Orientation vers des ressources de préparation | Diagnostic de votre éligibilité, profession et spécialité comprises<br>Explication de la procédure et du calendrier qui vous concernent<br>Aide à l'inscription à la session<br>Orientation vers des ressources de préparation<br>**Ne comprend pas le dépôt de votre dossier**<br>**Les 89 € sont déduits si vous poursuivez avec « Dossier & démarches »** |

> Deux points ajoutés, et le premier compte autant que le second : dire ce qu'une
> offre **ne** comprend **pas** prévient le litige que la déduction, seule, ne
> réglerait pas. Un praticien qui paie 89 € en croyant son dossier déposé a été
> trompé, même remboursé ensuite.

### 6.2 Dossier & démarches — 450 €

| Aujourd'hui | Demain |
|---|---|
| Vérification de la complétude du dossier<br>Relecture des conditions d'encadrement<br>Liste personnalisée des pièces à fournir<br>Suivi jusqu'à la commission | Vérification de la complétude du dossier<br>Relecture des conditions d'encadrement<br>Liste personnalisée des pièces à fournir<br>**Dépôt de votre dossier en votre nom, sur mandat**<br>Suivi jusqu'à la commission |

### 6.3 Mobilité & installation — 650 € *(inchangée)*

Aucun changement : cette offre ne dépose aucun dossier.

### 6.4 Pack Dossier + Mobilité — 990 €

Les points des deux offres réunies, **dépôt sur mandat compris** :

```
Vérification de la complétude du dossier
Relecture des conditions d'encadrement
Liste personnalisée des pièces à fournir
Dépôt de votre dossier en votre nom, sur mandat
Suivi jusqu'à la commission
Accompagnement des démarches de visa et de titre de séjour
Conseils pour le logement et l'assurance
Mise en relation avec des établissements d'accueil
Démarches auprès de l'Ordre
```

### 6.5 Ce qu'aucune offre ne dit

Ni « organisme officiel », ni « agréé », ni « mandaté par l'État », ni aucune
garantie de résultat. La mention sous les offres reste.

> **Attention au mot « mandat ».** Il désigne le mandat que **le candidat** nous
> donne, jamais un mandat public. Le point est rédigé « en votre nom, sur
> mandat » et non « mandatés », qui se lirait à contresens.

---

## 7. CGV, article 3.8 — proposition de réécriture

**À valider avant tout codage.** Les exclusions relatives aux séjours de
formation sont conservées ; l'article 3.9 (TVA) ne change pas.

> ### 3.8 Accompagnement « Exercer en France »
>
> **Périmètre.** Optimi Santé accompagne le praticien dans la procédure
> d'autorisation d'exercice en France. Selon la prestation souscrite,
> l'accompagnement comprend la préparation du dossier, la vérification de sa
> complétude et de sa conformité, et **le dépôt du dossier au nom du praticien,
> sur son mandat exprès**. Le mandat est donné par écrit, pour un dépôt
> déterminé ; il ne confère à Optimi Santé aucune autre représentation.
>
> **Obligations du praticien.** Le praticien fournit des pièces **exactes,
> authentiques et complètes**, dans les délais qui lui sont indiqués. Il répond
> aux demandes de compléments dans les mêmes délais. **Il valide le dossier avant
> son dépôt** : aucun dépôt n'est effectué sans cette validation. La production
> de documents inexacts ou falsifiés entraîne la fin immédiate de
> l'accompagnement, sans remboursement, sans préjudice des suites légales.
>
> **Limites.** Optimi Santé est tenue d'une **obligation de moyens**. Elle n'est
> pas un organisme officiel et ne délivre aucune autorisation d'exercice : les
> décisions relèvent exclusivement des autorités compétentes. **Aucun résultat
> n'est garanti**, ni quant à l'issue des épreuves, ni quant aux décisions
> administratives, ni quant aux délais d'instruction. La responsabilité d'Optimi
> Santé ne saurait être engagée lorsqu'un retard, un rejet ou une irrégularité
> résulte d'une pièce fournie tardivement, incomplète ou inexacte, ni d'une
> évolution des règles ou du calendrier décidée par l'autorité compétente.
>
> **Fin du mandat.** Le mandat prend fin **au dépôt du dossier**, ou par
> résiliation de l'une ou l'autre partie notifiée par écrit. En cas de
> résiliation avant le dépôt, les prestations déjà réalisées restent dues. Les
> pièces transmises sont restituées ou supprimées à la demande du praticien, sous
> réserve des obligations légales de conservation.
>
> **Ce que l'accompagnement ne comprend pas.** Au titre du seul accompagnement
> « Exercer en France », et sauf souscription distincte d'une autre prestation,
> les tarifs annoncés ne comprennent ni préparation pédagogique (notamment banque
> de questions, examens blancs ou cours), ni traduction, ni assurance, ni
> fourniture de logement. Ces exclusions sont sans effet sur les services
> organisés autour des séjours de formation, qui font l'objet de l'article 3.
>
> **Prix.** Les prix sont affichés toutes taxes comprises, en euros. L'équivalent
> indiqué en francs CFA est donné à titre indicatif, calculé à la parité fixe de
> 655,957 FCFA pour un euro et arrondi : il ne constitue pas un prix de vente, la
> facturation et le paiement s'effectuant en euros. Le régime de TVA applicable
> est précisé à l'article 3.9.
>
> **Déduction.** Lorsque le praticien souscrit la prestation « Orientation EVC »
> puis, par la suite, la prestation « Dossier & démarches », le montant déjà
> réglé au titre de l'Orientation EVC est déduit du prix de cette seconde
> prestation. Cette déduction est appliquée par Optimi Santé sur le devis établi
> pour la seconde prestation ; elle n'est ni automatique ni cumulable avec une
> autre remise, et ne donne lieu à aucun remboursement si la seconde prestation
> n'est pas souscrite.

### 7.1 Quatre ajouts qui viennent de moi

Votre liste ne les mentionnait pas, et je les crois nécessaires :

1. **« Le mandat est donné par écrit, pour un dépôt déterminé. »** Un mandat oral
   et général est à la fois contestable et dangereux. Écrit et borné, il protège
   les deux parties.
2. **« Aucun dépôt n'est effectué sans cette validation. »** Votre liste faisait
   de la validation une obligation du candidat ; en faire aussi **notre**
   engagement est ce qui équilibre la clause.
3. **Les délais d'instruction** parmi les choses non garanties. C'est le premier
   motif de réclamation sur ce type de prestation.
4. **L'évolution des règles décidée par l'autorité.** Un calendrier déplacé n'est
   pas une faute d'Optimi Santé.

### 7.2 Ce que je ne peux pas trancher

**Le mandat écrit doit exister quelque part.** Les CGV peuvent l'exiger, mais
aucune procuration n'est aujourd'hui produite ni archivée par la plateforme.
Trois options :

- une case cochée et horodatée au devis — faible valeur probante ;
- **un document signé, téléversé dans le dossier** — ce que je recommande ;
- un mandat papier conservé hors plateforme — simple, mais invisible du système.

À décider avec votre conseil, et à rattacher au chantier facturation, qui
produira déjà des devis signés.
