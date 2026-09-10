import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Hauteur de l'en-tête collante : rangée principale (80 px) + navigation secondaire (48 px),
 * plus une marge de respiration. Sans cette compensation, la section visée se retrouve
 * cachée sous l'en-tête et l'utilisateur croit que le lien n'a rien fait.
 */
const DECALAGE_ENTETE = 148;

/** Durée du signalement visuel de la section atteinte. */
const DUREE_SURBRILLANCE = 1600;

/**
 * Gère le défilement à chaque navigation.
 *
 * <p>React Router <b>n'atteint pas les ancres</b> : cliquer sur un lien vers
 * {@code /services#faq} depuis une autre page charge bien la page, mais laisse le visiteur en
 * haut. Le fragment est purement décoratif tant que personne ne le traite. Ce composant s'en
 * charge, et remet aussi le défilement en haut lors d'une navigation ordinaire — sans quoi on
 * arrive au milieu d'une page parce qu'on avait fait défiler la précédente.</p>
 *
 * <p><b>L'attente de l'élément est indispensable.</b> Les pages sont chargées à la demande
 * (code-splitting) : au moment où l'URL change, la cible n'existe pas encore dans le document.
 * Chercher une seule fois échouerait systématiquement lors d'une arrivée depuis une autre
 * page — précisément le cas d'usage d'un lien de pied de page.</p>
 */
export function ScrollManager() {
  const { pathname, hash } = useLocation();

  useEffect(() => {
    // Navigation ordinaire : on repart du haut.
    if (!hash) {
      window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
      return;
    }

    const id = decodeURIComponent(hash.slice(1));
    let annule = false;
    let essais = 0;

    const tenter = () => {
      if (annule) return;
      const cible = document.getElementById(id);

      if (!cible) {
        // Jusqu'à ~2 s : le temps que le module de la page soit récupéré et rendu.
        if (essais++ < 40) setTimeout(tenter, 50);
        return;
      }

      const y = cible.getBoundingClientRect().top + window.scrollY - DECALAGE_ENTETE;
      window.scrollTo({ top: Math.max(y, 0), behavior: 'smooth' });

      // Signalement visuel : sur une page longue, arriver au bon endroit sans rien voir
      // changer laisse douter d'avoir atterri où l'on voulait.
      cible.classList.add('section-visee');
      setTimeout(() => cible.classList.remove('section-visee'), DUREE_SURBRILLANCE);
    };

    tenter();
    return () => { annule = true; };
  }, [pathname, hash]);

  return null;
}
