"""Accès base de données : un moteur SQLAlchemy partagé, réutilisé par les rapports et le runner."""
from __future__ import annotations

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

from .settings import get_settings

_engine: Engine | None = None


def get_engine() -> Engine:
    global _engine
    if _engine is None:
        settings = get_settings()
        _engine = create_engine(
            settings.database_url,
            pool_pre_ping=True,
            pool_size=5,
            max_overflow=5,
            future=True,
        )
    return _engine


def ping() -> bool:
    try:
        with get_engine().connect() as conn:
            conn.execute(text("SELECT 1"))
        return True
    except Exception:
        return False
