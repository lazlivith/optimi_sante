import { axiosClient } from './axiosClient';
import type { OrderResponseDto } from './orderService';

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export const adminOrderService = {
  getAllOrders: async (page = 0, size = 20): Promise<Page<OrderResponseDto>> => {
    const { data } = await axiosClient.get<Page<OrderResponseDto>>(`/admin/orders?page=${page}&size=${size}`);
    return data;
  },

  getQuotes: async (page = 0, size = 20): Promise<Page<OrderResponseDto>> => {
    const { data } = await axiosClient.get<Page<OrderResponseDto>>(`/admin/orders/quotes?page=${page}&size=${size}`);
    return data;
  },

  updateQuoteStatus: async (orderId: string, status: 'VALIDATED' | 'REJECTED'): Promise<OrderResponseDto> => {
    const { data } = await axiosClient.patch<OrderResponseDto>(`/admin/orders/${orderId}/status?status=${status}`);
    return data;
  },

  confirmPayment: async (orderId: string): Promise<void> => {
    await axiosClient.patch(`/admin/orders/${orderId}/confirm-payment`);
  }
};
