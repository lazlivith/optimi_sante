import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { catalogService } from '../../api/catalogService';
import type { Category } from '../../api/catalogService';
import { ProductCard } from '../catalog/ProductCard';
import { visuelAFaire } from '../../api/productMediaService';

/**
 * Rayons dont cette section vit. Ce sont des identifiants du catalogue, pas des libellés :
 * renommer « Cabinet médical » en boutique ne casse rien, changer son slug si.
 *
 * <p>Si l'un des deux disparaît, la partie correspondante s'efface au lieu d'afficher une
 * rangée vide ou un lien mort — voir le repli plus bas.</p>
 */
const RAYON_CABINET = 'cabinet-medical';
const RAYON_MOBILIER = 'mobilier-medicale';

/** Trois cartes de front, comme la maquette ; le reste défile. */
const CARTES_VISIBLES = 3;

const nomLisible = (nom: string) => nom.replace(/&amp;/g, '&');

/**
 * « Votre cabinet médical clés en main » : équiper un cabinet, d'un seul rayon.
 *
 * <p><b>Ce qu'elle remplace.</b> La section précédente s'intitulait « Dispositifs de pointe »
 * et affichait les trois premiers produits du catalogue, dans l'ordre où la base les renvoie.
 * On y trouvait donc un coussin d'allaitement et une bandelette réactive : le titre annonçait
 * une sélection là où il n'y avait aucun critère. Ici, les produits viennent tous du rayon
 * « Cabinet médical » — divans d'examen, fauteuils, marchepieds, porte-sérum — et le titre dit
 * exactement ce qu'on voit.</p>
 *
 * <p><b>Les deux liens mènent au même endroit que ce qu'ils annoncent.</b> « Voir tout » ouvre
 * le rayon Cabinet médical, « Découvrir » ouvre le rayon Mobilier médical. L'ancienne section
 * renvoyait ses deux liens vers le catalogue entier.</p>
 */
export function CabinetSection({ categories }: { categories: Category[] }) {
  const [debut, setDebut] = useState(0);

  const cabinet = categories.find((c) => c.slug === RAYON_CABINET);
  const mobilier = categories.find((c) => c.slug === RAYON_MOBILIER);

  const { data } = useQuery({
    queryKey: ['rayon-cabinet', cabinet?.id],
    queryFn: () => catalogService.getProducts({ categoryId: cabinet!.id, size: 12 }),
    enabled: Boolean(cabinet),
  });

  const produits = data?.content ?? [];
  if (produits.length === 0) return null;

  const precedent = debut > 0;
  const suivant = debut + CARTES_VISIBLES < produits.length;

  return (
    <section className="container mx-auto px-4 py-10 md:px-8">
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div>
          <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-2xl font-bold text-brand-dark lg:text-3xl">
              Votre cabinet médical clés en main
            </h2>
            <Link
              to={`/category/${RAYON_CABINET}`}
              className="flex items-center gap-1.5 text-sm font-semibold text-brand hover:underline"
            >
              Voir tout <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
          </div>

          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            {produits.slice(debut, debut + CARTES_VISIBLES).map((p) => (
              <ProductCard key={p.id} product={p} actionEtendue />
            ))}
          </div>

          {(precedent || suivant) && (
            <div className="mt-5 flex items-center gap-2">
              <button
                type="button"
                onClick={() => setDebut((i) => Math.max(0, i - CARTES_VISIBLES))}
                disabled={!precedent}
                className="flex h-9 w-9 items-center justify-center rounded-full border
                           border-slate-200 bg-white text-brand-dark transition-colors
                           hover:border-brand hover:text-brand disabled:opacity-30"
              >
                <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                <span className="sr-only">Produits précédents</span>
              </button>
              <button
                type="button"
                onClick={() => setDebut((i) =>
                  Math.min(produits.length - CARTES_VISIBLES, i + CARTES_VISIBLES))}
                disabled={!suivant}
                className="flex h-9 w-9 items-center justify-center rounded-full border
                           border-slate-200 bg-white text-brand-dark transition-colors
                           hover:border-brand hover:text-brand disabled:opacity-30"
              >
                <ChevronRight className="h-4 w-4" aria-hidden="true" />
                <span className="sr-only">Produits suivants</span>
              </button>
            </div>
          )}
        </div>

        {/* Le panneau ne s'affiche que si le rayon existe et contient quelque chose : un
            « Découvrir » vers une page vide vaut moins que pas de panneau du tout. */}
        {mobilier && (mobilier.productCount ?? 0) > 0 && (
          <PanneauMobilier rayon={mobilier} />
        )}
      </div>
    </section>
  );
}

/**
 * Le panneau « Mobilier médical », et son bouton « Découvrir ».
 *
 * <p>La maquette de référence y pose une photo d'ambiance de cabinet. Nous n'en avons pas :
 * le catalogue ne contient que des packshots. Plutôt qu'aller en chercher une ailleurs — c'est
 * ce que faisait la section « Catégories » avec ses photos Unsplash —, le panneau montre un
 * produit du rayon, sur un aplat de la charte.</p>
 */
function PanneauMobilier({ rayon }: { rayon: Category }) {
  const nombre = rayon.productCount ?? 0;

  return (
    <div className="flex flex-col justify-between gap-5 overflow-hidden rounded-2xl
                    bg-brand-light p-6">
      <div>
        <p className="mb-2 text-xs font-bold uppercase tracking-[0.16em] text-brand">
          {nomLisible(rayon.name)}
        </p>
        <h3 className="text-xl font-bold leading-snug text-brand-dark">
          Équipez la salle, pas seulement la consultation
        </h3>
        {/* Le chiffre vient du rayon lui-même : il est vrai aujourd'hui et le restera
            quand le catalogue changera, sans que personne ait à y repenser. */}
        <p className="mt-2 text-sm text-slate-600">
          {nombre} référence{nombre > 1 ? 's' : ''} disponible{nombre > 1 ? 's' : ''}, de la
          table pliante au fauteuil de soins.
        </p>
        <Link
          to={`/category/${rayon.slug}`}
          className="mt-5 inline-flex items-center gap-2 rounded-full bg-brand px-5 py-2.5
                     text-sm font-bold text-white transition-colors hover:bg-brand-fonce"
        >
          Découvrir <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
      </div>

      {!visuelAFaire(rayon.imageUrl) && (
        <div className="flex h-32 items-center justify-center">
          {/* alt="" : la photo illustre le rayon que le titre au-dessus vient de nommer. */}
          <img
            src={rayon.imageUrl ?? undefined}
            alt=""
            loading="lazy"
            className="max-h-full max-w-full object-contain"
          />
        </div>
      )}
    </div>
  );
}
