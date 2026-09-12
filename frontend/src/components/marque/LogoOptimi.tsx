import { useState } from 'react';

/**
 * Sur quel fond le logo est-il posé ?
 *
 * <p>C'est la seule chose que l'appelant doit savoir : le composant en déduit la variante.
 * Demander directement un fichier reviendrait à laisser chaque écran choisir, et l'un d'eux
 * finirait par poser le tracé sombre sur le bleu de la marque.</p>
 */
type Fond = 'clair' | 'sombre';

interface Props {
  /** `sombre` pour un fond bleu ou encre, `clair` pour du blanc ou du papier. */
  fond?: Fond;

  /**
   * Avec ou sans la signature « soutenir le handicap et le soin. ».
   *
   * <p>Sans, dans une barre de navigation : à 48 pixels de haut, la signature n'est plus
   * lisible, et une mention illisible n'informe personne — elle ne fait que rapetisser le
   * logotype.</p>
   */
  signature?: boolean;

  /** Hauteur et largeur maximale, en classes utilitaires. */
  className?: string;

  /** Taille du repli textuel, quand l'image ne charge pas. */
  tailleRepli?: 'petit' | 'moyen';
}

const FICHIERS: Record<Fond, Record<'avec' | 'sans', string>> = {
  clair: { avec: '/marque/optimi-logo-sombre.webp', sans: '/marque/optimi-logotype-sombre.webp' },
  sombre: { avec: '/marque/optimi-logo-clair.webp', sans: '/marque/optimi-logotype-clair.webp' },
};

/**
 * Le logo Optimi Santé, posé au bon endroit avec la bonne variante.
 *
 * <p>Il remplace trois copies du même bloc — barre de navigation, pied de page, connexion —
 * qui différaient déjà entre elles : l'une gérait l'échec de chargement, l'autre non, et le
 * texte alternatif n'était pas le même partout. Un logo recopié finit toujours par diverger,
 * et c'est le genre d'écart que personne ne remarque avant qu'un client ne le signale.</p>
 *
 * <p><b>Le repli n'est pas une image cassée.</b> Si le fichier manque, le nom de la marque
 * s'affiche en toutes lettres : un pied de page sans logo reste lisible, un pied de page avec
 * un pictogramme brisé fait négligé.</p>
 */
export function LogoOptimi({
  fond = 'clair',
  signature = true,
  className = 'h-12 w-auto max-w-[190px]',
  tailleRepli = 'moyen',
}: Props) {
  const [charge, setCharge] = useState(true);

  if (charge) {
    return (
      <img
        src={FICHIERS[fond][signature ? 'avec' : 'sans']}
        // Le nom de la marque, pas celui du fichier. C'est ce que lit un lecteur d'écran, et
        // c'est aussi ce qui s'affiche si l'image ne charge pas.
        alt={signature ? 'Optimi Santé — soutenir le handicap et le soin' : 'Optimi Santé'}
        onError={() => setCharge(false)}
        className={`${className} object-contain`}
      />
    );
  }

  const surSombre = fond === 'sombre';
  const petit = tailleRepli === 'petit';

  return (
    <span className="flex items-center gap-2.5">
      <span
        className={[
          petit ? 'w-10 h-10 text-sm' : 'w-11 h-11 text-base',
          'bg-brand text-white font-bold rounded-xl flex items-center justify-center shrink-0',
        ].join(' ')}
      >
        OS
      </span>
      <span>
        <span
          className={[
            'block font-bold leading-tight',
            petit ? 'text-base' : 'text-xl',
            surSombre ? 'text-white' : 'text-brand-dark',
          ].join(' ')}
        >
          Optimi Santé
        </span>
        {signature && (
          <span
            className={[
              'block text-[11px] leading-tight',
              surSombre ? 'text-slate-300' : 'text-slate-500',
            ].join(' ')}
          >
            soutenir le handicap et le soin
          </span>
        )}
      </span>
    </span>
  );
}
