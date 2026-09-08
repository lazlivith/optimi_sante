"""Planificateur du batch nocturne : exécute tous les rapports une fois par nuit."""
from __future__ import annotations

import logging

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger

from .runner import run_all
from .settings import get_settings

logger = logging.getLogger("analytics.scheduler")

_scheduler: BackgroundScheduler | None = None


def _nightly_job() -> None:
    logger.info("Batch reporting nocturne : démarrage")
    results = run_all(trigger="SCHEDULED")
    ok = sum(1 for r in results if r.get("status") == "SUCCESS")
    logger.info("Batch reporting nocturne : %s/%s rapports OK", ok, len(results))


def start_scheduler() -> None:
    global _scheduler
    settings = get_settings()
    if not settings.scheduler_enabled:
        logger.info("Scheduler désactivé (scheduler_enabled=false)")
        return
    if _scheduler is not None:
        return
    _scheduler = BackgroundScheduler(timezone="UTC")
    _scheduler.add_job(
        _nightly_job,
        CronTrigger(hour=settings.schedule_cron_hour, minute=settings.schedule_cron_minute),
        id="nightly_reports",
        replace_existing=True,
        misfire_grace_time=3600,
    )
    _scheduler.start()
    logger.info(
        "Scheduler démarré — batch quotidien à %02d:%02d UTC",
        settings.schedule_cron_hour,
        settings.schedule_cron_minute,
    )


def shutdown_scheduler() -> None:
    global _scheduler
    if _scheduler is not None:
        _scheduler.shutdown(wait=False)
        _scheduler = None
