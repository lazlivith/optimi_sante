import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ArrowRight, ChevronDown } from 'lucide-react';
import { GROUPES_SERVICES } from '../lib/servicesMenu';

/**
 * Le menu « Services » de la barre de navigation, en grand format.
 *
 * <p>Un simple lien vers /services obligeait le visiteur à ouvrir la page pour savoir ce
 * qu'elle contient. Le panneau montre les deux métiers et leurs huit entrées d'un coup ;
 * chacune mène directement à sa section, et non au haut de la page.</p>
 *
 * <p><b>Il s'ouvre au survol ET au clic.</b> Le survol seul enferme la commande : au clavier
 * on ne survole pas, et une tablette encore moins. Le bouton reste donc actionnable, porte
 * son état dans <code>aria-expanded</code>, et le panneau se ferme à l'échappement, au clic
 * en dehors, et au changement de page — sans quoi il resterait ouvert par-dessus la page
 * suivante.</p>
 */
export function MenuServices() {
  const [ouvert, setOuvert] = useState(false);
  const conteneur = useRef<HTMLDivElement>(null);
  const bouton = useRef<HTMLButtonElement>(null);
  const { pathname, hash } = useLocation();

  // Le menu a rempli son rôle dès qu'on a navigué — y compris vers une ancre de la même page.
  useEffect(() => { setOuvert(false); }, [pathname, hash]);

  useEffect(() => {
    if (!ouvert) return;
    const auClavier = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      setOuvert(false);
      bouton.current?.focus();
    };
    const auClic = (e: MouseEvent) => {
      if (!conteneur.current?.contains(e.target as Node)) setOuvert(false);
    };
    document.addEventListener('keydown', auClavier);
    document.addEventListener('mousedown', auClic);
    return () => {
      document.removeEventListener('keydown', auClavier);
      document.removeEventListener('mousedown', auClic);
    };
  }, [ouvert]);

  return (
    <div
      ref={conteneur}
      className="relative"
      onMouseEnter={() => setOuvert(true)}
      onMouseLeave={() => setOuvert(false)}
    >
      <button
        ref={bouton}
        type="button"
        onClick={() => setOuvert((o) => !o)}
        aria-expanded={ouvert}
        aria-controls="menu-services"
        className={`flex items-center gap-1 whitespace-nowrap rounded-md px-3 py-1.5 text-xs font-medium transition-colors hover:bg-gray-50 ${
          ouvert ? 'text-brand-dark' : 'text-gray-600 hover:text-brand-dark'
        }`}
      >
        Nos Services
        <ChevronDown
          aria-hidden="true"
          className={`h-3 w-3 transition-transform ${ouvert ? 'rotate-180' : ''}`}
        />
      </button>

      {ouvert && (
        <div
          id="menu-services"
          className="absolute left-0 top-full z-50 w-[min(46rem,calc(100vw-3rem))] rounded-2xl border border-slate-200 bg-white p-6 shadow-xl"
        >
          <div className="grid gap-x-8 gap-y-6 sm:grid-cols-2">
            {GROUPES_SERVICES.map((groupe) => (
              <div key={groupe.titre}>
                <p className="mb-3 text-[11px] font-bold uppercase tracking-wider text-slate-400">
                  {groupe.titre}
                </p>
                <ul className="space-y-1">
                  {groupe.entrees.map((entree) => (
                    <li key={entree.to}>
                      <Link
                        to={entree.to}
                        className="group flex items-start gap-3 rounded-xl p-2.5 transition-colors hover:bg-brand-cream focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/30"
                      >
                        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-light text-brand">
                          <entree.icon aria-hidden="true" className="h-4 w-4" />
                        </span>
                        <span className="min-w-0">
                          <span className="block text-sm font-semibold text-brand-dark">
                            {entree.titre}
                          </span>
                          <span className="block text-xs leading-relaxed text-slate-500">
                            {entree.texte}
                          </span>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <div className="mt-5 flex items-center justify-between gap-4 border-t border-slate-100 pt-4">
            <p className="text-xs italic text-slate-500">
              Deux métiers, un seul interlocuteur.
            </p>
            <Link
              to="/services"
              className="inline-flex items-center gap-1.5 rounded text-sm font-semibold text-brand underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/30"
            >
              Voir tous les services
              <ArrowRight aria-hidden="true" className="h-4 w-4" />
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
