import { axiosClient } from './axiosClient';

export interface User {
  id: string;
  email: string;
  role: string;
  tenantCode?: string;
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
  }
};
