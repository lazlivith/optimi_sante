import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';

/** Largeur à partir de laquelle le menu est une colonne fixe (`lg` de Tailwind). */
const ECRAN_LARGE = '(min-width: 1024px)';

/**
 * Menu latéral des espaces médecin, CHU et administration, replié en tiroir sous 1024 px.
 *
 * <p>Ces trois espaces affichaient leur menu de 256 px en permanence : sur un téléphone de
 * 390 px, il restait 134 px au contenu. Le tiroir se ferme de lui-même quand on change de page,
 * au clavier (Échap), en touchant le voile, ou quand l'écran repasse en grand — sans quoi le
 * défilement de la page resterait bloqué.</p>
 *
 * <p>Tant qu'il est ouvert, le focus reste dans le tiroir et revient au bouton qui l'a ouvert :
 * même comportement que l'aperçu des documents.</p>
 */
export function useTiroirNavigation() {
  const [ouvert, setOuvert] = useState(false);
  const panneau = useRef<HTMLElement>(null);
  const bouton = useRef<HTMLButtonElement>(null);
  const { pathname } = useLocation();

  const fermer = useCallback(() => setOuvert(false), []);
  const ouvrir = useCallback(() => setOuvert(true), []);

  // Changement de page : le tiroir a rempli son rôle.
  useEffect(() => { setOuvert(false); }, [pathname]);

  useEffect(() => {
    const grand = window.matchMedia(ECRAN_LARGE);
    const auChangement = (e: MediaQueryListEvent) => { if (e.matches) setOuvert(false); };
    grand.addEventListener('change', auChangement);
    return () => grand.removeEventListener('change', auChangement);
  }, []);

  useEffect(() => {
    if (!ouvert) return;
    const retour = bouton.current;
    const defilement = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    panneau.current?.querySelector<HTMLElement>('a[href], button:not([disabled])')?.focus();

    const auClavier = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.preventDefault(); setOuvert(false); return; }
      if (e.key !== 'Tab' || !panneau.current) return;
      const cibles = panneau.current.querySelectorAll<HTMLElement>('a[href], button:not([disabled])');
      if (cibles.length === 0) return;
      const premier = cibles[0];
      const dernier = cibles[cibles.length - 1];
      if (e.shiftKey && document.activeElement === premier) { e.preventDefault(); dernier.focus(); }
      else if (!e.shiftKey && document.activeElement === dernier) { e.preventDefault(); premier.focus(); }
    };
    document.addEventListener('keydown', auClavier);

    return () => {
      document.removeEventListener('keydown', auClavier);
      document.body.style.overflow = defilement;
      retour?.focus();
    };
  }, [ouvert]);

  /**
   * Un lien vers la page déjà affichée ne change pas l'adresse : sans ce rattrapage, le tiroir
   * resterait ouvert après un clic sur l'entrée active.
   */
  const fermerSurLien = useCallback((e: React.MouseEvent) => {
    if ((e.target as HTMLElement).closest('a[href]')) setOuvert(false);
  }, []);

  return { ouvert, ouvrir, fermer, fermerSurLien, panneau, bouton };
}
