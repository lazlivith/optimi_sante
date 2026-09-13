import { useState } from 'react';
import { Image as ImageIcon } from 'lucide-react';

// Un import de données historique a rempli certains produits avec cette URL de secours
// codée en dur, qui n'a jamais été réellement uploadée sur Cloudinary (404 permanent).
// On la traite comme "pas d'image" au même titre qu'une valeur vide.
const KNOWN_BROKEN_URLS = [
  'https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/medical-placeholder.jpg',
];

interface ProductImageProps {
  src?: string | null;
  alt: string;
  className?: string;
  iconClassName?: string;
  objectFit?: 'cover' | 'contain';
  /**
   * `lazy` par défaut : le navigateur ne télécharge la photo qu'à l'approche de l'écran.
   *
   * <p>Sans cela, une page de catalogue à défilement infini lance une requête par produit
   * chargé — plus de mille si le visiteur descend jusqu'au bout —, toutes en même temps et
   * pour des images qu'il ne verra peut-être jamais.</p>
   *
   * <p>Passez `eager` pour une image visible dès l'ouverture, typiquement celle du bandeau :
   * la différer retarderait le premier affichage utile de la page.</p>
   */
  chargement?: 'lazy' | 'eager';
}

/**
 * Image produit avec repli fiable : si l'URL est absente, connue comme cassée, ou échoue
 * réellement au chargement (onError), affiche une icône plutôt qu'une image cassée du
 * navigateur ou une requête réseau vouée à échouer. Aucune dépendance à une image de secours
 * externe qui pourrait elle-même ne pas exister.
 */
export function ProductImage({ src, alt, className = '', iconClassName = 'w-10 h-10', objectFit = 'contain', chargement = 'lazy' }: ProductImageProps) {
  const [failed, setFailed] = useState(false);
  const isUsable = !!src && !KNOWN_BROKEN_URLS.includes(src);

  if (!isUsable || failed) {
    return (
      <div className={`flex items-center justify-center bg-slate-50 text-brand/30 ${className}`}>
        <ImageIcon className={iconClassName} />
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={alt}
      loading={chargement}
      // Décode hors du fil principal : sur une grille de vingt-quatre photos, un décodage
      // synchrone fige le défilement le temps que chacune s'affiche.
      decoding="async"
      className={`${className} ${objectFit === 'cover' ? 'object-cover' : 'object-contain'}`}
      onError={() => setFailed(true)}
    />
  );
}
