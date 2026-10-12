# Aperçus des e-mails « Exercer en France »

Ouvrez les deux fichiers `.html` de ce dossier dans votre navigateur.

| Fichier | Qui le reçoit | Quand |
|---|---|---|
| `accuse-de-reception.html` | le praticien | dès le dépôt de sa demande |
| `alerte-administration.html` | super admin et admin Mobilité **actifs** | dès le dépôt |

## Ils ne sont pas fabriqués à la main

Ils sortent du **même code** que les messages réellement envoyés, par le même chemin
(`EmailService` → `MimeMessage`). Un aperçu produit par un script à part finirait par montrer
autre chose que ce qui part : le script vieillit, le message change, personne ne s'en aperçoit.

Pour les régénérer après une modification du gabarit :

```sh
cd backend && ./mvnw test -Dtest=ApercusEmailAccompagnementTest
```

Les fichiers sont écrits dans `backend/target/apercus-email/`.

## Les données sont fictives

« Dr Samba Nkounkou », « samba.nkounkou@exemple.cg », « Congo ». Volontairement
reconnaissables comme telles.

## Deux choix à relire

**L'accusé ne porte ni prix ni lien d'action.** Les trois offres sont « sur devis » : le
praticien ne sait pas ce qu'il vient d'engager. Le message dit ce qui a été reçu et sous quel
délai on répond. Il reprend la mention « Optimi Santé n'est pas un organisme officiel » —
c'est le premier message que reçoit quelqu'un qui pourrait nous confondre avec le CNG.

**L'alerte interne ne porte PAS le message du praticien.** Ce texte parle de sa situation : il
a sa place dans la fiche, derrière une authentification et soumis à la durée de conservation,
pas dans une boîte aux lettres que personne n'anonymise. L'alerte dit qu'il y a quelque chose
à traiter, et où.

## Ce qui se règle sans redéploiement

| Paramètre | Valeur initiale |
|---|---|
| `accompagnement.delai_reponse` | `sous 48 h ouvrées` |
| `accompagnement.adresse_reponse` | `contact@optimisante.com` |

Posés par la migration V75 dans `parametres_plateforme`. Le délai est un engagement
commercial, pas une constante technique : il se révise pendant les congés ou quand l'équipe
grandit.

L'adresse de réponse est posée en `Reply-To` **et** écrite dans le texte. ⚠️ `contact@optimisante.com`
est une valeur de départ **à confirmer** : il faut que cette boîte soit réellement relevée.
