# Worker Reporting analytique (FastAPI) — Sprint 5

Service Python qui génère les **rapports batch et exports analytiques** d'Optimi Santé.
Consommé exclusivement par le backend Spring (`/api/v1/admin/reporting/**`), qui applique le
RBAC ; ce service n'est pas exposé publiquement.

## Rôle

- Un **catalogue de rapports** (`app/reports.py`), chacun = une requête SQL en lecture seule.
- Un **runner** (`app/runner.py`) qui exécute un rapport, écrit les livrables `CSV` + `XLSX`
  dans `EXPORT_DIR`, et trace chaque exécution dans la table `report_runs`.
- Un **batch nocturne** (APScheduler, 02:00 UTC par défaut) qui rejoue tous les rapports.
- Une **API** (`app/main.py`) : liste, aperçu JSON, déclenchement, historique, téléchargement.

## Rapports fournis

| clé | contenu |
|---|---|
| `revenue_daily` | CA payé / commandes / devis par jour (param `days`, défaut 90) |
| `sales_by_category` | revenu, quantités, commandes par catégorie |
| `acquisition_funnel` | brochure → candidature → paiement → inscription → convention → visa |
| `enrollment_pipeline` | détail de chaque dossier de mobilité + ancienneté |
| `rgpd_compliance` | soft delete, anonymisations, consentements manquants, données anciennes |
| `catalog_stock_alert` | produits au niveau ou sous le seuil de stock |
| `b2b_activity` | commandes / devis / CA par entreprise B2B |

## Endpoints

```
GET  /health
GET  /reports
GET  /reports/{key}/preview?limit=50
GET  /reports/{key}/runs?limit=20
POST /reports/{key}/run?trigger=MANUAL      body: { "days": 30 }
GET  /reports/{key}/download/latest?format=csv|xlsx
```

## Lancer en local (hors Docker)

```bash
cd workers/analytics_service
python -m venv .venv && . .venv/Scripts/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env         # ajuster DATABASE_URL si besoin
uvicorn app.main:app --port 8100 --reload
```

## Via docker-compose

Le service `analytics` est défini dans `docker/docker-compose.yml`. Il partage le volume
`optimisante-exports` (monté sur `/exports`) et se connecte à `postgres:5432`.
Le backend le joint via `REPORTING_WORKER_URL=http://analytics:8100`.
