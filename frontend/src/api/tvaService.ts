import { axiosClient } from './axiosClient';

/** Une base et sa taxe, pour un taux donne. */
export interface TauxVentile {
  taux: number;
  ht: number;
  taxe: number;
}

export interface VentilationPanier {
  /** Faux tant que la grille des taux n'est pas ouverte : l'ecran n'affiche alors rien. */
  affichee: boolean;
  totalHt: number;
  totalTva: number;
  ventilation: TauxVentile[];
}

export const tvaService = {
  /**
   * La taxe contenue dans un panier, calculee par le serveur.
   *
   * Le navigateur ne connait ni les taux ni la regle de prix : les lui livrer pour qu'il
   * calcule reviendrait a afficher le montant qu'il veut bien, et non celui qui sera facture.
   */
  ventilerPanier: async (
    items: { productId: string; quantity: number }[],
    remise?: number,
  ): Promise<VentilationPanier> => {
    const { data } = await axiosClient.post<VentilationPanier>('/catalog/tva/panier', {
      items, remise: remise ?? null,
    });
    return data;
  },
};
