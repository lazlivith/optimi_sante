import { axiosClient } from './axiosClient';

export interface Category {
  id: string;
  name: string;
  slug: string;
}

export interface Product {
  id: string;
  sku: string;
  name: string;
  slug: string;
  description: string;
  basePrice: number;
  finalPrice: number;
  b2bDiscountRate: number;
  stockQuantity: number;
  isQuoteOnly: boolean;
  imageUrl?: string;
  category?: Category;
  isOnPromo?: boolean;
  promoEndsAt?: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export const catalogService = {
  getProducts: async (params: { page?: number; size?: number; search?: string; categoryId?: string }) => {
    const { data } = await axiosClient.get<PaginatedResponse<Product>>('/catalog/products', { params });
    return data;
  },

  getProductBySlug: async (slug: string) => {
    const { data } = await axiosClient.get<Product>(`/catalog/products/${slug}`);
    return data;
  },
  
  getCategories: async () => {
    const { data } = await axiosClient.get<Category[]>('/catalog/categories');
    return data;
  }
};
