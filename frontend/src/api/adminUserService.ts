import { axiosClient } from './axiosClient';
import type { Page } from './adminOrderService';

export interface AdminUserSummaryDto {
  id: string;
  email: string;
  role: string;
  isActive: boolean;
  displayName: string | null;
  createdAt: string;
}

export const adminUserService = {
  listUsers: async (page = 0, size = 20, role?: string): Promise<Page<AdminUserSummaryDto>> => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (role) params.set('role', role);
    const { data } = await axiosClient.get<Page<AdminUserSummaryDto>>(`/admin/users?${params.toString()}`);
    return data;
  },

  setUserActive: async (userId: string, active: boolean): Promise<AdminUserSummaryDto> => {
    const { data } = await axiosClient.patch<AdminUserSummaryDto>(`/admin/users/${userId}/status?active=${active}`);
    return data;
  }
};
