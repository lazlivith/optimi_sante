import { axiosClient } from './axiosClient';

/** Une devise de présentation et son taux. */
export interface DeviseTaux {
  devise: string;
  /** Combien d'unités de cette devise valent un euro. */
  taux: number;
  /** Multiple auquel le montant converti remonte. */
  palierArrondi: number;
  actif: boolean;
  /** L'euro : devise de référence, non modifiable. */
  reference: boolean;
  decimales: number;
}

export interface ApercuConversion {
  montantEuros: number;
  montantConverti: number;
  devise: string;
}

export const adminDevisesService = {
  grille: async (): Promise<DeviseTaux[]> => {
    const { data } = await axiosClient.get<DeviseTaux[]>('/admin/devises');
    return data;
  },

  enregistrer: async (
    code: string,
    valeurs: { taux: number; palierArrondi: number; actif: boolean },
  ): Promise<DeviseTaux> => {
    const { data } = await axiosClient.put<DeviseTaux>(`/admin/devises/${code}`, valeurs);
    return data;
  },

  /** Ce que donnerait la conversion, sans rien enregistrer. */
  apercu: async (code: string, montant: number): Promise<ApercuConversion> => {
    const { data } = await axiosClient.get<ApercuConversion>(`/admin/devises/${code}/apercu`, {
      params: { montant },
    });
    return data;
  },
};
