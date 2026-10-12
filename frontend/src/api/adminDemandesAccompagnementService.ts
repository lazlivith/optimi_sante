import { axiosClient } from './axiosClient';

const base = '/admin/demandes-accompagnement';

/**
 * Une demande telle qu'elle apparaît dans la liste.
 *
 * <p><b>Ni le message, ni les notes internes.</b> Le serveur ne les envoie pas ici : un écran
 * de vingt-cinq lignes n'en a pas l'usage, et ce qui part vers le navigateur est lisible par
 * qui ouvre les outils de développement. Ils arrivent avec la fiche, dossier par dossier.</p>
 */
export interface DemandeResume {
  id: string;
  nom: string;
  email: string;
  telephoneWhatsapp: string | null;
  pays: string | null;
  profession: string | null;
  specialite: string | null;
  etape: string | null;
  offreCle: string | null;
  statut: string;
  assigneA: string | null;
  creeLe: string;
  derniereActiviteLe: string;
  anonymisee: boolean;
}

export interface EvenementVue {
  type: string;
  ancienStatut: string | null;
  nouveauStatut: string | null;
  contenu: string | null;
  auteurId: string | null;
  creeLe: string;
}

export interface DemandeVue extends Omit<DemandeResume, 'anonymisee'> {
  message: string | null;
  notesInternes: string | null;
  prestationFinLe: string | null;
  anonymisee: boolean;
  fil: EvenementVue[];
}

/** Ce que l'écran sait filtrer. Un champ laissé vide ne part pas. */
export interface FiltresDemandes {
  statut?: string;
  offreCle?: string;
  profession?: string;
  depuis?: string;
  jusqua?: string;
  recherche?: string;
}

export interface PageDemandes {
  content: DemandeResume[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** Les paramètres réellement envoyés : les champs vides sont retirés, pas envoyés vides. */
function parametres(f: FiltresDemandes): Record<string, string> {
  const p: Record<string, string> = {};
  Object.entries(f).forEach(([cle, valeur]) => {
    if (valeur !== undefined && valeur !== null && String(valeur).trim() !== '') {
      p[cle] = String(valeur).trim();
    }
  });
  return p;
}

/**
 * Télécharge un export sans quitter la page.
 *
 * <p><b>Pourquoi pas un simple lien.</b> La route exige le jeton d'authentification, qu'un
 * {@code <a href>} n'emporte pas : le navigateur recevrait un 401 et afficherait une page
 * blanche. Le fichier est donc demandé par l'appel authentifié habituel, puis remis au
 * navigateur comme un téléchargement.</p>
 */
async function telecharger(chemin: string, params: Record<string, string>, nom: string) {
  const reponse = await axiosClient.get(chemin, { params, responseType: 'blob' });
  const url = URL.createObjectURL(reponse.data as Blob);
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = nom;
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  // Sans cette libération, le fichier reste en mémoire jusqu'au rechargement de l'onglet.
  URL.revokeObjectURL(url);
}

export const adminDemandesAccompagnementService = {
  lister: async (filtres: FiltresDemandes, page: number, taille = 25): Promise<PageDemandes> =>
    (await axiosClient.get<PageDemandes>(base, {
      params: { ...parametres(filtres), page, taille },
    })).data,

  detail: async (id: string): Promise<DemandeVue> =>
    (await axiosClient.get<DemandeVue>(`${base}/${id}`)).data,

  changerStatut: async (id: string, statut: string, note?: string, prestationFinLe?: string) =>
    axiosClient.post(`${base}/${id}/statut`, { statut, note, prestationFinLe }),

  assigner: async (id: string, assigneA: string | null, note?: string) =>
    axiosClient.post(`${base}/${id}/assignation`, { assigneA, note }),

  ajouterNote: async (id: string, contenu: string) =>
    axiosClient.post(`${base}/${id}/notes`, { contenu }),

  exporter: (filtres: FiltresDemandes) =>
    telecharger(`${base}/export.csv`, parametres(filtres), 'demandes-accompagnement.csv'),

  exporterAlertes: () =>
    telecharger(`${base}/export-alertes.csv`, {}, 'contacts-a-prevenir.csv'),
};
