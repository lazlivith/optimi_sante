import { axiosClient } from './axiosClient';

export interface PartnershipRequestDto {
  id: string;
  institutionName: string;
  finessAccreditation: string | null;
  contactPersonName: string;
  contactEmail: string;
  contactPhone: string;
  address: string;
  conventionFileKey: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectionReason: string | null;
  createdAt: string;
  reviewedAt: string | null;
}

export interface SubmitPartnershipRequestPayload {
  institutionName: string;
  finessAccreditation?: string;
  contactPersonName: string;
  contactEmail: string;
  contactPhone: string;
  address: string;
  conventionFile: File;
}

/** Une anomalie située dans le classeur, telle que le serveur la rapporte. */
export interface AnomalieDossier {
  feuille: string;
  ligne: number;
  colonne: string;
  valeur: string;
  probleme: string;
  resume: string;
}

export interface RapportDossier {
  conforme: boolean;
  identite: Record<string, string>;
  capacites: Record<string, string>[];
  anomalies: AnomalieDossier[];
}

export const partnershipService = {
  getConventionTemplateUrl: async (): Promise<string> => {
    const { data } = await axiosClient.get<{ downloadUrl: string }>('/partnership/convention-template');
    return data.downloadUrl;
  },

  /** Adresse du modèle Excel. Le serveur le produit à la demande, il n'est jamais figé. */
  modeleDossierUrl: (): string => '/api/v1/partnership/dossier-modele',

  /**
   * Fait relire un dossier rempli et rend le rapport.
   *
   * <p>Un dossier fautif n'est pas une erreur de requête : le serveur répond 200 avec la liste
   * des anomalies. C'est ce qui permet de les afficher plutôt que de montrer un échec.</p>
   */
  verifierDossier: async (fichier: File): Promise<RapportDossier> => {
    const corps = new FormData();
    corps.append('fichier', fichier);
    const { data } = await axiosClient.post<RapportDossier>(
      '/partnership/dossier/verification', corps,
      { headers: { 'Content-Type': 'multipart/form-data' } });
    return data;
  },

  submitRequest: async (payload: SubmitPartnershipRequestPayload): Promise<PartnershipRequestDto> => {
    const formData = new FormData();
    formData.append('institutionName', payload.institutionName);
    if (payload.finessAccreditation) formData.append('finessAccreditation', payload.finessAccreditation);
    formData.append('contactPersonName', payload.contactPersonName);
    formData.append('contactEmail', payload.contactEmail);
    formData.append('contactPhone', payload.contactPhone);
    formData.append('address', payload.address);
    formData.append('conventionFile', payload.conventionFile);

    const { data } = await axiosClient.post<PartnershipRequestDto>('/partnership/requests', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },
};
