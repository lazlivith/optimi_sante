import { axiosClient } from './axiosClient';

/**
 * Une publication du blog.
 *
 * `isEvent` est calcule par le serveur a partir de la date de debut : la regle appartient au
 * domaine, et trois ecrans la reinterpreteraient chacun a leur facon.
 */
export interface BlogPost {
  id: string;
  title: string;
  slug: string;
  excerpt: string | null;
  content: string;
  coverImageUrl: string | null;
  eventLocation: string | null;
  /** Format ISO `AAAA-MM-JJ`. */
  eventStartsOn: string | null;
  eventEndsOn: string | null;
  isEvent: boolean;
  isPublished: boolean;
  publishedAt: string | null;
  createdAt: string;
}

export interface BlogPostPayload {
  title: string;
  excerpt?: string | null;
  content: string;
  coverImageUrl?: string | null;
  eventLocation?: string | null;
  eventStartsOn?: string | null;
  eventEndsOn?: string | null;
  published?: boolean;
}

export const blogService = {
  publications: async (): Promise<BlogPost[]> => {
    const { data } = await axiosClient.get<BlogPost[]>('/blog');
    return data;
  },

  parSlug: async (slug: string): Promise<BlogPost> => {
    const { data } = await axiosClient.get<BlogPost>(`/blog/${encodeURIComponent(slug)}`);
    return data;
  },

  /**
   * Le prochain evenement annonce, ou `null` quand l'agenda est vide — le serveur repond
   * alors 204, sans corps, et la banniere d'accueil garde sa presentation d'origine.
   */
  prochainEvenement: async (): Promise<BlogPost | null> => {
    const { data, status } = await axiosClient.get<BlogPost | ''>('/blog/prochain-evenement');
    return status === 204 || !data ? null : (data as BlogPost);
  },
};

export const adminBlogService = {
  /** Brouillons compris : c'est l'ecran ou on les reprend. */
  lister: async (): Promise<BlogPost[]> => {
    const { data } = await axiosClient.get<BlogPost[]>('/admin/blog');
    return data;
  },

  creer: async (payload: BlogPostPayload): Promise<BlogPost> => {
    const { data } = await axiosClient.post<BlogPost>('/admin/blog', payload);
    return data;
  },

  modifier: async (id: string, payload: BlogPostPayload): Promise<BlogPost> => {
    const { data } = await axiosClient.put<BlogPost>(`/admin/blog/${id}`, payload);
    return data;
  },

  changerMiseEnLigne: async (id: string, enLigne: boolean): Promise<BlogPost> => {
    const { data } = await axiosClient.patch<BlogPost>(
      `/admin/blog/${id}/publication`, null, { params: { enLigne } });
    return data;
  },

  supprimer: async (id: string): Promise<void> => {
    await axiosClient.delete(`/admin/blog/${id}`);
  },
};
