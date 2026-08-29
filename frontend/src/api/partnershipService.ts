import { axiosClient } from './axiosClient';

export interface PartnershipRequestDto {
  id: string;
  institutionName: string;
  finessAccreditation: string | null;
  contactPersonName: string;
  contactEmail: string;
  contactPhone: string;
  address: string;
  conventionFileKey: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectionReason: string | null;
  createdAt: string;
  reviewedAt: string | null;
}

export interface SubmitPartnershipRequestPayload {
  institutionName: string;
  finessAccreditation?: string;
  contactPersonName: string;
  contactEmail: string;
  contactPhone: string;
  address: string;
  conventionFile: File;
}

export const partnershipService = {
  getConventionTemplateUrl: async (): Promise<string> => {
    const { data } = await axiosClient.get<{ downloadUrl: string }>('/partnership/convention-template');
    return data.downloadUrl;
  },

  submitRequest: async (payload: SubmitPartnershipRequestPayload): Promise<PartnershipRequestDto> => {
    const formData = new FormData();
    formData.append('institutionName', payload.institutionName);
    if (payload.finessAccreditation) formData.append('finessAccreditation', payload.finessAccreditation);
    formData.append('contactPersonName', payload.contactPersonName);
    formData.append('contactEmail', payload.contactEmail);
    formData.append('contactPhone', payload.contactPhone);
    formData.append('address', payload.address);
    formData.append('conventionFile', payload.conventionFile);

    const { data } = await axiosClient.post<PartnershipRequestDto>('/partnership/requests', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },
};
