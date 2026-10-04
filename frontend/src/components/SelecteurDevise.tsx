import { useDevise } from '../context/DeviseContext';

/**
 * Le choix de la devise d'affichage.
 *
 * <p>Ne s'affiche que lorsqu'il y a vraiment un choix : tant qu'une seule devise est ouverte a
 * la vente, un selecteur a une option encombrerait la barre sans rien offrir.</p>
 *
 * <p>Un {@code select} natif, et non un menu dessine : il se manipule au clavier, se lit par un
 * lecteur d'ecran, et sur telephone ouvre la roue du systeme, que tout le monde sait utiliser.</p>
 */
export function SelecteurDevise() {
  const { devise, disponibles, choisir } = useDevise();

  if (disponibles.length < 2) return null;

  return (
    <label className="relative">
      <span className="sr-only">Devise d'affichage</span>
      <select
        value={devise.code}
        onChange={e => choisir(e.target.value)}
        className="cursor-pointer appearance-none rounded-full border border-gray-200 bg-gray-100/80 py-2 pl-3 pr-7 text-xs font-bold text-gray-600 transition-colors hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-brand/30"
      >
        {disponibles.map(d => (
          <option key={d.code} value={d.code}>{d.code}</option>
        ))}
      </select>
      <span
        aria-hidden="true"
        className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-[9px] text-gray-400"
      >
        ▼
      </span>
    </label>
  );
}
