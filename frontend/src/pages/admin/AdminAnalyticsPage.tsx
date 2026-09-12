import { useCallback, useEffect, useState } from 'react';
import {
  BarChart3, Download, Loader2, RefreshCw, TrendingUp, Users, GraduationCap,
  ShoppingBag, Receipt, Percent, UserPlus, FileText,
} from 'lucide-react';
import {
  adminAnalyticsService,
  type AnalyticsOverview,
  type FunnelData,
  type LabelValue,
  type TimeseriesPoint,
} from '../../api/adminAnalyticsService';
import { downloadApiFile } from '../../lib/download';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard, type StatTone } from '../../components/common/StatCard';
import { EmptyState } from '../../components/common/EmptyState';
import { CategoryBars, ChartCard, FunnelBars, TrendArea } from '../../components/common/Charts';

const euro = (v: number) => `${Number(v).toLocaleString('fr-FR', { maximumFractionDigits: 0 })} €`;
const RANGES = [30, 90, 180, 365];

function ExportButton({ dataset, days, limit }: { dataset: string; days?: number; limit?: number }) {
  const [busy, setBusy] = useState(false);
  return (
    <button
      onClick={async () => {
        setBusy(true);
        try {
          await downloadApiFile(adminAnalyticsService.exportUrl(dataset, days ?? 90, limit ?? 10), `${dataset}.csv`);
        } finally {
          setBusy(false);
        }
      }}
      disabled={busy}
      className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold text-slate-600 border border-slate-200 hover:bg-slate-50 disabled:opacity-60"
    >
      {busy ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Download className="w-3.5 h-3.5" />}
      CSV
    </button>
  );
}

