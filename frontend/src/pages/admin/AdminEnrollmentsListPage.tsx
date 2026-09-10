import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Loader2, GraduationCap, ChevronRight, Search, Building2, X, Inbox } from 'lucide-react';
import { adminEnrollmentService, type EnrollmentDetailDto } from '../../api/adminEnrollmentService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Avatar } from '../../components/common/Avatar';
import { Stepper, ENROLLMENT_STEPS } from '../../components/common/Stepper';

const FAILED_STATUSES = new Set(['REJECTED', 'CANCELLED']);

/** Étapes où la décision revient à OptimiSanté — celles qui appellent une action de l'admin. */
const A_TRAITER = new Set(['UNDER_OPTIMI_REVIEW']);

/** Regroupements proposés au filtre, au-delà du statut exact. */
const VUES = [
  { id: 'all', label: 'Tous les dossiers' },
  { id: 'todo', label: 'À traiter par OptimiSanté' },
  { id: 'partner', label: 'Chez le CHU' },
  { id: 'doctor', label: 'En attente du médecin' },
  { id: 'closed', label: 'Clos (refusés / annulés)' },
] as const;

type VueId = (typeof VUES)[number]['id'];

const VUE_STATUTS: Record<Exclude<VueId, 'all'>, Set<string>> = {
  todo: A_TRAITER,
  partner: new Set(['SUBMITTED_TO_PARTNER']),
  doctor: new Set(['ACTION_REQUIRED', 'PENDING_TUITION_FEE']),
  closed: FAILED_STATUSES,
};

export function AdminEnrollmentsListPage() {
  const [enrollments, setEnrollments] = useState<EnrollmentDetailDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const [partenaire, setPartenaire] = useState('');
  const [vue, setVue] = useState<VueId>('all');
  const [recherche, setRecherche] = useState('');

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

  // La liste des CHU est déduite des dossiers eux-mêmes, et non de la table des partenaires :
  // proposer un établissement sans aucune candidature conduirait à un filtre vide, que
  // l'utilisateur lirait comme une panne plutôt que comme une absence.
  const chus = useMemo(() => {
    const compte = new Map<string, { id: string; nom: string; total: number }>();
    enrollments.forEach((e) => {
      if (!e.partnerProfileId) return;
      const entree = compte.get(e.partnerProfileId)
        ?? { id: e.partnerProfileId, nom: e.partnerName ?? 'CHU inconnu', total: 0 };
      entree.total += 1;
      compte.set(e.partnerProfileId, entree);
    });
    return [...compte.values()].sort((a, b) => a.nom.localeCompare(b.nom, 'fr'));
  }, [enrollments]);

  const resultats = useMemo(() => {
    const terme = recherche.trim().toLowerCase();
    return enrollments.filter((e) => {
      if (partenaire && e.partnerProfileId !== partenaire) return false;
      if (vue !== 'all' && !VUE_STATUTS[vue].has(e.status)) return false;
      if (!terme) return true;
      return [e.doctorName, e.doctorEmail, e.trainingTitle, e.partnerName]
        .some((champ) => champ?.toLowerCase().includes(terme));
    });
  }, [enrollments, partenaire, vue, recherche]);

  const filtreActif = Boolean(partenaire || vue !== 'all' || recherche.trim());
  const aTraiter = enrollments.filter((e) => A_TRAITER.has(e.status)).length;

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Dossiers CHU / Mobilité"
        subtitle="Suivi des candidatures de médecins aux formations CHU."
      />

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 mb-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[210px]">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
            <input
              type="search"
              value={recherche}
              onChange={(e) => setRecherche(e.target.value)}
              placeholder="Médecin, email, formation…"
              aria-label="Rechercher un dossier par médecin, email ou formation"
              className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
            />
          </div>

          <select
            value={partenaire}
            onChange={(e) => setPartenaire(e.target.value)}
            aria-label="Filtrer par établissement partenaire"
            className="py-2 px-3 text-sm rounded-lg border border-slate-300 bg-white focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none max-w-[260px]"
          >
            <option value="">Tous les CHU ({enrollments.length})</option>
            {chus.map((chu) => (
              <option key={chu.id} value={chu.id}>{chu.nom} ({chu.total})</option>
            ))}
          </select>

          {/* Le filtre porte sur des étapes du cycle, pas sur les onze statuts bruts : ce qui
              intéresse l'administrateur est « qui doit agir », pas le nom technique de l'état. */}
          <select
            value={vue}
            onChange={(e) => setVue(e.target.value as VueId)}
            aria-label="Filtrer par étape du cycle"
            className="py-2 px-3 text-sm rounded-lg border border-slate-300 bg-white focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
          >
            {VUES.map((v) => <option key={v.id} value={v.id}>{v.label}</option>)}
          </select>

          {filtreActif && (
            <button
              type="button"
              onClick={() => { setPartenaire(''); setVue('all'); setRecherche(''); }}
              className="inline-flex items-center gap-1.5 py-2 px-3 text-sm text-slate-500 hover:text-brand-dark transition-colors"
            >
              <X className="w-4 h-4" /> Réinitialiser
            </button>
          )}
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
          <span>
            {resultats.length} dossier{resultats.length > 1 ? 's' : ''}
            {filtreActif ? ` sur ${enrollments.length}` : ''}
          </span>
          {aTraiter > 0 && vue !== 'todo' && (
            <button
              type="button"
              onClick={() => setVue('todo')}
              className="inline-flex items-center gap-1.5 font-semibold text-amber-700 hover:underline"
            >
              <Inbox className="w-3.5 h-3.5" />
              {aTraiter} en attente de votre revue
            </button>
          )}
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center items-center">
            <Loader2 className="w-8 h-8 text-emerald-600 animate-spin" />
          </div>
        ) : resultats.length === 0 ? (
          filtreActif ? (
            <EmptyState
              icon={Search}
              title="Aucun dossier ne correspond à ces filtres."
              description="Élargissez la recherche ou réinitialisez les filtres."
            />
          ) : (
            <EmptyState icon={GraduationCap} title="Aucun dossier pour le moment." />
          )
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Médecin</th>
                  <th className="px-6 py-4">Formation / CHU</th>
                  <th className="px-6 py-4">Soumis le</th>
                  <th className="px-6 py-4">Avancement</th>
                  <th className="px-6 py-4">Statut</th>
                  <th className="px-6 py-4"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {resultats.map((e) => {
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
                      <td className="px-6 py-4">
                        <div className="font-medium text-slate-700">{e.trainingTitle}</div>
                        {e.partnerName && (
                          <div className="text-xs text-slate-500 flex items-center gap-1 mt-0.5">
                            <Building2 className="w-3 h-3 shrink-0" />
                            <span className="truncate">{e.partnerName}</span>
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-4 text-slate-500 whitespace-nowrap">
                        {new Date(e.submittedAt).toLocaleDateString('fr-FR')}
                      </td>
                      <td className="px-6 py-4">
                        {isFailed
                          ? <span className="text-xs text-slate-400">Parcours interrompu</span>
                          : <Stepper steps={ENROLLMENT_STEPS} currentStepId={e.status} size="inline" />}
                      </td>
                      <td className="px-6 py-4">
                        <StatusBadge status={e.status} />
                      </td>
                      <td className="px-6 py-4 text-right">
                        <Link
                          to={`/admin/enrollments/${e.id}`}
                          className="inline-flex items-center text-xs font-bold text-brand-green hover:underline whitespace-nowrap"
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
