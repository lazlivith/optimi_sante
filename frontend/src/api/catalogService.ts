import { axiosClient } from './axiosClient';

export interface Category {
  id: string;
  name: string;
  slug: string;
  /**
   * Produits achetables dans le rayon. Absent des charges utiles imbriquées
   * (`Product.category`), où le serveur n'envoie qu'un résumé.
   */
  productCount?: number;
  /**
   * Photo d'un vrai produit du rayon, servant de vignette à la catégorie. `null` quand aucun
   * produit du rayon n'a encore de photo — il n'y a alors rien d'honnête à afficher.
   */
  imageUrl?: string | null;
  subcategories?: Category[];
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

/**
 * Adresse de la fiche d'un produit.
 *
 * <p><b>Le slug doit être encodé.</b> 146 produits sur 1 487 portent un slug contenant déjà
 * des caractères pour-cent — « tensiometre-precisa%c2%acae-n-shock-proof », où « ® » a été
 * encodé au lieu d'être retiré à l'import. Écrit tel quel dans une adresse, ce « %c2%ac » est
 * lu comme une séquence d'échappement : le routeur le décode en « ¬ », le slug demandé au
 * serveur ne correspond plus à celui stocké, et la fiche répond « produit introuvable ».
 * Ces 146 fiches étaient inatteignables.</p>
 *
 * <p>Sur un slug ordinaire — lettres, chiffres, tirets — cette fonction ne change rien.</p>
 */
export const cheminProduit = (slug: string) => `/product/${encodeURIComponent(slug)}`;

export const catalogService = {
  /** `promo: true` ne remonte que les produits dont la promotion est active maintenant. */
  getProducts: async (params: { page?: number; size?: number; search?: string; categoryId?: string; promo?: boolean }) => {
    const { data } = await axiosClient.get<PaginatedResponse<Product>>('/catalog/products', { params });
    return data;
  },

  getProductBySlug: async (slug: string) => {
    // Même raison que `cheminProduit` : un slug portant un « % » doit arriver au serveur
    // tel qu'il est stocké, et non décodé en cours de route.
    const { data } = await axiosClient.get<Product>(`/catalog/products/${encodeURIComponent(slug)}`);
    return data;
  },
  
  getCategories: async () => {
    const { data } = await axiosClient.get<Category[]>('/catalog/categories');
    return data;
  }
};
