import { axiosClient } from './axiosClient';

export interface DocumentItemDto {
  id: string;
  title: string;
  type: 'CONVENTION' | 'ATTESTATION' | 'INVOICE' | 'QUOTE' | 'PASSPORT' | 'DIPLOMA' | 'MEDICAL_COUNCIL_CERT' | 'FINANCIAL_GUARANTEE' | 'VISA_GRANT' | 'CONSULAR_LETTER' | 'ACCOMMODATION_PROOF' | 'OTHER';
  date: string;
  status: string;
  documentKey: string;
  sha256Checksum?: string;
}

/**
 * Libellés affichables des pièces du coffre-fort — source unique, partagée par l'espace
 * admin, l'espace CHU et celui du médecin. Sans elle, l'interface montrait l'identifiant
 * technique du type (« MEDICAL_COUNCIL_CERT ») à ceux qui doivent vérifier les pièces,
 * et chaque écran risquait de diverger avec sa propre table.
 */
const DOCUMENT_LABELS: Record<string, string> = {
  PASSPORT: 'Passeport',
  DIPLOMA: 'Diplôme de médecine',
  MEDICAL_COUNCIL_CERT: "Certificat de l'Ordre des Médecins",
  FINANCIAL_GUARANTEE: 'Garantie financière',
  VISA_GRANT: 'Attestation de visa',
  CONSULAR_LETTER: "Lettre d'Accompagnement Consulaire",
  ACCOMMODATION_PROOF: "Attestation d'Hébergement",
  CONVENTION: 'Convention tripartite',
  ATTESTATION: "Attestation d'accueil",
  INVOICE: 'Facture',
  QUOTE: 'Devis',
  OTHER: 'Autre pièce justificative',
};

/** Repli sur le type brut : une pièce d'un type inconnu reste affichée, jamais masquée. */
export const getDocumentLabel = (type: string): string => DOCUMENT_LABELS[type] ?? type;

export const vaultService = {
  getDoctorVault: async (): Promise<DocumentItemDto[]> => {
    const { data } = await axiosClient.get<DocumentItemDto[]>('/doctor/vault');
    return data;
  },

  getPresignedUrl: async (type: string, id: string): Promise<string> => {
    // We already have a generic endpoint from Sprint 4 ? 
    // Wait, the prompt says "GET /api/v1/documents/{type}/{id}/download".
    // I will call this endpoint.
    const { data } = await axiosClient.get<{ downloadUrl: string }>(`/documents/${type}/${id}/download`);
    return data.downloadUrl;
  }
};
