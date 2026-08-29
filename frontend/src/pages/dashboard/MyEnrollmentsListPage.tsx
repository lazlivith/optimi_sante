import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Loader2, FileStack, ChevronRight, GraduationCap, Stethoscope, CheckCircle2, Clock } from 'lucide-react';
import { enrollmentService, type EnrollmentDetailDto } from '../../api/enrollmentService';
import { useAuth } from '../../context/AuthContext';
import { HeroBanner } from '../../components/common/HeroBanner';
import { StatCard } from '../../components/common/StatCard';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Stepper, ENROLLMENT_STEPS } from '../../components/common/Stepper';

const FAILED_STATUSES = new Set(['REJECTED', 'CANCELLED']);

export function MyEnrollmentsListPage() {
  const { user } = useAuth();
  const [enrollments, setEnrollments] = useState<EnrollmentDetailDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchEnrollments = async () => {
      try {
        const data = await enrollmentService.getMyEnrollments();
        setEnrollments(data);
      } catch (error) {
        console.error('Failed to fetch my enrollments', error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchEnrollments();
  }, []);

  const readyCount = enrollments.filter(e => e.status === 'READY_TO_START').length;
  const inProgressCount = enrollments.filter(e => !FAILED_STATUSES.has(e.status) && e.status !== 'READY_TO_START').length;

  const displayName = user?.firstName ? `Dr. ${user.firstName}` : user?.email?.split('@')[0];

  return (
    <div className="p-8 max-w-5xl">
      <HeroBanner
        eyebrow="Espace Médecin"
        title={`Bienvenue, ${displayName}`}
        subtitle="Suivez ici l'avancement de vos candidatures de mobilité vers les CHU partenaires."
        icon={Stethoscope}
      />

      {!isLoading && enrollments.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 mb-8">
          <StatCard label="Dossiers au total" value={enrollments.length} icon={FileStack} tone="slate" />
          <StatCard label="En cours" value={inProgressCount} icon={Clock} tone="blue" />
          <StatCard label="Prêts à démarrer" value={readyCount} icon={CheckCircle2} tone="emerald" />
        </div>
      )}

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center items-center">
            <Loader2 className="w-8 h-8 text-emerald-600 animate-spin" />
          </div>
        ) : enrollments.length === 0 ? (
          <EmptyState
            icon={FileStack}
            title="Aucun dossier pour le moment."
            action={
              <Link to="/formations" className="inline-flex items-center text-brand-green font-semibold hover:underline">
                <GraduationCap className="w-4 h-4 mr-1.5" /> Découvrir les formations
              </Link>
            }
          />
        ) : (
          <div className="divide-y divide-slate-100">
            {enrollments.map((e) => {
              const isFailed = FAILED_STATUSES.has(e.status);
              return (
                <Link
                  key={e.id}
                  to={`/doctor/enrollments/${e.id}`}
                  className="flex items-center justify-between px-6 py-5 hover:bg-slate-50/50 transition-colors gap-6"
                >
                  <div className="min-w-0">
                    <div className="font-semibold text-slate-900 truncate">{e.trainingTitle}</div>
                    <div className="text-xs text-slate-500 mt-1">
                      Soumis le {new Date(e.submittedAt).toLocaleDateString('fr-FR')}
                    </div>
                  </div>
                  <div className="flex items-center gap-5 shrink-0">
                    {!isFailed && (
                      <Stepper steps={ENROLLMENT_STEPS} currentStepId={e.status} size="compact" />
                    )}
                    <StatusBadge status={e.status} />
                    <ChevronRight className="w-4 h-4 text-slate-400" />
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
