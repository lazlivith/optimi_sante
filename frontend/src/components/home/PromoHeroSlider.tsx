import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';
import { cheminProduit } from '../../api/catalogService';
import type { Product } from '../../api/catalogService';
import { PromoProductVisual } from '../catalog/PromoProductVisual';
import { useReducedMotion } from '../../hooks/useReducedMotion';

const DUREE_DIAPOSITIVE_MS = 6000;

/**
 * Bandeau des promotions en cours.
 *
 * <p><b>Une bannière, pas un écran.</b> La version précédente occupait près de 800 pixels de
 * haut en bloc sombre : le visiteur arrivait sur un panneau publicitaire et devait faire
 * défiler pour atteindre la boutique. Celle-ci tient en un tiers de cette hauteur, sur fond
 * clair — le produit y gagne, et la page commence enfin par la page.</p>
 *
 * <p><b>Le fond clair change la place de l'image.</b> Elle nécessitait auparavant un cadre blanc
 * dédié, parce qu'un packshot sur fond blanc posé sur du bleu nuit laisse un rectangle. Sur un
 * fond clair, elle se pose directement : un cadre de moins, et le produit paraît plus grand
 * sans que rien ne grandisse.</p>
 *
 * <p>Un carrousel vide serait pire que des diapositives figées : quand aucune promotion n'est
 * active, la page d'accueil retombe sur ses bannières statiques (voir `HomePage`).</p>
 */
