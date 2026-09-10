import { axiosClient } from './axiosClient';

export interface AdminTrainingDto {
  id: string;
  title: string;
  medicalSpecialty: string;
  description: string;
  durationDays: number;
  isLongStay: boolean;
  price: number;
  /** Tarif propre a la formation. `null` = valeur globale appliquee. */
  applicationFee: number | null;
  isPublished: boolean;
  approvalStatus: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';
  rejectionReason: string | null;
  brochureUrl: string | null;
  imageUrl: string | null;
  videoUrl: string | null;
  partnerInstitutionName: string;
  partnerContactEmail: string;
  createdAt: string;
}

export const adminTrainingService = {
  listTrainings: async (status?: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'): Promise<AdminTrainingDto[]> => {
    const { data } = await axiosClient.get<AdminTrainingDto[]>('/admin/trainings', {
      params: status ? { status } : undefined,
    });
    return data;
  },

  /**
   * Valide et publie la formation, en fixant ses frais de dossier dans le meme geste.
   *
   * `applicationFee` omis conserve les frais existants ; passe a `null` il retire le tarif
   * propre et fait revenir la formation a la valeur globale. Les confondre ferait perdre un
   * tarif deja saisi a chaque revalidation.
   */
  approve: async (id: string, applicationFee?: number | null): Promise<AdminTrainingDto> => {
    const { data } = await axiosClient.patch<AdminTrainingDto>(
      `/admin/trainings/${id}/approve`,
      applicationFee === undefined ? undefined : { applicationFee },
    );
    return data;
  },

  reject: async (id: string, reason: string): Promise<AdminTrainingDto> => {
    const { data } = await axiosClient.patch<AdminTrainingDto>(`/admin/trainings/${id}/reject`, { reason });
    return data;
  },

  /**
   * Fixe les frais de dossier de la formation. `null` retire le tarif propre et fait revenir
   * la formation a la valeur globale — a ne pas confondre avec 0, qui rendrait la candidature
   * gratuite.
   */
  setApplicationFee: async (id: string, applicationFee: number | null): Promise<AdminTrainingDto> => {
    const { data } = await axiosClient.patch<AdminTrainingDto>(
      `/admin/trainings/${id}/application-fee`, { applicationFee });
    return data;
  },
};
