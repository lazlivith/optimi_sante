import { axiosClient } from './axiosClient';

export interface Supplier {
  id: string;
  code: string;
  companyName: string;
  taxId: string | null;
  contactName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  commissionRate: number;
  notes: string | null;
  active: boolean;
  /** Produits livrés par ce fournisseur, désactivés compris. */
  productCount: number;
  createdAt: string;
}

export interface SupplierRequest {
  code?: string;
  companyName: string;
  taxId?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  commissionRate?: number;
  notes?: string;
}

/** ANALYSE et IMPORT sont des états transitoires : l'écran les interroge jusqu'à leur terme. */
export type ImportStatus = 'ANALYSE' | 'PRET' | 'IMPORT' | 'TERMINE' | 'ECHEC' | 'ANNULE';

export interface CatalogImport {
  id: string;
  supplierId: string;
  fileName: string;
  status: ImportStatus;
  totalRows: number;
  /** Ce que l'analyse annonce, avant toute écriture. */
  toCreate: number;
  toUpdate: number;
  ignoredRows: number;
  errorRows: number;
  /** Avancement, pendant et après l'écriture. */
  processedRows: number;
  createdCount: number;
  updatedCount: number;
  imageCount: number;
  /** D'où vient le prix de vente des lignes retenues (marge catégorie, fournisseur, aucune). */
  marginSummary: string | null;
  motifs: string[];
  motifsTronques: boolean;
  failureReason: string | null;
  createdAt: string;
  confirmedAt: string | null;
  finishedAt: string | null;
}

const MULTIPART = { headers: { 'Content-Type': 'multipart/form-data' } };
const base = '/admin/suppliers';

export const adminSupplierService = {
  list: async () => (await axiosClient.get<Supplier[]>(base)).data,
  get: async (id: string) => (await axiosClient.get<Supplier>(`${base}/${id}`)).data,
  create: async (body: SupplierRequest) => (await axiosClient.post<Supplier>(base, body)).data,
  update: async (id: string, body: SupplierRequest) => (await axiosClient.put<Supplier>(`${base}/${id}`, body)).data,
  setActive: async (id: string, active: boolean) =>
    (await axiosClient.patch<Supplier>(`${base}/${id}/status?active=${active}`)).data,

  /** Dépose le fichier et l'analyse ; rien n'est écrit au catalogue à ce stade. */
  analyser: async (id: string, file: File) => {
    const data = new FormData();
    data.append('file', file);
    return (await axiosClient.post<CatalogImport>(`${base}/${id}/imports`, data, MULTIPART)).data;
  },
  historique: async (id: string) => (await axiosClient.get<CatalogImport[]>(`${base}/${id}/imports`)).data,
  suivre: async (importId: string) => (await axiosClient.get<CatalogImport>(`${base}/imports/${importId}`)).data,
  confirmer: async (importId: string) =>
    (await axiosClient.post<CatalogImport>(`${base}/imports/${importId}/confirm`)).data,
  annuler: async (importId: string) =>
    (await axiosClient.post<CatalogImport>(`${base}/imports/${importId}/cancel`)).data,

  templateUrl: () => `${base}/import-template.csv`,
};

export const IMPORT_STATUS_LABELS: Record<ImportStatus, string> = {
  ANALYSE: 'Analyse en cours',
  PRET: 'À confirmer',
  IMPORT: 'Import en cours',
  TERMINE: 'Terminé',
  ECHEC: 'Échec',
  ANNULE: 'Abandonné',
};
