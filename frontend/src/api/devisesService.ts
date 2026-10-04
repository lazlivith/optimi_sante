import { axiosClient } from './axiosClient';
import type { DeviseAffichage } from '../lib/devises';

export const devisesService = {
  /**
   * Les devises ouvertes a la vente, avec leur regle de conversion.
   *
   * @param pays code ISO 3166-1 alpha-2, facultatif : marque la devise correspondante comme
   *             suggeree, sans jamais l'imposer.
   */
  actives: async (pays?: string): Promise<DeviseAffichage[]> => {
    const { data } = await axiosClient.get<DeviseAffichage[]>('/devises', {
      params: pays ? { pays } : undefined,
    });
    return data;
  },
};
