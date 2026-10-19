# Politique de confidentialité — paragraphe à ajouter

Rédigé pour **votre juriste**, à insérer dans la politique de confidentialité d'Optimi Santé.
Les durées correspondent à ce que la plateforme applique réellement ; elles sont modifiables
depuis l'administration sans redéploiement.

---

## Texte proposé

### Demandes d'accompagnement « Exercer en France »

**Finalité.** Lorsque vous déposez une demande d'accompagnement depuis la page
« Exercer en France », Optimi Santé traite vos données pour répondre à votre demande, établir
un devis, et assurer le suivi de la prestation si vous y souscrivez. Ces données ne sont
utilisées à aucune autre fin, et ne font l'objet d'aucune décision automatisée.

**Base légale.** Votre consentement, recueilli lors de l'envoi du formulaire, pour la prise de
contact et l'établissement d'un devis. L'exécution du contrat, puis le respect de nos
obligations légales, lorsque vous souscrivez à une prestation.

**Données collectées.** Nom et prénom ; adresse électronique ; numéro de téléphone
(WhatsApp) ; pays de résidence ; profession et spécialité médicale ; étape de votre parcours
d'autorisation d'exercice ; prestation qui vous intéresse ; message libre que vous rédigez.

Votre profession et votre spécialité sont nécessaires pour vous orienter : les démarches ne
sont pas les mêmes pour un médecin, un chirurgien-dentiste, une sage-femme ou un pharmacien,
ni d'une spécialité à l'autre.

**Destinataires.** Les personnes habilitées d'Optimi Santé chargées de l'accompagnement des
praticiens. Vos données ne sont ni vendues, ni cédées, ni transmises à un tiers à des fins de
prospection. Aucune donnée n'est communiquée au Centre National de Gestion, aux Agences
Régionales de Santé ni à un établissement de santé sans votre accord exprès et préalable.

**Hébergement.** Les données sont hébergées au sein de l'Union européenne.

**Durée de conservation.**

- *Demande restée sans suite* : vos données sont anonymisées **vingt-quatre (24) mois** après
  le dernier échange avec vous. Ce délai court à compter de la dernière action sur votre
  dossier, et non de son dépôt : un dossier que nous suivons encore n'est pas effacé.
- *Prestation souscrite et réglée* : vos données sont conservées **cinq (5) ans** après la fin
  de la prestation, durée de la prescription commerciale.
- *Pièces comptables* (factures) : conservées **dix (10) ans**, conformément au code de
  commerce.

L'anonymisation est irréversible : votre nom, votre adresse électronique, votre numéro de
téléphone, votre message et les notes internes vous concernant sont effacés. Seules
subsistent des informations qui ne permettent plus de vous identifier — pays, profession,
spécialité, issue de la demande — à des fins statistiques.

**Vos droits.** Vous disposez d'un droit d'accès, de rectification, d'effacement, de
limitation et d'opposition, ainsi que d'un droit à la portabilité de vos données. Vous pouvez
retirer votre consentement à tout moment ; ce retrait ne remet pas en cause les traitements
déjà effectués. Pour exercer ces droits, écrivez à **[ADRESSE DE CONTACT RGPD]**. Vous pouvez
également introduire une réclamation auprès de la Commission Nationale de l'Informatique et
des Libertés (CNIL).

---

## Points à faire trancher par le juriste

1. **Les 24 mois.** La CNIL retient usuellement trois ans à compter du dernier contact pour un
   prospect. Vingt-quatre mois est plus protecteur ; à confirmer qu'une durée plus courte que
   l'usage ne pose pas de difficulté au regard de la relation commerciale.

2. **Les 5 ans après la prestation.** Retenus par analogie avec la prescription commerciale
   (article L. 110-4 du code de commerce). À confirmer, notamment si la prestation relève
   plutôt du droit de la consommation.

3. **Profession et spécialité médicale.** Ce ne sont pas des données de santé du praticien :
   elles décrivent son exercice, pas son état. À confirmer qu'elles ne relèvent pas de
   l'article 9 du RGPD.

4. **La mise en relation avec les établissements.** Transmettre un dossier à un établissement
   d'accueil est une communication à un tiers : elle exige un consentement distinct, recueilli
   au moment où elle a lieu, et non la case cochée au dépôt du formulaire.

5. **L'adresse de contact RGPD** reste à renseigner, ainsi que la désignation éventuelle d'un
   délégué à la protection des données.

---

## Ce que la plateforme applique déjà

| Règle annoncée | Mise en œuvre |
|---|---|
| Consentement obligatoire | Contrainte en base : une demande sans consentement **ni sa date** ne peut pas exister |
| 24 mois après la dernière activité | Colonne `derniere_activite_le`, mise à jour à chaque action du dossier |
| 5 ans après la prestation | Colonne `prestation_fin_le` ; sans elle, le délai ne court pas et rien n'est effacé |
| Anonymisation irréversible | Tâche quotidienne à 3 h 15, par lots de 100 |
| Ce qui subsiste | Pays, profession, spécialité, statut — vérifié par test |
| Traçabilité | Le journal d'audit enregistre l'anonymisation **sans aucune donnée personnelle**, et n'est jamais modifié |

Les deux durées sont des paramètres d'exploitation
(`rgpd.accompagnement.mois_avant_anonymisation` et
`rgpd.accompagnement.mois_conservation_apres_paiement`) : l'avis du juriste s'applique sans
redéploiement.
