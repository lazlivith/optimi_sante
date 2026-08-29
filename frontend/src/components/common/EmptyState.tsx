import type { ComponentType, ReactNode } from 'react';

interface EmptyStateProps {
  icon: ComponentType<{ className?: string }>;
  title: string;
  description?: string;
  action?: ReactNode;
}

/**
 * État vide standard pour les listes/tableaux (Admin / Partenaire / Médecin) :
 * icône + message clair + action optionnelle (ex: lien vers une autre page).
 */
export function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="p-12 text-center">
      <Icon className="w-12 h-12 text-slate-300 mx-auto mb-4" />
      <p className="text-slate-600 font-medium">{title}</p>
      {description && <p className="text-sm text-slate-500 mt-1">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
