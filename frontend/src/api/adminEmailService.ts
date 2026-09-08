import { axiosClient } from './axiosClient';

export type EmailStatus = 'SENT' | 'FAILED';
export type EmailType = 'CREDENTIALS' | 'TEST';

export interface EmailLog {
  id: string;
  recipient: string;
  subject: string;
  emailType: EmailType;
  status: EmailStatus;
  errorMessage: string | null;
  sentAt: string;
  /** Le renvoi n'est possible que si le compte destinataire est encore rattaché. */
  canResend: boolean;
  recipientRole: string | null;
}

export interface DnsRecord {
  /** Nom court à saisir chez le registrar (ex. `mt02`, `_dmarc`). */
  name: string;
  /** Nom complet résultant (ex. `mt02.optimisante.fr`). */
  domain: string;
  type: string;
  value: string;
  /** `passed` si l'enregistrement est en place, `missing` sinon. */
  status: string;
}

export interface EmailStats {
  totalSent: number;
  totalFailed: number;
  mailtrapConnected: boolean;
  mailtrapError: string | null;
  sendingDomain: string | null;
  /** Tous les enregistrements DNS sont vérifiés : l'envoi réel est possible. */
  domainVerified: boolean;
  dnsRecords: DnsRecord[];
}

export interface EmailLogPage {
  content: EmailLog[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export const EMAIL_TYPE_LABELS: Record<EmailType, string> = {
  CREDENTIALS: 'Identifiants de connexion',
  TEST: 'Email de test',
};

export const adminEmailService = {
  getLogs: async (params: {
    status?: string;
    type?: string;
    search?: string;
    page?: number;
    size?: number;
  }) => {
    const { data } = await axiosClient.get<EmailLogPage>('/admin/emails', { params });
    return data;
  },

  getStats: async () => {
    const { data } = await axiosClient.get<EmailStats>('/admin/emails/stats');
    return data;
  },

  resend: async (id: string) => {
    const { data } = await axiosClient.post<{ message: string }>(`/admin/emails/${id}/resend`);
    return data;
  },

  sendTest: async (recipient: string) => {
    const { data } = await axiosClient.post<{ message: string }>('/admin/emails/test', { recipient });
    return data;
  },
};
