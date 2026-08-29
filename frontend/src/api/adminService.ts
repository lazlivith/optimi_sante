import { axiosClient } from './axiosClient';

export const adminService = {
  updateEnrollmentStatus: async (enrollmentId: string, status: string) => {
    // Le backend attend "status" en paramètre de requête (@RequestParam), pas dans le corps
    // JSON — sans ce params, l'appel échouait systématiquement (400 "Required request
    // parameter 'status' ... is not present"), quel que soit le statut visé.
    const { data } = await axiosClient.patch(`/admin/enrollments/${enrollmentId}/status`, null, { params: { status } });
    return data;
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
