import { axiosClient } from './axiosClient';

export interface UploadResponseDto {
  publicId: string;
  message: string;
}

export const storageService = {
  uploadFile: async (file: File, folder: string = 'general'): Promise<UploadResponseDto> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('folder', folder);

    const response = await axiosClient.post<UploadResponseDto>('/admin/storage/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },
};
