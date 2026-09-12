import { axiosClient } from './axiosClient';

export interface DocumentItemDto {
  id: string;
  title: string;
  type: 'CONVENTION' | 'ATTESTATION' | 'INVOICE' | 'QUOTE' | 'PASSPORT' | 'DIPLOMA' | 'MEDICAL_COUNCIL_CERT' | 'FINANCIAL_GUARANTEE' | 'VISA_GRANT' | 'CONSULAR_LETTER' | 'ACCOMMODATION_PROOF' | 'OTHER';
  date: string;
  status: string;
  documentKey: string;
  sha256Checksum?: string;
  /** Libellé calculé par le serveur à partir de son énumération — fait foi quand il est là. */
  typeLabel?: string;
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
  INTERVIEW_CONVOCATION: "Convocation à l'entretien visio",
  SERVICE_SUBSCRIPTION: 'Attestation de souscription',
  OTHER: 'Autre pièce justificative',
};

/**
 * Libellé d'une pièce.
 *
 * <p>Le serveur envoie désormais `typeLabel` avec chaque document : c'est la source qui fait
 * foi, et elle ne peut pas diverger de l'énumération puisqu'elle en vient. La table ci-dessus
 * n'est plus qu'un repli pour les quelques types fabriqués côté serveur sans libellé (le
 * coffre-fort du médecin), et pour les réponses d'une version antérieure.</p>
 *
 * <p>Elle avait d'ailleurs divergé : `INTERVIEW_CONVOCATION` et `SERVICE_SUBSCRIPTION`
 * existaient en base sans y figurer, si bien qu'un administrateur vérifiant un dossier lisait
 * le nom technique.</p>
 */
export const getDocumentLabel = (type: string, typeLabel?: string): string =>
  typeLabel || DOCUMENT_LABELS[type] || type;

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
