import { axiosClient } from './axiosClient';
import type { PageExercerEnFrance } from './exercerEnFranceService';

const base = '/admin/exercer-en-france';

export interface SectionAdmin {
  id: string;
  cle: string;
  surTitre: string | null;
  titre: string;
  contenu: string | null;
  ordre: number;
  visible: boolean;
  modifieLe: string;
}

export interface PeriodeAdmin {
  id: string;
  libelle: string;
  dateDebut: string | null;
  dateFin: string | null;
  specialitesNonOuvertes: string | null;
  sourceUrl: string;
  visible: boolean;
  ordre: number;
}

export interface LienAdmin {
  id: string;
  cle: string;
  libelle: string;
  url: string;
  verifieLe: string | null;
}

export interface SpecialiteAdmin {
  id: string;
  groupe: 'profession' | 'medecine';
  libelle: string;
  ordre: number;
}

export interface OffreAdmin {
  id: string;
  cle: string;
  surTitre: string | null;
  titre: string;
  points: string | null;
  prix: number | null;
  /** Prix de référence affiché barré ; nul = aucune comparaison. */
  prixBarre: number | null;
  /** Mention mise en avant sur la carte (« Le plus complet ») ; nulle = aucun badge. */
  badge: string | null;
  devise: string;
  libelleBouton: string;
  actif: boolean;
  technique: boolean;
  ordre: number;
}

/**
 * Les quatre chiffres du tableau de bord, et les quatre rappels.
 *
 * @param calendrierAVerifier le calendrier des commissions n'a pas été actualisé depuis plus
 *                            de 90 jours, ou sa date est inconnue
 * @param prestationsSansFin  demandes engagées depuis plus d'un an sans date de fin : leur
 *                            délai de conservation n'a jamais commencé
 */
export interface IndicateursEvc {
  demandesATraiter: number;
  demandesCeMois: number;
  demandesPayees: number;
  dernierePublication: string | null;
  version: number | null;
  liensNonVerifies: number;
  periodesSansDates: number;
  calendrierAVerifier: boolean;
  prestationsSansFin: number;
}

/**
 * Ce qu'une restauration a repris — elle ne publie RIEN.
 *
 * <p>La version choisie est recopiée dans le brouillon ; l'administrateur la relit par
 * l'aperçu, puis publie s'il la veut. Les nombres disent combien d'entrées ont été
 * réécrites : une entrée supprimée depuis n'est pas ressuscitée.</p>
 */
export interface Restauration {
  versionSource: number;
  sections: number;
  periodes: number;
  liens: number;
  offres: number;
}

export interface VersionPubliee {
  version: number;
  publieLe: string;
  publiePar: string | null;
  restaureDe: number | null;
}

export const adminExercerEnFranceService = {
  indicateurs: async (): Promise<IndicateursEvc> =>
    (await axiosClient.get<IndicateursEvc>(`${base}/indicateurs`)).data,

  // ── Sections ───────────────────────────────────────────────────────────────────────
  sections: async (): Promise<SectionAdmin[]> =>
    (await axiosClient.get<SectionAdmin[]>(`${base}/sections`)).data,
  creerSection: async (dto: Partial<SectionAdmin>) =>
    (await axiosClient.post<SectionAdmin>(`${base}/sections`, dto)).data,
  modifierSection: async (id: string, dto: Partial<SectionAdmin>) =>
    (await axiosClient.put<SectionAdmin>(`${base}/sections/${id}`, dto)).data,
  deplacerSection: async (id: string, versLeHaut: boolean) =>
    axiosClient.post(`${base}/sections/${id}/deplacer`, null, { params: { versLeHaut } }),
  supprimerSection: async (id: string) => axiosClient.delete(`${base}/sections/${id}`),

  // ── Périodes ───────────────────────────────────────────────────────────────────────
  periodes: async (): Promise<PeriodeAdmin[]> =>
    (await axiosClient.get<PeriodeAdmin[]>(`${base}/periodes`)).data,
  creerPeriode: async (dto: Partial<PeriodeAdmin>) =>
    (await axiosClient.post<PeriodeAdmin>(`${base}/periodes`, dto)).data,
  modifierPeriode: async (id: string, dto: Partial<PeriodeAdmin>) =>
    (await axiosClient.put<PeriodeAdmin>(`${base}/periodes/${id}`, dto)).data,
  supprimerPeriode: async (id: string) => axiosClient.delete(`${base}/periodes/${id}`),

  // ── Liens ──────────────────────────────────────────────────────────────────────────
  liens: async (): Promise<LienAdmin[]> =>
    (await axiosClient.get<LienAdmin[]>(`${base}/liens`)).data,
  creerLien: async (dto: Partial<LienAdmin>) =>
    (await axiosClient.post<LienAdmin>(`${base}/liens`, dto)).data,
  modifierLien: async (id: string, dto: Partial<LienAdmin>) =>
    (await axiosClient.put<LienAdmin>(`${base}/liens/${id}`, dto)).data,
  marquerLienVerifie: async (id: string) => axiosClient.post(`${base}/liens/${id}/verifier`),

  // ── Spécialités ────────────────────────────────────────────────────────────────────
  specialites: async (): Promise<SpecialiteAdmin[]> =>
    (await axiosClient.get<SpecialiteAdmin[]>(`${base}/specialites`)).data,
  creerSpecialite: async (dto: Partial<SpecialiteAdmin>) =>
    (await axiosClient.post<SpecialiteAdmin>(`${base}/specialites`, dto)).data,
  modifierSpecialite: async (id: string, dto: Partial<SpecialiteAdmin>) =>
    (await axiosClient.put<SpecialiteAdmin>(`${base}/specialites/${id}`, dto)).data,
  supprimerSpecialite: async (id: string) => axiosClient.delete(`${base}/specialites/${id}`),

  // ── Offres : lecture pour la Mobilité, écriture pour le super administrateur ───────
  offres: async (): Promise<OffreAdmin[]> =>
    (await axiosClient.get<OffreAdmin[]>(`${base}/offres`)).data,
  creerOffre: async (dto: Partial<OffreAdmin>) =>
    (await axiosClient.post<OffreAdmin>(`${base}/offres`, dto)).data,
  modifierOffre: async (id: string, dto: Partial<OffreAdmin>) =>
    (await axiosClient.put<OffreAdmin>(`${base}/offres/${id}`, dto)).data,

  // ── Réglages, aperçu, publication ──────────────────────────────────────────────────
  definirCalendrier: async (date: string) =>
    axiosClient.put(`${base}/reglages/calendrier-actualise-le`, null, { params: { date } }),
  /** Le brouillon tel qu'il serait publié : ce que l'aperçu affiche. */
  apercu: async (): Promise<PageExercerEnFrance> =>
    (await axiosClient.get<PageExercerEnFrance>(`${base}/apercu`)).data,
  publier: async (): Promise<VersionPubliee> =>
    (await axiosClient.post<VersionPubliee>(`${base}/publier`)).data,
  versions: async (): Promise<VersionPubliee[]> =>
    (await axiosClient.get<VersionPubliee[]>(`${base}/versions`)).data,
  restaurer: async (version: number): Promise<Restauration> =>
    (await axiosClient.post<Restauration>(`${base}/versions/${version}/restaurer`)).data,
};
