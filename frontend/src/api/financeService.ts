import { axiosClient } from './axiosClient';

/** Formatage monétaire unique, partagé par les écrans admin et partenaire. */
export const formatMoney = (amount: number | null | undefined, currency = 'EUR') =>
  amount == null
    ? '—'
    : new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount);

export const formatDate = (value: string | null | undefined) =>
  value ? new Date(value).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '—';

export type PaymentType = 'DOSSIER_FEE' | 'TUITION_FEE';
export type FinancePaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';
export type PayoutStatus = 'PENDING' | 'PAID' | 'CANCELLED';

export const PAYMENT_TYPE_LABELS: Record<PaymentType, string> = {
  DOSSIER_FEE: 'Frais de dossier',
  TUITION_FEE: 'Frais de formation',
};

/** Vue administration : porte la répartition complète. */
export interface AdminPaymentRow {
  id: string;
  enrollmentId: string;
  doctorName: string;
  doctorEmail: string;
  trainingTitle: string;
  partnerName: string | null;
  paymentType: PaymentType;
  status: FinancePaymentStatus;
  grossAmount: number;
  commissionRate: number;
  commissionAmount: number;
  partnerPayoutAmount: number;
  currency: string;
  paidAt: string | null;
  createdAt: string;
  payoutReference: string | null;
}

export interface AdminFinanceKpis {
  totalCollected: number;
  totalCommission: number;
  awaitingPayout: number;
  totalPaidOut: number;
  paidTransactions: number;
}

export interface PartnerDueRow {
  partnerProfileId: string;
  institutionName: string;
  pendingAmount: number;
  awaitingTransfer: number;
  paidOut: number;
  pendingPaymentsCount: number;
}

export interface PayoutDto {
  id: string;
  reference: string | null;
  totalAmount: number;
  currency: string;
  status: PayoutStatus;
  periodStart: string | null;
  periodEnd: string | null;
  paidAt: string | null;
  createdAt: string;
  /** Vrai lorsque le relevé PDF a été généré et peut être téléchargé. */
  statementAvailable: boolean;
}

/**
 * Vue partenaire : volontairement dépourvue de tout champ de commission — le serveur ne
 * l'envoie pas, ce n'est pas un simple masquage à l'affichage.
 */
export interface PartnerPaymentRow {
  id: string;
  doctorName: string;
  trainingTitle: string;
  sessionStartDate: string | null;
  paidAt: string | null;
  netAmount: number;
  currency: string;
  payoutReference: string | null;
}

export interface PartnerFinancialSummary {
  pendingAmount: number;
  awaitingTransfer: number;
  paidOut: number;
  pendingPaymentsCount: number;
  payoutsCount: number;
}

/** État d'une tranche du point de vue du reversement au CHU. */
export type EtatTranche = 'A_REVERSER' | 'EN_ATTENTE_MEDECIN' | 'VIREMENT_INITIE' | 'VIRE' | 'ANNULE';

export interface TrancheReversement {
  /** Nul tant que le médecin n'a pas réglé la tranche : rien à virer. */
  paymentId: string | null;
  tranche: 'DEPOSIT' | 'BALANCE' | 'FULL';
  trancheLabel: string;
  etat: EtatTranche;
  grossAmount: number;
  commissionRate: number;
  commissionAmount: number;
  netAmount: number;
  paidAt: string | null;
  payoutId: string | null;
  payoutReference: string | null;
  statementAvailable: boolean;
  /** Référence qu'aura le virement de cette seule tranche. */
  bankReference: string | null;
}

export interface DossierReversement {
  enrollmentId: string;
  dossierCode: string;
  doctorName: string;
  trainingTitle: string;
  sessionStart: string | null;
  /** Options réglées par le médecin : conservées par Optimi Santé, jamais reversées. */
  optionsAmount: number;
  tranches: TrancheReversement[];
}

export interface CoordonneesPartenaire {
  partnerProfileId: string;
  institutionName: string;
  commissionRate: number;
  iban: string | null;
  ibanMasque: string | null;
  bic: string | null;
  titulaire: string | null;
  /** IBAN et titulaire renseignés : condition pour émettre un virement. */
  complet: boolean;
}

export const financeService = {
  // ---- Reversement par dossier ----
  getPayoutDossiers: async (partnerProfileId: string) =>
    (await axiosClient.get<DossierReversement[]>(`/admin/partners/${partnerProfileId}/payout-dossiers`)).data,

  getBankDetails: async (partnerProfileId: string) =>
    (await axiosClient.get<CoordonneesPartenaire>(`/admin/partners/${partnerProfileId}/bank-details`)).data,

  saveBankDetails: async (partnerProfileId: string, body: { iban: string; bic: string; accountHolder: string }) =>
    (await axiosClient.put<CoordonneesPartenaire>(`/admin/partners/${partnerProfileId}/bank-details`, body)).data,

  /** Crée le virement d'une ou plusieurs tranches ; le bordereau est produit dans la foulée. */
  payoutForPayments: async (partnerProfileId: string, paymentIds: string[]) =>
    (await axiosClient.post<PayoutDto>(`/admin/partners/${partnerProfileId}/payouts/by-payments`, { paymentIds })).data,

  sepaUrl: (payoutId: string) => `/admin/payouts/${payoutId}/sepa`,


  // ---- Administration ----
  getKpis: async () => (await axiosClient.get<AdminFinanceKpis>('/admin/finance/kpis')).data,
  getAllPayments: async () => (await axiosClient.get<AdminPaymentRow[]>('/admin/payments')).data,
  getPartnersDue: async () => (await axiosClient.get<PartnerDueRow[]>('/admin/finance/partners-due')).data,
  getPartnerPayouts: async (partnerProfileId: string) =>
    (await axiosClient.get<PayoutDto[]>(`/admin/partners/${partnerProfileId}/payouts`)).data,

  generatePayout: async (partnerProfileId: string, periodStart?: string, periodEnd?: string) =>
    (await axiosClient.post<PayoutDto>(`/admin/partners/${partnerProfileId}/payouts`, null, {
      params: { periodStart: periodStart || undefined, periodEnd: periodEnd || undefined },
    })).data,

  generateStatement: async (payoutId: string) =>
    (await axiosClient.post<PayoutDto>(`/admin/payouts/${payoutId}/statement`)).data,

  /** URL signée du relevé ; le contrôle des droits est fait par le serveur. */
  getStatementUrl: async (payoutId: string) => {
    const { data } = await axiosClient.get<{ downloadUrl?: string; url?: string }>(
      `/documents/PAYOUT_STATEMENT/${payoutId}/download`,
    );
    return data.downloadUrl || data.url || null;
  },

  markPayoutPaid: async (payoutId: string) =>
    (await axiosClient.post<PayoutDto>(`/admin/payouts/${payoutId}/mark-paid`)).data,

  // ---- Partenaire ----
  getMySummary: async () =>
    (await axiosClient.get<PartnerFinancialSummary>('/partner/financial-summary')).data,
  getMyPayments: async () =>
    (await axiosClient.get<PartnerPaymentRow[]>('/partner/payments')).data,
  getMyPayouts: async () => (await axiosClient.get<PayoutDto[]>('/partner/payouts')).data,
};
