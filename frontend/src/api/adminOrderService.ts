import { axiosClient } from './axiosClient';
import type { OrderResponseDto } from './orderService';

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

/** Une ligne de devis vue par l'administration, avec l'état du stock. */
export interface LigneDevis {
  designation: string;
  quantite: number;
  prixUnitaire: number;
  sousTotal: number;
  /** Nul quand le produit a disparu du catalogue. */
  stockDisponible: number | null;
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

  /** Lignes d'un devis avec le stock disponible, pour l'ajustement avant validation. */
  getQuoteLines: async (orderId: string): Promise<LigneDevis[]> => {
    const { data } = await axiosClient.get<LigneDevis[]>(`/admin/orders/${orderId}/quote-lines`);
    return data;
  },

  /** Remise globale sur un devis ; le serveur réémet le PDF avec les montants ajustés. */
  applyQuoteDiscount: async (orderId: string, rate: number): Promise<OrderResponseDto> => {
    const { data } = await axiosClient.post<OrderResponseDto>(`/admin/orders/${orderId}/quote-discount?rate=${rate}`);
    return data;
  },

  confirmPayment: async (orderId: string): Promise<void> => {
    await axiosClient.patch(`/admin/orders/${orderId}/confirm-payment`);
  }
};
