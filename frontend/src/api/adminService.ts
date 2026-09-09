import { axiosClient } from './axiosClient';

export const adminService = {
  updateEnrollmentStatus: async (enrollmentId: string, status: string) => {
    // Le backend attend "status" en paramètre de requête (@RequestParam), pas dans le corps
    // JSON — sans ce params, l'appel échouait systématiquement (400 "Required request
    // parameter 'status' ... is not present"), quel que soit le statut visé.
    const { data } = await axiosClient.patch(`/admin/enrollments/${enrollmentId}/status`, null, { params: { status } });
    return data;
  },

  /**
   * Pré-qualification : les pièces sont conformes, le dossier part au CHU pour décision.
   * Distinct du changement de statut générique — celui-ci trace en plus qui a vérifié
   * et quand, et déclenche la notification « dossier transmis » au médecin.
   */
  submitEnrollmentToPartner: async (enrollmentId: string) => {
    const { data } = await axiosClient.post(`/admin/enrollments/${enrollmentId}/submit-to-partner`);
    return data;
  },

  /**
   * Renvoie le dossier au médecin pour correction. Le motif est obligatoire : c'est lui
   * qui est affiché au candidat et repris dans l'e-mail qu'il reçoit.
   */
  requestEnrollmentAction: async (enrollmentId: string, note: string) => {
    const { data } = await axiosClient.post(`/admin/enrollments/${enrollmentId}/request-action`, { note });
    return data;
  },

  /**
   * Retrait definitif d'une candidature. Le serveur refuse des que le dossier est parti au
   * CHU — l'interface masque deja le bouton, la garde serveur reste la reference.
   */
  deleteEnrollment: async (enrollmentId: string) => {
    await axiosClient.delete(`/admin/enrollments/${enrollmentId}`);
  },

  generateConvention: async (enrollmentId: string) => {
    const { data } = await axiosClient.post(`/admin/enrollments/${enrollmentId}/generate-convention`);
    return data;
  },

  generateAttestation: async (enrollmentId: string) => {
    const { data } = await axiosClient.post(`/admin/enrollments/${enrollmentId}/generate-attestation`);
    return data;
  },

  // Dépôt admin de pièces optionnelles (Lettre d'Accompagnement Consulaire, Attestation
  // d'Hébergement) — réutilise l'endpoint générique déjà ouvert aux admins.
  uploadEnrollmentDocument: async (enrollmentId: string, file: File, documentType: string) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('documentType', documentType);
    const { data } = await axiosClient.post(`/enrollments/${enrollmentId}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  }
};
