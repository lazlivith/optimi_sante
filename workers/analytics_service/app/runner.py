"""Exécution d'un rapport : lit la base, écrit les livrables CSV + XLSX, trace l'exécution
dans la table report_runs (partagée avec le backend Spring qui l'expose côté admin)."""
from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pandas as pd
from sqlalchemy import text

from .db import get_engine
from .reports import Report, get_report
from .settings import get_settings


def _export_root() -> Path:
    root = Path(get_settings().export_dir)
    root.mkdir(parents=True, exist_ok=True)
    return root


def _run_dir(key: str) -> Path:
    d = _export_root() / key
    d.mkdir(parents=True, exist_ok=True)
    return d


def read_dataframe(report: Report, params: dict[str, Any] | None = None) -> pd.DataFrame:
    resolved = report.resolved_params(params)
    with get_engine().connect() as conn:
        return pd.read_sql(text(report.sql), conn, params=resolved)


def preview(key: str, limit: int = 50) -> dict[str, Any]:
    report = get_report(key)
    df = read_dataframe(report)
    limited = df.head(max(1, min(limit, 500)))
    return {
        "key": key,
        "columns": list(limited.columns),
        "rows": json.loads(limited.to_json(orient="records", date_format="iso")),
        "totalRows": int(len(df)),
    }


def _insert_run(key: str, trigger: str, params: dict[str, Any]) -> str:
    with get_engine().begin() as conn:
        row = conn.execute(
            text(
                """
                INSERT INTO report_runs (report_key, status, trigger, params, started_at)
                VALUES (:key, 'RUNNING', :trigger, :params, now())
                RETURNING id
                """
            ),
            {"key": key, "trigger": trigger, "params": json.dumps(params or {})},
        ).scalar_one()
    return str(row)


def _finish_run(run_id: str, *, status: str, row_count: int | None = None,
                formats: str | None = None, csv_path: str | None = None,
                xlsx_path: str | None = None, error: str | None = None) -> None:
    with get_engine().begin() as conn:
        conn.execute(
            text(
                """
                UPDATE report_runs
                   SET status = :status, row_count = :row_count, formats = :formats,
                       csv_path = :csv_path, xlsx_path = :xlsx_path, error = :error,
                       finished_at = now()
                 WHERE id = :id
                """
            ),
            {
                "status": status, "row_count": row_count, "formats": formats,
                "csv_path": csv_path, "xlsx_path": xlsx_path, "error": error, "id": run_id,
            },
        )


def run_report(key: str, trigger: str = "MANUAL", params: dict[str, Any] | None = None) -> dict[str, Any]:
    report = get_report(key)
    resolved = report.resolved_params(params)
    run_id = _insert_run(key, trigger, resolved)
    try:
        df = read_dataframe(report, resolved)
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        run_dir = _run_dir(key)

        csv_path = run_dir / f"{key}_{stamp}.csv"
        xlsx_path = run_dir / f"{key}_{stamp}.xlsx"
        df.to_csv(csv_path, index=False, sep=";", encoding="utf-8-sig")
        with pd.ExcelWriter(xlsx_path, engine="openpyxl") as writer:
            df.to_excel(writer, index=False, sheet_name=key[:31])

        # Copies "latest" écrasées à chaque exécution pour un téléchargement direct.
        latest_csv = run_dir / f"{key}_latest.csv"
        latest_xlsx = run_dir / f"{key}_latest.xlsx"
        latest_csv.write_bytes(csv_path.read_bytes())
        latest_xlsx.write_bytes(xlsx_path.read_bytes())

        _finish_run(
            run_id, status="SUCCESS", row_count=int(len(df)), formats="csv,xlsx",
            csv_path=str(csv_path), xlsx_path=str(xlsx_path),
        )
        return {
            "runId": run_id, "key": key, "status": "SUCCESS", "trigger": trigger,
            "rowCount": int(len(df)), "params": resolved,
            "csvPath": str(csv_path), "xlsxPath": str(xlsx_path),
        }
    except Exception as exc:  # noqa: BLE001 - on veut tracer toute panne
        _finish_run(run_id, status="FAILED", error=str(exc)[:2000])
        return {"runId": run_id, "key": key, "status": "FAILED", "trigger": trigger, "error": str(exc)}


def latest_file(key: str, fmt: str) -> Path:
    get_report(key)  # valide la clé
    ext = "xlsx" if fmt.lower() == "xlsx" else "csv"
    path = _run_dir(key) / f"{key}_latest.{ext}"
    if not path.exists():
        raise FileNotFoundError(f"Aucun livrable '{ext}' pour '{key}'. Lancez d'abord le rapport.")
    return path


def list_runs(key: str, limit: int = 20) -> list[dict[str, Any]]:
    get_report(key)
    with get_engine().connect() as conn:
        rows = conn.execute(
            text(
                """
                SELECT id, report_key, status, trigger, row_count, formats, error,
                       started_at, finished_at
                FROM report_runs
                WHERE report_key = :key
                ORDER BY started_at DESC
                LIMIT :limit
                """
            ),
            {"key": key, "limit": max(1, min(limit, 200))},
        ).mappings().all()
    return [
        {
            "id": str(r["id"]),
            "reportKey": r["report_key"],
            "status": r["status"],
            "trigger": r["trigger"],
            "rowCount": r["row_count"],
            "formats": r["formats"],
            "error": r["error"],
            "startedAt": r["started_at"].isoformat() if r["started_at"] else None,
            "finishedAt": r["finished_at"].isoformat() if r["finished_at"] else None,
        }
        for r in rows
    ]


def run_all(trigger: str = "SCHEDULED") -> list[dict[str, Any]]:
    from .reports import REPORTS

    results = []
    for key in REPORTS:
        results.append(run_report(key, trigger=trigger))
    return results
