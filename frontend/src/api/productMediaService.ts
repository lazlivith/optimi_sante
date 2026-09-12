import { axiosClient } from './axiosClient';

/**
 * Médias d'un produit : vignette, galerie de détails, vidéo de démonstration.
 *
 * Tout part du poste de l'administrateur vers notre propre stockage — plus aucune extraction
 * depuis un site tiers. C'est ce service qui rend le catalogue autonome.
 */

export type VideoProvider = 'CLOUDINARY' | 'YOUTUBE' | 'VIMEO' | 'LOOM';

/** Hébergeurs proposés à la saisie d'un lien. Cloudinary en est absent : il correspond au
 *  téléversement direct, qui a son propre bouton. */
export const HEBERGEURS_VIDEO: { value: VideoProvider; label: string; exemple: string }[] = [
  { value: 'YOUTUBE', label: 'YouTube', exemple: 'https://www.youtube.com/watch?v=…' },
  { value: 'VIMEO', label: 'Vimeo', exemple: 'https://vimeo.com/…' },
  { value: 'LOOM', label: 'Loom', exemple: 'https://www.loom.com/share/…' },
];

/** Doit rester aligné sur ProductMediaService côté serveur, qui a le dernier mot. */
export const TAILLE_MAX_VIDEO_MO = 50;
export const TAILLE_MAX_IMAGE_MO = 10;
export const VISUELS_MAX = 12;

export interface GalleryImage {
  id: string;
  imageUrl: string;
  caption: string | null;
  displayOrder: number;
}

export interface ProductMedia {
  productId: string;
  imageUrl: string | null;
  videoUrl: string | null;
  videoProvider: VideoProvider | null;
  videoPromoted: boolean;
  gallery: GalleryImage[];
}

const BASE = '/admin/catalog/products';

export const productMediaService = {
  get: async (productId: string): Promise<ProductMedia> => {
    const { data } = await axiosClient.get<ProductMedia>(`${BASE}/${productId}/media`);
    return data;
  },

  /** Remplace la vignette : c'est l'opération du traitement en lot des fiches incomplètes. */
  replaceMainImage: async (productId: string, file: File): Promise<ProductMedia> => {
    const corps = new FormData();
    corps.append('file', file);
    const { data } = await axiosClient.put<ProductMedia>(
      `${BASE}/${productId}/media`, corps,
      { headers: { 'Content-Type': 'multipart/form-data' } });
    return data;
  },

  uploadVideo: async (productId: string, file: File): Promise<ProductMedia> => {
    const corps = new FormData();
    corps.append('file', file);
    const { data } = await axiosClient.post<ProductMedia>(
      `${BASE}/${productId}/video`, corps,
      { headers: { 'Content-Type': 'multipart/form-data' } });
    return data;
  },

  setVideoLink: async (
    productId: string, videoUrl: string, videoProvider: VideoProvider,
  ): Promise<ProductMedia> => {
    const { data } = await axiosClient.put<ProductMedia>(
      `${BASE}/${productId}/video-link`, { videoUrl, videoProvider });
    return data;
  },

  removeVideo: async (productId: string): Promise<ProductMedia> => {
    const { data } = await axiosClient.delete<ProductMedia>(`${BASE}/${productId}/video`);
    return data;
  },

  setVideoPromoted: async (productId: string, promoted: boolean): Promise<ProductMedia> => {
    const { data } = await axiosClient.patch<ProductMedia>(
      `${BASE}/${productId}/video-promotion`, { promoted });
    return data;
  },

  addToGallery: async (productId: string, files: File[]): Promise<GalleryImage[]> => {
    const corps = new FormData();
    files.forEach((f) => corps.append('files', f));
    const { data } = await axiosClient.post<GalleryImage[]>(
      `${BASE}/${productId}/gallery`, corps,
      { headers: { 'Content-Type': 'multipart/form-data' } });
    return data;
  },

  removeFromGallery: async (imageId: string): Promise<void> => {
    await axiosClient.delete(`${BASE}/gallery/${imageId}`);
  },

  reorderGallery: async (productId: string, imageIds: string[]): Promise<GalleryImage[]> => {
    const { data } = await axiosClient.put<GalleryImage[]>(
      `${BASE}/${productId}/gallery/order`, { imageIds });
    return data;
  },
};

/**
 * Le visuel de ce produit reste-t-il à faire ?
 *
 * Même règle que le filtre serveur — image absente, illustration générique, ou photo
 * d'illustration temporaire. Écrite ici pour que la liste puisse marquer les fiches
 * concernées sans rappeler l'API.
 */
export const visuelAFaire = (imageUrl: string | null | undefined): boolean =>
  !imageUrl
  || imageUrl.trim() === ''
  || imageUrl.includes('medical-placeholder')
  || imageUrl.includes('unsplash');
