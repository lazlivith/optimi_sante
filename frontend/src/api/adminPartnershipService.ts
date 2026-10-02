import { axiosClient } from './axiosClient';
import type { PartnershipRequestDto } from './partnershipService';

export const adminPartnershipService = {
  listRequests: async (): Promise<PartnershipRequestDto[]> => {
    const { data } = await axiosClient.get<PartnershipRequestDto[]>('/admin/partnership-requests');
    return data;
  },

  getDocumentUrl: async (id: string): Promise<string> => {
    const { data } = await axiosClient.get<{ downloadUrl: string }>(`/admin/partnership-requests/${id}/document`);
    return data.downloadUrl;
  },

  /**
   * Valide la demande et ouvre l'espace partenaire.
   *
   * `b2bDiscountRate` est la remise boutique accordee a l'etablissement, en pourcentage :
   * un partenaire achete aussi du materiel. `confirmerConversion` n'est necessaire que si
   * l'adresse appartient deja a un compte client ou medecin — le serveur refuse alors sans
   * elle, en disant ce qui serait perdu.
   */
  approve: async (
    id: string,
    decision: { b2bDiscountRate: number; confirmerConversion?: boolean },
  ): Promise<PartnershipRequestDto> => {
    const { data } = await axiosClient.patch<PartnershipRequestDto>(
      `/admin/partnership-requests/${id}/approve`, decision);
    return data;
  },

  reject: async (id: string, reason?: string): Promise<PartnershipRequestDto> => {
    const { data } = await axiosClient.patch<PartnershipRequestDto>(`/admin/partnership-requests/${id}/reject`, { reason });
    return data;
  },
};
