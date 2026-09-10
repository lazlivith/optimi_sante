import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  TrendingUp, ShoppingBag, Receipt, FileText, ArrowRight, CheckCircle2, Loader2,
} from 'lucide-react';
import { adminFinanceService, type FinanceSummaryDto } from '../../api/adminFinanceService';
import { adminOrderService } from '../../api/adminOrderService';
import { HeroBanner } from '../../components/common/HeroBanner';
import { StatCard } from '../../components/common/StatCard';
import { EmptyState } from '../../components/common/EmptyState';
import { formatMoney } from '../../api/financeService';

/**
 * Tableau de bord du négoce.
 *
 * N'interroge que les services de son univers : depuis la scission (V30), un appel vers la
 * mobilité renverrait 403 et l'écran se remplirait d'erreurs pour un droit qu'il n'a pas
 * — et n'a pas à avoir.
 */
export function AdminSalesDashboardPage() {
  const [summary, setSummary] = useState<FinanceSummaryDto | null>(null);
  const [pendingQuotes, setPendingQuotes] = useState(0);
  const [unpaidTransfers, setUnpaidTransfers] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      adminFinanceService.getSummary(),
      adminOrderService.getQuotes(0, 100),
      adminOrderService.getAllOrders(0, 100),
    ])
      .then(([s, quotes, orders]) => {
        setSummary(s);
        setPendingQuotes(quotes.content.filter((q: any) => q.paymentStatus === 'PENDING_APPROVAL').length);
        setUnpaidTransfers(orders.content.filter(
          (o: any) => o.paymentMethod === 'BANK_TRANSFER' && o.paymentStatus === 'UNPAID').length);
      })
      .catch((err) => console.error('Erreur lors du chargement du tableau de bord ventes', err))
      .finally(() => setIsLoading(false));
  }, []);

  const actions = [
    {
      count: pendingQuotes,
      label: 'devis B2B en attente de réponse',
      to: '/admin/quotes',
    },
    {
      count: unpaidTransfers,
      label: 'virements bancaires à confirmer',
      to: '/admin/orders',
    },
  ].filter((a) => a.count > 0);

  return (
    <div className="p-8">
      <HeroBanner
        title="Négoce"
        subtitle="Chiffre d'affaires, commandes et catalogue de la boutique"
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 my-8">
        <StatCard
          label="Chiffre d'affaires" value={formatMoney(summary?.totalRevenue)}
          icon={TrendingUp} tone="emerald"
          sub={summary ? `${formatMoney(summary.revenueThisMonth)} ce mois-ci` : undefined}
          to="/admin/finance"
        />
        <StatCard
          label="Commandes" value={summary?.ordersCount} icon={ShoppingBag} tone="blue"
          sub={summary ? `${summary.ordersToday} aujourd'hui` : undefined}
          to="/admin/orders"
        />
        <StatCard
          label="Panier moyen" value={formatMoney(summary?.averageOrderValue)}
          icon={Receipt} tone="purple"
        />
        <StatCard
          label="Devis en attente" value={pendingQuotes} icon={FileText}
          tone={pendingQuotes > 0 ? 'amber' : 'slate'}
          to="/admin/quotes"
        />
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-200">
          <h2 className="text-sm font-semibold text-brand-dark">Actions en attente</h2>
        </div>
        {isLoading ? (
          <div className="p-10 text-center">
            <Loader2 className="w-5 h-5 animate-spin mx-auto text-brand-green" />
          </div>
        ) : actions.length === 0 ? (
          <EmptyState
            icon={CheckCircle2} title="Rien à traiter"
            description="Aucun devis en attente ni virement à confirmer."
          />
        ) : (
          <ul className="divide-y divide-slate-100">
            {actions.map((a) => (
              <li key={a.to}>
                <Link
                  to={a.to}
                  className="flex items-center justify-between px-5 py-4 hover:bg-slate-50 transition-colors"
                >
                  <span className="text-slate-700">
                    <span className="font-bold text-brand-dark">{a.count}</span> {a.label}
                  </span>
                  <ArrowRight className="w-4 h-4 text-slate-400" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
