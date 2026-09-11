# Demande d'extraction unique — à adresser à l'équipe de l'ancien site

> Ce message est volontairement court et chiffré. Il demande **une seule opération, une fois**,
> et met explicitement fin au trafic dont l'équipe se plaint. Adapter les noms et la formule
> d'appel, puis joindre `produits_incomplets.csv`.

---

**Objet :** Extraction unique — mettre fin définitivement aux requêtes sur votre serveur

Bonjour,

Vous nous avez signalé que nos interrogations de la base pèsent sur votre infrastructure, et
parfois la perturbent. Vous avez raison, et nous souhaitons y mettre fin — définitivement.

Notre migration est aujourd'hui terminée à plus de 90 % : **1 409 produits sur 1 519** sont
repris avec leur fiche et leur photo. Notre plateforme n'appelle plus votre serveur : plus
aucune page, plus aucune image. Le seul trafic restant vient des quelques extractions que nous
lançons encore à la main.

Il nous manque un dernier lot, que nous avons identifié précisément :

| Ce qui manque | Nombre de produits |
|---|---|
| Photo d'origine | 111 |
| Description | 64 |

Ces produits sont listés dans le fichier joint, avec leur SKU et, quand nous l'avons, leur
**identifiant WooCommerce d'origine** (65 des 111) pour vous éviter toute recherche.

## Ce que nous vous demandons

**Une seule opération, à votre convenance**, au choix parmi ces trois formes — de la plus simple
pour vous à la plus complète :

1. **Le plus simple** — l'export CSV natif de WooCommerce :
   *Produits → Exporter → « Toutes les colonnes » → Générer le CSV*.
   Deux minutes, aucune requête déclenchée de notre côté. Il contient les descriptions et les
   adresses des images.

2. **Ou, si vous préférez cibler** — uniquement les fichiers images des 111 références listées
   en pièce jointe. Quelques mégaoctets.

3. **Ou, si cela vous convient mieux** — une archive du dossier `wp-content/uploads`, ou une
   sauvegarde complète de la base. Dans ce cas, un `mysqldump --single-transaction` ne pose
   aucun verrou : votre site reste disponible pendant l'export.

## Ce que nous nous engageons à faire en retour

Dès réception, et **sans attendre la fin de notre exploitation** :

- nous retirons vos identifiants de notre configuration ;
- nous vous demandons de révoquer la clé API WooCommerce, et vous confirmons qu'aucun de nos
  scripts ne peut plus l'utiliser ;
- plus aucune requête ne part vers votre serveur, de façon définitive.

Une extraction unique vous coûte quelques minutes et met fin à une gêne récurrente. Nous
pensons que c'est l'intérêt des deux parties.

Bien cordialement,

*[Signature]*

---

## Si la demande n'aboutit pas

Le blocage est supportable, et il est utile de le savoir avant d'engager la discussion :

- **Sur les 111 produits sans photo, 109 sont « sur devis »** — sans prix ferme, ils ne se
  vendent pas en ligne directement. Seuls **2 produits** sont réellement vendus sans photo.
- **2 produits** parmi les incomplets ont déjà fait l'objet d'une commande.

Autrement dit : l'absence de ces images ne bloque aucune vente en ligne. Les voies de repli sont
donc ouvertes et sans urgence :

- reprendre les visuels des **catalogues fabricants** (la plupart de ces références sont des
  produits de marque) ;
- **photographier** les articles réellement détenus en stock ;
- pour les descriptions, les **rédiger** à partir des noms et références, qui sont déjà précis.

Dans tous les cas, aucune de ces voies ne passe par l'ancien serveur.
