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
 * <p><b>Deux colonnes à toutes les largeurs.</b> La grille ne passait à deux colonnes qu'au-delà
 * de 1024 px : en dessous, l'image se posait au-dessus du texte et la bannière atteignait
 * 600 pixels sur une tablette — le reste de la page disparaissait sous la ligne de flottaison.
 * Le texte tient désormais à gauche et le visuel à droite dès le téléphone, comme une bande.</p>
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
  // à une diapositive donne l'impression d'un bug ; le survol à la souris ou le focus clavier
  // — on ne dérobe pas sous les yeux ce que quelqu'un est en train de lire (WCAG 2.2.2) ; et
  // le réglage système de réduction des animations, qui l'arrête complètement et reste le
  // moyen d'arrêt sur un appareil tactile, où il n'y a pas de survol.
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

  const rang = Math.min(current, total - 1);

  return (
    <section
      aria-roledescription="carrousel"
      aria-label="Promotions en cours"
      // Survol : uniquement pour un pointeur qui survole VRAIMENT — souris ou stylet.
      // Un ecran tactile emet lui aussi une entree de pointeur des qu'un doigt effleure la
      // zone, mais jamais la sortie correspondante : le carrousel restait alors fige pour
      // le reste de la visite, ce qui est exactement ce qu'on observait sur telephone.
      onPointerEnter={(e) => { if (e.pointerType !== 'touch') setEnPause(true); }}
      onPointerLeave={(e) => { if (e.pointerType !== 'touch') setEnPause(false); }}
      // Le focus clavier, lui, vaut sur tous les appareils : on ne derobe pas sous les yeux
      // ce que quelqu'un est en train de parcourir a la tabulation.
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

      {/* Les diapositives sont toutes rendues cote a cote et le rail coulisse : le passage
          d'une promotion a l'autre se voit, au lieu d'un remplacement instantane ou l'image
          semblait clignoter. `motion-reduce` rend le saut sec a qui a demande moins
          d'animations — le contenu change pareil, c'est le mouvement qui disparait. */}
      <div className="relative overflow-hidden">
        <div
          className="flex transition-transform duration-500 ease-out motion-reduce:transition-none"
          style={{ transform: `translateX(-${rang * 100}%)` }}
        >
          {products.map((produit, i) => (
            <Diapositive
              key={produit.id}
              produit={produit}
              rang={i}
              total={total}
              actif={i === rang}
            />
          ))}
        </div>
      </div>

      {total > 1 && (
        <>
          {/* Masquees sur telephone : a cette largeur elles mordraient sur le texte de la
              bande, et les pastilles suffisent a se deplacer. */}
          <button
            type="button" aria-label="Promotion précédente"
            onClick={() => setCurrent((c) => (c - 1 + total) % total)}
            className="absolute left-1 top-1/2 z-10 flex h-8 w-8 -translate-y-1/2 items-center
                       justify-center rounded-full bg-brand text-white shadow-md
                       transition-colors hover:bg-brand-fonce sm:left-2 sm:h-9 sm:w-9
                       lg:left-4 lg:h-10 lg:w-10"
          >
            <ChevronLeft className="h-4 w-4 sm:h-5 sm:w-5" aria-hidden="true" />
          </button>
          <button
            type="button" aria-label="Promotion suivante"
            onClick={() => setCurrent((c) => (c + 1) % total)}
            className="absolute right-1 top-1/2 z-10 flex h-8 w-8 -translate-y-1/2 items-center
                       justify-center rounded-full bg-brand text-white shadow-md
                       transition-colors hover:bg-brand-fonce sm:right-2 sm:h-9 sm:w-9
                       lg:right-4 lg:h-10 lg:w-10"
          >
            <ChevronRight className="h-4 w-4 sm:h-5 sm:w-5" aria-hidden="true" />
          </button>

          <div className="absolute bottom-2 left-1/2 flex -translate-x-1/2 gap-2 lg:bottom-4">
            {products.map((p, i) => (
              // La pastille visible reste fine, mais la zone cliquable fait 24 px de haut :
              // un point de 8 px sur 8 est hors d'atteinte au doigt (WCAG 2.2, critere 2.5.8
              // — cible d'au moins 24x24). Le repere visuel et la cible ne sont pas le meme
              // objet, et c'est ce qui permet de grossir l'un sans alourdir l'autre.
              <button
                key={p.id} type="button"
                onClick={() => setCurrent(i)}
                aria-label={`Promotion ${i + 1} sur ${total}`}
                aria-current={i === rang}
                className="group flex h-6 items-center justify-center px-2"
              >
                <span
                  aria-hidden="true"
                  className={`block h-2 rounded-full transition-all ${
                    i === rang ? 'w-7 bg-brand' : 'w-2 bg-slate-300 group-hover:bg-slate-400'
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

/** Titre dont le niveau depend de l'etat de la diapositive. */
function Titre({ niveau, className, children }: {
  niveau: 'h1' | 'h2';
  className: string;
  children: React.ReactNode;
}) {
  const Balise = niveau;
  return <Balise className={className}>{children}</Balise>;
}

/**
 * Une promotion du rail.
 *
 * <p>Les diapositives hors champ restent dans le document — c'est ce qui permet au rail de
 * coulisser — mais elles sortent de l'ordre de tabulation et du lecteur d'écran : sinon la
 * tabulation traverserait des liens invisibles, et la page annoncerait cinq promotions
 * là où une seule est affichée.</p>
 */
function Diapositive({ produit, rang, total, actif }: {
  produit: Product;
  rang: number;
  total: number;
  actif: boolean;
}) {
  const remise = produit.basePrice > 0
    ? Math.round((1 - produit.finalPrice / produit.basePrice) * 100)
    : 0;

  return (
    <div
      aria-roledescription="diapositive"
      aria-label={`Promotion ${rang + 1} sur ${total}`}
      aria-hidden={!actif}
      className={`w-full shrink-0 ${actif ? '' : 'pointer-events-none'}`}
    >
      <div
        className="relative grid grid-cols-[1.3fr_1fr] items-stretch gap-3
                   sm:grid-cols-[1.2fr_1fr]
                   pl-11 pr-10 py-4 sm:gap-5 sm:pl-14 sm:pr-12 sm:py-5
                   lg:grid-cols-[1.05fr_1fr] lg:gap-4 lg:py-10 lg:pl-20 lg:pr-14
                   h-[10.5rem] sm:h-[12rem] lg:h-[22rem]"
      >
        {/* ── Texte ─────────────────────────────────────────────────────────────────── */}
        <div className="flex max-w-xl flex-col justify-center">
          {remise > 0 && (
            // Le surtitre porte la remise : c'est l'information qui décide, elle passe avant
            // le nom du produit plutôt qu'après.
            <p className="mb-1 text-[9px] font-bold uppercase tracking-[0.14em]
                          text-brand-accent sm:mb-2 sm:tracking-[0.18em] sm:text-xs">
              Promotion — {remise} % de remise
            </p>
          )}

          {/* La diapositive affichee porte le titre principal de la page d'accueil : c'etait
              deja le cas avant que le rail ne rende toutes les promotions a la fois, et cinq
              <h1> simultanes n'auraient aucun sens. Les autres restent en second niveau. */}
          <Titre
            niveau={actif ? 'h1' : 'h2'}
            className="mb-1 line-clamp-2 text-sm font-extrabold uppercase leading-[1.15]
                       tracking-tight text-brand-dark sm:mb-1.5 sm:text-lg
                       lg:text-[2rem] lg:leading-[1.1]"
          >
            {produit.name}
          </Titre>

          {produit.category?.name && (
            // Sacrifiee sur telephone : a cette largeur, la bande doit tenir en quatre lignes
            // et le nom du produit dit deja de quoi il s'agit.
            <p className="mb-2 hidden text-sm text-slate-600 lg:mb-5 lg:block">
              {produit.category.name}
            </p>
          )}

          <div className="mb-2.5 flex flex-wrap items-baseline gap-x-2 gap-y-0.5 sm:mb-4 sm:gap-x-3 lg:mb-6">
            {/* L'orange de la charte plafonne à 3,00:1 sur fond clair : c'est sa variante
                assombrie qui porte le prix, à 5,13:1. */}
            <span className="text-base font-extrabold text-brand-accent sm:text-xl lg:text-4xl">
              {produit.finalPrice.toFixed(0)} €
            </span>
            <span className="text-xs text-slate-600/80 line-through sm:text-base lg:text-lg">
              {produit.basePrice.toFixed(0)} €
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 sm:gap-x-6 sm:gap-y-3">
            {/* Une seule action mise en avant. Deux boutons côte à côte se concurrencent, et
                le visiteur hésite là où il n'y a rien à arbitrer. */}
            <Link
              to={cheminProduit(produit.slug)}
              tabIndex={actif ? undefined : -1}
              className="group inline-flex items-center gap-1.5 rounded-full bg-brand px-3 py-1.5
                         text-[11px] font-bold text-white transition-colors hover:bg-brand-fonce
                         sm:gap-2 sm:px-6 sm:py-2.5 sm:text-sm lg:px-7 lg:py-3 lg:text-base"
            >
              Achetez maintenant
              <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5 sm:h-4 sm:w-4" />
            </Link>
            <Link
              to="/catalog?promo=true"
              tabIndex={actif ? undefined : -1}
              className="hidden text-sm font-semibold text-brand underline-offset-4
                         hover:underline sm:inline"
            >
              Toutes les promotions
            </Link>
          </div>

          {produit.promoEndsAt && (
            <p className="mt-2 hidden text-xs italic text-slate-600 lg:mt-5 lg:block">
              Offre valable jusqu'au{' '}
              {new Date(produit.promoEndsAt).toLocaleDateString('fr-FR')}
            </p>
          )}
        </div>

        {/* ── Visuel ────────────────────────────────────────────────────────────────── */}
        {/* `min-h-0` sur les deux : un element flex ne descend pas sous la hauteur de son
            contenu (`min-height: auto`), et l'image imposait donc ses 302 px malgre `h-full`
            — elle depassait la bande et s'y trouvait rognee. */}
        <div className="relative flex min-h-0 items-stretch justify-center lg:justify-end">
          <div className="relative h-full min-h-0 w-full overflow-hidden">
            <PromoProductVisual
              product={produit}
              objectFit="contain"
              // La premiere diapositive ouvre la page : la differer retarderait le premier
              // affichage. Les suivantes attendent, elles ne sont pas encore a l'ecran.
              chargement={rang === 0 ? 'eager' : 'lazy'}
              className="h-full w-full drop-shadow-[0_18px_28px_rgba(11,36,48,0.14)]"
              iconClassName="w-16 h-16"
            />
            {remise > 0 && (
              // Encre sur l'orange de la charte : 5,35:1. Du blanc n'y tiendrait pas (3,00:1),
              // et c'est justement ce que faisait la version précédente.
              <span className="absolute left-0 top-0 rounded-full bg-brand-orange px-2 py-0.5
                               text-[10px] font-bold text-brand-dark shadow-sm
                               sm:px-3 sm:py-1 sm:text-xs lg:left-2">
                −{remise} %
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
