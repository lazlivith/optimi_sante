import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  GraduationCap, Building2, BookOpen, Banknote, ArrowRight, CheckCircle2, Loader2,
} from 'lucide-react';
import { adminEnrollmentService } from '../../api/adminEnrollmentService';
import { adminPartnershipService } from '../../api/adminPartnershipService';
import { adminTrainingService } from '../../api/adminTrainingService';
import { financeService, formatMoney } from '../../api/financeService';
import { HeroBanner } from '../../components/common/HeroBanner';
import { StatCard } from '../../components/common/StatCard';
import { EmptyState } from '../../components/common/EmptyState';

/** Étapes où la balle est dans le camp d'OptimiSanté — celles qui appellent une action. */
const AWAITING_US = ['UNDER_OPTIMI_REVIEW'];
/** Dossiers encore en cours, tous acteurs confondus. */
const IN_PROGRESS = [
  'UNDER_OPTIMI_REVIEW', 'ACTION_REQUIRED', 'SUBMITTED_TO_PARTNER',
  'ACCEPTED_BY_PARTNER', 'PENDING_TUITION_FEE', 'CONFIRMED',
  'CONVENTION_ISSUED', 'VISA_SUBMITTED', 'VISA_GRANTED',
];

/**
 * Tableau de bord de la mobilité.
 *
 * Le bloc « Actions en attente » ne remonte que ce que CET administrateur peut réellement
 * traiter : un dossier transmis au CHU ou en attente de paiement du médecin n'est pas de
 * son ressort, l'y afficher créerait une charge fantôme.
 */
export function AdminMobilityDashboardPage() {
  const [awaitingReview, setAwaitingReview] = useState(0);
  const [inProgress, setInProgress] = useState(0);
  const [pendingTrainings, setPendingTrainings] = useState(0);
  const [pendingPartnerships, setPendingPartnerships] = useState(0);
  const [toPayout, setToPayout] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      adminEnrollmentService.listEnrollments(),
      adminTrainingService.listTrainings(),
      adminPartnershipService.listRequests(),
      financeService.getKpis(),
    ])
      .then(([enrollments, trainings, partnerships, kpis]) => {
        setAwaitingReview(enrollments.filter((e: any) => AWAITING_US.includes(e.status)).length);
        setInProgress(enrollments.filter((e: any) => IN_PROGRESS.includes(e.status)).length);
        setPendingTrainings(trainings.filter((t: any) => t.approvalStatus === 'PENDING_REVIEW').length);
        setPendingPartnerships(partnerships.filter((p: any) => p.status === 'PENDING').length);
        setToPayout(kpis.awaitingPayout);
      })
      .catch((err) => console.error('Erreur lors du chargement du tableau de bord mobilité', err))
      .finally(() => setIsLoading(false));
  }, []);

  const actions = [
    { count: awaitingReview, label: 'dossier(s) à pré-qualifier', to: '/admin/enrollments' },
    { count: pendingTrainings, label: 'formation(s) à valider', to: '/admin/trainings' },
    { count: pendingPartnerships, label: 'demande(s) de partenariat à étudier', to: '/admin/partnership-requests' },
  ].filter((a) => a.count > 0);

  return (
    <div className="p-8">
      <HeroBanner
        title="Mobilité"
        subtitle="Candidatures des médecins, formations partenaires et reversements aux CHU"
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 my-8">
        <StatCard
          label="À pré-qualifier" value={awaitingReview} icon={GraduationCap}
          tone={awaitingReview > 0 ? 'amber' : 'slate'}
          sub="En attente de votre revue" to="/admin/enrollments"
        />
        <StatCard
          label="Dossiers en cours" value={inProgress} icon={GraduationCap} tone="blue"
          sub="Toutes étapes confondues" to="/admin/enrollments"
        />
        <StatCard
          label="Formations à valider" value={pendingTrainings} icon={BookOpen}
          tone={pendingTrainings > 0 ? 'amber' : 'slate'} to="/admin/trainings"
        />
        <StatCard
          label="À reverser aux CHU" value={formatMoney(toPayout)} icon={Banknote}
          tone={toPayout ? 'purple' : 'slate'} to="/admin/payouts"
        />
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-200">
          <h2 className="text-sm font-semibold text-brand-dark">Actions en attente</h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Uniquement ce qui dépend de vous — les dossiers chez le CHU ou en attente de
            paiement n'y figurent pas.
          </p>
        </div>
        {isLoading ? (
          <div className="p-10 text-center">
            <Loader2 className="w-5 h-5 animate-spin mx-auto text-brand" />
          </div>
        ) : actions.length === 0 ? (
          <EmptyState
            icon={CheckCircle2} title="Rien à traiter"
            description="Aucun dossier, formation ni partenariat n'attend votre décision."
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

      <div className="mt-4 flex justify-end">
        <Link
          to="/admin/partnership-requests"
          className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-brand-dark transition-colors"
        >
          <Building2 className="w-4 h-4" />
          Voir toutes les demandes de partenariat
        </Link>
      </div>
    </div>
  );
}
