import { useState } from 'react';
import { Pause, Play } from 'lucide-react';
import { ProductImage } from '../common/ProductImage';
import { PromoVideoOverlay } from './ProductVideo';
import { useReducedMotion } from '../../hooks/useReducedMotion';
import type { Product } from '../../api/catalogService';

/**
 * Vignette d'une carte produit, animée quand la fiche porte une vidéo mise en avant.
 *
 * <p>C'est un remplacement direct de {@link ProductImage} : sans vidéo promue — soit presque
 * tout le catalogue — elle rend exactement la même image, sans écouteur ni état
 * supplémentaire.</p>
 *
 * <p>Deux comportements, selon ce que le visiteur a demandé à son système :</p>
 *
 * <ul>
 *   <li><b>Animation autorisée</b> — la vidéo se lance au survol, en boucle et sans son, puis
 *       revient au début quand le pointeur s'en va. Rien ne bouge tant que personne ne
 *       survole : une page d'accueil où dix vignettes s'agitent seules est illisible.</li>
 *   <li><b>Animation réduite</b> — la vignette reste fixe et un bouton <i>Lire</i> apparaît.
 *       La vidéo ne part alors que sur un geste explicite, et le même bouton l'arrête.</li>
 * </ul>
 */
export function PromoProductVisual({
  product, className = '', iconClassName, objectFit = 'contain',
}: {
  product: Product;
  className?: string;
  iconClassName?: string;
  objectFit?: 'contain' | 'cover';
}) {
  const mouvementReduit = useReducedMotion();
  const [survole, setSurvole] = useState(false);
  const [lectureDemandee, setLectureDemandee] = useState(false);

  const animable = Boolean(product.isVideoPromoted && product.videoUrl && product.videoProvider);

  const vignette = (
    <ProductImage
      src={product.imageUrl}
      alt={product.name}
      className={className}
      iconClassName={iconClassName}
      objectFit={objectFit}
    />
  );

  if (!animable) return vignette;

  const enLecture = mouvementReduit ? lectureDemandee : survole;

  return (
    <div
      className="relative h-full w-full"
      onMouseEnter={() => setSurvole(true)}
      onMouseLeave={() => setSurvole(false)}
    >
      {vignette}

      <PromoVideoOverlay
        videoUrl={product.videoUrl as string}
        videoProvider={product.videoProvider as 'CLOUDINARY'}
        actif={enLecture}
      />

      {mouvementReduit ? (
        <button
          type="button"
          // La vignette est presque toujours à l'intérieur d'un lien vers la fiche : sans ces
          // deux arrêts, appuyer sur « Lire » quitterait la page au lieu de lancer la vidéo.
          onClick={(e) => { e.preventDefault(); e.stopPropagation(); setLectureDemandee((v) => !v); }}
          className="absolute bottom-2 right-2 z-10 inline-flex items-center gap-1 rounded-full bg-brand-dark/85 px-2.5 py-1 text-[11px] font-semibold text-white transition hover:bg-brand-dark"
          aria-label={lectureDemandee
            ? `Arrêter la vidéo de ${product.name}`
            : `Lire la vidéo de ${product.name}`}
        >
          {lectureDemandee
            ? <><Pause className="h-3 w-3 fill-white" /> Arrêter</>
            : <><Play className="h-3 w-3 fill-white" /> Lire</>}
        </button>
      ) : (
        // Repère discret : sans lui, rien n'indique qu'il y a quelque chose à survoler.
        <span
          className={`pointer-events-none absolute bottom-2 right-2 z-10 inline-flex items-center gap-1 rounded-full bg-brand-dark/80 px-2 py-0.5 text-[11px] font-semibold text-white transition-opacity ${
            enLecture ? 'opacity-0' : 'opacity-100'
          }`}
        >
          <Play className="h-3 w-3 fill-white" aria-hidden="true" />
          Vidéo
        </span>
      )}
    </div>
  );
}
