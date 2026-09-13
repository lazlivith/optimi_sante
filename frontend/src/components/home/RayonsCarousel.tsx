import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { catalogService } from '../../api/catalogService';
import type { Category } from '../../api/catalogService';
import { ProductCard } from '../catalog/ProductCard';
import { visuelAFaire } from '../../api/productMediaService';

/** Onglets proposés. Neuf tiennent sur deux lignes sans que la rangée devienne un mur. */
const ONGLETS = 9;

/** Produits chargés par rayon : de quoi faire défiler sans rapatrier un rayon de 387 fiches. */
const PRODUITS_PAR_RAYON = 12;

const nomLisible = (nom: string) => nom.replace(/&amp;/g, '&');

/**
 * Parcourir le catalogue rayon par rayon, depuis l'accueil.
 *
 * <p><b>Ce que la section affichait avant.</b> Elle s'intitulait « Nos best-sellers par
 * catégorie » et recevait {@code products.slice(5, 11)} — six produits pris au rang 5 du
 * catalogue. Ses onglets étaient déduits des catégories de ces six-là, et chaque onglet
 * refiltrait la même poignée : un ou deux produits par onglet. D'où la rangée maigre.</p>
 *
 * <p>Chaque onglet interroge maintenant son rayon. Les produits sont ceux du rayon, en nombre,
 * et le passage d'un onglet à l'autre ne recharge pas ce qui a déjà été vu — react-query garde
 * chaque rayon en cache sous sa propre clé.</p>
 *
 * <p><b>Pourquoi le titre ne dit plus « best-sellers ».</b> Il faudrait des ventes pour
 * classer des ventes. La base en compte 37 lignes, toutes de test, réparties sur 14 produits :
 * un classement bâti dessus désignerait comme meilleures ventes des articles que personne n'a
 * achetés, et ne couvrirait que huit rayons sur 126. Le jour où les commandes réelles
 * arriveront, le classement deviendra calculable et le titre récupérable — voir le mot de
 * l'équipe dans le journal de ce changement.</p>
 */
export function RayonsCarousel({ categories }: { categories: Category[] }) {
  const [actif, setActif] = useState(0);
  const piste = useRef<HTMLDivElement>(null);

  const rayons = categories
    .filter((c) => c.productCount !== 0 && !visuelAFaire(c.imageUrl))
    .sort((a, b) => (b.productCount ?? 0) - (a.productCount ?? 0))
    .slice(0, ONGLETS);

  // Le rayon actif peut sortir des bornes si la liste raccourcit pendant que la page est
  // ouverte — un produit désactivé qui vide son rayon suffit.
  const rayon = rayons[Math.min(actif, rayons.length - 1)];

  const { data, isLoading } = useQuery({
    queryKey: ['rayon-apercu', rayon?.id],
    queryFn: () => catalogService.getProducts({
      categoryId: rayon!.id, size: PRODUITS_PAR_RAYON,
    }),
    enabled: Boolean(rayon),
  });

  if (rayons.length === 0) return null;

  const produits = data?.content ?? [];

  // Défiler d'un écran plutôt que d'un nombre fixe de cartes : la piste montre trois cartes
  // sur un téléphone et sept sur un grand écran, et avancer de sept sur un téléphone
  // sauterait quatre produits sans que personne ne les ait vus.
  const defiler = (sens: -1 | 1) => {
    const p = piste.current;
    if (p) p.scrollBy({ left: sens * p.clientWidth * 0.9, behavior: 'smooth' });
  };

  return (
    <section className="container mx-auto px-4 py-10 md:px-8">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-2xl font-bold text-brand-dark lg:text-3xl">
          Nos produits par rayon
        </h2>
        <Link
          to={`/category/${rayon.slug}`}
          className="flex items-center gap-1.5 text-sm font-semibold text-brand hover:underline"
        >
          Voir tout le rayon <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
      </div>

      {/* Des boutons, pas des onglets ARIA : de vrais onglets imposent la navigation aux
          flèches du clavier, que personne n'attend sur une rangée de filtres. `aria-pressed`
          dit lequel est enfoncé, ce qui est exactement ce qui se passe. */}
      <div className="mb-6 flex flex-wrap gap-2">
        {rayons.map((c, i) => (
          <button
            key={c.id}
            type="button"
            aria-pressed={c.id === rayon.id}
            onClick={() => {
              setActif(i);
              // Sans cela, changer d'onglet garde la piste là où on l'avait poussée : le
              // nouveau rayon s'ouvre à son cinquième produit.
              piste.current?.scrollTo({ left: 0 });
            }}
            className={`rounded-full px-4 py-2 text-sm font-semibold transition-colors ${
              c.id === rayon.id
                ? 'bg-brand text-white'
                : 'border border-slate-200 bg-white text-slate-600 hover:border-brand hover:text-brand'
            }`}
          >
            {nomLisible(c.name)}
          </button>
        ))}
      </div>

      <div className="relative">
        <div
          ref={piste}
          // `aria-label` nomme le rayon : sans lui, un lecteur d'écran annonce une liste de
          // douze produits sans dire de quel rayon il s'agit, alors que le bouton qui vient
          // d'être pressé est déjà loin dans l'ordre de lecture.
          aria-label={`Produits du rayon ${nomLisible(rayon.name)}`}
          className="flex snap-x snap-mandatory gap-4 overflow-x-auto scroll-smooth pb-2
                     [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          {isLoading && produits.length === 0
            ? Array.from({ length: 6 }).map((_, i) => (
                <div
                  key={i}
                  className="w-44 shrink-0 animate-pulse rounded-2xl border border-slate-200
                             bg-white p-4 sm:w-48"
                >
                  <div className="mb-4 h-28 rounded-xl bg-slate-100" />
                  <div className="mb-2 h-4 w-4/5 rounded bg-slate-100" />
                  <div className="h-4 w-1/2 rounded bg-slate-100" />
                </div>
              ))
            : produits.map((p) => (
                <div key={p.id} className="w-44 shrink-0 snap-start sm:w-48">
                  <ProductCard product={p} />
                </div>
              ))}
        </div>

        {/* Les flèches n'apparaissent qu'à partir du moment où il y a de quoi défiler, et
            seulement sur les écrans où la piste ne tient pas tout : au doigt, on pousse. */}
        {produits.length > 4 && (
          <>
            <button
              type="button"
              onClick={() => defiler(-1)}
              className="absolute -left-3 top-24 hidden h-10 w-10 -translate-y-1/2 items-center
                         justify-center rounded-full border border-slate-200 bg-white
                         text-brand-dark shadow-md transition-colors hover:border-brand
                         hover:text-brand md:flex"
            >
              <ChevronLeft className="h-5 w-5" aria-hidden="true" />
              <span className="sr-only">Produits précédents</span>
            </button>
            <button
              type="button"
              onClick={() => defiler(1)}
              className="absolute -right-3 top-24 hidden h-10 w-10 -translate-y-1/2 items-center
                         justify-center rounded-full border border-slate-200 bg-white
                         text-brand-dark shadow-md transition-colors hover:border-brand
                         hover:text-brand md:flex"
            >
              <ChevronRight className="h-5 w-5" aria-hidden="true" />
              <span className="sr-only">Produits suivants</span>
            </button>
          </>
        )}
      </div>
    </section>
  );
}
