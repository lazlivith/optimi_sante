import { axiosClient } from './axiosClient';

/**
 * Entretien de sélection : le CHU propose des créneaux, Optimi Santé les transmet,
 * le médecin en retient un.
 *
 * Les trois espaces passent par ce même fichier mais par des routes distinctes — un préfixe
 * par rôle, exactement comme le backend. Aucun appel ne permet au partenaire de s'adresser
 * directement au médecin : le circuit est porté par les routes, pas seulement par l'écran.
 */

export type InterviewStatus = 'PROPOSED' | 'TRANSMITTED' | 'CONFIRMED' | 'CANCELLED';
export type InterviewMode = 'VIDEO' | 'PHONE' | 'ON_SITE';

export const INTERVIEW_MODES: { value: InterviewMode; label: string; aide: string }[] = [
  { value: 'VIDEO', label: 'Visioconférence', aide: 'Lien de connexion' },
  { value: 'ON_SITE', label: 'Sur place', aide: 'Adresse complète' },
  // Le téléphone est le seul mode qui n'exige rien : c'est le CHU qui appelle.
  { value: 'PHONE', label: 'Par téléphone', aide: 'Facultatif' },
];

export const interviewModeLabel = (mode: InterviewMode) =>
  INTERVIEW_MODES.find((m) => m.value === mode)?.label ?? mode;

export const INTERVIEW_STATUS_LABELS: Record<InterviewStatus, string> = {
  PROPOSED: 'En attente de transmission',
  TRANSMITTED: 'En attente du choix du médecin',
  CONFIRMED: 'Rendez-vous confirmé',
  CANCELLED: 'Annulé',
};

export interface InterviewSlot {
  id: string;
  startsAt: string;
  endsAt: string;
  /** Vrai pour le créneau retenu par le médecin. */
  confirmed: boolean;
}

export interface InterviewSchedule {
  id: string;
  enrollmentId: string;
  status: InterviewStatus;
  mode: InterviewMode;
  locationOrLink: string | null;
  /** Consignes du CHU. Distinct de `adminNote` : le médecin doit savoir qui lui dit quoi. */
  partnerNote: string | null;
  adminNote: string | null;
  proposedAt: string;
  transmittedAt: string | null;
  confirmedAt: string | null;
  cancelledReason: string | null;
  slots: InterviewSlot[];
  doctorName: string;
  doctorEmail: string;
  trainingTitle: string;
  partnerInstitutionName: string;
  convocationDocumentId: string | null;
}

export interface SlotInput {
  startsAt: string;
  endsAt: string;
}

export interface ProposeInput {
  mode: InterviewMode;
  locationOrLink?: string | null;
  partnerNote?: string | null;
  slots: SlotInput[];
}

export const interviewService = {
  // ------------------------------------------------------------------- Partenaire ------

  propose: async (enrollmentId: string, input: ProposeInput): Promise<InterviewSchedule> => {
    const { data } = await axiosClient.post<InterviewSchedule>(
      `/partner/enrollments/${enrollmentId}/interview`,
      input,
    );
    return data;
  },

  historyForPartner: async (enrollmentId: string): Promise<InterviewSchedule[]> => {
    const { data } = await axiosClient.get<InterviewSchedule[]>(
      `/partner/enrollments/${enrollmentId}/interviews`,
    );
    return data;
  },

  // ---------------------------------------------------------------- Administration ------

  awaitingTransmission: async (): Promise<InterviewSchedule[]> => {
    const { data } = await axiosClient.get<InterviewSchedule[]>(
      '/admin/interviews/awaiting-transmission',
    );
    return data;
  },

  historyForAdmin: async (enrollmentId: string): Promise<InterviewSchedule[]> => {
    const { data } = await axiosClient.get<InterviewSchedule[]>(
      `/admin/enrollments/${enrollmentId}/interviews`,
    );
    return data;
  },

  transmit: async (scheduleId: string, adminNote?: string | null): Promise<InterviewSchedule> => {
    const { data } = await axiosClient.post<InterviewSchedule>(
      `/admin/interviews/${scheduleId}/transmit`,
      { adminNote: adminNote ?? null },
    );
    return data;
  },

  cancel: async (scheduleId: string, reason: string): Promise<InterviewSchedule> => {
    const { data } = await axiosClient.post<InterviewSchedule>(
      `/admin/interviews/${scheduleId}/cancel`,
      { reason },
    );
    return data;
  },

  // ----------------------------------------------------------------------- Médecin ------

  /**
   * L'entretien en cours du médecin, ou `null`.
   *
   * Le backend répond 204 tant qu'aucun entretien ne lui a été transmis — il n'existe pas
   * encore pour lui. Axios renvoie alors un corps vide, converti ici en `null` pour que
   * l'appelant n'ait pas à connaître ce détail de protocole.
   */
  current: async (enrollmentId: string): Promise<InterviewSchedule | null> => {
    const { data, status } = await axiosClient.get<InterviewSchedule | ''>(
      `/enrollments/${enrollmentId}/interview`,
    );
    return status === 204 || !data ? null : (data as InterviewSchedule);
  },

  confirm: async (scheduleId: string, slotId: string): Promise<InterviewSchedule> => {
    const { data } = await axiosClient.post<InterviewSchedule>(
      `/enrollments/interviews/${scheduleId}/confirm`,
      { slotId },
    );
    return data;
  },
};

/** Créneau en toutes lettres, dans le fuseau du lecteur. */
export const formatCreneau = (slot: InterviewSlot) => {
  const debut = new Date(slot.startsAt);
  const fin = new Date(slot.endsAt);
  const jour = debut.toLocaleDateString('fr-FR', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  });
  const heure = (d: Date) =>
    d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  return `${jour} · ${heure(debut)} – ${heure(fin)}`;
};

/** Un créneau déjà passé ne peut plus être retenu : l'écran doit le dire avant le serveur. */
export const estPasse = (slot: InterviewSlot) => new Date(slot.startsAt) < new Date();
