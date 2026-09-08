"""API FastAPI du worker de reporting. Consommée uniquement par le backend Spring (qui applique
le RBAC JWT et n'expose pas ce service publiquement)."""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import Any

from fastapi import Body, FastAPI, HTTPException, Query
from fastapi.responses import FileResponse

from .db import ping
from .reports import REPORTS, get_report
from .runner import latest_file, list_runs, preview, run_report
from .scheduler import shutdown_scheduler, start_scheduler
from .settings import get_settings

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("analytics.api")

settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI):
    try:
        start_scheduler()
    except Exception as exc:  # noqa: BLE001
        logger.warning("Scheduler non démarré : %s", exc)
    yield
    shutdown_scheduler()


app = FastAPI(title=settings.api_title, version=settings.api_version, lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "database": "ok" if ping() else "unreachable",
        "reports": len(REPORTS),
        "exportDir": settings.export_dir,
    }


@app.get("/reports")
def list_reports() -> list[dict[str, Any]]:
    return [
        {"key": r.key, "title": r.title, "description": r.description, "params": r.params}
        for r in REPORTS.values()
    ]


@app.get("/reports/{key}/preview")
def report_preview(key: str, limit: int = Query(50, ge=1, le=500)) -> dict[str, Any]:
    _require(key)
    try:
        return preview(key, limit)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.get("/reports/{key}/runs")
def report_runs(key: str, limit: int = Query(20, ge=1, le=200)) -> list[dict[str, Any]]:
    _require(key)
    return list_runs(key, limit)


@app.post("/reports/{key}/run")
def report_run(
    key: str,
    trigger: str = Query("MANUAL"),
    params: dict[str, Any] | None = Body(default=None),
) -> dict[str, Any]:
    _require(key)
    result = run_report(key, trigger=trigger, params=params)
    if result.get("status") == "FAILED":
        raise HTTPException(status_code=500, detail=result.get("error", "échec du rapport"))
    return result


@app.get("/reports/{key}/download/latest")
def report_download(key: str, format: str = Query("csv", pattern="^(csv|xlsx)$")) -> FileResponse:
    _require(key)
    try:
        path = latest_file(key, format)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    media = (
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        if format == "xlsx"
        else "text/csv"
    )
    return FileResponse(path, media_type=media, filename=path.name)


def _require(key: str) -> None:
    try:
        get_report(key)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail=f"Rapport inconnu : {key}") from exc
