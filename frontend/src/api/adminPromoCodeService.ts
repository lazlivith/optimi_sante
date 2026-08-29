import { axiosClient } from './axiosClient';

export interface PromoCodeDto {
  id: string;
  code: string;
  discountType: 'PERCENTAGE' | 'FIXED_AMOUNT';
  discountValue: number;
  minOrderAmount?: number;
  maxUses?: number;
  usedCount: number;
  startsAt?: string;
  endsAt?: string;
  isActive: boolean;
  createdAt: string;
}

export interface CreatePromoCodeRequest {
  code: string;
  discountType: 'PERCENTAGE' | 'FIXED_AMOUNT';
  discountValue: number;
  minOrderAmount?: number;
  maxUses?: number;
  startsAt?: string;
  endsAt?: string;
}

export const adminPromoCodeService = {
  listCodes: async (): Promise<PromoCodeDto[]> => {
    const { data } = await axiosClient.get<PromoCodeDto[]>('/admin/promo-codes');
    return data;
  },
  createCode: async (request: CreatePromoCodeRequest): Promise<PromoCodeDto> => {
    const { data } = await axiosClient.post<PromoCodeDto>('/admin/promo-codes', request);
    return data;
  },
  setActive: async (id: string, active: boolean): Promise<PromoCodeDto> => {
    const { data } = await axiosClient.patch<PromoCodeDto>(`/admin/promo-codes/${id}/status`, null, { params: { active } });
    return data;
  }
};
