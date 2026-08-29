import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Loader2, GraduationCap, ChevronRight } from 'lucide-react';
import { adminEnrollmentService, type EnrollmentDetailDto } from '../../api/adminEnrollmentService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Avatar } from '../../components/common/Avatar';
import { Stepper, ENROLLMENT_STEPS } from '../../components/common/Stepper';

const FAILED_STATUSES = new Set(['REJECTED', 'CANCELLED']);

export function AdminEnrollmentsListPage() {
  const [enrollments, setEnrollments] = useState<EnrollmentDetailDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchEnrollments = async () => {
      try {
        const data = await adminEnrollmentService.listEnrollments();
        setEnrollments(data.sort((a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime()));
      } catch (error) {
        console.error('Failed to fetch enrollments', error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchEnrollments();
  }, []);

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader title="Dossiers CHU / Mobilité" subtitle="Suivi des candidatures de médecins aux formations CHU." />

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center items-center">
            <Loader2 className="w-8 h-8 text-emerald-600 animate-spin" />
          </div>
        ) : enrollments.length === 0 ? (
          <EmptyState icon={GraduationCap} title="Aucun dossier pour le moment." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Médecin</th>
                  <th className="px-6 py-4">Formation</th>
                  <th className="px-6 py-4">Soumis le</th>
                  <th className="px-6 py-4">Parcours</th>
                  <th className="px-6 py-4">Statut</th>
                  <th className="px-6 py-4"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {enrollments.map((e) => {
                  const isFailed = FAILED_STATUSES.has(e.status);
                  return (
                    <tr key={e.id} className="hover:bg-slate-50/50 transition-colors">
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <Avatar name={e.doctorName || e.doctorEmail || '?'} size="sm" />
                          <div className="min-w-0">
                            <div className="font-semibold text-slate-900 truncate">{e.doctorName || '—'}</div>
                            <div className="text-xs text-slate-500 truncate">{e.doctorEmail}</div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 font-medium text-slate-700">{e.trainingTitle}</td>
                      <td className="px-6 py-4 text-slate-500">
                        {new Date(e.submittedAt).toLocaleDateString('fr-FR')}
                      </td>
                      <td className="px-6 py-4">
                        {!isFailed && <Stepper steps={ENROLLMENT_STEPS} currentStepId={e.status} size="compact" />}
                      </td>
                      <td className="px-6 py-4">
                        <StatusBadge status={e.status} />
                      </td>
                      <td className="px-6 py-4 text-right">
                        <Link
                          to={`/admin/enrollments/${e.id}`}
                          className="inline-flex items-center text-xs font-bold text-brand-green hover:underline"
                        >
                          Voir le dossier <ChevronRight className="w-3.5 h-3.5 ml-1" />
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
