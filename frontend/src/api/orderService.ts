import { axiosClient } from './axiosClient';

export interface CheckoutItemDto {
  productId: string;
  quantity: number;
}

export interface CheckoutRequestDto {
  items: CheckoutItemDto[];
  paymentMethod: 'STRIPE_CARD' | 'BANK_TRANSFER' | 'QUOTE_REQUEST';
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
  
  getMyOrders: async () => {
    const { data } = await axiosClient.get('/orders/my-orders');
    return data;
  }
};
