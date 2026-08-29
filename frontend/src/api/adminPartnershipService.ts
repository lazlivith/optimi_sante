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

  approve: async (id: string): Promise<PartnershipRequestDto> => {
    const { data } = await axiosClient.patch<PartnershipRequestDto>(`/admin/partnership-requests/${id}/approve`);
    return data;
  },

  reject: async (id: string, reason?: string): Promise<PartnershipRequestDto> => {
    const { data } = await axiosClient.patch<PartnershipRequestDto>(`/admin/partnership-requests/${id}/reject`, { reason });
    return data;
  },
};
