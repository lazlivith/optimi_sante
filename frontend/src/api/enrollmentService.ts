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
  attestationS3Key?: string;
  /** Établissement d'accueil de la session. */
  hostInstitution?: string | null;
  /** Frais de formation à régler, en euros. */
  tuitionAmount?: number | null;
  /** Échéancier calculé côté serveur : l'écran ne recalcule aucun pourcentage. */
  tuitionDepositAmount?: number | null;
  tuitionBalanceAmount?: number | null;
  tuitionDepositRate?: number | null;
  tuitionOutstanding?: number | null;
  /** Pièce ou correction réclamée, affichée au médecin en ACTION_REQUIRED. */
  actionRequiredNote?: string | null;
  /** Motif de refus ou d'annulation. */
  rejectionReason?: string | null;
}

export interface TuitionCheckoutDto {
  clientSecret: string;
  amount: number;
  currency: string;
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

  /**
   * Ouvre le paiement des frais de formation et renvoie le secret de la session Stripe.
   * Crée une ligne de paiement côté serveur : à n'appeler que sur action explicite.
   */
  createTuitionCheckout: async (enrollmentId: string): Promise<TuitionCheckoutDto> => {
    const { data } = await axiosClient.post<TuitionCheckoutDto>(
      `/enrollments/${enrollmentId}/tuition-checkout`,
    );
    return data;
  },

  /**
   * Ouvre le règlement du solde, appelé à la délivrance du visa.
   *
   * Route distincte de l'acompte : les deux moments n'ont ni les mêmes conditions ni les mêmes
   * effets — l'acompte réserve la place, le solde acquitte une dette sans faire avancer le
   * dossier.
   */
  createBalanceCheckout: async (enrollmentId: string): Promise<TuitionCheckoutDto> => {
    const { data } = await axiosClient.post<TuitionCheckoutDto>(
      `/enrollments/${enrollmentId}/tuition-balance-checkout`,
    );
    return data;
  },

  /** Resoumet le dossier après avoir déposé les pièces réclamées. */
  resubmitAfterAction: async (enrollmentId: string): Promise<EnrollmentResponseDto> => {
    const { data } = await axiosClient.post<EnrollmentResponseDto>(
      `/enrollments/${enrollmentId}/resubmit`,
    );
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
