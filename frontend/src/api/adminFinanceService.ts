import { axiosClient } from './axiosClient';

export interface FinanceSummaryDto {
  totalRevenue: number;
  revenueToday: number;
  revenueThisMonth: number;
  ordersCount: number;
  ordersToday: number;
  averageOrderValue: number;
}

export interface BestSellerDto {
  productId: string;
  productName: string;
  imageUrl?: string;
  totalQuantitySold: number;
  totalRevenue: number;
}

export interface RecentSaleDto {
  orderId: string;
  orderNumber: string;
  customerEmail: string;
  totalAmount: number;
  createdAt: string;
  itemsCount: number;
}

export const adminFinanceService = {
  getSummary: async (): Promise<FinanceSummaryDto> => {
    const { data } = await axiosClient.get<FinanceSummaryDto>('/admin/finance/summary');
    return data;
  },
  getBestSellers: async (limit = 10): Promise<BestSellerDto[]> => {
    const { data } = await axiosClient.get<BestSellerDto[]>('/admin/finance/best-sellers', { params: { limit } });
    return data;
  },
  getRecentSales: async (limit = 20): Promise<RecentSaleDto[]> => {
    const { data } = await axiosClient.get<RecentSaleDto[]>('/admin/finance/recent-sales', { params: { limit } });
    return data;
  }
};
