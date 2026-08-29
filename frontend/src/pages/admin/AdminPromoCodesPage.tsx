import { useEffect, useState } from 'react';
import { Loader2, Tag, Plus, X, Power } from 'lucide-react';
import { adminPromoCodeService, type PromoCodeDto, type CreatePromoCodeRequest } from '../../api/adminPromoCodeService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';

const EMPTY_FORM: CreatePromoCodeRequest = {
  code: '', discountType: 'PERCENTAGE', discountValue: 10, minOrderAmount: undefined,
  maxUses: undefined, startsAt: undefined, endsAt: undefined
};

export function AdminPromoCodesPage() {
  const [codes, setCodes] = useState<PromoCodeDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [form, setForm] = useState<CreatePromoCodeRequest>(EMPTY_FORM);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const fetchCodes = async () => {
    setIsLoading(true);
    try {
      setCodes(await adminPromoCodeService.listCodes());
    } catch {
      setToast({ message: 'Impossible de charger les codes promo.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { fetchCodes(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      await adminPromoCodeService.createCode({
        ...form,
        code: form.code.trim().toUpperCase(),
        startsAt: form.startsAt || undefined,
        endsAt: form.endsAt || undefined,
      });
      setToast({ message: 'Code promo créé.', type: 'success' });
      setIsModalOpen(false);
      setForm(EMPTY_FORM);
      fetchCodes();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la création.', type: 'error' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleToggle = async (dto: PromoCodeDto) => {
    try {
      await adminPromoCodeService.setActive(dto.id, !dto.isActive);
      fetchCodes();
    } catch {
      setToast({ message: 'Erreur lors de la mise à jour.', type: 'error' });
    }
  };

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Codes promo"
        subtitle="Codes de réduction utilisables au checkout de la boutique."
        actions={
          <button
            onClick={() => { setForm(EMPTY_FORM); setIsModalOpen(true); }}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors"
          >
            <Plus className="w-4 h-4" /> Nouveau code
          </button>
        }
      />

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center"><Loader2 className="w-8 h-8 text-brand-green animate-spin" /></div>
        ) : codes.length === 0 ? (
          <EmptyState icon={Tag} title="Aucun code promo pour le moment." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Code</th>
                  <th className="px-6 py-4">Remise</th>
                  <th className="px-6 py-4">Utilisation</th>
                  <th className="px-6 py-4">Validité</th>
                  <th className="px-6 py-4">Statut</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {codes.map((c) => (
                  <tr key={c.id} className={`hover:bg-slate-50/50 transition-colors ${!c.isActive ? 'opacity-60' : ''}`}>
                    <td className="px-6 py-4 font-mono font-bold text-brand-dark">{c.code}</td>
                    <td className="px-6 py-4">
                      {c.discountType === 'PERCENTAGE' ? `${c.discountValue}%` : `${c.discountValue.toFixed(2)} €`}
                      {c.minOrderAmount ? <span className="text-xs text-slate-400 block">dès {c.minOrderAmount} €</span> : null}
                    </td>
                    <td className="px-6 py-4 text-slate-600">{c.usedCount}{c.maxUses ? ` / ${c.maxUses}` : ''}</td>
                    <td className="px-6 py-4 text-xs text-slate-500">
                      {c.startsAt ? new Date(c.startsAt).toLocaleDateString('fr-FR') : '—'}
                      {' → '}
                      {c.endsAt ? new Date(c.endsAt).toLocaleDateString('fr-FR') : 'illimité'}
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={c.isActive ? 'ACTIVE' : 'INACTIVE'} />
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => handleToggle(c)}
                        className="inline-flex items-center justify-center p-2 border border-slate-200 rounded-lg text-slate-600 hover:bg-slate-50 transition-colors"
                        title={c.isActive ? 'Désactiver' : 'Réactiver'}
                      >
                        <Power className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg p-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-bold text-slate-900">Nouveau code promo</h2>
              <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-slate-600"><X className="w-5 h-5" /></button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Code</label>
                <input type="text" required value={form.code} onChange={e => setForm({ ...form, code: e.target.value })}
                  placeholder="ex. RENTREE2026" className="w-full rounded-md border-slate-300 border p-2.5 font-mono uppercase" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Type de remise</label>
                  <select value={form.discountType} onChange={e => setForm({ ...form, discountType: e.target.value as 'PERCENTAGE' | 'FIXED_AMOUNT' })}
                    className="w-full rounded-md border-slate-300 border p-2.5">
                    <option value="PERCENTAGE">Pourcentage (%)</option>
                    <option value="FIXED_AMOUNT">Montant fixe (€)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Valeur</label>
                  <input type="number" required min={0} step="0.01" value={form.discountValue}
                    onChange={e => setForm({ ...form, discountValue: parseFloat(e.target.value) || 0 })}
                    className="w-full rounded-md border-slate-300 border p-2.5" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Montant minimum (€)</label>
                  <input type="number" min={0} step="0.01" value={form.minOrderAmount ?? ''}
                    onChange={e => setForm({ ...form, minOrderAmount: e.target.value ? parseFloat(e.target.value) : undefined })}
                    className="w-full rounded-md border-slate-300 border p-2.5" placeholder="Optionnel" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Nombre d'utilisations max</label>
                  <input type="number" min={1} value={form.maxUses ?? ''}
                    onChange={e => setForm({ ...form, maxUses: e.target.value ? parseInt(e.target.value) : undefined })}
                    className="w-full rounded-md border-slate-300 border p-2.5" placeholder="Illimité" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Début (optionnel)</label>
                  <input type="date" value={form.startsAt ? form.startsAt.slice(0, 10) : ''}
                    onChange={e => setForm({ ...form, startsAt: e.target.value ? `${e.target.value}T00:00:00Z` : undefined })}
                    className="w-full rounded-md border-slate-300 border p-2.5" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Fin (optionnel)</label>
                  <input type="date" value={form.endsAt ? form.endsAt.slice(0, 10) : ''}
                    onChange={e => setForm({ ...form, endsAt: e.target.value ? `${e.target.value}T23:59:59Z` : undefined })}
                    className="w-full rounded-md border-slate-300 border p-2.5" />
                </div>
              </div>
              <button type="submit" disabled={isSubmitting} className="w-full py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] disabled:opacity-70 transition-colors">
                {isSubmitting ? 'Création...' : 'Créer le code promo'}
              </button>
            </form>
          </div>
        </div>
      )}

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
