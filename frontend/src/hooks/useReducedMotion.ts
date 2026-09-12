import { useEffect, useState } from 'react';

/**
 * L'utilisateur a-t-il demandé à son système de limiter les animations ?
 *
 * <p>Ce réglage existe dans tous les systèmes d'exploitation et se transmet au navigateur. Il
 * n'est pas une préférence esthétique : il est activé par des personnes que le mouvement rend
 * mal à l'aise — troubles vestibulaires, migraines, sensibilité photique. L'ignorer ne rend pas
 * le site « plus vivant » pour elles, il le rend pénible à parcourir.</p>
 *
 * <p>La valeur est suivie dans le temps, et non lue une seule fois : le réglage peut changer
 * pendant la visite, et une page qui garderait sa première lecture continuerait d'animer après
 * qu'on lui a demandé d'arrêter.</p>
 *
 * <p>WCAG 2.1, critère 2.2.2 — Pause, arrêt, masquer.</p>
 */
export function useReducedMotion(): boolean {
  const [reduit, setReduit] = useState(() => {
    // Rendu serveur ou navigateur ancien : on suppose qu'aucune réduction n'est demandée,
    // plutôt que d'échouer.
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  });

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const requete = window.matchMedia('(prefers-reduced-motion: reduce)');
    const surChangement = (e: MediaQueryListEvent) => setReduit(e.matches);

    // addEventListener sur un MediaQueryList n'existe pas partout : le repli sur addListener
    // couvre les navigateurs plus anciens, où l'absence de suivi vaudrait mieux qu'une erreur.
    if (requete.addEventListener) {
      requete.addEventListener('change', surChangement);
      return () => requete.removeEventListener('change', surChangement);
    }
    requete.addListener(surChangement);
    return () => requete.removeListener(surChangement);
  }, []);

  return reduit;
}
