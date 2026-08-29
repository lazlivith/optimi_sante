import { axiosClient } from './axiosClient';
import type { DocumentItemDto } from './vaultService';

export interface CreateSessionRequestDto {
  trainingId: string;
  startDate: string;
  endDate: string;
  capacity: number;
  location: string;
  price: number;
}

export interface PartnerSessionDto {
  id: string;
  trainingId: string;
  trainingTitle: string;
  startDate: string;
  endDate: string;
  capacity: number;
  availableSeats: number;
  location: string;
  price: number;
  status: string;
}

export interface EnrollmentDto {
  id: string;
  doctorEmail: string;
  doctorName: string;
  sessionStartDate: string;
  status: string;
  submittedAt: string;
  trainingTitle?: string;
  trainingId?: string;
}

export interface CreateTrainingRequestDto {
  title: string;
  medicalSpecialty: string;
  description: string;
  durationDays: number;
  isLongStay: boolean;
  price: number;
}

export interface PartnerTrainingDto {
  id: string;
  title: string;
  slug: string;
  medicalSpecialty: string;
  description: string;
  durationDays: number;
  isLongStay: boolean;
  price: number;
  isPublished: boolean;
  approvalStatus: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';
  rejectionReason: string | null;
  brochureUrl: string | null;
  imageUrl: string | null;
  videoUrl: string | null;
  createdAt: string;
}

export const partnerService = {

  getMySessions: async (): Promise<PartnerSessionDto[]> => {
    const { data } = await axiosClient.get<PartnerSessionDto[]>('/partner/sessions');
    return data;
  },

  createSession: async (request: CreateSessionRequestDto) => {
    const { data } = await axiosClient.post('/partner/sessions', request);
    return data;
  },

  reviewAcademic: async (enrollmentId: string, status: string = 'APPROVED_ACADEMIC') => {
    // Uses the PATCH endpoint for status update
    const { data } = await axiosClient.patch(`/partner/enrollments/${enrollmentId}/academic-review`, { status });
    return data;
  },

  getEnrollments: async (trainingId?: string): Promise<EnrollmentDto[]> => {
    const { data } = await axiosClient.get<EnrollmentDto[]>('/partner/enrollments', {
      params: trainingId ? { trainingId } : undefined
    });
    return data;
  },

  // Consultation des pièces du dossier (passeport, diplôme...) avant validation académique.
  getEnrollmentDocuments: async (enrollmentId: string): Promise<DocumentItemDto[]> => {
    const { data } = await axiosClient.get<DocumentItemDto[]>(`/partner/enrollments/${enrollmentId}/documents`);
    return data;
  },

  // --- Gestion des formations (CRUD partenaire) ---

  // Remplace l'ancien endpoint résumé (GET /partner/trainings) : cette version détaillée
  // (avec statut de validation, médias...) est désormais l'unique source pour l'espace partenaire.
  getMyTrainingsDetailed: async (): Promise<PartnerTrainingDto[]> => {
    const { data } = await axiosClient.get<PartnerTrainingDto[]>('/partner/trainings');
    return data;
  },

  createTraining: async (dto: CreateTrainingRequestDto): Promise<PartnerTrainingDto> => {
    const { data } = await axiosClient.post<PartnerTrainingDto>('/partner/trainings', dto);
    return data;
  },

  updateTraining: async (id: string, dto: CreateTrainingRequestDto): Promise<PartnerTrainingDto> => {
    const { data } = await axiosClient.put<PartnerTrainingDto>(`/partner/trainings/${id}`, dto);
    return data;
  },

  deleteTraining: async (id: string): Promise<void> => {
    await axiosClient.delete(`/partner/trainings/${id}`);
  },

  uploadTrainingImage: async (id: string, file: File): Promise<PartnerTrainingDto> => {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await axiosClient.post<PartnerTrainingDto>(`/partner/trainings/${id}/image`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },

  uploadTrainingVideo: async (id: string, file: File): Promise<PartnerTrainingDto> => {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await axiosClient.post<PartnerTrainingDto>(`/partner/trainings/${id}/video`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },
};
