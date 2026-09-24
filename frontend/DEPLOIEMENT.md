# Déploiement du site

## Le montage retenu : une seule origine

Le site est servi par Vercel sur `optimisante.com` ; l'API vit ailleurs, sur Render. Plutôt que
de faire dialoguer deux domaines, `vercel.json` relaie `/api/*` vers le backend. Pour le
navigateur, tout vient de `optimisante.com`.

```
navigateur ──► optimisante.com/            Vercel  (les pages)
            └► optimisante.com/api/v1/...  Vercel ──► Render (l'API)
```

**Pourquoi ce choix plutôt que d'appeler Render directement.**

- Aucune requête préalable : le navigateur n'a pas d'autorisation à demander avant chaque appel
  portant un en-tête, c'est-à-dire avant chacun de ceux que fait l'application.
- Aucune origine tierce n'est autorisée côté serveur, par construction — la liste blanche CORS
  reste une sécurité de secours, pas le mécanisme dont dépend le site.
- L'adresse de l'API n'est plus figée dans le bundle : changer d'hébergeur ne demande pas de
  reconstruire le site, seulement d'éditer ce fichier.

## Ce qu'il ne faut pas faire

**Ne renseignez pas `VITE_API_URL` sur Vercel.** Vide, l'application appelle `/api/v1` sur son
propre domaine, ce que le relais ci-dessus intercepte. La renseigner ferait appeler Render en
direct, donc repasserait par le CORS — et cette valeur étant figée à la construction, elle
imposerait de reconstruire le site à chaque changement d'adresse.

Cette variable existe pour un autre cas : héberger le site et l'API sur deux domaines réellement
distincts. Elle est documentée dans `.env.example`.

## La seconde règle de `vercel.json`

`/(.*)` vers `/index.html` est le repli d'une application à page unique : sans elle, ouvrir
directement `optimisante.com/formations/echographie` renverrait une erreur 404, faute de fichier
portant ce nom. Vercel sert les fichiers réellement présents avant d'appliquer les réécritures :
les scripts, les images et les feuilles de style ne passent pas par ce repli.

L'ordre compte. La règle `/api/*` vient en premier ; inversée, le repli happerait aussi les
appels à l'API et renverrait la page d'accueil à la place des données.

## Changer d'adresse d'API

Une seule ligne à modifier dans `vercel.json`, puis un redéploiement. Aucune reconstruction du
bundle n'est nécessaire, et le code ne change pas.
