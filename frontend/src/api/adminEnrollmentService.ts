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
}

export const adminEnrollmentService = {
  listEnrollments: async (): Promise<EnrollmentDetailDto[]> => {
    const { data } = await axiosClient.get<EnrollmentDetailDto[]>('/admin/enrollments');
    return data;
  }
};
