import type { ReactNode, RefObject } from 'react';
import { Menu, X } from 'lucide-react';

/**
 * Classes du menu latéral : tiroir glissant sous 1024 px, colonne fixe au-delà.
 *
 * <p>Fermé, le tiroir passe en `invisible` en plus d'être décalé hors écran : sans cela, ses
 * liens resteraient atteignables à la tabulation alors que personne ne les voit.</p>
 */
export function classesTiroir(ouvert: boolean): string {
  return [
    'fixed inset-y-0 left-0 z-50 w-72 max-w-[85vw] h-full flex flex-col',
    'transition-[transform,visibility] duration-200 motion-reduce:transition-none',
    ouvert ? 'translate-x-0 visible shadow-2xl' : '-translate-x-full invisible',
    'lg:visible lg:translate-x-0 lg:shadow-none lg:sticky lg:top-0 lg:w-64 lg:h-screen lg:shrink-0',
  ].join(' ');
}

/** Voile derrière le tiroir ouvert : le toucher referme le menu. */
export function VoileTiroir({ ouvert, onFermer }: { ouvert: boolean; onFermer: () => void }) {
  if (!ouvert) return null;
  return (
    <div
      className="fixed inset-0 z-40 bg-brand-dark/50 backdrop-blur-[1px] lg:hidden"
      onClick={onFermer}
      aria-hidden="true"
    />
  );
}

/** Bouton « Menu » de la barre du haut, masqué quand le menu est une colonne fixe. */
export function BoutonMenu({ ouvert, onOuvrir, bouton, idTiroir }: {
  ouvert: boolean;
  onOuvrir: () => void;
  bouton: RefObject<HTMLButtonElement | null>;
  idTiroir: string;
}) {
  return (
    <button
      ref={bouton}
      type="button"
      onClick={onOuvrir}
      aria-expanded={ouvert}
      aria-controls={idTiroir}
      className="lg:hidden inline-flex items-center gap-2 -ml-2 px-2 py-2 rounded-lg text-sm font-semibold text-brand-dark hover:bg-slate-100"
    >
      <Menu className="w-5 h-5" aria-hidden="true" />
      Menu
    </button>
  );
}

/** Croix de fermeture dans l'en-tête du tiroir, sur petit écran seulement. */
export function FermerTiroir({ onFermer, children }: { onFermer: () => void; children?: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onFermer}
      aria-label="Fermer le menu"
      className="lg:hidden ml-auto p-2 -mr-2 rounded-lg text-white/70 hover:text-white hover:bg-white/10"
    >
      {children ?? <X className="w-5 h-5" aria-hidden="true" />}
    </button>
  );
}
