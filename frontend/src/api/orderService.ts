import { axiosClient } from './axiosClient';
import type { Page } from './adminOrderService';

export interface CheckoutItemDto {
  productId: string;
  quantity: number;
}

export interface CheckoutRequestDto {
  items: CheckoutItemDto[];
  paymentMethod: 'STRIPE_CARD' | 'BANK_TRANSFER' | 'QUOTE_REQUEST';
  promoCode?: string;
}

export interface QuoteRequestDto {
  items: CheckoutItemDto[];
  notes?: string;
}

export interface OrderResponseDto {
  id: string;
  orderNumber: string;
  paymentMethod: string;
  paymentStatus: string;
  status: string;
  isQuote: boolean;
  totalAmount: number;
  paymentUrl?: string;
  /** Renseigné uniquement pour un paiement STRIPE_CARD : initialise le Payment Element intégré (ui_mode "elements"). */
  clientSecret?: string;
  documentS3Key?: string;
  promoCode?: string;
  discountAmount?: number;
  createdAt: string;
}

export interface PromoValidationResult {
  valid: boolean;
  discountAmount: number;
  discountType: 'PERCENTAGE' | 'FIXED_AMOUNT';
}

export interface CheckoutSessionStatusDto {
  status: string;
  paymentStatus: string;
  orderNumber?: string;
}

export const orderService = {
  checkout: async (request: CheckoutRequestDto) => {
    const { data } = await axiosClient.post<OrderResponseDto>('/orders/checkout', request);
    return data;
  },

  quoteRequest: async (request: QuoteRequestDto) => {
    const { data } = await axiosClient.post<OrderResponseDto>('/orders/quote-request', request);
    return data;
  },

  getMyOrders: async (page = 0, size = 20): Promise<Page<OrderResponseDto>> => {
    const { data } = await axiosClient.get<Page<OrderResponseDto>>(`/orders/my-orders?page=${page}&size=${size}`);
    return data;
  },

  getCheckoutSessionStatus: async (sessionId: string): Promise<CheckoutSessionStatusDto> => {
    const { data } = await axiosClient.get<CheckoutSessionStatusDto>('/orders/checkout-session-status', {
      params: { sessionId }
    });
    return data;
  },

  validatePromoCode: async (code: string, orderAmount: number): Promise<PromoValidationResult> => {
    const { data } = await axiosClient.post<PromoValidationResult>('/promo-codes/validate', { code, orderAmount });
    return data;
  }
};
