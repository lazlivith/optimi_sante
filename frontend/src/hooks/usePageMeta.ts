import { useEffect } from 'react';

const SITE_NAME = 'Optimi Santé';
const DEFAULT_DESCRIPTION =
  "Optimi Santé : dispositifs médicaux certifiés CE, formations cliniques dans les CHU partenaires et accompagnement mobilité médicale Afrique → France.";

function setMetaTag(attr: 'name' | 'property', key: string, content: string) {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`);
  if (!el) {
    el = document.createElement('meta');
    el.setAttribute(attr, key);
    document.head.appendChild(el);
  }
  el.setAttribute('content', content);
}

/**
 * Domaine officiel du site, sans barre finale.
 *
 * <p>Vide tant qu'aucun domaine définitif n'est arrêté — et c'est volontaire. Une balise
 * canonique doit porter une adresse absolue : sans domaine, on ne peut qu'en inventer un ou
 * désigner celui d'un tiers. La précédente désignait un domaine redirigeant (301) vers
 * l'ancien site WordPress, à qui elle attribuait le référencement de nos pages.</p>
 */
const ORIGINE = (import.meta.env.VITE_SITE_URL ?? '').trim().replace(/\/+$/, '');

/**
 * Pose — ou retire — la balise canonique de la page courante.
 *
 * <p>Sans domaine renseigné, la balise est <b>retirée</b> plutôt que laissée vide : un
 * {@code <link rel="canonical" href="">} est interprété par certains moteurs comme désignant
 * la page courante, ce qui est correct par accident, mais d'autres l'ignorent ou s'en méfient.
 * Ne rien déclarer est le seul comportement sans ambiguïté.</p>
 */
function setCanonical(chemin: string) {
  let el = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');

  if (!ORIGINE) {
    el?.remove();
    return;
  }
  if (!el) {
    el = document.createElement('link');
    el.setAttribute('rel', 'canonical');
    document.head.appendChild(el);
  }
  el.setAttribute('href', ORIGINE + chemin);
}

/**
 * Met à jour le <title>, les meta description/Open Graph et la canonique de la page courante.
 * Purement additif (référencement) : n'affecte aucune logique métier.
 */
export function usePageMeta(title: string, description: string = DEFAULT_DESCRIPTION) {
  useEffect(() => {
    const fullTitle = title ? `${title} · ${SITE_NAME}` : `${SITE_NAME} — Négoce médical & Mobilité clinique`;
    document.title = fullTitle;

    setMetaTag('name', 'description', description);
    setMetaTag('property', 'og:title', fullTitle);
    setMetaTag('property', 'og:description', description);

    // Le chemin seul, sans la requête ni le fragment : « /catalog?page=2 » et « /catalog »
    // sont la même page aux yeux d'un moteur, et deux canoniques différentes les feraient
    // concourir l'une contre l'autre.
    const chemin = window.location.pathname;
    setCanonical(chemin);
    if (ORIGINE) {
      setMetaTag('property', 'og:url', ORIGINE + chemin);
    }
  }, [title, description]);
}
