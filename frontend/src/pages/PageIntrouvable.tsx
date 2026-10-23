import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { usePageMeta } from '../hooks/usePageMeta';

/**
 * Une adresse qui ne mène nulle part, dans l'espace boutique.
 *
 * <p><b>Elle ne menait nulle part au sens propre.</b> L'espace boutique n'avait aucune route
 * de repli : une adresse inconnue affichait l'en-tête, le pied de page, et rien entre les
 * deux. Pas une erreur — une page blanche, que le visiteur ne peut distinguer d'un
 * chargement qui n'a pas abouti.</p>
 *
 * <p>Deux sorties plutôt qu'une : l'accueil pour qui s'est trompé d'adresse, le catalogue
 * pour qui cherchait un produit — c'est de loin le cas le plus fréquent ici.</p>
 */
export function PageIntrouvable() {
  usePageMeta('Page introuvable');

  return (
    <div className="min-h-[60vh] bg-slate-50">
      <div className="container mx-auto max-w-xl px-6 py-24 text-center">
        <p className="text-sm font-bold uppercase tracking-wider text-brand">Erreur 404</p>
        <h1 className="mt-2 text-2xl font-bold text-brand-dark">Cette page n'existe pas</h1>
        <p className="mt-3 leading-relaxed text-slate-600">
          L'adresse est peut-être incomplète, ou la page a été déplacée.
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <Link
            to="/"
            className="rounded-xl border border-slate-300 px-6 py-3 font-bold text-brand-dark
                       transition-colors hover:bg-white"
          >
            Retour à l'accueil
          </Link>
          <Link
            to="/catalog"
            className="inline-flex items-center gap-2 rounded-xl bg-brand px-6 py-3 font-bold
                       text-white transition-colors hover:bg-brand-fonce"
          >
            Voir le catalogue <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
      </div>
    </div>
  );
}
