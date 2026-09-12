import { useCallback, useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Play, X, ZoomIn } from 'lucide-react';
import { ProductImage } from '../common/ProductImage';
import { ProductVideo, type VideoProvider } from './ProductVideo';
import type { Product } from '../../api/catalogService';

/** Une vue de la galerie : une image, ou la vidéo de démonstration. */
type Vue =
  | { genre: 'image'; url: string | null | undefined; legende: string | null }
  | { genre: 'video'; url: string; provider: VideoProvider };

/**
 * Construit la suite des vues à parcourir.
 *
 * <p>Le visuel principal ouvre toujours la marche, même absent : sa place vide affiche le
 * pictogramme de repli, et le visiteur comprend qu'il regarde un produit sans photo plutôt
 * qu'un détail sorti de son contexte. La vidéo ferme la marche.</p>
 */
function construireVues(produit: Product): Vue[] {
  const vues: Vue[] = [
    { genre: 'image', url: produit.imageUrl, legende: null },
  ];

  for (const visuel of produit.gallery ?? []) {
    // Le serveur trie déjà par displayOrder ; on ne retrie pas, sous peine de faire diverger
    // l'ordre affiché au client de celui que l'administrateur a composé.
    vues.push({ genre: 'image', url: visuel.imageUrl, legende: visuel.caption });
  }

  if (produit.videoUrl && produit.videoProvider) {
    vues.push({ genre: 'video', url: produit.videoUrl, provider: produit.videoProvider });
  }

  return vues;
}

/**
 * Galerie de la fiche produit : visuel principal, visuels de détail, vidéo de démonstration.
 *
 * <p>Quand il n'y a qu'une seule vue — le cas de l'immense majorité du catalogue — ni vignettes
 * ni flèches n'apparaissent : l'affichage redevient exactement celui d'avant, une image seule
 * dans son cadre. Les commandes ne s'ajoutent que s'il y a réellement quelque chose à
 * parcourir.</p>
 */
