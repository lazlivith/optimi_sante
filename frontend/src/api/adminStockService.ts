import { axiosClient } from './axiosClient';

/** L'état affiché d'une ligne, du plus grave au plus rassurant. */
export type EtatStock = 'RUPTURE' | 'CRITIQUE' | 'NON_INVENTORIE' | 'OK';

export interface LigneStock {
  productId: string;
  nom: string;
  slug: string;
  categorie: string | null;
  /** Ce que la base croit détenir. */
  stock: number;
  /** Unités retenues par des paniers en cours, pas encore vendues. */
  reserve: number;
  /** Ce qu'un client peut réellement commander : stock moins réservations. */
  disponible: number;
  seuil: number;
  etat: EtatStock;
  /** `null` = jamais compté : le chiffre vient de l'import, pas d'un constat. */
  inventorieLe: string | null;
  actif: boolean;
}

export interface ResumeStock {
  produitsSuivis: number;
  enRupture: number;
  sousSeuil: number;
  jamaisInventories: number;
  unitesEnStock: number;
  unitesReservees: number;
}

export type MotifMouvement =
  | 'VENTE'
  | 'REAPPROVISIONNEMENT'
  | 'INVENTAIRE'
  | 'CORRECTION'
  | 'ANNULATION';

export interface LigneMouvement {
  id: string;
  productId: string | null;
  produit: string | null;
  delta: number;
  stockApres: number;
  motif: MotifMouvement;
  orderId: string | null;
  auteur: string | null;
  commentaire: string | null;
  date: string;
}

export type FiltreStock = '' | 'rupture' | 'critique' | 'jamais_inventorie';

const base = '/admin/stock';

export const adminStockService = {
  lignes: async (q = '', filtre: FiltreStock = '', limite = 100): Promise<LigneStock[]> => {
    const { data } = await axiosClient.get<LigneStock[]>(base, { params: { q, filtre, limite } });
    return data;
  },

  resume: async (): Promise<ResumeStock> => {
    const { data } = await axiosClient.get<ResumeStock>(`${base}/resume`);
    return data;
  },

  journal: async (limite = 50): Promise<LigneMouvement[]> => {
    const { data } = await axiosClient.get<LigneMouvement[]>(`${base}/mouvements`, {
      params: { limite },
    });
    return data;
  },

  historique: async (productId: string, limite = 50): Promise<LigneMouvement[]> => {
    const { data } = await axiosClient.get<LigneMouvement[]>(`${base}/${productId}/mouvements`, {
      params: { limite },
    });
    return data;
  },

  /**
   * Enregistre un mouvement. Un inventaire se saisit en quantité comptée, les autres motifs
   * en écart : calculer l'écart de tête avant de le saisir est le meilleur moyen de se
   * tromper de signe.
   */
  ajuster: async (
    productId: string,
    corps: {
      motif: Exclude<MotifMouvement, 'VENTE'>;
      delta?: number;
      quantiteConstatee?: number;
      commentaire?: string;
    },
  ): Promise<{ productId: string; stock: number }> => {
    const { data } = await axiosClient.post(`${base}/${productId}/ajustement`, corps);
    return data;
  },

  seuil: async (productId: string, seuil: number): Promise<void> => {
    await axiosClient.put(`${base}/${productId}/seuil`, { seuil });
  },
};
