import type { ComponentType, ReactNode } from 'react';

interface HeroBannerProps {
  eyebrow?: string;
  title: string;
  subtitle?: string;
  icon?: ComponentType<{ className?: string }>;
  actions?: ReactNode;
}

/**
 * Bannière d'accueil des tableaux de bord dédiés (Admin / Partenaire / Médecin).
 * Dégradé sur les couleurs de marque + motif décoratif discret. Purement visuel.
 */
export function HeroBanner({ eyebrow, title, subtitle, icon: Icon, actions }: HeroBannerProps) {
  return (
    <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-brand-dark to-brand-green text-white p-8 mb-8 shadow-lg">
      <div className="absolute -right-10 -top-16 w-64 h-64 rounded-full bg-white/5 pointer-events-none" />
      <div className="absolute right-16 -bottom-10 w-32 h-32 rounded-full bg-white/5 pointer-events-none" />

      <div className="relative flex items-start justify-between gap-6 flex-wrap">
        <div>
          {eyebrow && (
            <p className="text-xs font-bold uppercase tracking-wider text-white/60 mb-2">{eyebrow}</p>
          )}
          <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
          {subtitle && <p className="text-white/70 mt-2 max-w-xl leading-relaxed">{subtitle}</p>}
        </div>
        <div className="flex items-center gap-3 shrink-0">
          {actions}
          {Icon && (
            <div className="w-14 h-14 rounded-2xl bg-white/10 backdrop-blur flex items-center justify-center">
              <Icon className="w-7 h-7" />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
