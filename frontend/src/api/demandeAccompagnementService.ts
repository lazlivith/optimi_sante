import { axiosClient } from './axiosClient';

/**
 * Une demande d'accompagnement, telle que le formulaire l'envoie.
 *
 * <p><b>Aucun champ de tenant, et c'est délibéré.</b> La route est publique : le
 * rattachement est décidé par le serveur, à partir de sa configuration. Un champ ici, même
 * ignoré, finirait par être lu un jour.</p>
 *
 * @param piege champ invisible : rempli, c'est un robot
 * @param ouvertDepuisMs millisecondes entre l'ouverture du formulaire et l'envoi
 */
export interface DemandeRequest {
  nom: string;
  email: string;
  telephoneWhatsapp?: string;
  pays?: string;
  profession?: string;
  specialite?: string;
  etape?: string;
  offreCle?: string;
  message?: string;
  consentementRgpd: boolean;
  piege?: string;
  ouvertDepuisMs?: number;
}

/**
 * @param id nul quand le dépôt a été écarté — le message reste neutre, pour ne pas enseigner
 *           à l'auteur comment passer la fois suivante
 */
export interface DemandeConfirmation {
  id: string | null;
  message: string;
}

export const demandeAccompagnementService = {
  deposer: async (demande: DemandeRequest): Promise<DemandeConfirmation> =>
    (await axiosClient.post<DemandeConfirmation>(
      '/public/demandes-accompagnement', demande)).data,
};
