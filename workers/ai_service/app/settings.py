from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    Configuration du service IA. Aucune clé n'est obligatoire : sans clé, la capacité
    correspondante est simplement annoncée comme désactivée par /health et les endpoints
    renvoient une erreur explicite (même philosophie que Stripe/Cloudinary non configurés
    dans le backend Java).
    """

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # --- Fournisseurs ---------------------------------------------------------
    # Gemini : extraction / lecture de documents (vision + PDF natif, palier gratuit).
    gemini_api_key: str = ""
    # Mistral : chat, rédaction, résumés (hébergement UE — argument RGPD).
    mistral_api_key: str = ""

    # --- Modèles (noms au format LiteLLM : "<provider>/<model>") --------------
    # Changer de fournisseur = changer ces variables, rien d'autre dans le code.
    extraction_model: str = "gemini/gemini-3.6-flash"
    chat_model: str = "mistral/mistral-small-latest"
    draft_model: str = "mistral/mistral-small-latest"
    embedding_model: str = "mistral/mistral-embed"

    # --- Garde-fous ----------------------------------------------------------
    max_upload_mb: int = 15
    request_timeout_seconds: int = 120
    chat_max_history: int = 20
    chat_max_tokens: int = 1024
    extraction_max_tokens: int = 2048

    # --- Backend Java (pour les outils du chat : commandes, dossiers, catalogue) ---
    backend_base_url: str = "http://backend:8080"

    # --- Base de données (lecture de contexte catalogue) ----------------------
    database_url: str = "postgresql+psycopg2://postgres:Investx2026@postgres:5432/optimisante_db"

    def provider_of(self, model: str) -> str:
        return model.split("/", 1)[0] if "/" in model else model

    def key_for(self, model: str) -> str:
        provider = self.provider_of(model)
        return {"gemini": self.gemini_api_key, "mistral": self.mistral_api_key}.get(provider, "")

    def is_configured(self, model: str) -> bool:
        return bool(self.key_for(model))


settings = Settings()
