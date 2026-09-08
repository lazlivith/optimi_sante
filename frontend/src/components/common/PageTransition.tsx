import { type ReactNode, useEffect } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Enveloppe la zone de contenu d'une page pour lui donner une transition
 * d'entrée légère (fondu + léger glissement) à chaque changement de route,
 * et remonte le scroll en haut de la nouvelle page (comportement standard
 * attendu en navigation, que la SPA ne fournit pas par défaut).
 * `key={pathname}` force un remount du wrapper -> l'animation CSS repart
 * automatiquement, sans dépendance externe (pas de framer-motion).
 *
 * Purement présentationnel : ne touche à aucune logique métier, et n'enveloppe
 * jamais les sidebars/navbar (elles restent des éléments frères, jamais remontés).
 */
export function PageTransition({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
  }, [pathname]);

  return (
    <div key={pathname} className="page-transition">
      {children}
    </div>
  );
}
