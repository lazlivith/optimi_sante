import { axiosClient } from './axiosClient';

export interface TrainingSessionDto {
  id: string;
  startDate: string;
  endDate: string;
  capacity: number;
  availableSeats: number;
  location: string;
  price: number;
  status: string;
}

export interface EnrollmentResponseDto {
  id: string;
  status: string;
  diplomaUrl?: string;
  medicalBoardRegistrationUrl?: string;
  passportUrl?: string;
  submittedAt: string;
}

export interface EnrollmentRequestDto {
  sessionId: string;
}

export interface DocumentUploadRequestDto {
  diplomaUrl?: string;
  medicalBoardRegistrationUrl?: string;
  passportUrl?: string;
}

export interface EnrollmentDetailDto {
  id: string;
  status: string;
  trainingTitle: string;
  submittedAt: string;
  diplomaUrl?: string;
  medicalBoardRegistrationUrl?: string;
  passportUrl?: string;
  conventionS3Key?: string;
}

export const enrollmentService = {
  getAvailableSessions: async (trainingId: string): Promise<TrainingSessionDto[]> => {
    const { data } = await axiosClient.get<TrainingSessionDto[]>(`/trainings/${trainingId}/sessions`);
    return data;
  },

  getMyEnrollments: async (): Promise<EnrollmentDetailDto[]> => {
    const { data } = await axiosClient.get<EnrollmentDetailDto[]>('/enrollments');
    return data;
  },

  createEnrollment: async (request: EnrollmentRequestDto): Promise<EnrollmentResponseDto> => {
    const { data } = await axiosClient.post<EnrollmentResponseDto>('/enrollments', request);
    return data;
  },

  submitDocuments: async (enrollmentId: string, request: DocumentUploadRequestDto): Promise<EnrollmentResponseDto> => {
    const { data } = await axiosClient.put<EnrollmentResponseDto>(`/enrollments/${enrollmentId}/documents`, request);
    return data;
  },

  uploadDocument: async (enrollmentId: string, file: File, documentType: string, onProgress?: (percent: number) => void) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('documentType', documentType);

    const { data } = await axiosClient.post(`/enrollments/${enrollmentId}/documents`, formData, {
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
