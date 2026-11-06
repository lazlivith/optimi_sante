import { axiosClient } from './axiosClient';

export interface UploadResponseDto {
  /**
   * Référence signée du fichier déposé (DP-4). Ce n'est plus la clé de stockage : une référence
   * à courte durée, chiffrée, que le serveur résout en clé. L'inscription la renvoie telle quelle.
   *
   * Optionnelle dans le type, et c'est voulu : si le backend n'a pas encore été déployé, la
   * réponse ne la porte pas. Ne jamais la ranger sans la vérifier — `undefined` glissé dans
   * l'état laisse un bouton désactivé sans message, ce qui était la panne de la fenêtre de
   * déploiement.
   */
  reference?: string;
  /** Aperçu servi par notre backend (jamais une URL de stockage). */
  previewUrl?: string;
  message: string;
}

/**
 * Code renvoyé avec un 409 quand la page du navigateur précède DP-4 et présente une clé de
 * stockage là où le serveur attend une référence. Doit rester identique à
 * `ReferencePerimeeException.CODE` côté serveur.
 */
export const REFERENCE_PERIMEE = 'REFERENCE_PERIMEE';

/** Le texte montré au candidat dans ce cas, et dans aucun autre. */
export const MESSAGE_PAGE_A_RECHARGER =
  'La page a été mise à jour, merci de la recharger avant de déposer vos pièces.';

/** Vrai si l'erreur Axios reçue est le décalage de version, et non une autre panne. */
export function estPagePerimee(erreur: unknown): boolean {
  // Le garde sur null/undefined n'est pas de la prudence décorative : une panne réseau ou une
  // requête annulée arrive ici sans `response`, et parfois sans erreur du tout.
  if (!erreur || typeof erreur !== 'object') return false;
  const reponse = (erreur as { response?: { status?: number; data?: { code?: string } } }).response;
  return reponse?.status === 409 && reponse?.data?.code === REFERENCE_PERIMEE;
}

/**
 * Dossiers ouverts au dépôt générique. Le serveur tient la liste fermée (DossierStockage) : un
 * nom absent de sa liste est refusé, un chemin libre n'est plus accepté.
 */
export type DossierDepot = 'DOSSIERS_PIECES';

export const storageService = {
  uploadFile: async (file: File, folder: DossierDepot = 'DOSSIERS_PIECES'): Promise<UploadResponseDto> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('folder', folder);

    const response = await axiosClient.post<UploadResponseDto>('/storage/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },
};
