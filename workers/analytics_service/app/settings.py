"""Configuration du worker : lue depuis l'environnement (docker-compose) ou un fichier .env local."""
from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # Connexion PostgreSQL. En docker-compose : postgresql+psycopg2://postgres:...@postgres:5432/optimisante_db
    database_url: str = "postgresql+psycopg2://postgres:Investx2026@localhost:5051/optimisante_db"

    # Répertoire où sont écrits les livrables (CSV / XLSX). Monté en volume en docker-compose.
    export_dir: str = "/exports"

    # Planification du batch nocturne (cron). Vide => pas de batch automatique.
    schedule_cron_hour: int = 2
    schedule_cron_minute: int = 0
    scheduler_enabled: bool = True

    # Nombre de jours par défaut pour les rapports à fenêtre glissante.
    default_window_days: int = 90

    api_title: str = "Optimi Santé — Reporting analytique"
    api_version: str = "1.0.0"


@lru_cache
def get_settings() -> Settings:
    return Settings()
