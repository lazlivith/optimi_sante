/**
 * Les 8 étapes du parcours d'un médecin, telles qu'on les lui présente.
 *
 * <p><b>Une lecture des statuts, pas un nouvel automate.</b> Le serveur garde ses onze statuts
 * et leurs transitions ({@code EnrollmentTransitions}) ; ce fichier dit seulement à quelle étape
 * du parcours correspond chacun. `ENROLLMENT_STEPS` reste la liste des statuts sur laquelle
 * l'administration fait avancer un dossier : elle n'est pas remplacée.</p>
 */

export type Acteur = 'medecin' | 'optimi' | 'chu';

export interface EtapeParcours {
  numero: number;
  titre: string;
  detail: string;
  /** Qui doit agir pendant cette étape, dans l'ordre. */
  acteurs: Acteur[];
}

export const ETAPES_PARCOURS: EtapeParcours[] = [
  { numero: 1, titre: 'Candidature', detail: 'Pièces et frais de dossier', acteurs: ['medecin', 'optimi'] },
  { numero: 2, titre: 'Étude CHU & visio', detail: 'Créneaux, entretien, admission', acteurs: ['chu', 'medecin'] },
  { numero: 3, titre: 'Acompte 60 %', detail: 'Formation et options', acteurs: ['medecin'] },
  { numero: 4, titre: 'Inscription confirmée', detail: 'Place réservée', acteurs: ['optimi'] },
  { numero: 5, titre: 'Convention & programme', detail: 'Déposés au coffre-fort', acteurs: ['chu', 'optimi'] },
  { numero: 6, titre: 'Demande de visa', detail: 'Dossier consulaire', acteurs: ['medecin'] },
  { numero: 7, titre: 'Visa obtenu', detail: 'Visa VLS-T déposé', acteurs: ['medecin', 'optimi'] },
  { numero: 8, titre: 'Solde 40 % & kit', detail: 'Billets, hébergement, contacts', acteurs: ['medecin'] },
];

/**
 * Index de l'étape EN COURS (0 à 7), ou 8 quand tout est accompli.
 *
 * Deux étapes ne sont jamais « en cours » longtemps : l'inscription se confirme au paiement de
 * l'acompte, et l'obtention du visa ouvre aussitôt l'appel du solde. Un dossier confirmé est
 * donc à l'étape 5, un visa délivré à l'étape 8.
 *
 * @returns -1 pour un dossier refusé ou annulé, qui sort du parcours
 */
export function etapeEnCours(statut: string): number {
  switch (statut) {
    case 'UNDER_OPTIMI_REVIEW':
    case 'ACTION_REQUIRED':
      return 0;
    case 'SUBMITTED_TO_PARTNER':
    case 'ACCEPTED_BY_PARTNER':
      return 1;
    case 'PENDING_TUITION_FEE':
      return 2;
    case 'CONFIRMED':
      return 4;
    case 'CONVENTION_ISSUED':
      return 5;
    case 'VISA_SUBMITTED':
      return 6;
    case 'VISA_GRANTED':
      return 7;
    case 'READY_TO_START':
      return 8;
    default:
      return -1;
  }
}

export const LIBELLE_ACTEUR: Record<'medecin' | 'admin', Record<Acteur, string>> = {
  medecin: { medecin: 'Vous', optimi: 'Optimi Santé', chu: "L'établissement" },
  admin: { medecin: 'Médecin', optimi: 'Optimi Santé', chu: 'CHU' },
};
