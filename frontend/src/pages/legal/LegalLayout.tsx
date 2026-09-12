import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { FileText, Scale, ShieldCheck } from 'lucide-react';
import { LEGAL } from '../../config/legal';
import { SectionNav, type SectionLink } from '../../components/common/SectionNav';

const PAGES = [
  { to: '/mentions-legales', label: 'Mentions légales', icon: Scale },
  { to: '/cgv', label: 'CGV / CGU', icon: FileText },
  { to: '/politique-confidentialite', label: 'Confidentialité', icon: ShieldCheck },
] as const;

/**
 * Mise en page commune aux trois documents juridiques.
 *
 * <p>Ils se consultent rarement seuls : on arrive sur les mentions légales et l'on cherche
 * aussitôt les CGV. La navigation entre les trois est donc portée par la page elle-même, et
 * non seulement par le pied de page qu'il faudrait aller rechercher tout en bas.</p>
 */
export function LegalLayout({
  titre, chapo, sections, children,
}: { titre: string; chapo: string; sections?: readonly SectionLink[]; children: ReactNode }) {
  return (
    <div className="bg-slate-50 min-h-screen py-10">
      <div className={`container mx-auto px-4 ${sections ? 'max-w-6xl' : 'max-w-3xl'}`}>
        <header className="mb-8">
          <h1 className="text-3xl md:text-4xl font-bold text-brand-dark">{titre}</h1>
          <p className="text-slate-500 mt-2">{chapo}</p>
          <p className="text-xs text-slate-400 mt-3">
            Dernière mise à jour : {LEGAL.dateMaj}
          </p>
        </header>

        <nav className="flex flex-wrap gap-2 mb-8" aria-label="Documents juridiques">
          {PAGES.map(({ to, label, icon: Icon }) => (
            <Link
              key={to} to={to}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-white border border-slate-200 text-sm font-semibold text-slate-700 hover:border-brand hover:text-brand-dark transition-colors"
            >
              <Icon className="w-3.5 h-3.5" /> {label}
            </Link>
          ))}
        </nav>

        {/* Sommaire collant a gauche des qu'il y a des sections : ces documents se
            consultent pour un article precis, pas de bout en bout. */}
        <div className={sections ? 'grid lg:grid-cols-[240px_minmax(0,1fr)] gap-8 lg:gap-12' : ''}>
          {sections && (
            <aside>
              <SectionNav sections={sections} />
            </aside>
          )}
          <article className="min-w-0 bg-white rounded-2xl border border-slate-200 shadow-sm p-6 md:p-10 legal-prose">
            {children}
          </article>
        </div>
      </div>

      {/* Styles locaux : ces trois pages sont les seules du site a rendre de longs textes
          structures, et une classe utilitaire par balise les rendrait illisibles a editer. */}
      <style>{`
        .legal-prose h2 {
          font-size: 1.15rem; font-weight: 700; color: #0f2e29;
          margin: 2.25rem 0 .75rem; padding-bottom: .4rem;
          border-bottom: 1px solid #e2e8f0;
          scroll-margin-top: 10rem;
        }
        .legal-prose h3 { scroll-margin-top: 10rem; }
        .legal-prose h2:first-child { margin-top: 0; }
        .legal-prose h3 {
          font-size: .95rem; font-weight: 700; color: #1e293b; margin: 1.5rem 0 .5rem;
        }
        .legal-prose p { color: #475569; line-height: 1.7; margin-bottom: .9rem; }
        .legal-prose ul { margin: 0 0 1rem 1.1rem; color: #475569; line-height: 1.7; }
        .legal-prose li { margin-bottom: .4rem; }
        .legal-prose strong { color: #0f2e29; font-weight: 600; }
        .legal-prose a { color: #14524a; font-weight: 600; text-decoration: underline; }
        .legal-prose dl { margin: 0 0 1rem; }
        .legal-prose dt {
          font-size: .7rem; font-weight: 700; text-transform: uppercase;
          letter-spacing: .06em; color: #94a3b8; margin-top: .85rem;
        }
        .legal-prose dd { margin: .1rem 0 0; color: #334155; }
      `}</style>
    </div>
  );
}

/** Ligne d'un bloc d'identité : intitulé au-dessus, valeur en dessous. */
export function Ligne({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </>
  );
}

/** Met en évidence une valeur non renseignée, au lieu de la laisser passer inaperçue. */
export function Manquant({ valeur }: { valeur: string }) {
  const incomplet = valeur.startsWith('⚠️');
  return incomplet
    ? <span className="text-amber-700 bg-amber-50 border border-amber-200 rounded px-1.5 py-0.5 text-xs font-semibold">{valeur}</span>
    : <>{valeur}</>;
}
