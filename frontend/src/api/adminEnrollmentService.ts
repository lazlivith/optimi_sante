import { axiosClient } from './axiosClient';

export interface EnrollmentDetailDto {
  id: string;
  status: string;
  trainingTitle: string;
  doctorName?: string;
  doctorEmail?: string;
  submittedAt: string;
  diplomaUrl?: string;
  medicalBoardRegistrationUrl?: string;
  passportUrl?: string;
  conventionS3Key?: string;
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
