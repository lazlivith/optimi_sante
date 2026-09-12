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
  /** Vidéo de démonstration, absente sur la plupart des produits. */
  videoUrl?: string | null;
  /** Dit au lecteur s'il pose une balise <video> ou une <iframe>. */
  videoProvider?: 'CLOUDINARY' | 'YOUTUBE' | 'VIMEO' | 'LOOM' | null;
  /** Joue la vidéo sur la carte produit des sections promotionnelles. */
  isVideoPromoted?: boolean;
  /**
   * Visuels secondaires. **Renseignés uniquement sur la fiche détaillée** — en liste, le
   * serveur renvoie un tableau vide, pour ne pas déclencher une requête par produit.
   */
  gallery?: { id: string; imageUrl: string; caption: string | null; displayOrder: number }[];
  /** Formation qui apprend a utiliser l'equipement, absente si aucune. */
  relatedTraining?: {
    id: string;
    title: string;
    slug: string;
    price: number;
    durationDays: number;
  } | null;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export const catalogService = {
  /** `promo: true` ne remonte que les produits dont la promotion est active maintenant. */
  getProducts: async (params: { page?: number; size?: number; search?: string; categoryId?: string; promo?: boolean }) => {
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
