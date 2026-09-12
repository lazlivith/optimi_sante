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
  /** Formation rattachee (offre liee), null si aucune. */
  trainingId?: string | null;
  trainingTitle?: string | null;
}

export interface TrainingLookupDto {
  id: string;
  title: string;
  institutionName: string | null;
  price: number;
  durationDays: number;
  /** Deja rattachee a un autre produit : la relation est 1:1. */
  alreadyLinked: boolean;
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
  trainingId?: string | null;
}

export interface AdminCategoryDto {
  id: string;
  name: string;
  slug: string;
  /** Nombre de produits vivants (supprimes exclus, desactives inclus). */
  productCount: number;
}

/** Filtres du catalogue admin. Tout champ absent ou vide signifie « pas de filtre ». */
export interface CatalogFilters {
  search?: string;
  categoryId?: string;
  activeState?: 'ACTIVE' | 'INACTIVE';
  lowStock?: boolean;
  /** Ne remonte que les fiches dont le visuel reste à faire : image absente, illustration
   *  générique, ou photo d'illustration temporaire. */
  needsVisual?: boolean;
  sort?: 'name_asc' | 'price_asc' | 'price_desc';
}

export const adminCatalogService = {
  listProducts: async (page = 0, size = 20, filters: CatalogFilters = {}): Promise<Page<AdminProductDto>> => {
    // URLSearchParams plutot qu'une concatenation : les noms de produits contiennent des
    // esperluettes et des accents, qu'une interpolation manuelle casserait dans l'URL.
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (filters.search?.trim()) params.set('search', filters.search.trim());
    if (filters.categoryId) params.set('categoryId', filters.categoryId);
    if (filters.activeState) params.set('activeState', filters.activeState);
    if (filters.lowStock) params.set('lowStock', 'true');
    if (filters.needsVisual) params.set('needsVisual', 'true');
    // `sortBy` et non `sort` : Spring reserve `sort` au Pageable et ajouterait un second
    // ORDER BY a la requete, qui deviendrait invalide.
    if (filters.sort) params.set('sortBy', filters.sort);
    const { data } = await axiosClient.get<Page<AdminProductDto>>(`/admin/catalog/products?${params}`);
    return data;
  },

  /**
   * Combien de fiches attendent encore leur visuel.
   *
   * Demande une seule ligne et ne lit que le total : le compteur n'a pas besoin des produits
   * eux-mêmes, et en rapatrier vingt pour afficher un nombre serait du gaspillage à chaque
   * rafraîchissement.
   */
  countNeedingVisual: async (): Promise<number> => {
    const { data } = await axiosClient.get<Page<AdminProductDto>>(
      '/admin/catalog/products?page=0&size=1&needsVisual=true',
    );
    return data.totalElements ?? 0;
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

  /** Formations proposees au rattachement. Perimetre negoce, contenu minimal. */
  listTrainingsForLinking: async (): Promise<TrainingLookupDto[]> => {
    const { data } = await axiosClient.get<TrainingLookupDto[]>('/admin/catalog/trainings-lookup');
    return data;
  },

  listCategories: async (): Promise<AdminCategoryDto[]> => {
    const { data } = await axiosClient.get<AdminCategoryDto[]>('/admin/catalog/categories');
    return data;
  }
};
