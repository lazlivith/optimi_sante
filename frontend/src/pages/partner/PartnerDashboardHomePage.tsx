import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Users, CalendarPlus, Loader2, TrendingUp, XCircle, Building2, ArrowRight } from 'lucide-react';
import { partnerService, type EnrollmentDto } from '../../api/partnerService';
import { useAuth } from '../../context/AuthContext';
import { HeroBanner } from '../../components/common/HeroBanner';
import { StatCard } from '../../components/common/StatCard';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Avatar } from '../../components/common/Avatar';

const RECENT_ENROLLMENTS_COUNT = 5;

export function PartnerDashboardHomePage() {
  const { user } = useAuth();
  const [enrollments, setEnrollments] = useState<EnrollmentDto[]>([]);
  const [sessionsCount, setSessionsCount] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const [enrollmentsData, sessions] = await Promise.all([
          partnerService.getEnrollments(),
          partnerService.getMySessions(),
        ]);
        setEnrollments(enrollmentsData);
        setSessionsCount(sessions.filter(s => s.status === 'OPEN').length);
      } catch (error) {
        console.error('Failed to fetch partner stats', error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchStats();
  }, []);

  const pendingCount = enrollments.filter(e => e.status === 'PENDING_REVIEW').length;
  const rejectedCount = enrollments.filter(e => e.status === 'REJECTED').length;
  const approvedCount = enrollments.filter(e =>
    ['APPROVED_ACADEMIC', 'APPROVED_ADMINISTRATIVE', 'READY_TO_START'].includes(e.status)
  ).length;
  const decidedCount = approvedCount + rejectedCount;
  const acceptanceRate = decidedCount > 0 ? `${Math.round((approvedCount / decidedCount) * 100)}%` : '—';

  const recentEnrollments = [...enrollments]
    .sort((a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime())
    .slice(0, RECENT_ENROLLMENTS_COUNT);

  const tiles = [
    { label: 'Candidatures en attente', value: pendingCount, icon: Users, to: '/partner/enrollments', tone: 'amber' as const },
    { label: 'Sessions ouvertes', value: sessionsCount, icon: CalendarPlus, to: '/partner/sessions', tone: 'emerald' as const },
    { label: "Taux d'acceptation", value: acceptanceRate, icon: TrendingUp, tone: 'blue' as const },
    { label: 'Candidatures rejetées', value: rejectedCount, icon: XCircle, tone: 'rose' as const },
  ];

  const displayName = user?.companyName || user?.firstName || user?.email?.split('@')[0];

  return (
    <div className="p-8 max-w-6xl">
      <HeroBanner
        eyebrow="Espace Partenaire"
        title={`Bienvenue, ${displayName}`}
        subtitle="Voici un aperçu des candidatures et sessions de formation liées à votre établissement."
        icon={Building2}
      />

      {isLoading ? (
        <div className="flex justify-center py-16"><Loader2 className="w-8 h-8 text-brand-green animate-spin" /></div>
      ) : (
        <div className="space-y-8">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {tiles.map(({ label, value, icon, to, tone }) => (
              <StatCard key={label} label={label} value={value} icon={icon} to={to} tone={tone} />
            ))}
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold text-brand-dark">Dernières candidatures</h2>
                <p className="text-xs text-slate-500 mt-0.5">Les {RECENT_ENROLLMENTS_COUNT} dossiers les plus récents.</p>
              </div>
              <Link
                to="/partner/enrollments"
                className="inline-flex items-center gap-1 text-xs font-bold text-brand-green hover:gap-1.5 transition-all"
              >
                Voir tout <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
            {recentEnrollments.length === 0 ? (
              <EmptyState icon={Users} title="Aucune candidature reçue pour le moment." />
            ) : (
              <div className="divide-y divide-slate-100">
                {recentEnrollments.map((e) => (
                  <div key={e.id} className="px-6 py-4 flex items-center gap-4 hover:bg-slate-50/60 transition-colors">
                    <Avatar name={e.doctorName} size="sm" />
                    <div className="min-w-0 flex-1">
                      <div className="text-sm font-semibold text-brand-dark truncate">{e.doctorName}</div>
                      <div className="text-xs text-slate-500 truncate">{e.trainingTitle || 'Formation non spécifiée'}</div>
                    </div>
                    <div className="flex items-center gap-4 shrink-0">
                      <span className="text-xs text-slate-400 hidden sm:block">{new Date(e.submittedAt).toLocaleDateString('fr-FR')}</span>
                      <StatusBadge status={e.status} />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
