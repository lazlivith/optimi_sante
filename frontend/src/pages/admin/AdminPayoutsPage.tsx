import { useCallback, useEffect, useState } from 'react';
import {
  Wallet, TrendingUp, Clock, Banknote, Search, Send, CheckCircle2, Inbox, Loader2, Building2, FileText,
} from 'lucide-react';
import {
  financeService, formatMoney, formatDate, PAYMENT_TYPE_LABELS,
  type AdminPaymentRow, type AdminFinanceKpis, type PartnerDueRow, type PayoutDto,
} from '../../api/financeService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard } from '../../components/common/StatCard';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Toast, type ToastType } from '../../components/common/Toast';

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

  const [selectedPartner, setSelectedPartner] = useState<string>('');
  const [payouts, setPayouts] = useState<PayoutDto[]>([]);
  const [periodStart, setPeriodStart] = useState('');
  const [periodEnd, setPeriodEnd] = useState('');
  const [isWorking, setIsWorking] = useState(false);

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
      if (!selectedPartner && d.length > 0) setSelectedPartner(d[0].partnerProfileId);
    } catch (err) {
      console.error('Erreur lors du chargement des données financières', err);
      setToast({ message: 'Impossible de charger les données financières.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [selectedPartner]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  useEffect(() => {
    if (!selectedPartner) return;
    financeService.getPartnerPayouts(selectedPartner).then(setPayouts).catch(() => setPayouts([]));
  }, [selectedPartner]);

  const partner = partners.find((p) => p.partnerProfileId === selectedPartner);

  const handleGenerate = async () => {
    if (!selectedPartner) return;
    setIsWorking(true);
    try {
      const payout = await financeService.generatePayout(selectedPartner, periodStart, periodEnd);
      setToast({
        message: `Reversement ${payout.reference} généré : ${formatMoney(payout.totalAmount)}.`,
        type: 'success',
      });
      await fetchAll();
      setPayouts(await financeService.getPartnerPayouts(selectedPartner));
    } catch (err: any) {
      console.error('Échec de la génération du reversement', err);
      setToast({
        message: err.response?.data?.message || 'Le reversement n\'a pas pu être généré.',
        type: 'error',
      });
    } finally {
      setIsWorking(false);
    }
  };

  const handleMarkPaid = async (payoutId: string) => {
    setIsWorking(true);
    try {
      await financeService.markPayoutPaid(payoutId);
      setToast({ message: 'Reversement marqué comme viré.', type: 'success' });
      await fetchAll();
      setPayouts(await financeService.getPartnerPayouts(selectedPartner));
    } catch (err: any) {
      setToast({
        message: err.response?.data?.message || 'Action impossible.',
        type: 'error',
      });
    } finally {
      setIsWorking(false);
    }
  };

  const handleStatement = async (payoutId: string) => {
    setIsWorking(true);
    try {
      await financeService.generateStatement(payoutId);
      setToast({ message: 'Relevé généré et mis à disposition du partenaire.', type: 'success' });
      setPayouts(await financeService.getPartnerPayouts(selectedPartner));
    } catch (err: any) {
      console.error('Échec de la génération du relevé', err);
      setToast({
        message: err.response?.data?.message || "Le relevé n'a pas pu être généré.",
        type: 'error',
      });
    } finally {
      setIsWorking(false);
    }
  };

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
      tab === value ? 'bg-white text-brand-green shadow-sm ring-1 ring-slate-200' : 'text-slate-500 hover:text-slate-700'
    }`;

  return (
    <div className="p-8">
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
          <Loader2 className="w-6 h-6 animate-spin mx-auto mb-3 text-brand-green" />
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
                className="w-full pl-9 rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand-green focus:ring-2 focus:ring-brand-green/20 focus:outline-none"
              />
            </div>
            <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}
              className="rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand-green focus:outline-none">
              <option value="">Tous les types</option>
              <option value="DOSSIER_FEE">Frais de dossier</option>
              <option value="TUITION_FEE">Frais de formation</option>
            </select>
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}
              className="rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand-green focus:outline-none">
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
        <div className="space-y-6">
          <div className="bg-white rounded-xl border border-slate-200 p-6">
            <label className="block text-sm font-medium text-slate-700 mb-2">
              Établissement partenaire
            </label>
            <select
              value={selectedPartner} onChange={(e) => setSelectedPartner(e.target.value)}
              className="w-full sm:max-w-md rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand-green focus:outline-none"
            >
              {partners.map((p) => (
                <option key={p.partnerProfileId} value={p.partnerProfileId}>
                  {p.institutionName} — {formatMoney(p.pendingAmount)} à reverser
                </option>
              ))}
            </select>

            {partner && (
              <>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-6">
                  <div className="p-4 rounded-xl bg-amber-50 border border-amber-200">
                    <p className="text-xs font-semibold uppercase tracking-wider text-amber-700">Dû, non ordonnancé</p>
                    <p className="text-2xl font-bold text-amber-900 mt-1 tabular-nums">
                      {formatMoney(partner.pendingAmount)}
                    </p>
                    <p className="text-xs text-amber-700 mt-1">
                      {partner.pendingPaymentsCount} encaissement(s)
                    </p>
                  </div>
                  <div className="p-4 rounded-xl bg-blue-50 border border-blue-200">
                    <p className="text-xs font-semibold uppercase tracking-wider text-blue-700">Virement en attente</p>
                    <p className="text-2xl font-bold text-blue-900 mt-1 tabular-nums">
                      {formatMoney(partner.awaitingTransfer)}
                    </p>
                  </div>
                  <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200">
                    <p className="text-xs font-semibold uppercase tracking-wider text-emerald-700">Déjà viré</p>
                    <p className="text-2xl font-bold text-emerald-900 mt-1 tabular-nums">
                      {formatMoney(partner.paidOut)}
                    </p>
                  </div>
                </div>

                <div className="mt-6 pt-6 border-t border-slate-100 flex flex-col sm:flex-row gap-3 sm:items-end">
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1.5">Début de période</label>
                    <input type="date" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)}
                      className="rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand-green focus:outline-none" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1.5">Fin (exclue)</label>
                    <input type="date" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)}
                      className="rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand-green focus:outline-none" />
                  </div>
                  <button
                    type="button" onClick={handleGenerate}
                    disabled={isWorking || partner.pendingAmount <= 0}
                    className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg bg-brand-green text-white text-sm font-medium hover:bg-[#0f3c35] disabled:opacity-60 transition-colors"
                  >
                    <Send className="w-4 h-4" />
                    Générer un reversement
                  </button>
                  <p className="text-xs text-slate-500 sm:ml-auto sm:max-w-xs">
                    Sans période, tous les encaissements non encore reversés sont regroupés.
                  </p>
                </div>
              </>
            )}
          </div>

          <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-200">
              <h2 className="text-sm font-semibold text-brand-dark">Historique des ordonnancements</h2>
            </div>
            {payouts.length === 0 ? (
              <EmptyState
                icon={Building2} title="Aucun reversement pour cet établissement"
                description="Générez un reversement dès qu'un encaissement est disponible."
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-slate-50 text-left text-xs uppercase tracking-wider text-slate-500">
                    <tr>
                      <th className="px-4 py-3 font-medium">Référence</th>
                      <th className="px-4 py-3 font-medium">Période</th>
                      <th className="px-4 py-3 font-medium text-right">Montant</th>
                      <th className="px-4 py-3 font-medium">Statut</th>
                      <th className="px-4 py-3 font-medium">Viré le</th>
                      <th className="px-4 py-3 font-medium text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {payouts.map((p) => (
                      <tr key={p.id} className="hover:bg-slate-50 transition-colors">
                        <td className="px-4 py-3 font-mono text-xs text-brand-dark">{p.reference}</td>
                        <td className="px-4 py-3 text-slate-500 whitespace-nowrap">
                          {p.periodStart || p.periodEnd
                            ? `${formatDate(p.periodStart)} → ${formatDate(p.periodEnd)}`
                            : 'Tous encaissements'}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums font-medium text-brand-dark">
                          {formatMoney(p.totalAmount, p.currency)}
                        </td>
                        <td className="px-4 py-3"><StatusBadge status={p.status} /></td>
                        <td className="px-4 py-3 text-slate-500">{formatDate(p.paidAt)}</td>
                        <td className="px-4 py-3 text-right">
                          <div className="flex items-center justify-end gap-2">
                            <button
                              type="button" onClick={() => handleStatement(p.id)} disabled={isWorking}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60 transition-colors"
                              title={p.statementAvailable
                                ? 'Régénérer le relevé PDF'
                                : 'Générer le relevé PDF remis au partenaire'}
                            >
                              <FileText className="w-3.5 h-3.5" />
                              {p.statementAvailable ? 'Régénérer' : 'Relevé PDF'}
                            </button>
                            {p.status === 'PENDING' && (
                              <button
                                type="button" onClick={() => handleMarkPaid(p.id)} disabled={isWorking}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60 transition-colors"
                              >
                                <CheckCircle2 className="w-3.5 h-3.5" />
                                Marquer viré
                              </button>
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
        </div>
      )}

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
