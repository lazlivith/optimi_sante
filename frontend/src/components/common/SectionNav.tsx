import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';

export interface SectionLink {
  id: string;
  label: string;
}

/**
 * Sommaire d'une page longue, avec repérage de la section en cours de lecture.
 *
 * <p>Une page de conditions générales ou de présentation se parcourt rarement en entier : on y
 * arrive pour un point précis, souvent depuis un lien de pied de page. Sans sommaire, il faut
 * faire défiler pour savoir ce que la page contient, et rien n'indique où l'on se trouve une
 * fois arrivé.</p>
 *
 * <p>Le repérage utilise un {@code IntersectionObserver} plutôt qu'un calcul à chaque
 * défilement : le navigateur signale lui-même les entrées et sorties, sans faire tourner du
 * code à chaque pixel parcouru.</p>
 */
export function SectionNav({ sections }: { sections: readonly SectionLink[] }) {
  const [active, setActive] = useState<string>(sections[0]?.id ?? '');
  const { hash } = useLocation();

  useEffect(() => {
    const cibles = sections
      .map((s) => document.getElementById(s.id))
      .filter((el): el is HTMLElement => el !== null);
    if (cibles.length === 0) return;

    // La marge haute correspond à l'en-tête collante : une section masquée derrière elle ne
    // doit pas être considérée comme lue. La marge basse évite que plusieurs sections soient
    // actives à la fois sur un grand écran.
    const observer = new IntersectionObserver(
      (entrees) => {
        const visibles = entrees
          .filter((e) => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visibles.length > 0) setActive(visibles[0].target.id);
      },
      { rootMargin: '-150px 0px -55% 0px', threshold: 0 },
    );

    cibles.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [sections]);

  // Une arrivée par ancre doit marquer la bonne entrée immédiatement, sans attendre que
  // l'observateur ait vu passer la section.
  useEffect(() => {
    if (hash) setActive(decodeURIComponent(hash.slice(1)));
  }, [hash]);

  const allerA = (id: string) => {
    const cible = document.getElementById(id);
    if (!cible) return;
    const y = cible.getBoundingClientRect().top + window.scrollY - 148;
    window.scrollTo({ top: Math.max(y, 0), behavior: 'smooth' });
    setActive(id);
    // On met à jour l'adresse sans recharger : le lien reste partageable.
    window.history.replaceState(null, '', `#${id}`);
  };

  return (
    <nav aria-label="Sommaire de la page">
      {/* Écran large : sommaire vertical collant, à côté du contenu. */}
      <ul className="hidden lg:block sticky top-40 space-y-1">
        {sections.map((s) => (
          <li key={s.id}>
            <button
              type="button"
              onClick={() => allerA(s.id)}
              aria-current={active === s.id ? 'true' : undefined}
              className={`w-full text-left text-sm px-3 py-2 rounded-lg border-l-2 transition-colors ${
                active === s.id
                  ? 'border-brand-green bg-brand-green/5 text-brand-dark font-semibold'
                  : 'border-transparent text-slate-500 hover:text-brand-dark hover:bg-slate-100'
              }`}
            >
              {s.label}
            </button>
          </li>
        ))}
      </ul>

      {/* Écran étroit : le sommaire devient une barre horizontale défilante, collée sous
          l'en-tête. Un sommaire vertical y occuperait un écran entier avant le contenu. */}
      <div className="lg:hidden sticky top-32 z-20 -mx-4 px-4 py-2 bg-slate-50/95 backdrop-blur border-b border-slate-200 overflow-x-auto">
        <ul className="flex gap-2 w-max">
          {sections.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                onClick={() => allerA(s.id)}
                aria-current={active === s.id ? 'true' : undefined}
                className={`whitespace-nowrap text-xs font-semibold px-3 py-1.5 rounded-full transition-colors ${
                  active === s.id
                    ? 'bg-brand-green text-white'
                    : 'bg-white text-slate-600 border border-slate-200'
                }`}
              >
                {s.label}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </nav>
  );
}