export function ProductGallery({ product }: { product: Product }) {
  const vues = construireVues(product);
  const [index, setIndex] = useState(0);
  const [pleinEcran, setPleinEcran] = useState(false);

  // Un produit peut changer sous le composant (navigation d'une fiche à l'autre) : sans cela,
  // on garderait l'index du produit précédent, qui peut ne pas exister ici.
  useEffect(() => { setIndex(0); setPleinEcran(false); }, [product.id]);

  const total = vues.length;
  const aller = useCallback((pas: number) => {
    setIndex((i) => (i + pas + total) % total);
  }, [total]);

  const vue = vues[Math.min(index, total - 1)];
  const estVideo = vue.genre === 'video';

  // Le plein écran se ferme par Échap et se parcourt aux flèches : c'est ce qu'attend quiconque
  // a déjà agrandi une image ailleurs, et c'est le seul moyen d'en sortir au clavier.
  useEffect(() => {
    if (!pleinEcran) return;
    const surTouche = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setPleinEcran(false);
      if (e.key === 'ArrowRight') aller(1);
      if (e.key === 'ArrowLeft') aller(-1);
    };
    window.addEventListener('keydown', surTouche);
    // La page derrière ne doit pas défiler pendant que la vue agrandie est ouverte.
    const defilement = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', surTouche);
      document.body.style.overflow = defilement;
    };
  }, [pleinEcran, aller]);

  return (
    <div className="flex flex-col gap-4">
      <div className="aspect-square bg-white border border-slate-100 rounded-2xl overflow-hidden relative group p-6">
        {vue.genre === 'video' ? (
          <ProductVideo
            videoUrl={vue.url}
            videoProvider={vue.provider}
            productName={product.name}
          />
        ) : (
          <>
            {/* Visuel principal en `contain` : c'est l'image sur laquelle on décide d'acheter,
                elle doit montrer l'article entier. En `cover`, un équipement large était
                tronqué à gauche et à droite sans que rien ne l'indique. */}
            <ProductImage
              src={vue.url}
              alt={vue.legende ?? product.name}
              className="w-full h-full group-hover:scale-105 transition-transform duration-700"
              iconClassName="w-32 h-32 opacity-50"
              objectFit="contain"
            />
            {vue.url && (
              <button
                type="button"
                onClick={() => setPleinEcran(true)}
                className="absolute bottom-4 right-4 inline-flex items-center gap-1.5 rounded-full bg-white/90 px-3 py-1.5 text-xs font-semibold text-brand-dark opacity-0 shadow-sm transition-opacity focus-visible:opacity-100 group-hover:opacity-100"
                aria-label="Agrandir le visuel"
              >
                <ZoomIn className="h-3.5 w-3.5" />
                Agrandir
              </button>
            )}
          </>
        )}

        {total > 1 && !estVideo && (
          <>
            <button
              type="button"
              onClick={() => aller(-1)}
              className="absolute left-3 top-1/2 -translate-y-1/2 rounded-full bg-white/90 p-2 text-brand-dark shadow-sm transition hover:bg-white"
              aria-label="Visuel précédent"
            >
              <ChevronLeft className="h-5 w-5" />
            </button>
            <button
              type="button"
              onClick={() => aller(1)}
              className="absolute right-3 top-1/2 -translate-y-1/2 rounded-full bg-white/90 p-2 text-brand-dark shadow-sm transition hover:bg-white"
              aria-label="Visuel suivant"
            >
              <ChevronRight className="h-5 w-5" />
            </button>
          </>
        )}
      </div>

      {vue.genre === 'image' && vue.legende && (
        <p className="text-center text-sm text-slate-500">{vue.legende}</p>
      )}

      {total > 1 && (
        <ul className="flex flex-wrap gap-3" aria-label="Visuels du produit">
          {vues.map((v, i) => (
            <li key={`${v.genre}-${v.url ?? 'vide'}-${i}`}>
              <button
                type="button"
                onClick={() => setIndex(i)}
                aria-current={i === index}
                className={`h-16 w-16 overflow-hidden rounded-xl border bg-white p-1 transition ${
                  i === index
                    ? 'border-brand-green ring-2 ring-brand-green/30'
                    : 'border-slate-200 hover:border-slate-300'
                }`}
                aria-label={v.genre === 'video'
                  ? 'Vidéo de démonstration'
                  : `Visuel ${i + 1} sur ${total}`}
              >
                {v.genre === 'video' ? (
                  <span className="flex h-full w-full items-center justify-center rounded-lg bg-brand-dark">
                    <Play className="h-5 w-5 fill-white text-white" />
                  </span>
                ) : (
                  <ProductImage
                    src={v.url}
                    alt=""
                    className="h-full w-full"
                    iconClassName="w-6 h-6 opacity-40"
                    objectFit="contain"
                  />
                )}
              </button>
            </li>
          ))}
        </ul>
      )}

      {pleinEcran && vue.genre === 'image' && vue.url && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-brand-dark/95 p-4"
          role="dialog"
          aria-modal="true"
          aria-label={`${product.name} — visuel agrandi`}
          onClick={() => setPleinEcran(false)}
        >
          <button
            type="button"
            onClick={() => setPleinEcran(false)}
            className="absolute right-5 top-5 rounded-full bg-white/10 p-2 text-white transition hover:bg-white/20"
            aria-label="Fermer la vue agrandie"
          >
            <X className="h-6 w-6" />
          </button>

          {total > 1 && (
            <>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); aller(-1); }}
                className="absolute left-4 rounded-full bg-white/10 p-3 text-white transition hover:bg-white/20"
                aria-label="Visuel précédent"
              >
                <ChevronLeft className="h-6 w-6" />
              </button>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); aller(1); }}
                className="absolute right-4 rounded-full bg-white/10 p-3 text-white transition hover:bg-white/20"
                aria-label="Visuel suivant"
              >
                <ChevronRight className="h-6 w-6" />
              </button>
            </>
          )}

          {/* Le clic sur l'image elle-même ne referme pas : on vient de l'agrandir pour la
              regarder, et un clic malencontreux ferait tout perdre. */}
          <img
            src={vue.url}
            alt={vue.legende ?? product.name}
            onClick={(e) => e.stopPropagation()}
            className="max-h-[88vh] max-w-full object-contain"
          />
          {vue.legende && (
            <p className="absolute bottom-6 left-1/2 -translate-x-1/2 rounded-full bg-black/50 px-4 py-1.5 text-sm text-white">
              {vue.legende}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
