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
