import { axiosClient } from './axiosClient';

export interface User {
  id: string;
  email: string;
  role: string;
  tenantCode?: string;
  // Profil fields (mostly for doctors)
  firstName?: string;
  lastName?: string;
  phoneWhatsapp?: string;
  countryOfResidence?: string;
  medicalSpecialty?: string;
  medicalCouncilNumber?: string;
  currentHospital?: string;
  // B2B fields
  companyName?: string;
  /** Identifiant fiscal générique (ex-siretFiness, renommé pour l'international). */
  taxId?: string;
  vatNumber?: string;
  billingAddress?: string;
  country?: string;
  facilityType?: FacilityType;
  contactName?: string;
}

/** Types d'établissement B2B — aligné sur l'enum Java `FacilityType`. */
export type FacilityType =
  | 'CLINIC'
  | 'HOSPITAL'
  | 'MEDICAL_PRACTICE'
  | 'LABORATORY'
  | 'DISTRIBUTOR'
  | 'OTHER';

export const FACILITY_TYPE_LABELS: Record<FacilityType, string> = {
  CLINIC: 'Clinique privée',
  HOSPITAL: 'Hôpital public',
  MEDICAL_PRACTICE: 'Cabinet médical',
  LABORATORY: 'Laboratoire',
  DISTRIBUTOR: 'Revendeur / Grossiste',
  OTHER: 'Autre',
};

export interface RegisterB2CPayload {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface RegisterB2BPayload {
  companyName: string;
  facilityType: FacilityType;
  country: string;
  taxId: string;
  contactName: string;
  phone: string;
  email: string;
  password: string;
}

export interface JwtResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

export const authService = {
  login: async (credentials: any) => {
    const { data } = await axiosClient.post<JwtResponse>('/auth/login', credentials);
    return data;
  },
  registerB2C: async (payload: RegisterB2CPayload) => {
    await axiosClient.post('/auth/register/b2c', payload);
  },
  registerB2B: async (payload: RegisterB2BPayload) => {
    await axiosClient.post('/auth/register/b2b', payload);
  },
  registerDoctor: async (payload: any) => {
    await axiosClient.post('/auth/register/doctor', payload);
  },
  getProfile: async () => {
    const { data } = await axiosClient.get<User>('/auth/profile');
    return data;
  },
  updateProfile: async (payload: Partial<User>) => {
    const { data } = await axiosClient.put<User>('/auth/profile', payload);
    return data;
  },
  changePassword: async (currentPassword: string, newPassword: string) => {
    await axiosClient.put('/auth/change-password', { currentPassword, newPassword });
  }
};
