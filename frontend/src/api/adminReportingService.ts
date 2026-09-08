import { axiosClient } from './axiosClient';

export interface ReportDefinition {
  key: string;
  title: string;
  description: string;
  params: Record<string, unknown>;
}

export interface ReportRun {
  id: string;
  reportKey: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED';
  trigger: 'MANUAL' | 'SCHEDULED';
  rowCount: number | null;
  formats: string | null;
  error: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface ReportPreview {
  key: string;
  columns: string[];
  rows: Record<string, unknown>[];
  totalRows: number;
}

export interface ReportingStatus {
  configured: boolean;
  reachable: boolean;
  worker?: Record<string, unknown>;
  error?: string;
}

const base = '/admin/reporting';

export const adminReportingService = {
  status: async (): Promise<ReportingStatus> => {
    const { data } = await axiosClient.get<ReportingStatus>(`${base}/status`);
    return data;
  },
  listReports: async (): Promise<ReportDefinition[]> => {
    const { data } = await axiosClient.get<ReportDefinition[]>(`${base}/reports`);
    return data;
  },
  runs: async (key: string, limit = 20): Promise<ReportRun[]> => {
    const { data } = await axiosClient.get<ReportRun[]>(`${base}/reports/${key}/runs`, { params: { limit } });
    return data;
  },
  preview: async (key: string, limit = 50): Promise<ReportPreview> => {
    const { data } = await axiosClient.get<ReportPreview>(`${base}/reports/${key}/preview`, { params: { limit } });
    return data;
  },
  run: async (key: string, params?: Record<string, unknown>): Promise<ReportRun> => {
    const { data } = await axiosClient.post<ReportRun>(`${base}/reports/${key}/run`, params ?? {});
    return data;
  },
  downloadUrl: (key: string, format: 'csv' | 'xlsx'): string =>
    `${base}/reports/${key}/download/latest?format=${format}`,
};
