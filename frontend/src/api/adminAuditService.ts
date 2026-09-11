import { axiosClient } from './axiosClient';

export interface AuditLogEntry {
  id: string;
  actorEmail: string | null;
  actorRole: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  httpMethod: string | null;
  path: string | null;
  statusCode: number | null;
  ipAddress: string | null;
  summary: string | null;
  metadata: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface AuditStats {
  windowDays: number;
  totalEntries: number;
  entriesLast24h: number;
  byAction: { label: string; total: number }[];
  topActors: { label: string; total: number }[];
}

export interface AuditFilters {
  action?: string;
  entityType?: string;
  actorEmail?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

const base = '/admin/audit';

export const adminAuditService = {
  list: async (filters: AuditFilters = {}): Promise<Page<AuditLogEntry>> => {
    const { data } = await axiosClient.get<Page<AuditLogEntry>>(`${base}/logs`, { params: filters });
    return data;
  },
  stats: async (windowDays = 30): Promise<AuditStats> => {
    const { data } = await axiosClient.get<AuditStats>(`${base}/stats`, { params: { windowDays } });
    return data;
  },
  exportUrl: (filters: AuditFilters = {}): string => {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
    });
    const qs = params.toString();
    return `${base}/logs/export${qs ? `?${qs}` : ''}`;
  },
};
