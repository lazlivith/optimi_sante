"""
Service IA d'Optimi Santé — API interne appelée par le backend Java.

Deux capacités, deux fournisseurs, toutes deux optionnelles :
  * extraction documentaire -> Gemini (vision + PDF natif)
  * chat / rédaction / résumés -> Mistral (hébergement UE)

Sans clé API, le service démarre quand même : /health annonce la capacité comme désactivée
et les endpoints concernés renvoient 503 avec un message clair. Le reste de la plateforme
n'est jamais bloqué.
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import Body, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from . import chat as chat_module
from . import drafting, extraction
from .llm import LlmError, NotConfiguredError
from .schemas import DOCUMENT_SCHEMAS
from .settings import settings

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)

app = FastAPI(
    title="Optimi Santé — Service IA",
    version="1.0.0",
    description="Extraction documentaire (Gemini) et assistant conversationnel (Mistral).",
)


# --------------------------------------------------------------------------- santé


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "capabilities": {
            "extraction": {
                "configured": settings.is_configured(settings.extraction_model),
                "model": settings.extraction_model,
            },
            "chat": {
                "configured": settings.is_configured(settings.chat_model),
                "model": settings.chat_model,
            },
            "drafting": {
                "configured": settings.is_configured(settings.draft_model),
                "model": settings.draft_model,
            },
        },
        "documentTypes": list(DOCUMENT_SCHEMAS.keys()),
    }


# --------------------------------------------------------------------------- extraction


@app.get("/extract/types")
def extraction_types() -> list[dict[str, Any]]:
    return [
        {"key": key, "label": spec["label"], "fields": list(spec["fields"].keys())}
        for key, spec in DOCUMENT_SCHEMAS.items()
    ]


@app.post("/extract")
async def extract_document(
    file: UploadFile = File(...),
    document_type: str = Form("OTHER"),
) -> dict[str, Any]:
    content = await file.read()

    max_bytes = settings.max_upload_mb * 1024 * 1024
    if len(content) > max_bytes:
        raise HTTPException(413, f"Fichier trop volumineux (max {settings.max_upload_mb} Mo).")
    if not content:
        raise HTTPException(400, "Fichier vide.")

    mime = (file.content_type or "").lower().split(";")[0]
    if mime not in extraction.SUPPORTED_MIME:
        raise HTTPException(
            415,
            f"Type de fichier non pris en charge : {mime or 'inconnu'}. "
            f"Formats acceptés : PDF, JPEG, PNG, WEBP, HEIC, TIFF.",
        )

    try:
        result = extraction.extract(content, mime, document_type)
    except NotConfiguredError as e:
        raise HTTPException(503, str(e)) from e
    except LlmError as e:
        raise HTTPException(502, str(e)) from e

    return result.model_dump()


# --------------------------------------------------------------------------- rédaction


class DraftRequest(BaseModel):
    draftType: str = Field(default="GENERIC_EMAIL")
    context: dict[str, Any] | None = None
    extra: str | None = None


class SummarizeRequest(BaseModel):
    kind: str = Field(default="GENERIC")
    context: dict[str, Any] | None = None


@app.get("/draft/types")
def draft_types() -> list[dict[str, str]]:
    return drafting.draft_types()


@app.post("/draft")
def draft(request: DraftRequest = Body(...)) -> dict[str, Any]:
    try:
        text = drafting.draft(request.draftType, request.context, request.extra)
    except NotConfiguredError as e:
        raise HTTPException(503, str(e)) from e
    except LlmError as e:
        raise HTTPException(502, str(e)) from e
    return {"draftType": request.draftType.upper(), "text": text, "model": settings.draft_model}


@app.post("/summarize")
def summarize(request: SummarizeRequest = Body(...)) -> dict[str, Any]:
    try:
        text = drafting.summarize(request.kind, request.context)
    except NotConfiguredError as e:
        raise HTTPException(503, str(e)) from e
    except LlmError as e:
        raise HTTPException(502, str(e)) from e
    return {"kind": request.kind.upper(), "text": text, "model": settings.draft_model}


# --------------------------------------------------------------------------- chat


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    question: str
    history: list[ChatMessage] = Field(default_factory=list)
    userRole: str | None = None
    catalogContext: str | None = None


def _history(request: ChatRequest) -> list[dict[str, Any]]:
    allowed = {"user", "assistant"}
    return [
        {"role": m.role, "content": m.content}
        for m in request.history
        if m.role in allowed and m.content
    ]


@app.post("/chat")
def chat(
    request: ChatRequest = Body(...),
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    if not request.question.strip():
        raise HTTPException(400, "Question vide.")
    try:
        text = chat_module.answer(
            request.question, _history(request), request.userRole, authorization,
            request.catalogContext,
        )
    except NotConfiguredError as e:
        raise HTTPException(503, str(e)) from e
    except LlmError as e:
        raise HTTPException(502, str(e)) from e
    return {"answer": text, "model": settings.chat_model}


@app.post("/chat/stream")
def chat_stream(
    request: ChatRequest = Body(...),
    authorization: str | None = Header(default=None),
) -> StreamingResponse:
    if not request.question.strip():
        raise HTTPException(400, "Question vide.")

    def events():
        try:
            for piece in chat_module.answer_stream(
                request.question, _history(request), request.userRole, authorization,
                request.catalogContext,
            ):
                yield _sse("delta", piece)
            yield _sse("done", "")
        except NotConfiguredError as e:
            yield _sse("error", str(e))
        except LlmError as e:
            yield _sse("error", str(e))
        except Exception as e:  # noqa: BLE001
            log.exception("chat stream failed")
            yield _sse("error", f"Erreur inattendue : {e}")

    return StreamingResponse(
        events(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


def _sse(event: str, data: str) -> str:
    # Une seule ligne `data:` : on échappe les sauts de ligne pour ne pas casser la trame.
    payload = data.replace("\r", "").replace("\n", "\\n")
    return f"event: {event}\ndata: {payload}\n\n"
