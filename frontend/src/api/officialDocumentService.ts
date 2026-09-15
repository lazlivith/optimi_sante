import { axiosClient } from './axiosClient';

export type OfficialDocumentCategory =
  | 'PROGRAMME' | 'CONVENTION_CHU' | 'BILLET' | 'HEBERGEMENT' | 'CONTACTS' | 'KIT_AUTRE';
export type OfficialDocumentStatus = 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED';

/** Un document officiel du dossier, vu par l'administration ou le CHU. */
export interface OfficialDocument {
  id: string;
  category: OfficialDocumentCategory;
  categoryLabel: string;
  departureKit: boolean;
  title: string;
  fileName: string | null;
  issuer: 'PARTNER' | 'OPTIMI';
  status: OfficialDocumentStatus;
  rejectionReason: string | null;
  uploadedAt: string;
  reviewedAt: string | null;
}

export type VaultEntryState = 'AVAILABLE' | 'LOCKED' | 'UPCOMING';

/** Une ligne du coffre-fort du médecin. */
export interface VaultEntry {
  key: string;
  title: string;
  categoryLabel: string;
  issuerLabel: string;
  date: string | null;
  state: VaultEntryState;
  /** Pourquoi le document n'est pas (encore) ouvrable. */
  reason: string | null;
  downloadType: string | null;
  downloadId: string | null;
}

export interface VaultDossier {
  enrollmentId: string;
  trainingTitle: string;
  institutionName: string;
  sessionStart: string | null;
  status: string;
  entries: VaultEntry[];
}

/** Catégories que le CHU dépose ; le kit de départ est réservé à Optimi Santé. */
export const CATEGORIES_PARTENAIRE: { value: OfficialDocumentCategory; label: string }[] = [
  { value: 'PROGRAMME', label: 'Programme officiel de la formation' },
  { value: 'CONVENTION_CHU', label: "Convention de formation de l'établissement" },
];

export const CATEGORIES_ADMIN: { value: OfficialDocumentCategory; label: string }[] = [
  ...CATEGORIES_PARTENAIRE,
  { value: 'BILLET', label: 'Kit de départ · Billets de voyage' },
  { value: 'HEBERGEMENT', label: "Kit de départ · Réservation d'hébergement" },
  { value: 'CONTACTS', label: 'Kit de départ · Contacts sur place' },
  { value: 'KIT_AUTRE', label: 'Kit de départ · Autre document' },
];

/** Le client envoie du JSON par défaut : sans cet en-tête, le fichier ne part pas en multipart. */
const MULTIPART = { headers: { 'Content-Type': 'multipart/form-data' } };

const formulaire = (file: File, category: OfficialDocumentCategory, title?: string) => {
  const data = new FormData();
  data.append('file', file);
  data.append('category', category);
  if (title?.trim()) data.append('title', title.trim());
  return data;
};

export const officialDocumentService = {
  // ---- Médecin ----
  getMyVault: async () => (await axiosClient.get<VaultDossier[]>('/doctor/vault/dossiers')).data,

  // ---- Partenaire ----
  listForPartner: async (enrollmentId: string) =>
    (await axiosClient.get<OfficialDocument[]>(`/partner/enrollments/${enrollmentId}/official-documents`)).data,
  uploadByPartner: async (enrollmentId: string, file: File, category: OfficialDocumentCategory, title?: string) =>
    (await axiosClient.post<OfficialDocument>(`/partner/enrollments/${enrollmentId}/official-documents`,
      formulaire(file, category, title), MULTIPART)).data,
  deleteByPartner: async (documentId: string) => {
    await axiosClient.delete(`/partner/official-documents/${documentId}`);
  },

  // ---- Administration ----
  listForAdmin: async (enrollmentId: string) =>
    (await axiosClient.get<OfficialDocument[]>(`/admin/enrollments/${enrollmentId}/official-documents`)).data,
  uploadByAdmin: async (enrollmentId: string, file: File, category: OfficialDocumentCategory, title?: string) =>
    (await axiosClient.post<OfficialDocument>(`/admin/enrollments/${enrollmentId}/official-documents`,
      formulaire(file, category, title), MULTIPART)).data,
  review: async (documentId: string, accept: boolean, reason?: string) =>
    (await axiosClient.post<OfficialDocument>(`/admin/official-documents/${documentId}/review`, { accept, reason })).data,
  deleteByAdmin: async (documentId: string) => {
    await axiosClient.delete(`/admin/official-documents/${documentId}`);
  },
};
