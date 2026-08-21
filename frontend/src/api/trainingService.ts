import { axiosClient } from './axiosClient';

export interface LeadCaptureRequestDto {
  email: string;
  firstName: string;
  lastName: string;
  phoneWhatsapp: string;
  country: string;
  specialty: string;
}

export interface LeadCaptureResponseDto {
  brochureDownloadUrl: string;
}

export const trainingService = {
  captureLead: async (trainingId: string, request: LeadCaptureRequestDto) => {
    const { data } = await axiosClient.post<LeadCaptureResponseDto>(`/trainings/${trainingId}/lead-capture`, request);
    return data;
  },

  uploadBrochure: async (
    trainingId: string, 
    file: File,
    onProgress?: (percent: number) => void
  ): Promise<{ publicId: string; message: string }> => {
    const formData = new FormData();
    formData.append('file', file);

    const { data } = await axiosClient.post(`/partner/trainings/${trainingId}/brochure`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(percent);
        }
      },
    });

    return data;
  }
};
