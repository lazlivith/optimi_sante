import { axiosClient } from './axiosClient';

/** Taxonomie de classement d'une pièce. Miroir de l'énumération `DocumentType` du backend. */
export const DOCUMENT_TYPES = [
  { value: 'PASSPORT', label: 'Passeport' },
  { value: 'DIPLOMA', label: 'Diplôme' },
  { value: 'MEDICAL_COUNCIL_CERT', label: 'Inscription à l\'Ordre' },
  { value: 'FINANCIAL_GUARANTEE', label: 'Garantie financière' },
  { value: 'VISA_GRANT', label: 'Visa' },
  { value: 'CONSULAR_LETTER', label: 'Courrier consulaire' },
  { value: 'ACCOMMODATION_PROOF', label: 'Justificatif d\'hébergement' },
  { value: 'INTERVIEW_CONVOCATION', label: "Convocation à l'entretien visio" },
  { value: 'OTHER', label: 'Autre pièce' },
] as const;

export type DocumentTypeValue = (typeof DOCUMENT_TYPES)[number]['value'];

export const documentTypeLabel = (value: string) =>
  DOCUMENT_TYPES.find((t) => t.value === value)?.label ?? value;

export type DocumentRequestStatus =
  | 'PENDING' | 'SUBMITTED' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';

export interface DocumentRequestView {
  id: string;
  documentType: DocumentTypeValue;
  label: string;
  instructions: string | null;
  status: DocumentRequestStatus;
  dueDate: string | null;
  requestedAt: string;
  submittedAt: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  documentId: string | null;
  /** Vrai tant que la demande appelle une action de l'un ou de l'autre. */
  open: boolean;
}

export interface DossierSummary {
  total: number;
  accepted: number;
  /** PENDING + REJECTED : la balle est chez le médecin. */
  awaitingDoctor: number;
  /** SUBMITTED : la balle est chez l'administration. */
  awaitingReview: number;
  complete: boolean;
  requests: DocumentRequestView[];
}

export interface CreateDocumentRequest {
  documentType: DocumentTypeValue;
  label: string;
  instructions?: string;
  dueDate?: string;
}

export const documentRequestService = {
  // ---- Administration -------------------------------------------------------
  listForAdmin: async (enrollmentId: string): Promise<DossierSummary> =>
    (await axiosClient.get<DossierSummary>(`/admin/enrollments/${enrollmentId}/document-requests`)).data,

  create: async (enrollmentId: string, body: CreateDocumentRequest): Promise<DocumentRequestView> =>
    (await axiosClient.post<DocumentRequestView>(`/admin/enrollments/${enrollmentId}/document-requests`, body)).data,

  review: async (requestId: string, accepted: boolean, rejectionReason?: string): Promise<DocumentRequestView> =>
    (await axiosClient.post<DocumentRequestView>(`/admin/document-requests/${requestId}/review`,
      { accepted, rejectionReason })).data,

  cancel: async (requestId: string): Promise<DocumentRequestView> =>
    (await axiosClient.post<DocumentRequestView>(`/admin/document-requests/${requestId}/cancel`)).data,

  // ---- Médecin ---------------------------------------------------------------
  listForDoctor: async (enrollmentId: string): Promise<DossierSummary> =>
    (await axiosClient.get<DossierSummary>(`/enrollments/${enrollmentId}/document-requests`)).data,

  /**
   * Dépôt de la pièce réclamée.
   *
   * Route distincte de l'envoi générique de document : c'est elle qui rattache le fichier à la
   * demande. Déposée ailleurs, la pièce arriverait dans le dossier mais la demande resterait
   * ouverte, et les deux parties croiraient qu'il manque encore quelque chose.
   */
  fulfil: async (requestId: string, file: File): Promise<DocumentRequestView> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await axiosClient.post<DocumentRequestView>(
      `/enrollments/document-requests/${requestId}/fulfil`, form,
      { headers: { 'Content-Type': 'multipart/form-data' } });
    return data;
  },
};
