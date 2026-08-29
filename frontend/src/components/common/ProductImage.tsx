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
}

/**
 * Image produit avec repli fiable : si l'URL est absente, connue comme cassée, ou échoue
 * réellement au chargement (onError), affiche une icône plutôt qu'une image cassée du
 * navigateur ou une requête réseau vouée à échouer. Aucune dépendance à une image de secours
 * externe qui pourrait elle-même ne pas exister.
 */
export function ProductImage({ src, alt, className = '', iconClassName = 'w-10 h-10', objectFit = 'contain' }: ProductImageProps) {
  const [failed, setFailed] = useState(false);
  const isUsable = !!src && !KNOWN_BROKEN_URLS.includes(src);

  if (!isUsable || failed) {
    return (
      <div className={`flex items-center justify-center bg-slate-50 text-brand-green/30 ${className}`}>
        <ImageIcon className={iconClassName} />
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={alt}
      className={`${className} ${objectFit === 'cover' ? 'object-cover' : 'object-contain'}`}
      onError={() => setFailed(true)}
    />
  );
}
