import type { RegistrationType } from './enrollmentService';
import { axiosClient } from './axiosClient';

export interface EnrollmentDetailDto {
  id: string;
  status: string;
  /** INTERNATIONAL_VISA ou LOCAL_FRANCE : détermine le parcours affiché. */
  registrationType?: RegistrationType;
  rppsNumber?: string | null;
  trainingTitle: string;
  doctorName?: string;
  doctorEmail?: string;
  submittedAt: string;
  // DP-4 : plus de clé de pièce ni de convention ; présence seulement, téléchargement par
  // /documents/{type}/{id}/download sous contrôle de rôle.
  hasConvention?: boolean;
  hasAttestation?: boolean;
  /** Lieu de la session (texte libre) — a ne pas confondre avec le CHU partenaire. */
  hostInstitution?: string;
  /** CHU dont releve la formation. L'identifiant sert au filtre, le nom a l'affichage. */
  partnerProfileId?: string;
  partnerName?: string;
}

export const adminEnrollmentService = {
  listEnrollments: async (): Promise<EnrollmentDetailDto[]> => {
    const { data } = await axiosClient.get<EnrollmentDetailDto[]>('/admin/enrollments');
    return data;
  }
};
