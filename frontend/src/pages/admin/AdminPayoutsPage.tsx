import { useCallback, useEffect, useState } from 'react';
import {
  Wallet, TrendingUp, Clock, Banknote, Search, Inbox, Loader2,
} from 'lucide-react';
import {
  financeService, formatMoney, formatDate, PAYMENT_TYPE_LABELS,
  type AdminPaymentRow, type AdminFinanceKpis, type PartnerDueRow,
} from '../../api/financeService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard } from '../../components/common/StatCard';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Toast, type ToastType } from '../../components/common/Toast';
import { ReversementsChuPanel } from '../../components/finance/ReversementsChuPanel';

type Tab = 'registre' | 'reversements';

export function AdminPayoutsPage() {
  const [tab, setTab] = useState<Tab>('registre');
  const [kpis, setKpis] = useState<AdminFinanceKpis | null>(null);
  const [payments, setPayments] = useState<AdminPaymentRow[]>([]);
  const [partners, setPartners] = useState<PartnerDueRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const fetchAll = useCallback(async () => {
    try {
      const [k, p, d] = await Promise.all([
        financeService.getKpis(),
        financeService.getAllPayments(),
        financeService.getPartnersDue(),
      ]);
      setKpis(k);
      setPayments(p);
      setPartners(d);
    } catch (err) {
      console.error('Erreur lors du chargement des données financières', err);
      setToast({ message: 'Impossible de charger les données financières.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  const notify = useCallback((message: string, type: ToastType) => setToast({ message, type }), []);

  const filtered = payments.filter((p) => {
    if (typeFilter && p.paymentType !== typeFilter) return false;
    if (statusFilter && p.status !== statusFilter) return false;
    if (!search) return true;
    const needle = search.toLowerCase();
    return [p.doctorName, p.trainingTitle, p.partnerName].some(
      (v) => v?.toLowerCase().includes(needle),
    );
  });

  const tabClass = (value: Tab) =>
    `px-4 py-2 text-sm font-medium rounded-lg transition-all ${
      tab === value ? 'bg-white text-brand shadow-sm ring-1 ring-slate-200' : 'text-slate-500 hover:text-slate-700'
    }`;

  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <PageHeader
        title="Finance & reversements"
        subtitle="Registre des encaissements, commission d'agence et virements aux établissements partenaires"
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard
          label="Total encaissé" value={formatMoney(kpis?.totalCollected)} icon={Wallet} tone="blue"
          sub={kpis ? `${kpis.paidTransactions} transaction(s)` : undefined}
        />
        <StatCard
          label="Commission Optimi Santé" value={formatMoney(kpis?.totalCommission)}
          icon={TrendingUp} tone="emerald" sub="Revenu plateforme"
        />
        <StatCard
          label="À reverser aux CHU" value={formatMoney(kpis?.awaitingPayout)}
          icon={Clock} tone={kpis && kpis.awaitingPayout > 0 ? 'amber' : 'slate'}
          sub="Non encore ordonnancé"
        />
        <StatCard
          label="Déjà viré" value={formatMoney(kpis?.totalPaidOut)} icon={Banknote} tone="purple"
        />
      </div>

      <div className="flex gap-1 p-1 bg-slate-100 rounded-xl mb-6 w-fit">
        <button type="button" onClick={() => setTab('registre')} className={tabClass('registre')}>
          Registre des paiements
        </button>
        <button type="button" onClick={() => setTab('reversements')} className={tabClass('reversements')}>
          Reversements CHU
        </button>
      </div>

      {isLoading ? (
        <div className="bg-white rounded-xl border border-slate-200 p-12 text-center text-slate-500 text-sm">
          <Loader2 className="w-6 h-6 animate-spin mx-auto mb-3 text-brand" />
          Chargement du registre...
        </div>
      ) : tab === 'registre' ? (
        <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
          <div className="p-4 border-b border-slate-200 flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text" value={search} onChange={(e) => setSearch(e.target.value)}
                placeholder="Médecin, formation ou établissement..."
                className="w-full pl-9 rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand focus:ring-2 focus:ring-brand/20 focus:outline-none"
              />
            </div>
            <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}
              className="rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand focus:outline-none">
              <option value="">Tous les types</option>
              <option value="DOSSIER_FEE">Frais de dossier</option>
              <option value="TUITION_FEE">Frais de formation</option>
            </select>
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}
              className="rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand focus:outline-none">
              <option value="">Tous les statuts</option>
              <option value="PAID">Payé</option>
              <option value="PENDING">En attente</option>
              <option value="REFUNDED">Remboursé</option>
              <option value="FAILED">Échec</option>
            </select>
          </div>

          {filtered.length === 0 ? (
            <EmptyState
              icon={Inbox} title="Aucune transaction"
              description="Les encaissements apparaîtront ici dès le premier règlement."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-4 py-3 font-medium">Date</th>
                    <th className="px-4 py-3 font-medium">Médecin</th>
                    <th className="px-4 py-3 font-medium">Formation / CHU</th>
                    <th className="px-4 py-3 font-medium">Type</th>
                    <th className="px-4 py-3 font-medium text-right">Brut</th>
                    <th className="px-4 py-3 font-medium text-right">Commission</th>
                    <th className="px-4 py-3 font-medium text-right">Part CHU</th>
                    <th className="px-4 py-3 font-medium">Statut</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filtered.map((p) => (
                    <tr key={p.id} className="hover:bg-slate-50 transition-colors">
                      <td className="px-4 py-3 text-slate-500 whitespace-nowrap">
                        {formatDate(p.paidAt || p.createdAt)}
                      </td>
                      <td className="px-4 py-3">
                        <div className="font-medium text-brand-dark">{p.doctorName}</div>
                        <div className="text-xs text-slate-500">{p.doctorEmail}</div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="text-slate-700">{p.trainingTitle}</div>
                        {p.partnerName && <div className="text-xs text-slate-500">{p.partnerName}</div>}
                      </td>
                      <td className="px-4 py-3 text-slate-600 whitespace-nowrap">
                        {PAYMENT_TYPE_LABELS[p.paymentType] ?? p.paymentType}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums font-medium text-brand-dark whitespace-nowrap">
                        {formatMoney(p.grossAmount, p.currency)}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums text-emerald-700 whitespace-nowrap">
                        {formatMoney(p.commissionAmount, p.currency)}
                        <span className="text-xs text-slate-400 ml-1">({p.commissionRate}%)</span>
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums text-slate-600 whitespace-nowrap">
                        {formatMoney(p.partnerPayoutAmount, p.currency)}
                        {p.payoutReference && (
                          <div className="text-xs text-slate-400">{p.payoutReference}</div>
                        )}
                      </td>
                      <td className="px-4 py-3"><StatusBadge status={p.status} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : (
        <ReversementsChuPanel partners={partners} onDataChanged={fetchAll} notify={notify} />
      )}

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
