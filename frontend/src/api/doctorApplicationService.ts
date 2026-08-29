import { axiosClient } from './axiosClient';

export interface DoctorApplicationRequestDto {
  tenantCode: string;
  sessionId: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneWhatsapp: string;
  countryOfResidence: string;
  medicalSpecialty: string;
  medicalCouncilNumber?: string;
  currentHospital?: string;
  passportNumber?: string;
}

export interface DoctorApplicationResponseDto {
  id: string;
  status: 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED';
  feeAmount: number;
  trainingTitle: string;
  createdAt: string;
  paidAt?: string;
  /** Renseigné uniquement à la création : initialise le Payment Element intégré (ui_mode "elements"). */
  clientSecret?: string;
}

export const doctorApplicationService = {
  submitApplication: async (payload: DoctorApplicationRequestDto): Promise<DoctorApplicationResponseDto> => {
    const { data } = await axiosClient.post<DoctorApplicationResponseDto>('/doctor-applications', payload);
    return data;
  },

  getStatus: async (id: string): Promise<DoctorApplicationResponseDto> => {
    const { data } = await axiosClient.get<DoctorApplicationResponseDto>(`/doctor-applications/${id}/status`);
    return data;
  },

  getStatusByStripeSession: async (stripeSessionId: string): Promise<DoctorApplicationResponseDto> => {
    const { data } = await axiosClient.get<DoctorApplicationResponseDto>('/doctor-applications/status-by-stripe-session', {
      params: { sessionId: stripeSessionId }
    });
    return data;
  }
};
