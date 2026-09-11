"""
Extraction structurée d'un document (passeport, diplôme, inscription à l'Ordre...).

Le modèle lit l'image ou le PDF **nativement** — aucun moteur OCR à installer. Le résultat
est toujours du JSON validé par Pydantic, avec un indice de confiance par champ.

Principe de sûreté : le service **ne remplit jamais un dossier tout seul**. Il renvoie une
proposition ; l'écran de validation côté admin/médecin fait confirmer champ par champ avant
tout enregistrement. C'est une exigence RGPD autant qu'une précaution métier.
"""

from __future__ import annotations

import json
import logging

from .llm import complete_json, document_part
from .schemas import ExtractedField, ExtractionResult, known_types, schema_for
from .settings import settings

log = logging.getLogger(__name__)

SUPPORTED_MIME = {
    "application/pdf",
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
    "image/heic",
    "image/tiff",
}

_SYSTEM = (
    "Tu es un assistant d'instruction de dossiers pour une plateforme de mobilité médicale. "
    "Tu lis un document officiel et tu en extrais les informations demandées, sans rien inventer.\n"
    "Règles impératives :\n"
    "- N'invente aucune valeur. Si un champ n'est pas lisible ou absent, mets value = null "
    "et confidence = 0.\n"
    "- Recopie les valeurs telles qu'elles apparaissent (ne traduis pas les noms propres).\n"
    "- Normalise toutes les dates au format AAAA-MM-JJ.\n"
    "- confidence est ta certitude de lecture entre 0 et 1 (1 = parfaitement lisible).\n"
    "- Réponds UNIQUEMENT par un objet JSON valide, sans texte autour."
)


def build_prompt(document_type: str) -> tuple[str, str]:
    schema = schema_for(document_type)
    fields_doc = "\n".join(f'  - "{name}" : {desc}' for name, desc in schema["fields"].items())
    field_keys = list(schema["fields"].keys())
    skeleton = {
        "type_matches": True,
        "detected_type": f"<un type parmi {known_types()} ou une courte description>",
        "fields": {k: {"value": None, "confidence": 0.0} for k in field_keys},
        "warnings": ["<anomalies constatées : document expiré, illisible, raturé...>"],
    }
    instruction = (
        f"Type de document attendu : {schema['label']} ({document_type.upper()}).\n\n"
        f"Champs à extraire :\n{fields_doc}\n\n"
        "Indique aussi :\n"
        '  - "type_matches" : false si le document fourni n\'est visiblement pas un '
        f"{schema['label']}.\n"
        '  - "detected_type" : ce que tu penses que le document est réellement.\n'
        '  - "warnings" : liste (éventuellement vide) des anomalies (document expiré, '
        "photo illisible, informations contradictoires...).\n\n"
        "Structure exacte de la réponse :\n"
        f"{json.dumps(skeleton, ensure_ascii=False, indent=2)}"
    )
    return _SYSTEM, instruction


def extract(content: bytes, mime_type: str, document_type: str) -> ExtractionResult:
    doc_type = (document_type or "OTHER").upper()
    system, instruction = build_prompt(doc_type)

    messages = [
        {"role": "system", "content": system},
        {
            "role": "user",
            "content": [
                {"type": "text", "text": instruction},
                document_part(content, mime_type),
            ],
        },
    ]

    raw = complete_json(
        settings.extraction_model, messages, max_tokens=settings.extraction_max_tokens
    )
    return _to_result(raw, doc_type)


def _to_result(raw: dict, doc_type: str) -> ExtractionResult:
    expected = schema_for(doc_type)["fields"].keys()
    raw_fields = raw.get("fields") or {}

    fields: dict[str, ExtractedField] = {}
    for key in expected:
        entry = raw_fields.get(key)
        if isinstance(entry, dict):
            value = entry.get("value")
            confidence = entry.get("confidence", 0.0)
        else:
            # Tolérance : certains modèles renvoient directement la valeur.
            value, confidence = entry, 0.5 if entry else 0.0
        try:
            confidence = max(0.0, min(1.0, float(confidence)))
        except (TypeError, ValueError):
            confidence = 0.0
        text = None if value in (None, "", "null") else str(value).strip()
        fields[key] = ExtractedField(value=text, confidence=confidence if text else 0.0)

    warnings = raw.get("warnings") or []
    if not isinstance(warnings, list):
        warnings = [str(warnings)]
    warnings = [str(w) for w in warnings if w]

    type_matches = raw.get("type_matches")
    type_matches = True if type_matches is None else bool(type_matches)
    if not type_matches:
        warnings.insert(
            0,
            "Le document ne semble pas correspondre au type attendu "
            f"({schema_for(doc_type)['label']}).",
        )

    return ExtractionResult(
        document_type=doc_type,
        type_matches=type_matches,
        detected_type=(raw.get("detected_type") or None),
        fields=fields,
        warnings=warnings,
        model=settings.extraction_model,
    )
