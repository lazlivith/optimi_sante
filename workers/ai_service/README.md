# Service IA — Optimi Santé

Service Python (FastAPI) qui porte les fonctions d'intelligence artificielle de la plateforme.
Il est appelé **uniquement par le backend Java** (`AiClient`), jamais directement par le navigateur.

## Répartition des modèles

| Usage | Fournisseur | Pourquoi |
|---|---|---|
| Extraction documentaire (passeport, diplôme, inscription à l'Ordre…) | **Gemini** | Lecture native des images **et** des PDF, palier gratuit |
| Chat, rédaction, résumés | **Mistral** | Hébergement UE (argument RGPD), coût faible |

Tout passe par **LiteLLM** : changer de fournisseur revient à changer une variable
d'environnement (`EXTRACTION_MODEL`, `CHAT_MODEL`, `DRAFT_MODEL`), sans toucher au code —
Groq, Together, OpenRouter, Ollama local ou OpenAI fonctionnent avec la même signature.

## Sans clé API

Le service **démarre quand même**. `/health` annonce chaque capacité comme `configured: false`
et les endpoints concernés renvoient `503` avec un message explicite. Le backend relaie ce
message ; aucune autre fonction de la plateforme n'est bloquée.

## Endpoints

| Méthode | Chemin | Rôle |
|---|---|---|
| `GET` | `/health` | État du service et des capacités |
| `GET` | `/extract/types` | Types de documents et champs extraits |
| `POST` | `/extract` | `multipart` (`file`, `document_type`) → champs extraits + confiance + anomalies |
| `GET` | `/draft/types` | Types de brouillons disponibles |
| `POST` | `/draft` | `{draftType, context, extra}` → texte proposé |
| `POST` | `/summarize` | `{kind, context}` → résumé en puces |
| `POST` | `/chat` | `{question, history, userRole}` → réponse complète |
| `POST` | `/chat/stream` | Idem, en **SSE** (`event: delta` / `done` / `error`) |

## Sécurité

- Les outils du chat (commandes, dossiers) **rappellent le backend Java avec le JWT de
  l'utilisateur** : tenant, rôles et suppression logique restent appliqués. Le service
  n'accède jamais aux données personnelles par la base.
- L'extraction ne fait que **proposer** des valeurs. Rien n'est enregistré sans validation
  humaine — exigence RGPD autant que précaution métier.
- Le prompt système interdit le conseil médical et la divulgation de données tierces.

## Lancement

En Docker (recommandé), avec le reste de la pile :

```bash
cd docker
docker compose up -d ai
```

Hors Docker :

```bash
cd workers/ai_service
python -m venv .venv && . .venv/Scripts/activate   # Windows : .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env      # puis renseignez les clés
uvicorn app.main:app --reload --port 8200
```

## Clés API

| Fournisseur | Où | Variable |
|---|---|---|
| Gemini | https://aistudio.google.com/apikey | `GEMINI_API_KEY` |
| Mistral | https://console.mistral.ai/api-keys | `MISTRAL_API_KEY` |

À renseigner dans `docker/.env` (jamais versionné), puis
`docker compose up -d --no-deps ai`.
