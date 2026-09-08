import { axiosClient } from './axiosClient';

export interface AiStatus {
  configured: boolean;
  reachable: boolean;
  error?: string;
  worker?: {
    status?: string;
    capabilities?: Record<string, { configured: boolean; model: string }>;
    documentTypes?: string[];
  };
}

export interface ExtractedField {
  value: string | null;
  confidence: number;
}

export interface ExtractionResult {
  jobId: string;
  document_type: string;
  type_matches: boolean;
  detected_type: string | null;
  fields: Record<string, ExtractedField>;
  warnings: string[];
  model: string | null;
}

export interface DocumentTypeSpec {
  key: string;
  label: string;
  fields: string[];
}

export interface DraftTypeSpec {
  key: string;
  label: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

const base = '/ai';

export const aiService = {
  status: async (): Promise<AiStatus> => {
    const { data } = await axiosClient.get<AiStatus>(`${base}/status`);
    return data;
  },

  // --- extraction documentaire (Gemini) ---------------------------------------

  extractionTypes: async (): Promise<DocumentTypeSpec[]> => {
    const { data } = await axiosClient.get<DocumentTypeSpec[]>(`${base}/extract/types`);
    return data;
  },

  extract: async (file: File, documentType: string): Promise<ExtractionResult> => {
    const form = new FormData();
    form.append('file', file);
    form.append('documentType', documentType);
    const { data } = await axiosClient.post<ExtractionResult>(`${base}/extract`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 180_000,
    });
    return data;
  },

  /** Confirme que les valeurs proposées ont bien été reportées dans le dossier par un humain. */
  markApplied: async (jobId: string): Promise<void> => {
    await axiosClient.post(`${base}/extract/${jobId}/applied`);
  },

  // --- rédaction assistée (Mistral) -------------------------------------------

  draftTypes: async (): Promise<DraftTypeSpec[]> => {
    const { data } = await axiosClient.get<DraftTypeSpec[]>(`${base}/draft/types`);
    return data;
  },

  draft: async (
    draftType: string,
    context: Record<string, unknown>,
    extra?: string,
  ): Promise<{ text: string; model: string }> => {
    const { data } = await axiosClient.post(`${base}/draft`, { draftType, context, extra }, {
      timeout: 120_000,
    });
    return data;
  },

  summarize: async (
    kind: string,
    context: Record<string, unknown>,
  ): Promise<{ text: string; model: string }> => {
    const { data } = await axiosClient.post(`${base}/summarize`, { kind, context }, {
      timeout: 120_000,
    });
    return data;
  },

  // --- assistant conversationnel ----------------------------------------------

  chat: async (
    question: string,
    conversationId?: string | null,
  ): Promise<{ answer: string; conversationId: string; model: string }> => {
    const { data } = await axiosClient.post(`${base}/chat`, { question, conversationId }, {
      timeout: 180_000,
    });
    return data;
  },

  messages: async (conversationId: string): Promise<ChatMessage[]> => {
    const { data } = await axiosClient.get<ChatMessage[]>(
      `${base}/conversations/${conversationId}/messages`,
    );
    return data;
  },
};

// --------------------------------------------------------------------------- streaming

export interface ChatStreamHandlers {
  onConversation?: (conversationId: string) => void;
  onDelta: (text: string) => void;
  onDone?: () => void;
  onError?: (message: string) => void;
}

/**
 * Flux SSE de la réponse de l'assistant.
 *
 * On passe par `fetch` + `ReadableStream` et non par `EventSource` : notre endpoint est en POST
 * et doit porter l'en-tête `Authorization`, ce qu'`EventSource` ne sait pas faire. On analyse
 * donc les trames SSE à la main.
 *
 * Renvoie une fonction d'annulation (à appeler au démontage du composant).
 */
export function streamChat(
  question: string,
  conversationId: string | null | undefined,
  handlers: ChatStreamHandlers,
): () => void {
  const controller = new AbortController();
  const token = localStorage.getItem('token');
  const tenantId = localStorage.getItem('tenantId') || 'FR_MAIN';

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    'X-Tenant-Id': tenantId,
  };
  if (token) headers.Authorization = `Bearer ${token}`;

  (async () => {
    try {
      const response = await fetch('/api/v1/ai/chat/stream', {
        method: 'POST',
        headers,
        body: JSON.stringify({ question, conversationId: conversationId ?? null }),
        signal: controller.signal,
      });

      if (!response.ok || !response.body) {
        handlers.onError?.(
          response.status === 409
            ? "Assistant indisponible : le service IA n'est pas démarré ou sa clé API manque."
            : `Assistant indisponible (erreur ${response.status}).`,
        );
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // Les trames SSE sont séparées par une ligne vide.
        const frames = buffer.split('\n\n');
        buffer = frames.pop() ?? '';

        for (const frame of frames) {
          let event = 'message';
          const dataLines: string[] = [];
          for (const line of frame.split('\n')) {
            if (line.startsWith('event:')) event = line.slice(6).trim();
            else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''));
          }
          const data = dataLines.join('\n');

          if (event === 'delta') handlers.onDelta(data);
          else if (event === 'conversation') {
            try {
              handlers.onConversation?.(JSON.parse(data).conversationId);
            } catch {
              /* trame de contrôle malformée : sans conséquence */
            }
          } else if (event === 'done') handlers.onDone?.();
          else if (event === 'error') handlers.onError?.(data);
        }
      }
      handlers.onDone?.();
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        handlers.onError?.("L'assistant a été interrompu.");
      }
    }
  })();

  return () => controller.abort();
}

/** Libellés lisibles des champs extraits, pour l'écran de validation. */
export const FIELD_LABELS: Record<string, string> = {
  lastName: 'Nom',
  firstName: 'Prénom(s)',
  holderName: 'Titulaire',
  occupantName: 'Personne hébergée',
  hostName: 'Hébergeant',
  recipientName: 'Destinataire',
  guarantorName: 'Garant',
  beneficiaryName: 'Bénéficiaire',
  passportNumber: 'N° de passeport',
  councilNumber: "N° d'inscription à l'Ordre",
  councilName: "Ordre / conseil",
  diplomaNumber: 'N° du diplôme',
  visaNumber: 'N° de visa',
  referenceNumber: 'N° de référence',
  nationality: 'Nationalité',
  birthDate: 'Date de naissance',
  birthPlace: 'Lieu de naissance',
  sex: 'Sexe',
  issueDate: 'Date de délivrance',
  expiryDate: "Date d'expiration",
  issuingCountry: 'Pays émetteur',
  country: 'Pays',
  mrz: 'Bande MRZ',
  degreeTitle: 'Intitulé du diplôme',
  specialty: 'Spécialité',
  institution: 'Établissement',
  graduationDate: "Date d'obtention",
  mention: 'Mention',
  registrationDate: "Date d'inscription",
  validUntil: 'Valide jusqu\'au',
  status: 'Statut',
  amount: 'Montant',
  currency: 'Devise',
  bankName: 'Banque',
  visaType: 'Type de visa',
  durationOfStay: 'Durée de séjour',
  entries: "Nombre d'entrées",
  consulate: 'Consulat',
  purpose: 'Objet',
  address: 'Adresse',
  city: 'Ville',
  postalCode: 'Code postal',
  startDate: 'Début',
  endDate: 'Fin',
  documentNature: 'Nature du document',
  summary: 'Résumé',
};

export const fieldLabel = (key: string): string =>
  FIELD_LABELS[key] ?? key.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase());
