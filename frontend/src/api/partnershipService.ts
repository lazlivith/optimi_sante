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
  /** Jeton remis par le serveur à l'affichage du formulaire ; voir `jetonFormulaire`. */
  jeton: string | null;
  /** Contenu du champ qu'aucun visiteur ne voit. Vide, sauf si un robot l'a rempli. */
  piege: string;
}

/** Formats et taille que le serveur accepte pour la convention : la même règle des deux côtés. */
export const CONVENTION_EXTENSIONS = ['pdf', 'doc', 'docx'] as const;
export const CONVENTION_TAILLE_MAX_OCTETS = 10 * 1024 * 1024;

/**
 * Dit pourquoi le serveur refuserait cette convention, ou `null` si elle a une chance de passer.
 *
 * <p>Ce contrôle n'ajoute aucune sécurité — le serveur refait tout, et vérifie en plus le contenu.
 * Il évite seulement d'envoyer dix mégaoctets pour apprendre que le fichier était trop lourd.</p>
 */
export function refusConvention(fichier: File): string | null {
  const extension = fichier.name.includes('.') ? fichier.name.split('.').pop()!.toLowerCase() : '';
  if (!(CONVENTION_EXTENSIONS as readonly string[]).includes(extension)) {
    return 'Format non accepté : la convention doit être un PDF, un DOC ou un DOCX.';
  }
  if (fichier.size > CONVENTION_TAILLE_MAX_OCTETS) {
    return 'La convention dépasse 10 Mo.';
  }
  return null;
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
  /** Adresse du modèle Excel. Le serveur le produit à la demande, il n'est jamais figé. */
  modeleDossierUrl: (): string => '/api/v1/partnership/dossier-modele',

  /** Modele de convention cadre, en classeur : les cases se saisissent au clavier. */
  modeleConventionUrl: (): string => '/api/v1/partnership/convention-modele',

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

  /**
   * Jeton à demander à l'affichage du formulaire, et à renvoyer avec la demande.
   *
   * <p>Il date l'affichage : le serveur refuse un envoi qui n'en porte pas, ou qui arrive dans
   * les secondes qui suivent. Rend `null` si la demande échoue — l'envoi sera alors refusé par
   * le serveur avec un message qui dit de recharger la page, ce qui est la bonne consigne.</p>
   */
  jetonFormulaire: async (): Promise<string | null> => {
    try {
      const { data } = await axiosClient.get<{ jeton: string }>('/partnership/formulaire');
      return data.jeton ?? null;
    } catch {
      return null;
    }
  },

  submitRequest: async (payload: SubmitPartnershipRequestPayload): Promise<PartnershipRequestDto> => {
    const formData = new FormData();
    if (payload.jeton) formData.append('jeton', payload.jeton);
    formData.append('siteInternet', payload.piege);
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
