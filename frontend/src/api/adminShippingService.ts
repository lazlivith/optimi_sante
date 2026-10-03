import { axiosClient } from './axiosClient';

/** Un tarif de livraison, zone par zone. */
export interface TarifLivraison {
  zone: string;
  libelle: string;
  amount: number;
  /**
   * Montant de commande à partir duquel la livraison est offerte.
   * `null` : jamais offerte, quel que soit le montant.
   */
  freeFrom: number | null;
  /** Une zone désactivée n'est plus proposée : la commande est refusée, pas facturée à zéro. */
  active: boolean;
}

export interface EstimationLivraison {
  zone: string;
  zoneLibelle: string;
  montant: number;
  offerte: boolean;
  /** Hors de France : le destinataire acquitte droits et taxes à l'arrivée (DAP). */
  droitsALArrivee: boolean;
}

export interface Destination {
  codePays: string;
  zone: string;
  zoneLibelle: string;
}

export const shippingService = {
  /** Les destinations desservies, pour le choix du pays au moment de commander. */
  destinations: async (): Promise<Destination[]> => {
    const { data } = await axiosClient.get<Destination[]>('/shipping/destinations');
    return data;
  },

  /**
   * Ce que coûterait la livraison vers ce pays, pour ce montant d'articles.
   *
   * Le serveur refuse une destination non desservie : l'appelant doit donc traiter l'erreur
   * et proposer un devis de transport, plutôt que d'afficher un montant inventé.
   */
  estimer: async (pays: string, montant: number): Promise<EstimationLivraison> => {
    const { data } = await axiosClient.get<EstimationLivraison>('/shipping/estimation', {
      params: { pays, montant },
    });
    return data;
  },
};

export const adminShippingService = {
  grille: async (): Promise<TarifLivraison[]> => {
    const { data } = await axiosClient.get<TarifLivraison[]>('/admin/shipping-rates');
    return data;
  },

  enregistrer: async (
    zone: string,
    tarif: { amount: number; freeFrom: number | null; active: boolean },
  ): Promise<TarifLivraison> => {
    const { data } = await axiosClient.put<TarifLivraison>(`/admin/shipping-rates/${zone}`, tarif);
    return data;
  },
};