export function PromoHeroSlider({ products }: { products: Product[] }) {
  const [current, setCurrent] = useState(0);
  const [enPause, setEnPause] = useState(false);
  const total = products.length;
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const mouvementReduit = useReducedMotion();

  // Le défilement s'arrête dans trois cas : une seule promotion — faire clignoter un carrousel
  // à une diapositive donne l'impression d'un bug ; le survol ou le focus clavier — on ne
  // dérobe pas sous les yeux ce que quelqu'un est en train de lire (WCAG 2.2.2) ; et le
  // réglage système de réduction des animations, qui vaut ici comme ailleurs.
  const doitDefiler = total > 1 && !enPause && !mouvementReduit;

  useEffect(() => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    if (doitDefiler) {
      intervalRef.current = setInterval(
        () => setCurrent((c) => (c + 1) % total), DUREE_DIAPOSITIVE_MS);
    }
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, [doitDefiler, total]);

  // Si le nombre de promotions diminue pendant que la page est ouverte, l'index courant peut
  // pointer au-delà du tableau : on le ramène dans les bornes plutôt que de rendre du vide.
  useEffect(() => { if (current >= total) setCurrent(0); }, [total, current]);

  if (total === 0) return null;

  const produit = products[Math.min(current, total - 1)];
  const remise = produit.basePrice > 0
    ? Math.round((1 - produit.finalPrice / produit.basePrice) * 100)
    : 0;

  return (
    <section
      aria-roledescription="carrousel"
      aria-label="Promotions en cours"
      onMouseEnter={() => setEnPause(true)}
      onMouseLeave={() => setEnPause(false)}
      onFocusCapture={() => setEnPause(true)}
      onBlurCapture={() => setEnPause(false)}
      className="relative mx-4 md:mx-8 mt-4 overflow-hidden rounded-3xl bg-brand-cream
                 ring-1 ring-slate-200/70"
    >
      {/* Teinte de marque posee DERRIERE LE PRODUIT seulement. Le degre precedent traversait
          toute la largeur : sur son extremite teintee, l'ancien prix tombait a 2,17:1 et la
          categorie a 4,03:1. Une decoration ne doit pas se payer en lisibilite — elle se
          deplace donc la ou il n'y a pas de texte. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-y-0 right-0 w-full lg:w-3/5
                   bg-[radial-gradient(60%_70%_at_75%_50%,theme(colors.brand-light)_0%,transparent_70%)]"
      />
      <div
        aria-roledescription="diapositive"
        aria-label={`Promotion ${current + 1} sur ${total}`}
        className="relative grid items-center gap-6 lg:grid-cols-[1.05fr_1fr] lg:gap-4
                   px-6 py-8 sm:px-10 lg:py-10 lg:pl-16 lg:pr-0
                   min-h-[19rem] sm:min-h-[21rem] lg:min-h-[22rem]"
      >
        {/* ── Texte ─────────────────────────────────────────────────────────────────── */}
        <div className="order-2 max-w-xl lg:order-1">
          {remise > 0 && (
            // Le surtitre porte la remise : c'est l'information qui décide, elle passe avant
            // le nom du produit plutôt qu'après.
            <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-brand-accent">
              Promotion — {remise} % de remise
            </p>
          )}

          <h1 className="mb-1.5 line-clamp-2 text-2xl font-extrabold uppercase leading-[1.08]
                         tracking-tight text-brand-dark sm:text-3xl lg:text-[2.5rem]">
            {produit.name}
          </h1>

          {produit.category?.name && (
            <p className="mb-5 text-sm text-slate-600">{produit.category.name}</p>
          )}

          <div className="mb-6 flex flex-wrap items-baseline gap-x-3 gap-y-1">
            {/* L'orange de la charte plafonne à 3,00:1 sur fond clair : c'est sa variante
                assombrie qui porte le prix, à 5,13:1. */}
            <span className="text-3xl font-extrabold text-brand-accent lg:text-4xl">
              {produit.finalPrice.toFixed(0)} €
            </span>
            <span className="text-lg text-slate-600/80 line-through">
              {produit.basePrice.toFixed(0)} €
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
            {/* Une seule action mise en avant. Deux boutons côte à côte se concurrencent, et
                le visiteur hésite là où il n'y a rien à arbitrer. */}
            <Link
              to={cheminProduit(produit.slug)}
              className="group inline-flex items-center gap-2 rounded-full bg-brand px-7 py-3
                         text-base font-bold text-white transition-colors hover:bg-brand-fonce"
            >
              Achetez maintenant
              <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
            </Link>
            <Link
              to="/catalog?promo=true"
              className="text-sm font-semibold text-brand underline-offset-4 hover:underline"
            >
              Toutes les promotions
            </Link>
          </div>

          {produit.promoEndsAt && (
            <p className="mt-5 text-xs italic text-slate-600">
              Offre valable jusqu'au{' '}
              {new Date(produit.promoEndsAt).toLocaleDateString('fr-FR')}
            </p>
          )}
        </div>

        {/* ── Visuel ────────────────────────────────────────────────────────────────── */}
        <div className="relative order-1 flex justify-center lg:order-2 lg:justify-end">
          <div className="relative h-44 w-full max-w-sm sm:h-52 lg:h-72 lg:max-w-none">
            <PromoProductVisual
              key={produit.id}
              product={produit}
              objectFit="contain"
              // Premiere image de la page : la differer retarderait le premier affichage.
              chargement="eager"
              className="h-full w-full drop-shadow-[0_18px_28px_rgba(11,36,48,0.14)]"
              iconClassName="w-16 h-16"
            />
            {remise > 0 && (
              // Encre sur l'orange de la charte : 5,35:1. Du blanc n'y tiendrait pas (3,00:1),
              // et c'est justement ce que faisait la version précédente.
              <span className="absolute left-0 top-0 rounded-full bg-brand-orange px-3 py-1
                               text-xs font-bold text-brand-dark shadow-sm lg:left-2">
                −{remise} %
              </span>
            )}
          </div>
        </div>
      </div>

      {total > 1 && (
        <>
          <button
            type="button" aria-label="Promotion précédente"
            onClick={() => setCurrent((c) => (c - 1 + total) % total)}
            className="absolute left-3 top-1/2 z-10 flex h-10 w-10 -translate-y-1/2 items-center
                       justify-center rounded-full bg-brand text-white shadow-md
                       transition-colors hover:bg-brand-fonce md:left-5"
          >
            <ChevronLeft className="h-5 w-5" aria-hidden="true" />
          </button>
          <button
            type="button" aria-label="Promotion suivante"
            onClick={() => setCurrent((c) => (c + 1) % total)}
            className="absolute right-3 top-1/2 z-10 flex h-10 w-10 -translate-y-1/2 items-center
                       justify-center rounded-full bg-brand text-white shadow-md
                       transition-colors hover:bg-brand-fonce md:right-5"
          >
            <ChevronRight className="h-5 w-5" aria-hidden="true" />
          </button>

          <div className="absolute bottom-4 left-1/2 flex -translate-x-1/2 gap-2">
            {products.map((p, i) => (
              // La pastille visible reste fine, mais la zone cliquable fait 24 px de haut :
              // un point de 8 px sur 8 est hors d'atteinte au doigt (WCAG 2.2, critere 2.5.8
              // — cible d'au moins 24x24). Le repere visuel et la cible ne sont pas le meme
              // objet, et c'est ce qui permet de grossir l'un sans alourdir l'autre.
              <button
                key={p.id} type="button"
                onClick={() => setCurrent(i)}
                aria-label={`Promotion ${i + 1} sur ${total}`}
                aria-current={i === current}
                className="group flex h-6 items-center justify-center px-2"
              >
                <span
                  aria-hidden="true"
                  className={`block h-2 rounded-full transition-all ${
                    i === current ? 'w-7 bg-brand' : 'w-2 bg-slate-300 group-hover:bg-slate-400'
                  }`}
                />
              </button>
            ))}
          </div>
        </>
      )}
    </section>
  );
}
