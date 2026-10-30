import { axiosClient } from './axiosClient';

export interface UploadResponseDto {
  /**
   * Référence signée du fichier déposé (DP-4). Ce n'est plus le `public_id` Cloudinary : une
   * référence à courte durée que le serveur résout en clé. L'inscription la renvoie telle quelle.
   */
  reference: string;
  /** Aperçu servi par notre backend (jamais une URL Cloudinary). */
  previewUrl: string;
  message: string;
}

/**
 * Dossiers ouverts au dépôt générique. Le serveur tient la liste fermée (DossierStockage) : un
 * nom absent de sa liste est refusé, un chemin libre n'est plus accepté.
 */
export type DossierDepot = 'DOSSIERS_PIECES';

export const storageService = {
  uploadFile: async (file: File, folder: DossierDepot = 'DOSSIERS_PIECES'): Promise<UploadResponseDto> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('folder', folder);

    const response = await axiosClient.post<UploadResponseDto>('/storage/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },
};
