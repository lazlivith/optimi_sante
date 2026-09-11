import { axiosClient } from './axiosClient';
import type { NotificationSeverity, Page } from './notificationService';

export interface AlertRow {
  id: string;
  type: string;
  severity: NotificationSeverity;
  title: string;
  body: string | null;
  linkUrl: string | null;
  groupCount: number;
  read: boolean;
  acknowledged: boolean;
  snoozedUntil: string | null;
  createdAt: string;
}

export interface AlertThreshold {
  key: string;
  label: string;
  enabled: boolean;
  thresholdValue: number;
  windowHours: number;
  severity: NotificationSeverity;
  updatedAt: string;
}

export type AlertStatusFilter = 'open' | 'acknowledged' | 'all';
export type AlertSeverityFilter = 'ALERT' | 'ALL' | 'WARNING' | 'CRITICAL';

const base = '/admin/alerts';

export const adminAlertService = {
  list: async (
    severity: AlertSeverityFilter = 'ALERT',
    status: AlertStatusFilter = 'open',
    page = 0,
    size = 20,
  ): Promise<Page<AlertRow>> => {
    const { data } = await axiosClient.get<Page<AlertRow>>(base, {
      params: { severity, status, page, size },
    });
    return data;
  },

  thresholds: async (): Promise<AlertThreshold[]> => {
    const { data } = await axiosClient.get<AlertThreshold[]>(`${base}/thresholds`);
    return data;
  },

  updateThreshold: async (
    key: string,
    body: Partial<Pick<AlertThreshold, 'enabled' | 'thresholdValue' | 'windowHours' | 'severity'>>,
  ): Promise<AlertThreshold> => {
    const { data } = await axiosClient.put<AlertThreshold>(`${base}/thresholds/${key}`, body);
    return data;
  },
};
