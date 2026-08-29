import { axiosClient } from './axiosClient';
import type { Page } from './adminOrderService';

export interface AdminProductDto {
  id: string;
  sku: string;
  name: string;
  slug: string;
  description: string | null;
  basePrice: number;
  stockQuantity: number;
  stockThreshold: number;
  isQuoteOnly: boolean;
  isActive: boolean;
  imageUrl: string | null;
  categoryId: string | null;
  categoryName: string | null;
  promoPrice?: number | null;
  promoStartsAt?: string | null;
  promoEndsAt?: string | null;
}

export interface AdminProductRequestDto {
  sku: string;
  name: string;
  description?: string;
  basePrice: number;
  stockQuantity?: number;
  stockThreshold?: number;
  isQuoteOnly?: boolean;
  categoryId?: string;
  imageUrl?: string;
  promoPrice?: number | null;
  promoStartsAt?: string | null;
  promoEndsAt?: string | null;
}

export interface AdminCategoryDto {
  id: string;
  name: string;
  slug: string;
}

export const adminCatalogService = {
  listProducts: async (page = 0, size = 20): Promise<Page<AdminProductDto>> => {
    const { data } = await axiosClient.get<Page<AdminProductDto>>(`/admin/catalog/products?page=${page}&size=${size}`);
    return data;
  },

  createProduct: async (request: AdminProductRequestDto): Promise<AdminProductDto> => {
    const { data } = await axiosClient.post<AdminProductDto>('/admin/catalog/products', request);
    return data;
  },

  updateProduct: async (id: string, request: AdminProductRequestDto): Promise<AdminProductDto> => {
    const { data } = await axiosClient.put<AdminProductDto>(`/admin/catalog/products/${id}`, request);
    return data;
  },

  setProductActive: async (id: string, active: boolean): Promise<AdminProductDto> => {
    const { data } = await axiosClient.patch<AdminProductDto>(`/admin/catalog/products/${id}/status?active=${active}`);
    return data;
  },

  listCategories: async (): Promise<AdminCategoryDto[]> => {
    const { data } = await axiosClient.get<AdminCategoryDto[]>('/admin/catalog/categories');
    return data;
  }
};
