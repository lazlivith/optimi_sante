import { axiosClient } from './axiosClient';

export interface PersonalData {
  identity: {
    email: string;
    firstName: string | null;
    lastName: string | null;
    phone: string | null;
    role: string;
    createdAt: string | null;
    paymentProviderId: string | null;
  };
  company: {
    companyName: string | null;
    taxId: string | null;
    vatNumber: string | null;
    billingAddress: string | null;
    country: string | null;
    facilityType: string | null;
    contactName: string | null;
    b2bDiscountRate: number | null;
  } | null;
  orders: Array<{
    orderNumber: string;
    createdAt: string | null;
    status: string | null;
    paymentStatus: string | null;
    paymentMethod: string | null;
    isQuote: boolean | null;
    totalAmount: number | null;
    itemCount: number;
  }>;
  emails: Array<{
    type: string | null;
    subject: string | null;
    status: string | null;
    sentAt: string | null;
  }>;
  generatedAt: string;
}

export const personalDataService = {
  get: async (): Promise<PersonalData> =>
    (await axiosClient.get<PersonalData>('/me/personal-data')).data,

  /**
   * Telecharge l'export CSV.
   *
   * Passe par axios et non par un simple lien : l'endpoint exige un jeton d'authentification,
   * qu'un `<a href>` n'enverrait pas — le navigateur recevrait un 401 et afficherait une page
   * blanche. Le fichier est donc recupere en memoire, puis remis au navigateur.
   */
  downloadCsv: async (): Promise<void> => {
    const response = await axiosClient.get('/me/personal-data/export.csv', {
      responseType: 'blob',
    });

    const url = window.URL.createObjectURL(new Blob([response.data], { type: 'text/csv;charset=utf-8' }));
    const lien = document.createElement('a');
    lien.href = url;
    lien.download = `optimisante-donnees-personnelles-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(lien);
    lien.click();
    lien.remove();
    // Sans revocation, le blob reste en memoire tant que l'onglet est ouvert.
    window.URL.revokeObjectURL(url);
  },
};
