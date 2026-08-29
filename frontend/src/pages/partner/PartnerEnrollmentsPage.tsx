import { useEffect, useState } from 'react';
import { partnerService, type EnrollmentDto, type PartnerTrainingDto } from '../../api/partnerService';
import type { DocumentItemDto } from '../../api/vaultService';
import { vaultService } from '../../api/vaultService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Avatar } from '../../components/common/Avatar';
import { Loader2, CheckCircle, FileText, Filter, FolderOpen, X, ExternalLink } from 'lucide-react';

const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  PASSPORT: 'Passeport',
  DIPLOMA: 'Diplôme de médecine',
  MEDICAL_COUNCIL_CERT: "Certificat de l'Ordre des Médecins",
  FINANCIAL_GUARANTEE: 'Garantie financière',
  VISA_GRANT: 'Attestation de visa',
  CONSULAR_LETTER: "Lettre d'Accompagnement Consulaire",
  ACCOMMODATION_PROOF: "Attestation d'Hébergement",
  OTHER: 'Autre pièce justificative',
};

export function PartnerEnrollmentsPage() {
  const [enrollments, setEnrollments] = useState<EnrollmentDto[]>([]);
  const [trainings, setTrainings] = useState<PartnerTrainingDto[]>([]);
  const [trainingFilter, setTrainingFilter] = useState<string>('');
  const [isLoading, setIsLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const [viewingDocsFor, setViewingDocsFor] = useState<EnrollmentDto | null>(null);
  const [docs, setDocs] = useState<DocumentItemDto[]>([]);
  const [isLoadingDocs, setIsLoadingDocs] = useState(false);

  const fetchEnrollments = async (trainingId?: string) => {
    setIsLoading(true);
    try {
      const data = await partnerService.getEnrollments(trainingId || undefined);
      setEnrollments(data);
    } catch (err) {
      console.error(err);
      setToast({ message: "Impossible de charger les candidatures.", type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchEnrollments();
    partnerService.getMyTrainingsDetailed().then(setTrainings).catch(() => {});
  }, []);

  const handleFilterChange = (trainingId: string) => {
    setTrainingFilter(trainingId);
    fetchEnrollments(trainingId);
  };

  const handleApprove = async (id: string) => {
    try {
      await partnerService.reviewAcademic(id, 'APPROVED_ACADEMIC');
      setToast({ message: 'Candidature validée sur le plan académique.', type: 'success' });
      fetchEnrollments(trainingFilter || undefined);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la validation.', type: 'error' });
    }
  };

  const handleViewDocuments = async (enrollment: EnrollmentDto) => {
    setViewingDocsFor(enrollment);
    setIsLoadingDocs(true);
    try {
      const data = await partnerService.getEnrollmentDocuments(enrollment.id);
      setDocs(data);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Impossible de charger les documents.', type: 'error' });
      setViewingDocsFor(null);
    } finally {
      setIsLoadingDocs(false);
    }
  };

  const handleOpenDocument = async (doc: DocumentItemDto) => {
    try {
      const url = await vaultService.getPresignedUrl(doc.type, doc.id);
      window.open(url, '_blank');
    } catch {
      setToast({ message: "Impossible d'ouvrir le document.", type: 'error' });
    }
  };

  const handleReject = async (id: string) => {
    if (!window.confirm("Êtes-vous sûr de vouloir rejeter cette candidature ?")) return;
    try {
      await partnerService.reviewAcademic(id, 'REJECTED');
      setToast({ message: 'Candidature rejetée.', type: 'success' });
      fetchEnrollments(trainingFilter || undefined);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors du rejet.', type: 'error' });
    }
  };

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Candidatures reçues"
        subtitle="Dossiers en attente de validation académique."
        actions={
          <>
            <Filter className="w-4 h-4 text-slate-400" />
            <select
              value={trainingFilter}
              onChange={(e) => handleFilterChange(e.target.value)}
              className="text-sm rounded-lg border-slate-300 border py-2 px-3 bg-white text-slate-700 focus:border-brand-green focus:ring-brand-green"
            >
              <option value="">Toutes les formations</option>
              {trainings.map(t => (
                <option key={t.id} value={t.id}>{t.title}</option>
              ))}
            </select>
          </>
        }
      />

      <div className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center text-slate-400">
            <Loader2 className="w-8 h-8 animate-spin" />
          </div>
        ) : enrollments.length === 0 ? (
          <EmptyState icon={FileText} title="Aucune candidature reçue pour le moment." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="px-6 py-4 font-semibold">Médecin</th>
                  <th className="px-6 py-4 font-semibold">Formation</th>
                  <th className="px-6 py-4 font-semibold">Date de soumission</th>
                  <th className="px-6 py-4 font-semibold">Statut</th>
                  <th className="px-6 py-4 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {enrollments.map((e) => (
                  <tr key={e.id} className="border-b border-slate-100 hover:bg-slate-50/50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <Avatar name={e.doctorName} size="sm" />
                        <div className="min-w-0">
                          <div className="font-semibold text-brand-dark truncate">{e.doctorName}</div>
                          <div className="text-slate-500 text-xs truncate">{e.doctorEmail}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 font-medium text-slate-700">{e.trainingTitle || 'Formation non spécifiée'}</td>
                    <td className="px-6 py-4 text-slate-500">{new Date(e.submittedAt).toLocaleDateString('fr-FR')}</td>
                    <td className="px-6 py-4">
                      <StatusBadge status={e.status} />
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => handleViewDocuments(e)}
                          className="inline-flex items-center px-3 py-1.5 text-xs font-bold text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors"
                        >
                          <FolderOpen className="w-3.5 h-3.5 mr-1.5" /> Documents
                        </button>
                        {e.status === 'PENDING_REVIEW' && (
                          <>
                            <button onClick={() => handleApprove(e.id)} className="inline-flex items-center px-3 py-1.5 text-xs font-bold text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors">
                              <CheckCircle className="w-3.5 h-3.5 mr-1.5" /> Approuver
                            </button>
                            <button onClick={() => handleReject(e.id)} className="inline-flex items-center px-3 py-1.5 text-xs font-bold text-white bg-red-500 rounded-lg hover:bg-red-600 transition-colors">
                              Rejeter
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {viewingDocsFor && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white rounded-2xl w-full max-w-lg overflow-hidden shadow-xl border border-slate-200 max-h-[80vh] flex flex-col">
            <div className="flex justify-between items-center p-6 border-b border-slate-100 bg-slate-50 shrink-0">
              <div>
                <h2 className="text-lg font-bold text-brand-dark">Pièces du dossier</h2>
                <p className="text-xs text-slate-500 mt-0.5">{viewingDocsFor.doctorName}</p>
              </div>
              <button onClick={() => setViewingDocsFor(null)} className="text-slate-400 hover:text-brand-dark">
                <X className="w-6 h-6" />
              </button>
            </div>
            <div className="overflow-y-auto flex-1">
              {isLoadingDocs ? (
                <div className="p-12 flex justify-center"><Loader2 className="w-8 h-8 text-brand-green animate-spin" /></div>
              ) : docs.length === 0 ? (
                <EmptyState icon={FolderOpen} title="Aucun document déposé pour le moment." />
              ) : (
                <div className="divide-y divide-slate-100">
                  {docs.map((doc) => (
                    <div key={doc.id} className="px-6 py-4 flex items-center justify-between gap-4">
                      <div className="flex items-center gap-3 min-w-0">
                        <FileText className="w-5 h-5 text-slate-400 shrink-0" />
                        <div className="min-w-0">
                          <div className="text-sm font-semibold text-slate-800 truncate">
                            {DOCUMENT_TYPE_LABELS[doc.type] || doc.type}
                          </div>
                          <div className="text-xs text-slate-400">{new Date(doc.date).toLocaleDateString('fr-FR')}</div>
                        </div>
                      </div>
                      <button
                        onClick={() => handleOpenDocument(doc)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-brand-green bg-brand-light rounded-lg hover:bg-brand-green hover:text-white transition-colors shrink-0"
                      >
                        <ExternalLink className="w-3.5 h-3.5" /> Ouvrir
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
