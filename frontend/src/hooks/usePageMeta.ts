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
 * Met à jour le <title> et les meta description/Open Graph de la page courante.
 * Purement additif (référencement) : n'affecte aucune logique métier.
 */
export function usePageMeta(title: string, description: string = DEFAULT_DESCRIPTION) {
  useEffect(() => {
    const fullTitle = title ? `${title} · ${SITE_NAME}` : `${SITE_NAME} — Négoce médical & Mobilité clinique`;
    document.title = fullTitle;

    setMetaTag('name', 'description', description);
    setMetaTag('property', 'og:title', fullTitle);
    setMetaTag('property', 'og:description', description);
  }, [title, description]);
}
