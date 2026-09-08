"""
Schémas d'extraction, un par type de document du dossier CHU.

Les clés correspondent à l'énumération `DocumentType` du backend Java
(PASSPORT, DIPLOMA, MEDICAL_COUNCIL_CERT, ...). Chaque champ extrait est **optionnel** :
un document flou ou partiel doit produire un JSON incomplet plutôt qu'une valeur inventée.
Le champ `confidence` permet à l'écran de validation de signaler ce qui doit être relu.
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class ExtractedField(BaseModel):
    value: str | None = None
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)


class ExtractionResult(BaseModel):
    document_type: str
    type_matches: bool = Field(
        default=True,
        description="Le document fourni correspond-il bien au type attendu ?",
    )
    detected_type: str | None = None
    fields: dict[str, ExtractedField] = Field(default_factory=dict)
    warnings: list[str] = Field(default_factory=list)
    model: str | None = None


# --------------------------------------------------------------------------- définitions

# Pour chaque type : libellé lisible + champs attendus (nom technique -> description FR).
DOCUMENT_SCHEMAS: dict[str, dict[str, Any]] = {
    "PASSPORT": {
        "label": "Passeport",
        "fields": {
            "lastName": "Nom de famille tel qu'imprimé",
            "firstName": "Prénom(s)",
            "passportNumber": "Numéro du passeport",
            "nationality": "Nationalité",
            "birthDate": "Date de naissance au format AAAA-MM-JJ",
            "birthPlace": "Lieu de naissance",
            "sex": "Sexe (M/F) si présent",
            "issueDate": "Date de délivrance au format AAAA-MM-JJ",
            "expiryDate": "Date d'expiration au format AAAA-MM-JJ",
            "issuingCountry": "Pays émetteur",
            "mrz": "Ligne(s) MRZ en bas du document, si lisibles",
        },
    },
    "DIPLOMA": {
        "label": "Diplôme",
        "fields": {
            "holderName": "Nom complet du titulaire",
            "degreeTitle": "Intitulé exact du diplôme",
            "specialty": "Spécialité médicale si mentionnée",
            "institution": "Établissement ayant délivré le diplôme",
            "country": "Pays de l'établissement",
            "graduationDate": "Date d'obtention au format AAAA-MM-JJ",
            "mention": "Mention ou note globale si présente",
            "diplomaNumber": "Numéro ou référence du diplôme",
        },
    },
    "MEDICAL_COUNCIL_CERT": {
        "label": "Inscription à l'Ordre des médecins",
        "fields": {
            "holderName": "Nom complet du médecin",
            "councilNumber": "Numéro d'inscription à l'Ordre",
            "councilName": "Nom de l'Ordre / du conseil",
            "country": "Pays",
            "specialty": "Spécialité inscrite",
            "registrationDate": "Date d'inscription au format AAAA-MM-JJ",
            "validUntil": "Date de fin de validité au format AAAA-MM-JJ si présente",
            "status": "Statut (en exercice, suspendu, ...) si mentionné",
        },
    },
    "FINANCIAL_GUARANTEE": {
        "label": "Garantie financière",
        "fields": {
            "guarantorName": "Nom du garant (personne ou organisme)",
            "beneficiaryName": "Nom du bénéficiaire",
            "amount": "Montant garanti (chiffres uniquement)",
            "currency": "Devise (code ISO si possible)",
            "bankName": "Établissement bancaire",
            "issueDate": "Date d'émission au format AAAA-MM-JJ",
            "validUntil": "Date de fin de validité au format AAAA-MM-JJ",
        },
    },
    "VISA_GRANT": {
        "label": "Visa accordé",
        "fields": {
            "holderName": "Nom complet du titulaire",
            "visaNumber": "Numéro du visa",
            "visaType": "Type / catégorie de visa",
            "issuingCountry": "Pays émetteur",
            "issueDate": "Date de délivrance au format AAAA-MM-JJ",
            "expiryDate": "Date d'expiration au format AAAA-MM-JJ",
            "durationOfStay": "Durée de séjour autorisée",
            "entries": "Nombre d'entrées autorisées",
        },
    },
    "CONSULAR_LETTER": {
        "label": "Lettre consulaire",
        "fields": {
            "recipientName": "Destinataire",
            "consulate": "Consulat / ambassade émetteur",
            "referenceNumber": "Numéro de référence",
            "issueDate": "Date au format AAAA-MM-JJ",
            "purpose": "Objet de la lettre en une phrase",
        },
    },
    "ACCOMMODATION_PROOF": {
        "label": "Justificatif d'hébergement",
        "fields": {
            "occupantName": "Nom de la personne hébergée",
            "hostName": "Nom de l'hébergeant ou de l'établissement",
            "address": "Adresse complète du logement",
            "city": "Ville",
            "postalCode": "Code postal",
            "country": "Pays",
            "startDate": "Début de l'hébergement au format AAAA-MM-JJ",
            "endDate": "Fin de l'hébergement au format AAAA-MM-JJ",
        },
    },
    "OTHER": {
        "label": "Document non typé",
        "fields": {
            "documentNature": "Nature du document en quelques mots",
            "holderName": "Nom de la personne concernée si identifiable",
            "issueDate": "Date du document au format AAAA-MM-JJ",
            "summary": "Résumé du contenu en une à deux phrases",
        },
    },
}


def known_types() -> list[str]:
    return list(DOCUMENT_SCHEMAS.keys())


def schema_for(document_type: str) -> dict[str, Any]:
    return DOCUMENT_SCHEMAS.get(document_type.upper(), DOCUMENT_SCHEMAS["OTHER"])
