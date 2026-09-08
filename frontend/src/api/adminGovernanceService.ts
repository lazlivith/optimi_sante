import { axiosClient } from './axiosClient';
import type { Page } from './adminAuditService';

export interface SoftDeleteTableReport {
  table: string;
  entity: string;
  globalFilterEnforced: boolean;
  totalRows: number;
  activeRows: number;
  softDeletedRows: number;
  anonymizedRows: number;
  note: string;
}

export interface SoftDeleteReport {
  tables: SoftDeleteTableReport[];
  verdict: string;
  healthy: boolean;
}

export interface RetentionCandidate {
  userId: string;
  email: string;
  role: string;
  createdAt: string;
  ordersCount: number;
  enrollmentsCount: number;
}

export interface RetentionReport {
  staleThresholdMonths: number;
  staleActiveUsers: number;
  oldLeads: number;
  anonymizedUsers: number;
  usersWithoutConsent: number;
  staleUserSample: RetentionCandidate[];
}

export interface RgpdRequestRow {
  id: string;
  requestType: string;
  subjectEmail: string;
  status: string;
  requestedByEmail: string | null;
  resultSummary: string | null;
  createdAt: string;
}

export interface RgpdSubjectSummary {
  account: Record<string, unknown>;
  ordersCount: number;
  enrollmentsCount: number;
  leadsCount: number;
  applicationsCount: number;
  alreadyAnonymized: boolean;
}

const base = '/admin/governance';

export const adminGovernanceService = {
  softDeleteReport: async (): Promise<SoftDeleteReport> => {
    const { data } = await axiosClient.get<SoftDeleteReport>(`${base}/soft-delete-report`);
    return data;
  },
  retentionReport: async (months = 24): Promise<RetentionReport> => {
    const { data } = await axiosClient.get<RetentionReport>(`${base}/retention-report`, { params: { months } });
    return data;
  },
  rgpdRequests: async (page = 0, size = 20): Promise<Page<RgpdRequestRow>> => {
    const { data } = await axiosClient.get<Page<RgpdRequestRow>>(`${base}/rgpd/requests`, { params: { page, size } });
    return data;
  },
  lookupSubject: async (email: string): Promise<RgpdSubjectSummary> => {
    const { data } = await axiosClient.get<RgpdSubjectSummary>(`${base}/rgpd/subject`, { params: { email } });
    return data;
  },
  exportSubject: async (email: string): Promise<Record<string, unknown>> => {
    const { data } = await axiosClient.get<Record<string, unknown>>(`${base}/rgpd/subject/export`, { params: { email } });
    return data;
  },
  anonymizeSubject: async (email: string): Promise<{ status: string; anonymizedEmail: string; summary: string }> => {
    const { data } = await axiosClient.post(`${base}/rgpd/subject/anonymize`, { email });
    return data;
  },
};
