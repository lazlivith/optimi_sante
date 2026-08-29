import { useEffect, useState } from 'react';
import { Loader2, GraduationCap, Check, X, Image as ImageIcon, Video } from 'lucide-react';
import { adminTrainingService, type AdminTrainingDto } from '../../api/adminTrainingService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Toast, type ToastType } from '../../components/common/Toast';

export function AdminTrainingsPage() {
  const [trainings, setTrainings] = useState<AdminTrainingDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const fetchTrainings = async () => {
    setIsLoading(true);
    try {
      const data = await adminTrainingService.listTrainings();
      setTrainings(data);
    } catch {
      setToast({ message: 'Impossible de charger les formations.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { fetchTrainings(); }, []);

  const handleApprove = async (t: AdminTrainingDto) => {
    setProcessingId(t.id);
    try {
      const updated = await adminTrainingService.approve(t.id);
      setTrainings(prev => prev.map(x => x.id === t.id ? updated : x));
      setToast({ message: `"${t.title}" validée et publiée sur le catalogue.`, type: 'success' });
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la validation.', type: 'error' });
    } finally {
      setProcessingId(null);
    }
  };

  const handleReject = async (t: AdminTrainingDto) => {
    const reason = window.prompt(`Motif du rejet de "${t.title}" (visible par le partenaire) :`);
    if (!reason || !reason.trim()) return;
    setProcessingId(t.id);
    try {
      const updated = await adminTrainingService.reject(t.id, reason.trim());
      setTrainings(prev => prev.map(x => x.id === t.id ? updated : x));
      setToast({ message: 'Formation rejetée.', type: 'success' });
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors du rejet.', type: 'error' });
    } finally {
      setProcessingId(null);
    }
  };

  const pendingCount = trainings.filter(t => t.approvalStatus === 'PENDING_REVIEW').length;

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Formations des partenaires"
        subtitle={`Validez les formations créées par les CHU avant leur publication sur le catalogue public.${pendingCount > 0 ? ` ${pendingCount} en attente.` : ''}`}
      />

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center"><Loader2 className="w-8 h-8 text-brand-green animate-spin" /></div>
        ) : trainings.length === 0 ? (
          <EmptyState icon={GraduationCap} title="Aucune formation soumise pour le moment." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Formation</th>
                  <th className="px-6 py-4">Partenaire</th>
                  <th className="px-6 py-4">Prix</th>
                  <th className="px-6 py-4">Médias</th>
                  <th className="px-6 py-4">Statut</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {trainings.map((t) => (
                  <tr key={t.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="font-semibold text-slate-900">{t.title}</div>
                      <div className="text-xs text-slate-500">{t.medicalSpecialty} · {t.durationDays} j.</div>
                    </td>
                    <td className="px-6 py-4">
                      <div className="text-slate-700">{t.partnerInstitutionName}</div>
                      <div className="text-xs text-slate-400">{t.partnerContactEmail}</div>
                    </td>
                    <td className="px-6 py-4 font-semibold text-slate-900">{t.price.toFixed(2)} €</td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2 text-slate-400">
                        <ImageIcon className={`w-4 h-4 ${t.imageUrl ? 'text-emerald-600' : ''}`} />
                        <Video className={`w-4 h-4 ${t.videoUrl ? 'text-emerald-600' : ''}`} />
                      </div>
                    </td>
                    <td className="px-6 py-4"><StatusBadge status={t.approvalStatus} /></td>
                    <td className="px-6 py-4 text-right">
                      {t.approvalStatus === 'PENDING_REVIEW' && (
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => handleApprove(t)}
                            disabled={processingId === t.id}
                            className="inline-flex items-center justify-center p-2 bg-emerald-100 text-emerald-700 rounded-lg hover:bg-emerald-200 transition disabled:opacity-50"
                            title="Valider et publier"
                          >
                            {processingId === t.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                          </button>
                          <button
                            onClick={() => handleReject(t)}
                            disabled={processingId === t.id}
                            className="inline-flex items-center justify-center p-2 bg-rose-100 text-rose-700 rounded-lg hover:bg-rose-200 transition disabled:opacity-50"
                            title="Rejeter"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
