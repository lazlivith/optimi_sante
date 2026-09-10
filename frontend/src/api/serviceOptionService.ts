import { axiosClient } from './axiosClient';

/**
 * Services organisés par Optimi Santé autour du séjour : assurance, hébergement, transport.
 *
 * Le catalogue est tenu par l'administration de la mobilité, jamais par le partenaire — le CHU
 * ne fixe pas le prix d'une prestation qu'il ne fournit pas. Aucun appel partenaire n'existe
 * donc ici, et ce n'est pas un oubli.
 */

export type ServiceOptionType = 'INSURANCE' | 'HOUSING' | 'TRANSPORT';
export type ServiceOptionStatus = 'SELECTED' | 'PAID' | 'CANCELLED';

export const SERVICE_OPTION_TYPES: {
  value: ServiceOptionType;
  label: string;
  exemple: string;
}[] = [
  { value: 'INSURANCE', label: 'Assurance', exemple: 'Couverture maladie, accident, rapatriement' },
  { value: 'HOUSING', label: 'Hébergement', exemple: 'Studio, chambre en résidence, internat' },
  { value: 'TRANSPORT', label: 'Transport', exemple: 'Transferts aéroport, navette quotidienne' },
];

export const serviceOptionTypeLabel = (t: ServiceOptionType) =>
  SERVICE_OPTION_TYPES.find((o) => o.value === t)?.label ?? t;

export interface CatalogOption {
  id: string;
  trainingId: string;
  optionType: ServiceOptionType;
  label: string;
  description: string | null;
  price: number;
  active: boolean;
  createdAt: string;
}

export interface ServiceSubscription {
  id: string;
  optionType: ServiceOptionType;
  label: string;
  unitPrice: number;
  status: ServiceOptionStatus;
  selectedAt: string;
  paidAt: string | null;
}

export interface ServiceSummary {
  /** Offres encore souscriptibles — celles déjà retenues en sont retirées. */
  available: CatalogOption[];
  subscriptions: ServiceSubscription[];
  amountDue: number;
  amountPaid: number;
  subscriptionDocumentId: string | null;
}

export interface CatalogOptionInput {
  optionType: ServiceOptionType;
  label: string;
  description?: string | null;
  price: number;
}

export const serviceOptionService = {
  // ---------------------------------------------------------------- Catalogue admin ------

  catalog: async (trainingId: string): Promise<CatalogOption[]> => {
    const { data } = await axiosClient.get<CatalogOption[]>(
      `/admin/trainings/${trainingId}/service-options`,
    );
    return data;
  },

  /** Dépose l'offre d'un type. Celle qui était en vigueur est retirée du catalogue. */
  upsert: async (trainingId: string, input: CatalogOptionInput): Promise<CatalogOption> => {
    const { data } = await axiosClient.post<CatalogOption>(
      `/admin/trainings/${trainingId}/service-options`,
      input,
    );
    return data;
  },

  deactivate: async (optionId: string): Promise<CatalogOption> => {
    const { data } = await axiosClient.post<CatalogOption>(
      `/admin/service-options/${optionId}/deactivate`,
    );
    return data;
  },

  summaryForAdmin: async (enrollmentId: string): Promise<ServiceSummary> => {
    const { data } = await axiosClient.get<ServiceSummary>(
      `/admin/enrollments/${enrollmentId}/service-options`,
    );
    return data;
  },

  // ------------------------------------------------------------------ Côté médecin ------

  summary: async (enrollmentId: string): Promise<ServiceSummary> => {
    const { data } = await axiosClient.get<ServiceSummary>(
      `/enrollments/${enrollmentId}/service-options`,
    );
    return data;
  },

  select: async (enrollmentId: string, catalogOptionIds: string[]): Promise<ServiceSummary> => {
    const { data } = await axiosClient.post<ServiceSummary>(
      `/enrollments/${enrollmentId}/service-options`,
      { catalogOptionIds },
    );
    return data;
  },

  cancel: async (subscriptionId: string): Promise<ServiceSummary> => {
    const { data } = await axiosClient.post<ServiceSummary>(
      `/enrollments/service-options/${subscriptionId}/cancel`,
    );
    return data;
  },

  checkout: async (
    enrollmentId: string,
  ): Promise<{ clientSecret: string; amount: number; currency: string }> => {
    const { data } = await axiosClient.post<{
      clientSecret: string; amount: number; currency: string;
    }>(`/enrollments/${enrollmentId}/service-options/checkout`);
    return data;
  },
};
