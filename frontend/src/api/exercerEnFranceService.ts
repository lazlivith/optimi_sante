import { axiosClient } from './axiosClient';

/** Une section de la page, désignée par une clé que le gabarit cite. */
export interface SectionEvc {
  cle: string;
  surTitre: string | null;
  titre: string;
  contenu: string | null;
}

/** @param statut calculé par le serveur à chaque lecture : jamais figé. */
export interface PeriodeEvc {
  libelle: string;
  dateDebut: string | null;
  dateFin: string | null;
  specialitesNonOuvertes: string | null;
  sourceUrl: string;
  statut: 'ouvert' | 'a_venir' | 'ferme';
}

export interface LienEvc {
  cle: string;
  libelle: string;
  url: string;
}

export interface SpecialiteEvc {
  groupe: 'profession' | 'medecine';
  libelle: string;
}

/**
 * @param prix      nul : la carte affiche « Sur devis ». Les prix sont TTC.
 * @param prixBarre prix de référence affiché barré ; nul = aucune comparaison
 * @param badge     mention mise en avant sur la carte ; nulle = aucun badge
 */
export interface OffreEvc {
  cle: string;
  surTitre: string | null;
  titre: string;
  points: string[];
  prix: number | null;
  prixBarre: number | null;
  badge: string | null;
  devise: string;
  libelleBouton: string;
}

export interface PageExercerEnFrance {
  sections: SectionEvc[];
  periodes: PeriodeEvc[];
  liens: LienEvc[];
  specialites: SpecialiteEvc[];
  offres: OffreEvc[];
  misAJourLe: string | null;
  version: number | null;
  /** Dernière publication du calendrier officiel que NOUS avons constatée. */
  calendrierActualiseLe: string | null;
}

/**
 * La page « Exercer en France », en un seul appel.
 *
 * <p>Un appel et non six : la page n'a pas de sens par morceaux, et six requêtes depuis un
 * téléphone en connexion lente font six occasions d'afficher un écran à moitié construit.</p>
 *
 * <p>Aucun en-tête de tenant n'est envoyé, et c'est délibéré : la route est publique, le
 * contenu est le même pour tous, et le rattachement d'une demande est décidé par le
 * serveur.</p>
 */
export const exercerEnFranceService = {
  page: async (): Promise<PageExercerEnFrance> =>
    (await axiosClient.get<PageExercerEnFrance>('/public/exercer-en-france')).data,
};
