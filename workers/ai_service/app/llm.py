"""
Couche d'accès aux modèles — **le seul fichier qui connaît un fournisseur**.

Tout passe par LiteLLM : un unique appel `completion(model="<provider>/<model>", ...)` parle
à Gemini, Mistral, Groq, Ollama, OpenAI... Changer de fournisseur se fait dans `settings.py`
(variables d'environnement), sans toucher au reste du service ni au backend Java.

Répartition retenue pour ce projet :
  * extraction documentaire -> Gemini (vision + PDF natif, palier gratuit)
  * chat / rédaction / résumés -> Mistral (hébergement UE, argument RGPD)
"""

from __future__ import annotations

import base64
import json
import logging
import os
import re
from typing import Any, Iterator

import litellm

from .settings import settings

log = logging.getLogger(__name__)

# LiteLLM ignore silencieusement les paramètres qu'un fournisseur ne supporte pas
# (ex. response_format sur un modèle qui n'en veut pas) au lieu de planter.
litellm.drop_params = True
litellm.suppress_debug_info = True


class NotConfiguredError(RuntimeError):
    """Levée quand la clé API du fournisseur visé est absente."""


class LlmError(RuntimeError):
    """Erreur d'appel au modèle (réseau, quota, réponse inexploitable)."""


def _export_keys() -> None:
    """LiteLLM lit les clés depuis l'environnement du processus."""
    if settings.gemini_api_key:
        os.environ.setdefault("GEMINI_API_KEY", settings.gemini_api_key)
    if settings.mistral_api_key:
        os.environ.setdefault("MISTRAL_API_KEY", settings.mistral_api_key)


_export_keys()


def require_configured(model: str) -> None:
    if not settings.is_configured(model):
        provider = settings.provider_of(model)
        raise NotConfiguredError(
            f"Le fournisseur « {provider} » n'est pas configuré : renseignez "
            f"{provider.upper()}_API_KEY dans docker/.env puis redémarrez le conteneur 'ai'."
        )


# --------------------------------------------------------------------------- documents


def document_part(content: bytes, mime_type: str) -> dict[str, Any]:
    """
    Bloc de contenu multimodal pour une image ou un PDF.

    On passe par une data-URI dans `image_url` : c'est la forme que LiteLLM normalise pour
    tous les fournisseurs multimodaux (Gemini accepte aussi bien les images que les PDF par
    ce canal). Rester sur une seule forme évite d'avoir du code spécifique par fournisseur.
    """
    encoded = base64.b64encode(content).decode("ascii")
    return {"type": "image_url", "image_url": {"url": f"data:{mime_type};base64,{encoded}"}}


# --------------------------------------------------------------------------- appels


def complete(
    model: str,
    messages: list[dict[str, Any]],
    *,
    max_tokens: int = 1024,
    temperature: float = 0.2,
) -> str:
    """Un appel simple, réponse en texte brut."""
    require_configured(model)
    try:
        response = litellm.completion(
            model=model,
            messages=messages,
            max_tokens=max_tokens,
            temperature=temperature,
            timeout=settings.request_timeout_seconds,
        )
    except Exception as e:  # noqa: BLE001 - on remonte un message exploitable côté admin
        log.warning("LLM call failed (%s): %s", model, e)
        raise LlmError(f"Appel au modèle {model} impossible : {e}") from e
    return (response.choices[0].message.content or "").strip()


def complete_json(
    model: str,
    messages: list[dict[str, Any]],
    *,
    max_tokens: int = 2048,
) -> dict[str, Any]:
    """
    Un appel dont la réponse doit être un objet JSON.

    On demande le mode JSON au fournisseur quand il le supporte (`response_format`), mais on
    reparse et on nettoie systématiquement : certains modèles encadrent encore la sortie de
    ```json ... ```. La validation métier se fait ensuite avec Pydantic dans `extraction.py`.
    """
    require_configured(model)
    try:
        response = litellm.completion(
            model=model,
            messages=messages,
            max_tokens=max_tokens,
            temperature=0.0,
            response_format={"type": "json_object"},
            timeout=settings.request_timeout_seconds,
        )
    except Exception as e:  # noqa: BLE001
        log.warning("LLM JSON call failed (%s): %s", model, e)
        raise LlmError(f"Appel au modèle {model} impossible : {e}") from e

    raw = (response.choices[0].message.content or "").strip()
    return _parse_json_object(raw)


def stream_chat(
    model: str,
    messages: list[dict[str, Any]],
    *,
    tools: list[dict[str, Any]] | None = None,
    max_tokens: int = 1024,
) -> Iterator[Any]:
    """Flux de réponse (SSE côté API). Rend les chunks bruts LiteLLM."""
    require_configured(model)
    try:
        return litellm.completion(
            model=model,
            messages=messages,
            tools=tools or None,
            max_tokens=max_tokens,
            temperature=0.3,
            stream=True,
            timeout=settings.request_timeout_seconds,
        )
    except Exception as e:  # noqa: BLE001
        log.warning("LLM stream failed (%s): %s", model, e)
        raise LlmError(f"Appel au modèle {model} impossible : {e}") from e


def complete_with_tools(
    model: str,
    messages: list[dict[str, Any]],
    *,
    tools: list[dict[str, Any]] | None = None,
    max_tokens: int = 1024,
) -> Any:
    """Appel non streamé renvoyant le message complet (utilisé pour la boucle d'outils)."""
    require_configured(model)
    try:
        response = litellm.completion(
            model=model,
            messages=messages,
            tools=tools or None,
            max_tokens=max_tokens,
            temperature=0.3,
            timeout=settings.request_timeout_seconds,
        )
    except Exception as e:  # noqa: BLE001
        log.warning("LLM tool call failed (%s): %s", model, e)
        raise LlmError(f"Appel au modèle {model} impossible : {e}") from e
    return response.choices[0].message


# --------------------------------------------------------------------------- utilitaires

_FENCE = re.compile(r"^```(?:json)?\s*|\s*```$", re.IGNORECASE)


def _parse_json_object(raw: str) -> dict[str, Any]:
    text = _FENCE.sub("", raw).strip()
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        # Dernier recours : isoler le premier objet JSON de la réponse.
        start, end = text.find("{"), text.rfind("}")
        if start == -1 or end <= start:
            raise LlmError("Le modèle n'a pas renvoyé de JSON exploitable.")
        try:
            parsed = json.loads(text[start : end + 1])
        except json.JSONDecodeError as e:
            raise LlmError("Le modèle n'a pas renvoyé de JSON exploitable.") from e
    if not isinstance(parsed, dict):
        raise LlmError("Le modèle a renvoyé un JSON qui n'est pas un objet.")
    return parsed
