"""
Rédaction assistée et résumés (Mistral).

Le service produit du **texte**, jamais un PDF : la mise en forme reste au moteur existant
(`PdfGeneratorService` côté Java, gabarits Thymeleaf). On sépare volontairement « ce qui est
écrit » de « comment c'est mis en page ».

Comme pour l'extraction, la sortie est une **proposition** : l'admin relit et modifie avant
envoi ou génération.
"""

from __future__ import annotations

import json
from typing import Any

from .llm import complete
from .settings import settings

_SYSTEM = (
    "Tu rédiges pour Optimi Santé, une plateforme française qui accompagne la mobilité en "
    "formation des médecins d'Afrique vers des CHU partenaires.\n"
    "Style : français professionnel, clair, courtois, sans emphase commerciale ni emoji. "
    "Vouvoiement. Phrases courtes. Pas de formule creuse.\n"
    "N'invente jamais un fait, un montant, une date ou un nom qui ne figure pas dans le "
    "contexte fourni : si une information manque, laisse un repère explicite entre crochets "
    "(par exemple [date à compléter]).\n"
    "Réponds uniquement par le texte demandé, sans préambule ni commentaire."
)

DRAFT_TYPES: dict[str, dict[str, str]] = {
    "ENROLLMENT_DECISION": {
        "label": "Décision sur un dossier CHU",
        "instruction": (
            "Rédige l'e-mail annonçant au médecin la décision prise sur son dossier de "
            "formation. Annonce la décision dès la première phrase, explique brièvement, puis "
            "indique la prochaine étape concrète. 120 à 180 mots. Termine par une formule de "
            "politesse et la signature « L'équipe Optimi Santé »."
        ),
    },
    "PARTNERSHIP_DECISION": {
        "label": "Décision sur une demande de partenariat",
        "instruction": (
            "Rédige l'e-mail annonçant à l'établissement la décision prise sur sa demande de "
            "partenariat. Ton institutionnel. Précise la suite du processus. 120 à 180 mots."
        ),
    },
    "DOCUMENT_REQUEST": {
        "label": "Demande de pièces complémentaires",
        "instruction": (
            "Rédige l'e-mail demandant au médecin les pièces manquantes ou à corriger. Liste "
            "les pièces sous forme de tirets, une ligne par pièce, en expliquant en quelques "
            "mots ce qui ne va pas. Indique où les déposer (espace médecin) et sous quel délai."
        ),
    },
    "CONVENTION_INTRO": {
        "label": "Préambule de convention tripartite",
        "instruction": (
            "Rédige le préambule d'une convention tripartite entre Optimi Santé, le CHU "
            "d'accueil et le médecin. Registre juridique sobre, 100 à 150 mots, sans titre. "
            "Rappelle l'objet, les parties et la période concernée."
        ),
    },
    "TRAINING_DESCRIPTION": {
        "label": "Description d'une formation",
        "instruction": (
            "Rédige la description publique de cette formation pour le catalogue : un "
            "paragraphe d'accroche de 60 à 90 mots, puis une liste à tirets de 4 à 6 objectifs "
            "pédagogiques. Factuel, sans superlatif."
        ),
    },
    "GENERIC_EMAIL": {
        "label": "E-mail libre",
        "instruction": (
            "Rédige un e-mail professionnel correspondant à l'intention décrite dans le "
            "contexte. 100 à 200 mots."
        ),
    },
}

SUMMARY_TYPES: dict[str, str] = {
    "ENROLLMENT": (
        "Résume ce dossier de formation pour un administrateur qui doit le traiter vite. "
        "Format : 3 à 5 puces maximum. Chaque puce = un fait actionnable (état, pièces "
        "manquantes, points de vigilance, prochaine étape). Pas d'introduction."
    ),
    "PARTNERSHIP": (
        "Résume cette demande de partenariat en 3 à 4 puces : établissement, complétude du "
        "dossier, points de vigilance, recommandation."
    ),
    "ORDER": (
        "Résume cette commande en 3 puces : contenu, statut de paiement et de livraison, "
        "point de vigilance éventuel."
    ),
    "GENERIC": "Résume le contexte fourni en 3 à 5 puces factuelles.",
}


def draft_types() -> list[dict[str, str]]:
    return [{"key": k, "label": v["label"]} for k, v in DRAFT_TYPES.items()]


def _context_block(context: dict[str, Any] | None) -> str:
    if not context:
        return "Aucun contexte fourni."
    return json.dumps(context, ensure_ascii=False, indent=2, default=str)


def draft(draft_type: str, context: dict[str, Any] | None, extra: str | None = None) -> str:
    spec = DRAFT_TYPES.get((draft_type or "").upper(), DRAFT_TYPES["GENERIC_EMAIL"])
    user = (
        f"{spec['instruction']}\n\n"
        f"Contexte (données réelles de la plateforme) :\n{_context_block(context)}"
    )
    if extra:
        user += f"\n\nConsignes supplémentaires de l'administrateur :\n{extra.strip()}"

    return complete(
        settings.draft_model,
        [{"role": "system", "content": _SYSTEM}, {"role": "user", "content": user}],
        max_tokens=900,
        temperature=0.4,
    )


def summarize(kind: str, context: dict[str, Any] | None) -> str:
    instruction = SUMMARY_TYPES.get((kind or "").upper(), SUMMARY_TYPES["GENERIC"])
    user = f"{instruction}\n\nContexte :\n{_context_block(context)}"
    return complete(
        settings.draft_model,
        [{"role": "system", "content": _SYSTEM}, {"role": "user", "content": user}],
        max_tokens=500,
        temperature=0.2,
    )
