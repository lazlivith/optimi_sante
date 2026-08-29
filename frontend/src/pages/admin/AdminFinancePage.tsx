import { useEffect, useState, useCallback } from 'react';
import { RefreshCw, TrendingUp, ShoppingBag, Calendar, Loader2, Package, Wallet, CalendarDays, Receipt } from 'lucide-react';
import { adminFinanceService, type FinanceSummaryDto, type BestSellerDto, type RecentSaleDto } from '../../api/adminFinanceService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard, type StatTone } from '../../components/common/StatCard';
import { EmptyState } from '../../components/common/EmptyState';

function formatCurrency(amount: number) {
  return `${amount.toFixed(2)} €`;
}

export function AdminFinancePage() {
  const [summary, setSummary] = useState<FinanceSummaryDto | null>(null);
  const [bestSellers, setBestSellers] = useState<BestSellerDto[]>([]);
  const [recentSales, setRecentSales] = useState<RecentSaleDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const fetchAll = useCallback(async () => {
    const [s, b, r] = await Promise.all([
      adminFinanceService.getSummary(),
      adminFinanceService.getBestSellers(8),
      adminFinanceService.getRecentSales(15),
    ]);
    setSummary(s);
    setBestSellers(b);
    setRecentSales(r);
  }, []);

  useEffect(() => {
    fetchAll().finally(() => setIsLoading(false));
  }, [fetchAll]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      await fetchAll();
    } finally {
      setIsRefreshing(false);
    }
  };

  const tiles = summary ? [
    { label: 'Revenus totaux', value: formatCurrency(summary.totalRevenue), sub: `${summary.ordersCount} commande${summary.ordersCount > 1 ? 's' : ''} payée${summary.ordersCount > 1 ? 's' : ''}`, icon: TrendingUp, tone: 'emerald' as StatTone },
    { label: "Revenus aujourd'hui", value: formatCurrency(summary.revenueToday), sub: `${summary.ordersToday} vente${summary.ordersToday > 1 ? 's' : ''} aujourd'hui`, icon: CalendarDays, tone: 'blue' as StatTone },
    { label: 'Revenus ce mois-ci', value: formatCurrency(summary.revenueThisMonth), sub: 'Depuis le 1er du mois', icon: Wallet, tone: 'purple' as StatTone },
    { label: 'Panier moyen', value: formatCurrency(summary.averageOrderValue), sub: 'Sur commandes payées', icon: Receipt, tone: 'amber' as StatTone },
  ] : [];

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Finance"
        subtitle="Ventes, revenus et produits les plus vendus."
        actions={
          <button
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="inline-flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm font-semibold text-slate-700 hover:bg-slate-50 transition-colors disabled:opacity-60"
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin' : ''}`} />
            Rafraîchir
          </button>
        }
      />

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="w-8 h-8 text-brand-green animate-spin" />
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            {tiles.map((tile) => (
              <StatCard key={tile.label} label={tile.label} value={tile.value} icon={tile.icon} tone={tile.tone} sub={tile.sub} />
            ))}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Best sellers */}
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="px-6 py-4 border-b border-slate-100 flex items-center gap-2">
                <Package className="w-4 h-4 text-slate-400" />
                <h2 className="font-bold text-slate-900">Produits les plus vendus</h2>
              </div>
              {bestSellers.length === 0 ? (
                <EmptyState icon={Package} title="Aucune vente enregistrée pour le moment." />
              ) : (
                <ul className="divide-y divide-slate-100">
                  {bestSellers.map((item, i) => (
                    <li key={item.productId} className="px-6 py-3 flex items-center gap-3">
                      <span className="w-6 text-xs font-bold text-slate-400">{i + 1}</span>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-slate-800 truncate">{item.productName}</p>
                        <p className="text-xs text-slate-400">{item.totalQuantitySold} vendu{item.totalQuantitySold > 1 ? 's' : ''}</p>
                      </div>
                      <span className="text-sm font-bold text-slate-900">{formatCurrency(item.totalRevenue)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* Recent sales */}
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="px-6 py-4 border-b border-slate-100 flex items-center gap-2">
                <ShoppingBag className="w-4 h-4 text-slate-400" />
                <h2 className="font-bold text-slate-900">Ventes récentes</h2>
              </div>
              {recentSales.length === 0 ? (
                <EmptyState icon={ShoppingBag} title="Aucune vente enregistrée pour le moment." />
              ) : (
                <ul className="divide-y divide-slate-100">
                  {recentSales.map((sale) => (
                    <li key={sale.orderId} className="px-6 py-3 flex items-center gap-3">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-slate-800 truncate">{sale.orderNumber}</p>
                        <p className="text-xs text-slate-400 truncate">{sale.customerEmail}</p>
                      </div>
                      <div className="text-right shrink-0">
                        <span className="text-sm font-bold text-slate-900 block">{formatCurrency(sale.totalAmount)}</span>
                        <span className="text-[11px] text-slate-400 flex items-center gap-1 justify-end">
                          <Calendar className="w-3 h-3" />
                          {new Date(sale.createdAt).toLocaleDateString('fr-FR')}
                        </span>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
