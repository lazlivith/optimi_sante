import type { ComponentType } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';

export type StatTone = 'amber' | 'emerald' | 'blue' | 'purple' | 'rose' | 'slate';

const TONE_CLASSES: Record<StatTone, string> = {
  amber: 'text-amber-600 bg-amber-50',
  emerald: 'text-emerald-600 bg-emerald-50',
  blue: 'text-blue-600 bg-blue-50',
  purple: 'text-purple-600 bg-purple-50',
  rose: 'text-rose-600 bg-rose-50',
  slate: 'text-slate-600 bg-slate-50',
};

interface StatCardProps {
  label: string;
  value: string | number | null | undefined;
  icon: ComponentType<{ className?: string }>;
  tone?: StatTone;
  /** Si fourni, la carte devient cliquable et renvoie vers cette route. */
  to?: string;
  /** Ligne secondaire optionnelle sous le libellé (ex: "12 ventes aujourd'hui"). */
  sub?: string;
}

/**
 * Tuile de statistique standard des tableaux de bord (Admin / Partenaire / Médecin).
 * Affiche "—" tant que la valeur n'est pas encore chargée, sans jamais planter sur `null`/`undefined`.
 */
export function StatCard({ label, value, icon: Icon, tone = 'slate', to, sub }: StatCardProps) {
  const content = (
    <>
      <div className={`w-12 h-12 rounded-2xl flex items-center justify-center mb-4 ${TONE_CLASSES[tone]}`}>
        <Icon className="w-6 h-6" />
      </div>
      <div className="text-3xl font-extrabold text-slate-900 tracking-tight">{value ?? '—'}</div>
      <div className="text-sm text-slate-500 mt-1 font-medium">{label}</div>
      {sub && <div className="text-xs text-slate-400 mt-0.5">{sub}</div>}
      {to && (
        <div className="mt-3 flex items-center gap-1 text-xs font-semibold text-brand-green opacity-0 group-hover:opacity-100 transition-opacity">
          Voir le détail <ArrowRight className="w-3 h-3" />
        </div>
      )}
    </>
  );

  const className = 'relative bg-white rounded-2xl border border-slate-200 p-6 shadow-sm hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 group';

  if (to) {
    return (
      <Link to={to} className={className}>
        {content}
      </Link>
    );
  }
  return <div className={className}>{content}</div>;
}
