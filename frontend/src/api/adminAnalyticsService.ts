import { axiosClient } from './axiosClient';

export interface AnalyticsOverview {
  revenueTotal: number;
  revenueThisMonth: number;
  averageOrderValue: number;
  ordersPaid: number;
  quotesTotal: number;
  payingCustomers: number;
  activeUsers: number;
  newUsers30d: number;
  leads30d: number;
  paidApplications: number;
  enrollmentsTotal: number;
  enrollmentsActive: number;
  applicationToEnrollmentRate: number;
}

export interface TimeseriesPoint {
  day: string;
  revenue: number;
  count: number;
}

export interface LabelValue {
  label: string;
  value: number;
  secondary: number;
}

export interface FunnelStep {
  label: string;
  count: number;
  shareOfTop: number;
}

export interface FunnelData {
  steps: FunnelStep[];
}

const base = '/admin/analytics';

export const adminAnalyticsService = {
  getOverview: async (): Promise<AnalyticsOverview> => {
    const { data } = await axiosClient.get<AnalyticsOverview>(`${base}/overview`);
    return data;
  },
  getRevenueTimeseries: async (days = 90): Promise<TimeseriesPoint[]> => {
    const { data } = await axiosClient.get<TimeseriesPoint[]>(`${base}/revenue-timeseries`, { params: { days } });
    return data;
  },
  getUserGrowth: async (days = 90): Promise<TimeseriesPoint[]> => {
    const { data } = await axiosClient.get<TimeseriesPoint[]>(`${base}/user-growth`, { params: { days } });
    return data;
  },
  getSalesByCategory: async (): Promise<LabelValue[]> => {
    const { data } = await axiosClient.get<LabelValue[]>(`${base}/sales-by-category`);
    return data;
  },
  getEnrollmentsByStatus: async (): Promise<LabelValue[]> => {
    const { data } = await axiosClient.get<LabelValue[]>(`${base}/enrollments-by-status`);
    return data;
  },
  getTopTrainings: async (limit = 10): Promise<LabelValue[]> => {
    const { data } = await axiosClient.get<LabelValue[]>(`${base}/top-trainings`, { params: { limit } });
    return data;
  },
  getSegments: async (): Promise<LabelValue[]> => {
    const { data } = await axiosClient.get<LabelValue[]>(`${base}/segments`);
    return data;
  },
  getAcquisitionFunnel: async (): Promise<FunnelData> => {
    const { data } = await axiosClient.get<FunnelData>(`${base}/acquisition-funnel`);
    return data;
  },
  /** Renvoie l'URL d'export CSV (le navigateur télécharge via un lien authentifié côté axios). */
  exportUrl: (dataset: string, days = 90, limit = 10): string =>
    `${base}/export?dataset=${encodeURIComponent(dataset)}&days=${days}&limit=${limit}`,
};
