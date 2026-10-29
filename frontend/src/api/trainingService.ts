import { axiosClient } from './axiosClient';

export interface LeadCaptureRequestDto {
  email: string;
  firstName: string;
  lastName: string;
  phoneWhatsapp: string;
  country: string;
  specialty: string;
}

/**
 * La réponse ne porte plus aucun lien, et c'est tout son objet.
 *
 * Elle portait `brochureDownloadUrl` : un lien vers la brochure **d'origine du partenaire**,
 * rendu à quiconque laissait une adresse sur le formulaire public. Les coordonnées du CHU
 * partaient avec. Rien ici ne doit redevenir une URL, un jeton ou une clé de fichier.
 */
export interface LeadCaptureResponseDto {
  demandeEnregistree: boolean;
}

export interface TrainingSummaryDto {
  id: string;
  title: string;
  medicalSpecialty: string;
  description: string | null;
  durationDays: number;
  isLongStay: boolean;
  location: string | null;
  /* Aucun champ de brochure : le serveur ne sert plus le fichier d'origine du partenaire sur
     cette route publique. Le bouton « Brochure » garde son message d'indisponibilité. */
  imageUrl: string | null;
  videoUrl: string | null;
  price: number;
  /** Frais de dossier applicables, repli global deja resolu par le serveur. */
  applicationFee: number | null;
  /** Le centre qui dispense la formation, et l'adresse de ses locaux. */
  centerName: string | null;
  centerAddress: string | null;
}

export const trainingService = {
  getTrainings: async (): Promise<TrainingSummaryDto[]> => {
    const { data } = await axiosClient.get<TrainingSummaryDto[]>('/trainings');
    return data;
  },

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
