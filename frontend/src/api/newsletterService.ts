import { axiosClient } from './axiosClient';

export const newsletterService = {
  /**
   * Inscrit une adresse a la lettre d'information.
   *
   * `consentText` est le libelle exact affiche a la personne : il est transmis pour etre
   * conserve avec l'inscription. Sans lui, on saurait qu'elle a coche une case, pas a quoi
   * elle a consenti — ce qui ne suffit pas a prouver le consentement.
   */
  subscribe: async (email: string, consentText: string): Promise<void> => {
    await axiosClient.post('/newsletter/subscribe', { email, consentText });
  },
};
