import { axiosClient } from './axiosClient';

/** Ce qu'une revision tarifaire changerait, sans rien changer. */
export interface ApercuRevision {
  produitsTouches: number;
  prixMoyenActuel: number;
  prixMoyenApres: number;
  prixMinApres: number;
  prixMaxApres: number;
}

/** Une revision deja appliquee. */
export interface RevisionPrix {
  id: string;
  pourcentage: number;
  categorieId: string | null;
  categorieNom: string | null;
  produitsTouches: number;
  libelle: string | null;
  appliqueeLe: string;
  appliqueePar: string | null;
  annuleeLe: string | null;
  annuleePar: string | null;
}

export const adminFiscaliteService = {
  /** La ventilation de la TVA s'imprime-t-elle sur les documents ? */
  lireAffichageTva: async (): Promise<boolean> => {
    const { data } = await axiosClient.get<{ actif: boolean }>('/admin/fiscalite/tva/affichage');
    return data.actif;
  },

  definirAffichageTva: async (actif: boolean): Promise<boolean> => {
    const { data } = await axiosClient.put<{ actif: boolean }>(
      '/admin/fiscalite/tva/affichage', { actif });
    return data.actif;
  },

  apercuRevision: async (pourcentage: number, categorieId?: string): Promise<ApercuRevision> => {
    const { data } = await axiosClient.get<ApercuRevision>('/admin/fiscalite/prix/apercu', {
      params: categorieId ? { pourcentage, categorieId } : { pourcentage },
    });
    return data;
  },

  appliquerRevision: async (
    pourcentage: number, categorieId: string | undefined, libelle: string,
  ): Promise<string> => {
    const { data } = await axiosClient.post<{ id: string }>('/admin/fiscalite/prix/revisions', {
      pourcentage, categorieId: categorieId || null, libelle,
    });
    return data.id;
  },

  historiqueRevisions: async (): Promise<RevisionPrix[]> => {
    const { data } = await axiosClient.get<RevisionPrix[]>('/admin/fiscalite/prix/revisions');
    return data;
  },

  /** Restitue les prix d'avant. Rend le nombre de prix remis en place. */
  annulerRevision: async (id: string): Promise<number> => {
    const { data } = await axiosClient.post<{ prixRestaures: number }>(
      `/admin/fiscalite/prix/revisions/${id}/annulation`);
    return data.prixRestaures;
  },
};