export function AdminAnalyticsPage() {
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
  const [revenue, setRevenue] = useState<TimeseriesPoint[]>([]);
  const [byCategory, setByCategory] = useState<LabelValue[]>([]);
  const [byStatus, setByStatus] = useState<LabelValue[]>([]);
  const [topTrainings, setTopTrainings] = useState<LabelValue[]>([]);
  const [segments, setSegments] = useState<LabelValue[]>([]);
  const [funnel, setFunnel] = useState<FunnelData | null>(null);
  const [days, setDays] = useState(90);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const fetchAll = useCallback(async (windowDays: number) => {
    const [o, r, c, s, t, seg, f] = await Promise.all([
      adminAnalyticsService.getOverview(),
      adminAnalyticsService.getRevenueTimeseries(windowDays),
      adminAnalyticsService.getSalesByCategory(),
      adminAnalyticsService.getEnrollmentsByStatus(),
      adminAnalyticsService.getTopTrainings(10),
      adminAnalyticsService.getSegments(),
      adminAnalyticsService.getAcquisitionFunnel(),
    ]);
    setOverview(o);
    setRevenue(r);
    setByCategory(c);
    setByStatus(s);
    setTopTrainings(t);
    setSegments(seg);
    setFunnel(f);
  }, []);

  useEffect(() => {
    fetchAll(days).finally(() => setIsLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const refresh = async (windowDays = days) => {
    setIsRefreshing(true);
    try {
      await fetchAll(windowDays);
    } finally {
      setIsRefreshing(false);
    }
  };

  const tiles = overview
    ? [
        { label: 'Revenus totaux', value: euro(overview.revenueTotal), sub: `${overview.ordersPaid} commandes payées`, icon: TrendingUp, tone: 'emerald' as StatTone },
        { label: 'Revenus ce mois', value: euro(overview.revenueThisMonth), sub: 'Depuis le 1er du mois', icon: ShoppingBag, tone: 'blue' as StatTone },
        { label: 'Panier moyen', value: euro(overview.averageOrderValue), sub: `${overview.payingCustomers} clients payants`, icon: Receipt, tone: 'purple' as StatTone },
        { label: 'Devis B2B', value: overview.quotesTotal, sub: 'Total historique', icon: FileText, tone: 'amber' as StatTone },
        { label: 'Comptes actifs', value: overview.activeUsers, sub: `+${overview.newUsers30d} sur 30 j`, icon: Users, tone: 'blue' as StatTone },
        { label: 'Nouveaux leads (30 j)', value: overview.leads30d, sub: 'Brochures téléchargées', icon: UserPlus, tone: 'slate' as StatTone },
        { label: 'Inscriptions actives', value: overview.enrollmentsActive, sub: `${overview.enrollmentsTotal} au total`, icon: GraduationCap, tone: 'emerald' as StatTone },
        { label: 'Conversion candidature', value: `${overview.applicationToEnrollmentRate} %`, sub: 'Candidature payée → inscription', icon: Percent, tone: 'purple' as StatTone },
      ]
    : [];

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Analytics"
        subtitle="Vue direction : revenus, acquisition, mobilité. Chaque bloc est exportable en CSV."
        actions={
          <button
            onClick={() => refresh()}
            disabled={isRefreshing}
            className="inline-flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin' : ''}`} />
            Rafraîchir
          </button>
        }
      />

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="w-8 h-8 text-brand animate-spin" />
        </div>
      ) : !overview ? (
        <EmptyState icon={BarChart3} title="Aucune donnée analytique" description="Les indicateurs apparaîtront dès les premières ventes et inscriptions." />
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {tiles.map((t) => (
              <StatCard key={t.label} label={t.label} value={t.value} icon={t.icon} tone={t.tone} sub={t.sub} />
            ))}
          </div>

          <ChartCard
            title="Revenu quotidien"
            subtitle={`Commandes payées, ${days} derniers jours`}
            actions={
              <div className="flex items-center gap-2">
                <select
                  value={days}
                  onChange={(e) => {
                    const d = Number(e.target.value);
                    setDays(d);
                    refresh(d);
                  }}
                  className="text-xs border border-slate-200 rounded-lg px-2 py-1.5 text-slate-600"
                >
                  {RANGES.map((r) => (
                    <option key={r} value={r}>{r} j</option>
                  ))}
                </select>
                <ExportButton dataset="revenue-timeseries" days={days} />
              </div>
            }
          >
            <TrendArea data={revenue} mode="revenue" />
          </ChartCard>

          <div className="grid lg:grid-cols-2 gap-6">
            <ChartCard title="Ventes par catégorie" subtitle="Revenu payé" actions={<ExportButton dataset="sales-by-category" />}>
              {byCategory.length ? (
                <CategoryBars data={byCategory.map((d) => ({ label: d.label, value: d.value }))} unit="euro" colorByCategory />
              ) : (
                <EmptyState icon={ShoppingBag} title="Aucune vente" description="Pas encore de commande payée." />
              )}
            </ChartCard>

            <ChartCard title="Revenu par segment" subtitle="B2B vs B2C" actions={<ExportButton dataset="segments" />}>
              {segments.length ? (
                <CategoryBars data={segments.map((d) => ({ label: d.label, value: d.value }))} unit="euro" colorByCategory />
              ) : (
                <EmptyState icon={ShoppingBag} title="Aucune vente" description="Pas encore de commande payée." />
              )}
            </ChartCard>
          </div>

          <ChartCard
            title="Entonnoir d'acquisition mobilité"
            subtitle="De la brochure téléchargée au visa obtenu"
            actions={<ExportButton dataset="acquisition-funnel" />}
          >
            {funnel && funnel.steps.length ? (
              <FunnelBars steps={funnel.steps} />
            ) : (
              <EmptyState icon={BarChart3} title="Aucune donnée" description="L'entonnoir se remplit avec les premiers leads." />
            )}
          </ChartCard>

          <div className="grid lg:grid-cols-2 gap-6">
            <ChartCard title="Inscriptions par statut" subtitle="Pipeline des dossiers de mobilité" actions={<ExportButton dataset="enrollments-by-status" />}>
              {byStatus.length ? (
                <CategoryBars data={byStatus.map((d) => ({ label: d.label, value: d.value }))} colorByCategory />
              ) : (
                <EmptyState icon={GraduationCap} title="Aucune inscription" description="Pas encore de dossier." />
              )}
            </ChartCard>

            <ChartCard title="Formations les plus demandées" subtitle="Nombre d'inscriptions" actions={<ExportButton dataset="top-trainings" limit={10} />}>
              {topTrainings.length ? (
                <CategoryBars data={topTrainings.map((d) => ({ label: d.label, value: d.value }))} />
              ) : (
                <EmptyState icon={GraduationCap} title="Aucune formation" description="Aucune formation avec inscription." />
              )}
            </ChartCard>
          </div>
        </div>
      )}
    </div>
  );
}
