import { axiosClient } from './axiosClient';

export type NotificationSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL';

export interface AppNotification {
  id: string;
  type: string;
  severity: NotificationSeverity;
  title: string;
  body: string | null;
  linkUrl: string | null;
  read: boolean;
  acknowledged: boolean;
  groupCount: number;
  snoozedUntil: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface NotificationPreference {
  type: string;
  inAppEnabled: boolean;
  emailEnabled: boolean;
}

const base = '/notifications';

export const notificationService = {
  list: async (page = 0, size = 20, unreadOnly = false): Promise<Page<AppNotification>> => {
    const { data } = await axiosClient.get<Page<AppNotification>>(base, {
      params: { page, size, unreadOnly },
    });
    return data;
  },

  unreadCount: async (): Promise<number> => {
    const { data } = await axiosClient.get<{ count: number }>(`${base}/unread-count`);
    return data.count;
  },

  markRead: async (id: string): Promise<void> => {
    await axiosClient.post(`${base}/${id}/read`);
  },

  markAllRead: async (): Promise<void> => {
    await axiosClient.post(`${base}/read-all`);
  },

  acknowledge: async (id: string): Promise<void> => {
    await axiosClient.post(`${base}/${id}/ack`);
  },

  snooze: async (id: string, hours = 24): Promise<void> => {
    await axiosClient.post(`${base}/${id}/snooze`, null, { params: { hours } });
  },

  getPreferences: async (): Promise<NotificationPreference[]> => {
    const { data } = await axiosClient.get<NotificationPreference[]>(`${base}/preferences`);
    return data;
  },

  setPreference: async (
    type: string,
    body: { inAppEnabled: boolean; emailEnabled: boolean },
  ): Promise<NotificationPreference> => {
    const { data } = await axiosClient.put<NotificationPreference>(`${base}/preferences/${type}`, body);
    return data;
  },
};

/** Types de notification connus + libellés lisibles (pour l'écran de préférences). */
export const NOTIFICATION_TYPE_LABELS: Record<string, string> = {
  ORDER_PAID: 'Paiement de commande confirmé',
  ORDER_STATUS: 'Changement de statut de commande',
  ENROLLMENT_STATUS: 'Évolution de mon dossier de formation',
  ACCOUNT_VALIDATED: 'Activation de compte',
  PARTNERSHIP_REQUEST: 'Nouvelle demande de partenariat (admin)',
  DOCTOR_APPLICATION: 'Nouvelle candidature médecin (admin)',
  REPORT_FAILURE: 'Rapports du worker en échec (admin)',
  ORDER_UNPAID_STALE: 'Commandes impayées anciennes (admin)',
  ENROLLMENT_STALE: 'Dossiers CHU en attente (admin)',
};
