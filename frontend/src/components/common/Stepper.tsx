import { CheckCircle } from 'lucide-react';

export interface StepperStep {
  id: string;
  label: string;
}

/** Étapes du parcours de mobilité d'un médecin, partagées entre la liste et le détail d'un dossier. */
export const ENROLLMENT_STEPS: StepperStep[] = [
  // Cycle commercial : OptimiSanté pré-qualifie, le CHU décide, le médecin paie.
  { id: 'UNDER_OPTIMI_REVIEW', label: 'Dossier en revue' },
  { id: 'SUBMITTED_TO_PARTNER', label: 'Transmis au CHU' },
  { id: 'ACCEPTED_BY_PARTNER', label: 'Accepté par le CHU' },
  { id: 'PENDING_TUITION_FEE', label: 'Paiement attendu' },
  { id: 'CONFIRMED', label: 'Inscription confirmée' },
  // Cycle de mobilité, inchangé.
  { id: 'CONVENTION_ISSUED', label: 'Convention émise' },
  { id: 'VISA_SUBMITTED', label: 'Visa soumis' },
  { id: 'VISA_GRANTED', label: 'Visa obtenu' },
  { id: 'READY_TO_START', label: 'Prêt à démarrer' },
];

/**
 * États hors parcours linéaire : ils n'apparaissent pas comme une étape du stepper.
 * ACTION_REQUIRED renvoie le dossier à l'étape de revue, REJECTED/CANCELLED le terminent.
 */
export const ENROLLMENT_EXCEPTION_STATUSES = ['ACTION_REQUIRED', 'REJECTED', 'CANCELLED'];

/**
 * Position d'un statut dans le parcours.
 *
 * `ACTION_REQUIRED` n'est pas une etape : c'est un retour en arriere: le dossier repasse
 * en revue le temps que le medecin fournisse la piece demandee. Le placer a l'etape de revue
 * dit la verite du dossier ; le laisser hors du parcours (index -1) affichait une progression
 * vide, comme si rien n'avait commence.
 */
export function stepIndexForStatus(steps: StepperStep[], statusId: string): number {
  if (statusId === 'ACTION_REQUIRED') {
    return steps.findIndex((s) => s.id === 'UNDER_OPTIMI_REVIEW');
  }
  return steps.findIndex((s) => s.id === statusId);
}

interface StepperProps {
  steps: StepperStep[];
  currentStepId: string;
  isFailed?: boolean;
  size?: 'full' | 'compact' | 'inline';
}

const getStepColor = (stepIndex: number, currentIndex: number, isFailed: boolean, size: 'full' | 'compact') => {
  if (isFailed) return size === 'full' ? 'bg-rose-50 text-rose-700 border-rose-200 border-2' : 'bg-rose-300';
  if (stepIndex < currentIndex) return size === 'full' ? 'bg-emerald-50 text-emerald-700 border-emerald-200 border-2' : 'bg-emerald-500';
  if (stepIndex === currentIndex) return size === 'full' ? 'bg-blue-600 text-white shadow-md border-2 border-blue-600' : 'bg-blue-600';
  return size === 'full' ? 'bg-slate-100 text-slate-400 border-2 border-slate-200' : 'bg-slate-200';
};

/**
 * Stepper horizontal de parcours (candidature → convention → visa → départ...), utilisé sur
 * le détail d'un dossier (size="full", avec libellés) et en version compacte sur les listes
 * (size="compact", juste des points reliés — pas de libellé, la StatusBadge s'en charge déjà).
 */
export function Stepper({ steps, currentStepId, isFailed = false, size = 'full' }: StepperProps) {
  const currentIndex = stepIndexForStatus(steps, currentStepId);

  /**
   * Variante de liste : « Etape 3/9 » + le libelle de l'etape en cours + une barre de
   * progression. Elle remplace neuf pastilles de 8 pixels qui, sans libelle, demandaient de
   * compter les points pour situer un dossier — et ne distinguaient pas la 6e de la 7e.
   */
  if (size === 'inline') {
    const enAttenteMedecin = currentStepId === 'ACTION_REQUIRED';
    const position = currentIndex >= 0 ? currentIndex + 1 : 0;
    const progression = steps.length > 1 && currentIndex >= 0
      ? (currentIndex / (steps.length - 1)) * 100
      : 0;
    const teinte = isFailed ? 'bg-rose-400' : enAttenteMedecin ? 'bg-amber-400' : 'bg-emerald-500';

    return (
      <div className="min-w-[150px] max-w-[190px]">
        <div className="flex items-baseline justify-between gap-2 mb-1.5">
          <span className={`text-xs font-semibold truncate ${
            isFailed ? 'text-rose-600' : enAttenteMedecin ? 'text-amber-700' : 'text-slate-700'
          }`}>
            {enAttenteMedecin ? 'Pièce demandée' : steps[currentIndex]?.label ?? '—'}
          </span>
          <span className="text-[11px] text-slate-400 tabular-nums shrink-0">
            {position}/{steps.length}
          </span>
        </div>
        <div className="h-1.5 rounded-full bg-slate-200 overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ${teinte}`}
            style={{ width: `${Math.max(progression, currentIndex >= 0 ? 6 : 0)}%` }}
          />
        </div>
      </div>
    );
  }

  if (size === 'compact') {
    return (
      <div className="flex items-center gap-1" title={steps[currentIndex]?.label}>
        {steps.map((step, index) => (
          <div key={step.id} className="flex items-center">
            <div className={`w-2 h-2 rounded-full ${getStepColor(index, currentIndex, isFailed, 'compact')}`} />
            {index < steps.length - 1 && (
              <div className={`w-3 h-0.5 ${index < currentIndex && !isFailed ? 'bg-emerald-500' : 'bg-slate-200'}`} />
            )}
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="py-6">
      <div className="flex items-center justify-between relative">
        <div className="absolute left-0 top-1/2 -translate-y-1/2 w-full h-1 bg-slate-200 -z-10 rounded-full" />
        {!isFailed && (
          <div
            className="absolute left-0 top-1/2 -translate-y-1/2 h-1 bg-blue-500 -z-10 rounded-full transition-all duration-500"
            style={{ width: `${(currentIndex / (steps.length - 1)) * 100}%` }}
          />
        )}
        {steps.map((step, index) => (
          <div key={step.id} className="flex flex-col items-center relative z-10 w-32">
            <div className={`w-10 h-10 rounded-full flex items-center justify-center font-bold text-sm mb-3 transition-colors ${getStepColor(index, currentIndex, isFailed, 'full')}`}>
              {index < currentIndex && !isFailed ? <CheckCircle className="w-5 h-5" /> : index + 1}
            </div>
            <div className={`text-xs font-semibold text-center leading-tight ${index <= currentIndex && !isFailed ? 'text-brand-dark' : 'text-slate-400'}`}>
              {step.label}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
