import type { RefObject } from 'react';
import { Link } from 'react-router-dom';
import { ShoppingCart, User as UserIcon, X } from 'lucide-react';
import { LogoOptimi } from './marque/LogoOptimi';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';

/**
 * Menu de navigation du site sur telephone et tablette.
 *
 * <p>Sous 1024 px, la rangee de liens de l'en-tete devenait une bande qui defile
 * horizontalement : six intitules dont « Formations Medicales » et « Devenir Partenaire »
 * ne tiennent pas sur 360 px, et rien n'indique qu'il en reste hors champ. Ils passent donc
 * dans un panneau plein ecran, une entree par ligne — la forme attendue sur mobile.</p>
 *
 * <p>Le panneau reprend l'en-tete du site (fermeture, logotype, compte, panier) parce qu'il
 * couvre tout l'ecran : sans cela, on perdrait l'acces au panier tant qu'il est ouvert.</p>
 *
 * <p>Ferme, il est mis en `invisible` en plus d'etre decale hors ecran, sans quoi ses liens
 * resteraient atteignables a la tabulation alors que personne ne les voit. Meme regle que le
 * tiroir des espaces d'administration.</p>
 */
export function MenuMobile({ ouvert, fermer, fermerSurLien, panneau }: {
  ouvert: boolean;
  fermer: () => void;
  fermerSurLien: (e: React.MouseEvent) => void;
  panneau: RefObject<HTMLElement | null>;
}) {
  const { user } = useAuth();
  const { totalItems } = useCart();

  return (
    <div
      id="menu-site"
      ref={panneau as RefObject<HTMLDivElement>}
      className={[
        'fixed inset-0 z-50 bg-white flex flex-col lg:hidden',
        'transition-[transform,visibility] duration-200 motion-reduce:transition-none',
        ouvert ? 'translate-x-0 visible' : '-translate-x-full invisible',
      ].join(' ')}
      onClick={fermerSurLien}
    >
      <div className="h-20 px-4 flex items-center gap-3 border-b border-slate-100 shrink-0">
        <button
          type="button"
          onClick={fermer}
          aria-label="Fermer le menu"
          className="p-2 -ml-2 rounded-lg text-slate-500 hover:bg-slate-100"
        >
          <X className="w-6 h-6" aria-hidden="true" />
        </button>

        <Link to="/" aria-label="Optimi Santé — accueil" className="shrink-0">
          <LogoOptimi fond="clair" signature={false} className="h-10 w-auto max-w-[150px]" />
        </Link>

        <div className="ml-auto flex items-center gap-1.5">
          {/* Le panneau couvre l'ecran : sans ces deux raccourcis, le compte et le panier
              seraient inaccessibles tant qu'il est ouvert. L'etat de connexion est celui de
              la barre du haut — une seule source, pas deux comportements. */}
          <Link
            to={user ? '/profile' : '/login'}
            aria-label={user ? 'Mon espace' : 'Se connecter'}
            className="p-2.5 rounded-full border border-slate-200 text-slate-600 hover:bg-slate-50"
          >
            <UserIcon className="w-5 h-5" aria-hidden="true" />
          </Link>
          <Link
            to="/cart"
            aria-label="Panier"
            className="relative p-2.5 rounded-full border border-slate-200 text-slate-600 hover:bg-slate-50"
          >
            <ShoppingCart className="w-5 h-5" aria-hidden="true" />
            {totalItems > 0 && (
              <span className="absolute -top-0.5 -right-0.5 inline-flex items-center justify-center w-4 h-4 text-[9px] font-bold text-white bg-danger rounded-full">
                {totalItems}
              </span>
            )}
          </Link>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-5 divide-y divide-slate-100">
        <Entree to="/">Accueil</Entree>
        <Entree to="/formations">Formations Médicales</Entree>
        <Entree to="/services">Nos Services</Entree>
        <Entree to="/devenir-partenaire">Devenir Partenaire</Entree>
        <Entree to="/catalog">Catalogue</Entree>
        <Entree to="/blog">Blog &amp; événements</Entree>
        <Entree to="/catalog?promo=true">
          Promotions
          <span className="ml-2 bg-danger text-white text-[9px] font-bold px-1.5 py-0.5 rounded uppercase align-middle">
            New
          </span>
        </Entree>
      </nav>
    </div>
  );
}

/** Une entree du menu : pleine largeur, assez haute pour etre touchee sans viser. */
function Entree({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <Link
      to={to}
      className="block py-4 text-[15px] font-semibold text-brand-dark hover:text-brand transition-colors"
    >
      {children}
    </Link>
  );
}
