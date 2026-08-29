import { CheckCircle } from 'lucide-react';

export interface StepperStep {
  id: string;
  label: string;
}

/** Étapes du parcours de mobilité d'un médecin, partagées entre la liste et le détail d'un dossier. */
export const ENROLLMENT_STEPS: StepperStep[] = [
  { id: 'PENDING_REVIEW', label: 'En attente' },
  { id: 'APPROVED_ACADEMIC', label: 'Validé Académique' },
  { id: 'APPROVED_ADMINISTRATIVE', label: 'Validé Administratif' },
  { id: 'CONVENTION_ISSUED', label: 'Convention émise' },
  { id: 'VISA_SUBMITTED', label: 'Visa soumis' },
  { id: 'VISA_GRANTED', label: 'Visa obtenu' },
  { id: 'READY_TO_START', label: 'Prêt à démarrer' },
];

interface StepperProps {
  steps: StepperStep[];
  currentStepId: string;
  isFailed?: boolean;
  size?: 'full' | 'compact';
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
  const currentIndex = steps.findIndex(s => s.id === currentStepId);

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
