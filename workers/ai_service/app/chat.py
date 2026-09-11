"""
Assistant conversationnel (Mistral).

Déroulé d'un tour :
  1. quelques rondes d'appels d'outils **non streamées** (le modèle décide s'il a besoin de
     données réelles : catalogue, commandes, dossiers) ;
  2. la réponse finale est **streamée** vers le navigateur en SSE.

Ce découpage évite d'avoir à streamer des appels d'outils partiels tout en gardant une
réponse qui s'affiche au fil de l'eau.
"""

from __future__ import annotations

import json
import logging
from typing import Any, Iterator

from .llm import LlmError, complete_with_tools, stream_chat
from .settings import settings
from .tools import execute, specs_for

log = logging.getLogger(__name__)

MAX_TOOL_ROUNDS = 3

_BASE_RULES = (
    "Tu es l'assistant d'Optimi Santé, plateforme française d'équipement médical et de "
    "mobilité en formation pour les médecins d'Afrique vers des CHU partenaires.\n\n"
    "Règles :\n"
    "- Réponds en français, avec des phrases courtes. Pas d'emoji.\n"
    "- Utilise les outils dès qu'une question porte sur des données réelles (catalogue, "
    "formations, commandes, dossiers). N'invente jamais un prix, une date, un statut ou une "
    "disponibilité.\n"
    "- Si un outil renvoie une erreur ou rien, dis-le simplement et propose de contacter "
    "l'équipe.\n"
    "- Tu ne donnes jamais de conseil médical, de diagnostic ni de posologie : sur ce terrain, "
    "renvoie vers un professionnel de santé.\n"
    "- Tu ne communiques jamais les données d'un autre utilisateur, et tu ne demandes jamais "
    "de mot de passe ni de numéro de carte bancaire.\n"
    "- Si tu ne sais pas, dis-le et oriente vers le bon écran de la plateforme."
)

_ROLE_HINTS: dict[str, str] = {
    "MEDECIN": (
        "\n\nL'utilisateur est un médecin inscrit. Tu peux consulter ses dossiers de formation "
        "et ses commandes. Aide-le à comprendre l'état de son dossier, les pièces attendues et "
        "les prochaines étapes (espace médecin : /doctor)."
    ),
    "CLIENT_B2C": (
        "\n\nL'utilisateur est un client particulier. Aide-le sur le catalogue, ses commandes "
        "(/my-orders) et les formations. "
    ),
    "CLIENT_B2B": (
        "\n\nL'utilisateur est un client professionnel (structure de soin). Il bénéficie de "
        "remises négociées et peut demander des devis. Aide-le sur le catalogue et ses devis."
    ),
    "CENTRE_FORMATION": (
        "\n\nL'utilisateur est un partenaire (CHU / centre de formation). Oriente-le vers son "
        "espace partenaire (/partner) pour ses formations, sessions et dossiers à examiner."
    ),
    "ADMIN": (
        "\n\nL'utilisateur fait partie de l'équipe Optimi Santé. Tu peux être plus direct et "
        "technique, et l'orienter vers les écrans d'administration (/admin)."
    ),
}
_ROLE_HINTS["SUPER_ADMIN"] = _ROLE_HINTS["ADMIN"]

_ANONYMOUS_HINT = (
    "\n\nL'utilisateur n'est pas connecté. Tu peux présenter le catalogue et les formations. "
    "Pour toute question sur une commande ou un dossier personnel, invite-le à se connecter."
)


def system_prompt(role: str | None, catalog_context: str | None = None) -> str:
    prompt = _BASE_RULES + (_ROLE_HINTS.get(role or "", "") if role else _ANONYMOUS_HINT)
    if catalog_context:
        prompt += (
            "\n\nContexte catalogue (données à jour, à privilégier sur ta mémoire) :\n"
            + catalog_context
        )
    return prompt


def _trim(history: list[dict[str, Any]]) -> list[dict[str, Any]]:
    limit = settings.chat_max_history
    return history[-limit:] if len(history) > limit else history


def _run_tool_rounds(
    messages: list[dict[str, Any]], role: str | None, auth_header: str | None
) -> list[dict[str, Any]]:
    """Laisse le modèle appeler ses outils jusqu'à ce qu'il n'en demande plus."""
    tools = specs_for(role)
    if not tools:
        return messages

    for _ in range(MAX_TOOL_ROUNDS):
        message = complete_with_tools(
            settings.chat_model, messages, tools=tools, max_tokens=settings.chat_max_tokens
        )
        tool_calls = getattr(message, "tool_calls", None)
        if not tool_calls:
            return messages

        messages.append(
            {
                "role": "assistant",
                "content": message.content or "",
                "tool_calls": [
                    {
                        "id": call.id,
                        "type": "function",
                        "function": {
                            "name": call.function.name,
                            "arguments": call.function.arguments,
                        },
                    }
                    for call in tool_calls
                ],
            }
        )

        for call in tool_calls:
            try:
                args = json.loads(call.function.arguments or "{}")
            except json.JSONDecodeError:
                args = {}
            result = execute(call.function.name, args, auth_header)
            messages.append(
                {"role": "tool", "tool_call_id": call.id, "name": call.function.name, "content": result}
            )

    return messages


def answer_stream(
    question: str,
    history: list[dict[str, Any]] | None,
    role: str | None,
    auth_header: str | None,
    catalog_context: str | None = None,
) -> Iterator[str]:
    """Génère la réponse par morceaux de texte (à envelopper en SSE par l'API)."""
    messages: list[dict[str, Any]] = [
        {"role": "system", "content": system_prompt(role, catalog_context)}
    ]
    messages.extend(_trim(history or []))
    messages.append({"role": "user", "content": question})

    messages = _run_tool_rounds(messages, role, auth_header)

    for chunk in stream_chat(settings.chat_model, messages, max_tokens=settings.chat_max_tokens):
        try:
            delta = chunk.choices[0].delta
            piece = getattr(delta, "content", None)
        except (AttributeError, IndexError):
            continue
        if piece:
            yield piece


def answer(
    question: str,
    history: list[dict[str, Any]] | None,
    role: str | None,
    auth_header: str | None,
    catalog_context: str | None = None,
) -> str:
    """Version non streamée (utile pour les tests et les appels serveur à serveur)."""
    parts: list[str] = []
    try:
        for piece in answer_stream(question, history, role, auth_header, catalog_context):
            parts.append(piece)
    except LlmError:
        raise
    return "".join(parts).strip()
