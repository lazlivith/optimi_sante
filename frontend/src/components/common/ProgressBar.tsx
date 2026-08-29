export type ProgressTone = 'emerald' | 'amber' | 'rose' | 'blue';

const BAR_CLASSES: Record<ProgressTone, string> = {
  emerald: 'bg-emerald-500',
  amber: 'bg-amber-500',
  rose: 'bg-rose-500',
  blue: 'bg-blue-500',
};

interface ProgressBarProps {
  /** Valeur entre 0 et 100. */
  value: number;
  tone?: ProgressTone;
  className?: string;
}

/** Barre de progression fine, réutilisable (taux de remplissage, avancement...). */
export function ProgressBar({ value, tone = 'emerald', className = 'w-24' }: ProgressBarProps) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div className={`h-1.5 bg-slate-100 rounded-full overflow-hidden ${className}`}>
      <div
        className={`h-full rounded-full transition-all duration-500 ${BAR_CLASSES[tone]}`}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
}
