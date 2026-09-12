import { useEffect, useState } from 'react';
import { Banknote, Clock, CheckCircle2, Users, Inbox, Loader2, Receipt } from 'lucide-react';
import {
  financeService, formatMoney, formatDate,
  type PartnerFinancialSummary, type PartnerPaymentRow, type PayoutDto,
} from '../../api/financeService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard } from '../../components/common/StatCard';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Toast, type ToastType } from '../../components/common/Toast';
import { DocumentButton } from '../../components/documents/DocumentButton';

/**
 * Suivi financier de l'établissement partenaire.
 *
 * Ne présente que la part nette revenant au CHU : ni le montant brut réglé par le médecin,
 * ni la commission d'agence, ni les frais de dossier de la plateforme. Cette restriction est
 * appliquée par le serveur (`/partner/payments` ne transporte tout simplement pas ces
 * champs) — l'écran n'a rien à masquer.
 */
export function PartnerFinancePage() {
  const [summary, setSummary] = useState<PartnerFinancialSummary | null>(null);
  const [payments, setPayments] = useState<PartnerPaymentRow[]>([]);
  const [payouts, setPayouts] = useState<PayoutDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  useEffect(() => {
    Promise.all([
      financeService.getMySummary(),
      financeService.getMyPayments(),
      financeService.getMyPayouts(),
    ])
      .then(([s, p, o]) => { setSummary(s); setPayments(p); setPayouts(o); })
      .catch((err) => {
        console.error('Erreur lors du chargement des données financières', err);
        setToast({ message: 'Impossible de charger vos données financières.', type: 'error' });
      })
      .finally(() => setIsLoading(false));
  }, []);

  const lastPayout = payouts.find((p) => p.status === 'PAID');

  if (isLoading) {
    return (
      <div className="p-8">
        <PageHeader title="Mes revenus" subtitle="Chargement..." />
        <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
          <Loader2 className="w-6 h-6 animate-spin mx-auto text-brand-green" />
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <PageHeader
        title="Mes revenus"
        subtitle="Inscriptions réglées, part vous revenant et virements reçus d'Optimi Santé"
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard
          label="Inscriptions réglées" value={payments.length} icon={Users} tone="blue"
          sub="Candidats ayant payé leur formation"
        />
        <StatCard
          label="En attente de virement" value={formatMoney(summary?.pendingAmount)}
          icon={Clock} tone={summary && summary.pendingAmount > 0 ? 'amber' : 'slate'}
          sub="Part vous revenant, non encore ordonnancée"
        />
        <StatCard
          label="Virement en préparation" value={formatMoney(summary?.awaitingTransfer)}
          icon={Banknote} tone="purple" sub="Ordonnancé, en cours d'exécution"
        />
        <StatCard
          label="Total reçu" value={formatMoney(summary?.paidOut)} icon={CheckCircle2} tone="emerald"
          sub={lastPayout ? `Dernier virement le ${formatDate(lastPayout.paidAt)}` : 'Aucun virement à ce jour'}
        />
      </div>

      {/* Inscriptions réglées */}
      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden mb-8">
        <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-brand-dark">Candidats inscrits et réglés</h2>
          <span className="text-xs text-slate-500">Montants nets vous revenant</span>
        </div>
        {payments.length === 0 ? (
          <EmptyState
            icon={Inbox} title="Aucune inscription réglée"
            description="Les candidats apparaîtront ici dès qu'ils auront réglé leur formation."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Médecin</th>
                  <th className="px-4 py-3 font-medium">Formation</th>
                  <th className="px-4 py-3 font-medium">Début de session</th>
                  <th className="px-4 py-3 font-medium">Réglé le</th>
                  <th className="px-4 py-3 font-medium text-right">Part vous revenant</th>
                  <th className="px-4 py-3 font-medium">Virement</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {payments.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-4 py-3 font-medium text-brand-dark">{p.doctorName}</td>
                    <td className="px-4 py-3 text-slate-700">{p.trainingTitle}</td>
                    <td className="px-4 py-3 text-slate-500 whitespace-nowrap">
                      {formatDate(p.sessionStartDate)}
                    </td>
                    <td className="px-4 py-3 text-slate-500 whitespace-nowrap">{formatDate(p.paidAt)}</td>
                    <td className="px-4 py-3 text-right tabular-nums font-semibold text-brand-dark whitespace-nowrap">
                      {formatMoney(p.netAmount, p.currency)}
                    </td>
                    <td className="px-4 py-3">
                      {p.payoutReference ? (
                        <span className="font-mono text-xs text-slate-600">{p.payoutReference}</span>
                      ) : (
                        <span className="text-xs text-amber-700">À venir</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Relevés de virement */}
      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-200">
          <h2 className="text-sm font-semibold text-brand-dark">Relevés de virement</h2>
        </div>
        {payouts.length === 0 ? (
          <EmptyState
            icon={Receipt} title="Aucun virement à ce jour"
            description="Vos relevés apparaîtront ici dès le premier reversement d'Optimi Santé."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Référence</th>
                  <th className="px-4 py-3 font-medium">Période couverte</th>
                  <th className="px-4 py-3 font-medium text-right">Montant viré</th>
                  <th className="px-4 py-3 font-medium">Statut</th>
                  <th className="px-4 py-3 font-medium">Date du virement</th>
                  <th className="px-4 py-3 font-medium text-right">Relevé</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {payouts.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-4 py-3 font-mono text-xs text-brand-dark">{p.reference}</td>
                    <td className="px-4 py-3 text-slate-500 whitespace-nowrap">
                      {p.periodStart || p.periodEnd
                        ? `${formatDate(p.periodStart)} → ${formatDate(p.periodEnd)}`
                        : 'Toutes inscriptions réglées'}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums font-semibold text-brand-dark">
                      {formatMoney(p.totalAmount, p.currency)}
                    </td>
                    <td className="px-4 py-3"><StatusBadge status={p.status} /></td>
                    <td className="px-4 py-3 text-slate-500">{formatDate(p.paidAt)}</td>
                    <td className="px-4 py-3 text-right">
                      {p.statementAvailable ? (
                        <DocumentButton
                          libelle="Télécharger"
                          obtenirLien={() => financeService.getStatementUrl(p.id)}
                          messageIndisponible="Ce relevé n'est pas encore disponible."
                        />
                      ) : (
                        <span className="text-xs text-slate-400">Bientôt disponible</span>
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
