import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Users, FileText, GraduationCap, Loader2, TrendingUp, ShieldCheck,
  Building2, BookOpen, Landmark, CheckCircle2, ArrowRight,
} from 'lucide-react';
import { adminUserService } from '../../api/adminUserService';
import { adminOrderService } from '../../api/adminOrderService';
import { adminEnrollmentService } from '../../api/adminEnrollmentService';
import { adminFinanceService } from '../../api/adminFinanceService';
import { adminPartnershipService } from '../../api/adminPartnershipService';
import { adminTrainingService } from '../../api/adminTrainingService';
import { HeroBanner } from '../../components/common/HeroBanner';
import { StatCard } from '../../components/common/StatCard';
import { EmptyState } from '../../components/common/EmptyState';

interface Stats {
  totalUsers: number;
  pendingQuotes: number;
  activeEnrollments: number;
  totalRevenue: number;
}

interface Alert {
  label: string;
  count: number;
  icon: typeof FileText;
  to: string;
  actionLabel: string;
}

const ACTIVE_ENROLLMENT_STATUSES = ['PENDING_REVIEW', 'APPROVED_ACADEMIC', 'APPROVED_ADMINISTRATIVE', 'CONVENTION_ISSUED', 'VISA_SUBMITTED'];

export function AdminDashboardPage() {
  const [stats, setStats] = useState<Stats | null>(null);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const [usersPage, quotesPage, enrollments, financeSummary, partnershipRequests, trainings, allOrders] = await Promise.all([
          adminUserService.listUsers(0, 1),
          adminOrderService.getQuotes(0, 100),
          adminEnrollmentService.listEnrollments(),
          adminFinanceService.getSummary(),
          adminPartnershipService.listRequests(),
          adminTrainingService.listTrainings(),
          adminOrderService.getAllOrders(0, 100),
        ]);

        const pendingQuotes = quotesPage.content.filter(q => q.status === 'PENDING').length;
        const pendingPartnerships = partnershipRequests.filter(r => r.status === 'PENDING').length;
        const pendingTrainings = trainings.filter(t => t.approvalStatus === 'PENDING_REVIEW').length;
        const unpaidBankTransfers = allOrders.content.filter(o => o.paymentMethod === 'BANK_TRANSFER' && o.paymentStatus === 'UNPAID').length;

        setStats({
          totalUsers: usersPage.totalElements,
          pendingQuotes,
          activeEnrollments: enrollments.filter(e => ACTIVE_ENROLLMENT_STATUSES.includes(e.status)).length,
          totalRevenue: financeSummary.totalRevenue,
        });

        setAlerts([
          { label: 'Devis B2B en attente', count: pendingQuotes, icon: FileText, to: '/admin/quotes', actionLabel: 'Traiter' },
          { label: 'Virements bancaires non confirmés', count: unpaidBankTransfers, icon: Landmark, to: '/admin/orders', actionLabel: 'Confirmer' },
          { label: 'Formations en attente de validation', count: pendingTrainings, icon: BookOpen, to: '/admin/trainings', actionLabel: 'Valider' },
          { label: 'Demandes de partenariat en attente', count: pendingPartnerships, icon: Building2, to: '/admin/partnership-requests', actionLabel: 'Examiner' },
        ]);
      } catch (error) {
        console.error('Failed to fetch admin stats', error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchStats();
  }, []);

  const tiles = [
    { label: 'Revenus totaux', value: stats ? `${stats.totalRevenue.toFixed(0)} €` : undefined, icon: TrendingUp, to: '/admin/finance', tone: 'emerald' as const },
    { label: 'Utilisateurs', value: stats?.totalUsers, icon: Users, to: '/admin/users', tone: 'blue' as const },
    { label: 'Devis B2B en attente', value: stats?.pendingQuotes, icon: FileText, to: '/admin/quotes', tone: 'amber' as const },
    { label: 'Dossiers CHU actifs', value: stats?.activeEnrollments, icon: GraduationCap, to: '/admin/enrollments', tone: 'purple' as const },
  ];

  const totalAlerts = alerts.reduce((sum, a) => sum + a.count, 0);

  return (
    <div className="p-8 max-w-6xl">
      <HeroBanner
        eyebrow="Espace Administration"
        title="Tableau de bord"
        subtitle="Vue d'ensemble de la plateforme Optimi Santé — ventes, utilisateurs, dossiers CHU et actions en attente."
        icon={ShieldCheck}
      />

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="w-8 h-8 text-brand-green animate-spin" />
        </div>
      ) : (
        <div className="space-y-8">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {tiles.map(({ label, value, icon, to, tone }) => (
              <StatCard key={label} label={label} value={value} icon={icon} to={to} tone={tone} />
            ))}
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="px-6 py-5 border-b border-slate-100">
              <h2 className="text-base font-bold text-brand-dark">Actions en attente</h2>
              <p className="text-xs text-slate-500 mt-0.5">Ce qui nécessite votre validation aujourd'hui.</p>
            </div>
            {totalAlerts === 0 ? (
              <EmptyState icon={CheckCircle2} title="Tout est à jour, aucune action en attente." />
            ) : (
              <div className="divide-y divide-slate-100">
                {alerts.filter(a => a.count > 0).map((alert) => (
                  <Link
                    key={alert.label}
                    to={alert.to}
                    className="flex items-center justify-between px-6 py-4 hover:bg-slate-50/60 transition-colors group"
                  >
                    <div className="flex items-center gap-4">
                      <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
                        <alert.icon className="w-5 h-5" />
                      </div>
                      <div>
                        <div className="font-semibold text-slate-900">{alert.count} {alert.label.toLowerCase()}</div>
                      </div>
                    </div>
                    <span className="inline-flex items-center gap-1 text-xs font-bold text-brand-green group-hover:gap-1.5 transition-all">
                      {alert.actionLabel} <ArrowRight className="w-3.5 h-3.5" />
                    </span>
                  </Link>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
