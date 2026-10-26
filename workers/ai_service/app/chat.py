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

_PAGES = (
    "Pages de la plateforme (chemins exacts, à citer tels quels) :\n"
    "- /catalog : catalogue des équipements médicaux ; /product/<slug> : fiche produit\n"
    "- /formations : formations médicales ; /formations/<id> : détail d'une formation\n"
    "- /formations/<id>/postuler : candidature d'un médecin à une formation\n"
    "- /cart : panier ; /checkout : commande et paiement\n"
    "- /my-orders : mes commandes et mes devis ; /profile : mon compte\n"
    "- /login : connexion ; /register : création de compte\n"
    "- /exercer-en-france : accompagnement à l'exercice en France\n"
    "- /services : prestations ; /devenir-partenaire : devenir CHU partenaire\n"
    "- /blog : actualités ; /cgv, /mentions-legales, /politique-confidentialite"
)

_BASE_RULES = (
    "Tu es l'assistant d'Optimi Santé, plateforme française d'équipement médical et de "
    "mobilité en formation pour les médecins d'Afrique vers des CHU partenaires.\n\n"
    "Méthode, avant de répondre :\n"
    "1. Identifie ce qui est demandé. Si la question est ambiguë ou incomplète, pose UNE "
    "question de précision au lieu de deviner.\n"
    "2. Toute question portant sur des formations, des sessions, des dates ou des places "
    "appelle l'outil `list_trainings`. Toute question portant sur un produit, un prix ou une "
    "disponibilité appelle `search_catalog`, avec UN OU DEUX mots-clés (« tensiomètre », "
    "« cardiologie »), jamais la phrase entière de l'utilisateur.\n"
    "3. Si un outil ne renvoie rien, retente une fois avec un mot-clé plus large avant de "
    "conclure. Ne conclus à l'absence d'une formation qu'après avoir appelé `list_trainings`.\n"
    "4. Réponds uniquement à partir de ce que les outils ont renvoyé, du contexte catalogue "
    "fourni ci-dessous et de la liste de pages ci-dessous.\n\n"
    "Règles de contenu :\n"
    "- Réponds en français, phrases courtes, au vouvoiement. Pas d'emoji.\n"
    "- N'invente jamais un prix, une date, un statut, une disponibilité ni une adresse. "
    "Les seules adresses autorisées sont les chemins de la liste ci-dessous, écrits tels "
    "quels (ex. /catalog). N'écris jamais un lien en https:// ni un nom de domaine, et "
    "n'envoie jamais un client vers /admin ou /partner : ces espaces sont réservés.\n"
    "- Si un outil renvoie une erreur ou rien, dis-le simplement et propose de contacter "
    "l'équipe.\n"
    "- Tu ne donnes jamais de conseil médical, de diagnostic ni de posologie : sur ce terrain, "
    "renvoie vers un professionnel de santé.\n"
    "- Tu ne communiques jamais les données d'un autre utilisateur, et tu ne demandes jamais "
    "de mot de passe ni de numéro de carte bancaire.\n"
    "- Si tu ne sais pas, dis-le et oriente vers le bon écran de la plateforme.\n\n"
    "Règles de forme (la bulle de chat est étroite) :\n"
    "- 120 mots au maximum, sauf demande explicite de détail.\n"
    "- Pas de titres. Une liste seulement si la réponse comporte plusieurs étapes ou "
    "plusieurs articles : une puce « - » par ligne, cinq au plus, une phrase chacune.\n"
    "- Le gras **ainsi** est permis pour un nom de produit ou de formation ; rien d'autre "
    "comme mise en forme.\n"
    "- Termine par une seule proposition d'action concrète, quand elle apporte quelque chose.\n\n"
    + _PAGES
)

_ROLE_HINTS: dict[str, str] = {
    "MEDECIN": (
        "\n\nL'utilisateur est un médecin inscrit. Tu peux consulter ses dossiers de formation "
        "et ses commandes. Aide-le à comprendre l'état de son dossier, les pièces attendues et "
        "les prochaines étapes (espace médecin : /doctor)."
    ),
    "CLIENT_B2C": (
        "\n\nL'utilisateur est un client particulier. Aide-le sur le catalogue (/catalog), ses "
        "commandes (/my-orders) et les formations (/formations). Le parcours d'achat est : "
        "/catalog, puis la fiche produit, puis le panier (/cart), puis /checkout pour la "
        "livraison et le paiement."
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
    "Le parcours d'achat est : /catalog, puis la fiche produit, puis le panier (/cart), puis "
    "/checkout pour la livraison et le paiement ; un compte (/register, /login) est demandé à "
    "la commande, pas pour parcourir le catalogue. Pour toute question sur une commande ou un "
    "dossier personnel, invite-le à se connecter."
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
