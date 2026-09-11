"""
Outils de l'assistant conversationnel.

Point important de sécurité : le service IA **n'interroge jamais la base directement** pour
les données d'un utilisateur. Chaque outil rappelle le backend Java en réutilisant le JWT de
l'utilisateur courant — donc le contexte multi-tenant, les rôles et le filtre de suppression
logique (`@SQLRestriction`) restent appliqués exactement comme pour un appel normal. Le
modèle ne peut voir que ce que l'utilisateur a déjà le droit de voir.
"""

from __future__ import annotations

import json
import logging
from typing import Any

import httpx

from .settings import settings

log = logging.getLogger(__name__)

_BASE = "/api/v1/ai/tools"

TOOL_SPECS: list[dict[str, Any]] = [
    {
        "type": "function",
        "function": {
            "name": "search_catalog",
            "description": (
                "Recherche des produits et des formations dans le catalogue Optimi Santé. "
                "À utiliser dès que l'utilisateur demande un produit, une formation, un prix "
                "ou une disponibilité."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Mots-clés de recherche (ex. « cardiologie », « tensiomètre »).",
                    }
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_trainings",
            "description": (
                "Liste les formations médicales proposées et leurs sessions ouvertes "
                "(dates, places restantes, établissement d'accueil)."
            ),
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_my_orders",
            "description": (
                "Récupère les commandes de l'utilisateur connecté avec leur statut de paiement "
                "et de traitement. À utiliser pour « où en est ma commande », « mes factures »."
            ),
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_my_enrollments",
            "description": (
                "Récupère les dossiers de formation (inscriptions CHU) de l'utilisateur "
                "connecté : statut, session, pièces manquantes. Réservé aux médecins."
            ),
            "parameters": {"type": "object", "properties": {}},
        },
    },
]

_ROUTES: dict[str, str] = {
    "search_catalog": f"{_BASE}/catalog/search",
    "list_trainings": f"{_BASE}/trainings",
    "get_my_orders": f"{_BASE}/my-orders",
    "get_my_enrollments": f"{_BASE}/my-enrollments",
}


def specs_for(role: str | None) -> list[dict[str, Any]]:
    """Les outils exposés dépendent du rôle : un visiteur anonyme n'a que le catalogue."""
    public = {"search_catalog", "list_trainings"}
    if not role:
        allowed = public
    elif role == "MEDECIN":
        allowed = public | {"get_my_orders", "get_my_enrollments"}
    else:
        allowed = public | {"get_my_orders"}
    return [s for s in TOOL_SPECS if s["function"]["name"] in allowed]


def execute(name: str, arguments: dict[str, Any], auth_header: str | None) -> str:
    """Exécute un outil et renvoie son résultat sérialisé (toujours une chaîne pour le modèle)."""
    route = _ROUTES.get(name)
    if route is None:
        return json.dumps({"error": f"Outil inconnu : {name}"}, ensure_ascii=False)

    headers = {"Accept": "application/json"}
    if auth_header:
        headers["Authorization"] = auth_header

    params = {}
    if name == "search_catalog":
        params["q"] = str(arguments.get("query", ""))[:200]

    url = settings.backend_base_url.rstrip("/") + route
    try:
        with httpx.Client(timeout=20.0) as client:
            response = client.get(url, params=params, headers=headers)
        if response.status_code == 401 or response.status_code == 403:
            return json.dumps(
                {"error": "Cette information nécessite d'être connecté avec les droits requis."},
                ensure_ascii=False,
            )
        response.raise_for_status()
        payload = response.json()
    except Exception as e:  # noqa: BLE001 - le modèle doit pouvoir répondre malgré l'échec
        log.warning("Tool %s failed: %s", name, e)
        return json.dumps(
            {"error": "Donnée momentanément indisponible, réessayez plus tard."},
            ensure_ascii=False,
        )

    return json.dumps(payload, ensure_ascii=False, default=str)[:8000]
