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
  siretFiness?: string;
  vatNumber?: string;
  billingAddress?: string;
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
  registerB2C: async (payload: any) => {
    await axiosClient.post('/auth/register/b2c', payload);
  },
  registerB2B: async (payload: any) => {
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
