# Registre des incidents de sécurité — Optimi Santé

Ce fichier est **suivi par git**. Un registre qui n'existe que sur un poste n'est pas un
registre : c'est une note. Il se complète au moment du constat, pas après coup, et il n'est
jamais réécrit — un fait corrigé s'ajoute, daté, sous le fait d'origine.

**Ce que porte chaque entrée, dans cet ordre, et les sept :**

1. **Découverte** — date, et comment on l'a su.
2. **Nature** — ce qui est exposé, à qui, par quel chemin. Sans euphémisme.
3. **Preuve** — le test fait, sa date, sa réponse. Un soupçon n'est pas un incident ; un
   incident sans preuve écrite n'est pas défendable.
4. **Accès constatés** — le chiffre, ou l'aveu qu'on ne peut pas le savoir, avec la raison.
5. **Mesures** — chacune datée **du jour de son déploiement en production**, pas du jour où le
   code a été écrit. Les deux dates diffèrent, parfois de six jours.
6. **Notification CNIL** — faite ou non, et le raisonnement. Une violation de données
   personnelles se notifie sous 72 heures ; l'absence de notification est une décision qui
   s'écrit.
7. **Suivi** — ce qui reste ouvert, et quel lot le fermera.

La date de fin de migration de l'incident n° 1 sera renseignée par **`SP‑5`** (retrait du
repli, suppression chez Cloudinary, demande écrite à leur support pour leurs sauvegardes).

---

## Incident n° 1 — Documents accessibles publiquement sur Cloudinary

Découverte : 29/10/2026, lors de l'analyse du chantier brochures.

Nature : les documents non publics (pièces des candidats : passeport, diplôme, inscription à l'ordre ; reçus ; conventions ; attestations ; relevés de reversement) sont stockés en mode public. Un document est lisible par quiconque connaît son adresse. Les adresses contiennent un identifiant aléatoire, donc difficile à deviner, mais elles étaient renvoyées au navigateur de l'utilisateur au moment du dépôt.

Preuve : test du 29/10/2026 sur un reçu de commande de test, réponse 200 sans signature.

Accès constatés : impossible à vérifier (offre gratuite, pas de journaux d'accès). Aucun indice d'accès malveillant à ce jour.

Mesures :

- **29/10/2026** : BR-1 déployé (la brochure d'origine du partenaire n'est plus diffusée au public) ;
- DOC-PRIV lancé : nouveaux dépôts en mode privé, arrêt de l'envoi des adresses au navigateur, puis migration des fichiers existants.
- **`DP‑4` déployé en deux fois**, depuis le même `main fb508cd` :
  - **06/11/2026**, miroir backend (`aa4e334`) — plus aucune clé de stockage envoyée au navigateur ;
  - **12/11/2026**, miroir frontend (`171b086`) — l'écran d'inscription ne manipule plus de clé.

  Les six jours d'écart sont réels : la moitié backend de la mesure a protégé la production
  avant que la moitié frontend ne soit en ligne.
- **Reste ouvert** : la migration des fichiers déjà déposés en mode public. Tant qu'elle n'a
  pas eu lieu, les documents antérieurs au 06/11/2026 demeurent exposés. C'est l'objet de
  `SP‑1` à `SP‑5`.

Notification CNIL : non effectuée à ce stade (faille sans accès constaté). À réévaluer si un accès est découvert.

Suivi : date de fin de migration à compléter — elle viendra de `SP‑5`.

---

## Autres mesures de sécurité déployées

Ces déploiements ne répondent à aucun incident constaté : ce sont des fermetures de surface
faites avant qu'il n'en survienne un. Ils figurent ici pour que le registre donne l'état réel
des défenses à une date, et non la seule liste de ce qui a mal tourné.

- **15/11/2026**, les deux miroirs, depuis `main 5f2fb65` :
  - **`SEC‑3`** (miroir backend `5f9338e`, miroir frontend `07c297b`) — le dépôt anonyme du
    formulaire de partenariat est fermé et gardé ;
  - **`SEC‑1a`** (mêmes commits) — les routes d'IA sont plafonnées en débit.
- **27/11/2026**, miroir frontend (`9ee3113`), depuis `main d2e8b83` — `M1`. Sans portée de
  sécurité ; noté parce que la ligne précédente laisserait croire que rien n'a été déployé
  depuis le 15 novembre.
