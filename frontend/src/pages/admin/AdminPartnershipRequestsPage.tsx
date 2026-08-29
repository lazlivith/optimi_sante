import { useEffect, useState } from 'react';
import { Loader2, Building2, ExternalLink, Check, X } from 'lucide-react';
import { adminPartnershipService } from '../../api/adminPartnershipService';
import type { PartnershipRequestDto } from '../../api/partnershipService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';

export function AdminPartnershipRequestsPage() {
  const [requests, setRequests] = useState<PartnershipRequestDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const fetchRequests = async () => {
    setIsLoading(true);
    try {
      const data = await adminPartnershipService.listRequests();
      setRequests(data.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
    } catch (error) {
      console.error('Failed to fetch partnership requests', error);
      setToast({ message: 'Impossible de charger les demandes.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { fetchRequests(); }, []);

  const handleViewDocument = async (id: string) => {
    try {
      const url = await adminPartnershipService.getDocumentUrl(id);
      window.open(url, '_blank');
    } catch {
      setToast({ message: 'Impossible d\'ouvrir le document.', type: 'error' });
    }
  };

  const handleApprove = async (id: string) => {
    setProcessingId(id);
    try {
      const updated = await adminPartnershipService.approve(id);
      setRequests(prev => prev.map(r => r.id === id ? updated : r));
      setToast({ message: 'Partenariat validé — identifiants envoyés par email.', type: 'success' });
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la validation.', type: 'error' });
    } finally {
      setProcessingId(null);
    }
  };

  const handleReject = async (id: string) => {
    if (!window.confirm('Rejeter cette demande de partenariat ?')) return;
    setProcessingId(id);
    try {
      const updated = await adminPartnershipService.reject(id);
      setRequests(prev => prev.map(r => r.id === id ? updated : r));
      setToast({ message: 'Demande rejetée.', type: 'success' });
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors du rejet.', type: 'error' });
    } finally {
      setProcessingId(null);
    }
  };

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader title="Demandes de Partenariat" subtitle="Établissements souhaitant rejoindre le réseau Optimi Santé." />

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center items-center"><Loader2 className="w-8 h-8 text-emerald-600 animate-spin" /></div>
        ) : requests.length === 0 ? (
          <EmptyState icon={Building2} title="Aucune demande de partenariat pour le moment." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Établissement</th>
                  <th className="px-6 py-4">Contact</th>
                  <th className="px-6 py-4">Reçu le</th>
                  <th className="px-6 py-4">Convention</th>
                  <th className="px-6 py-4">Statut</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {requests.map((r) => (
                  <tr key={r.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="font-semibold text-slate-900">{r.institutionName}</div>
                      {r.finessAccreditation && <div className="text-xs text-slate-400">FINESS: {r.finessAccreditation}</div>}
                    </td>
                    <td className="px-6 py-4">
                      <div className="text-slate-700">{r.contactPersonName}</div>
                      <div className="text-xs text-slate-500">{r.contactEmail}</div>
                    </td>
                    <td className="px-6 py-4 text-slate-500">{new Date(r.createdAt).toLocaleDateString('fr-FR')}</td>
                    <td className="px-6 py-4">
                      <button onClick={() => handleViewDocument(r.id)} className="inline-flex items-center text-xs font-medium text-emerald-600 hover:text-emerald-700 hover:underline">
                        <ExternalLink className="w-3 h-3 mr-1" /> Voir le document
                      </button>
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={r.status} />
                    </td>
                    <td className="px-6 py-4 text-right space-x-2">
                      {r.status === 'PENDING' && (
                        <>
                          <button
                            onClick={() => handleApprove(r.id)}
                            disabled={processingId === r.id}
                            className="inline-flex items-center justify-center p-2 bg-emerald-100 text-emerald-700 rounded-lg hover:bg-emerald-200 transition disabled:opacity-50"
                            title="Valider et créer le compte"
                          >
                            {processingId === r.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                          </button>
                          <button
                            onClick={() => handleReject(r.id)}
                            disabled={processingId === r.id}
                            className="inline-flex items-center justify-center p-2 bg-rose-100 text-rose-700 rounded-lg hover:bg-rose-200 transition disabled:opacity-50"
                            title="Rejeter"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </>
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
