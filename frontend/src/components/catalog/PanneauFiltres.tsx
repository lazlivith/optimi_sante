import { Info, X } from 'lucide-react';
import {
  FILTRES_VIDES, TEXTE_SUR_DEVIS, type FiltresRayon,
} from '../../lib/filtresRayon';

interface Props {
  filtres: FiltresRayon;
  modifier: (f: FiltresRayon) => void;
  /** Vrai si une campagne de prix est active dans ce rayon. Sinon le bloc n'existe pas. */
  promotionsDansLeRayon: boolean;
  /** Nombre de sous-rayons. Zéro aujourd'hui pour tous : le bloc reste caché. */
  nombreSousRayons: number;
}

/**
 * La colonne de filtres d'un rayon.
 *
 * <p><b>Aucun bloc vide, jamais.</b> Un filtre qui n'a rien à proposer — « En promotion »
 * dans un rayon sans promotion, « Sous-catégorie » dans un rayon sans enfants — n'est pas
 * neutre : il fait croire que le rayon est pauvre, et il coûte au visiteur le temps de
 * l'ouvrir pour constater qu'il est vide. Chaque bloc ci-dessous teste d'abord s'il a de
 * quoi exister.</p>
 *
 * <p><b>Le bloc « Sous-catégorie » est écrit mais inatteignable.</b> Les 211 catégories du
 * catalogue sont aujourd'hui toutes racines : aucune n'a d'enfant, et le bloc n'aurait rien
 * à cocher. Il apparaîtra de lui-même quand l'arborescence sera posée, sans qu'une ligne
 * soit ajoutée ici.</p>
 */
export function PanneauFiltres({
  filtres, modifier, promotionsDansLeRayon, nombreSousRayons,
}: Props) {
  const prix = (champ: 'prixMin' | 'prixMax') => (valeur: string) => {
    const n = valeur.trim() === '' ? undefined : Number(valeur);
    modifier({ ...filtres, [champ]: Number.isFinite(n) && (n as number) >= 0 ? n : undefined });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-baseline justify-between">
        <h2 className="text-base font-bold text-brand-dark">Filtres</h2>
        <button
          type="button"
          onClick={() => modifier(FILTRES_VIDES)}
          className="text-sm font-semibold text-brand hover:underline"
        >
          Réinitialiser
        </button>
      </div>

      {/* ── Sous-catégorie : construit, masqué tant qu'il n'y a rien à cocher ───────── */}
      {nombreSousRayons > 0 && (
        <Bloc titre="Sous-catégorie">
          <p className="text-xs text-slate-500">
            {nombreSousRayons} sous-rayon{nombreSousRayons > 1 ? 's' : ''}.
          </p>
        </Bloc>
      )}

      {/* ── Prix ────────────────────────────────────────────────────────────────────── */}
      <Bloc titre="Prix (€)">
        <div className="flex items-center gap-2">
          <input
            type="number" min={0} inputMode="numeric" placeholder="Min"
            aria-label="Prix minimum en euros"
            value={filtres.prixMin ?? ''}
            onChange={(e) => prix('prixMin')(e.target.value)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-800"
          />
          <span aria-hidden="true" className="text-slate-400">–</span>
          <input
            type="number" min={0} inputMode="numeric" placeholder="Max"
            aria-label="Prix maximum en euros"
            value={filtres.prixMax ?? ''}
            onChange={(e) => prix('prixMax')(e.target.value)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-800"
          />
        </div>
      </Bloc>

      {/* ── Type d'achat ────────────────────────────────────────────────────────────── */}
      <Bloc titre="Type d'achat">
        <Case
          libelle="Achat en ligne"
          coche={filtres.surDevis === false}
          basculer={() => modifier({
            ...filtres, surDevis: filtres.surDevis === false ? undefined : false,
          })}
        />
        <Case
          libelle="Sur devis"
          coche={filtres.surDevis === true}
          basculer={() => modifier({
            ...filtres, surDevis: filtres.surDevis === true ? undefined : true,
          })}
        />
      </Bloc>

      {/* ── Promotions : seulement si le rayon en porte ─────────────────────────────── */}
      {promotionsDansLeRayon && (
        <Bloc titre="En promotion">
          <Case
            libelle="Seulement les promotions"
            coche={filtres.promo === true}
            basculer={() => modifier({ ...filtres, promo: filtres.promo ? undefined : true })}
          />
        </Bloc>
      )}

      {/*
        « Marque » n'existe pas : le produit ne porte aucun champ de marque en base. Le bloc
        n'est donc pas écrit du tout — plutôt qu'écrit et masqué comme « Sous-catégorie »,
        dont les données existent mais sont vides.
      */}

      <div className="flex gap-3 rounded-xl border border-warning/30 bg-warning/10 p-3">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
        <p className="text-xs leading-relaxed text-slate-700">
          <span className="font-semibold">Bon à savoir :</span> {TEXTE_SUR_DEVIS}
        </p>
      </div>
    </div>
  );
}

function Bloc({ titre, children }: { titre: string; children: React.ReactNode }) {
  return (
    <fieldset>
      <legend className="mb-2 text-sm font-semibold text-brand-dark">{titre}</legend>
      <div className="space-y-2">{children}</div>
    </fieldset>
  );
}

function Case({ libelle, coche, basculer }: {
  libelle: string; coche: boolean; basculer: () => void;
}) {
  return (
    <label className="flex cursor-pointer items-center gap-2.5 text-sm text-slate-700">
      <input
        type="checkbox"
        checked={coche}
        onChange={basculer}
        className="h-4 w-4 rounded border-slate-300 text-brand focus:ring-brand"
      />
      {libelle}
    </label>
  );
}

/** Les filtres posés, effaçables d'un clic, au-dessus de la grille. */
export function EtiquettesFiltres({ liste, retirer }: {
  liste: { cle: string; texte: string }[];
  retirer: (cle: string) => void;
}) {
  if (liste.length === 0) return null;
  return (
    <ul className="mb-5 flex flex-wrap gap-2">
      {liste.map((e) => (
        <li key={e.cle}>
          <button
            type="button"
            onClick={() => retirer(e.cle)}
            aria-label={`Retirer le filtre ${e.texte}`}
            className="inline-flex items-center gap-1.5 rounded-full border border-brand/30
                       bg-brand-light/50 py-1 pl-3 pr-2 text-xs font-semibold text-brand-dark
                       hover:bg-brand-light"
          >
            {e.texte}
            <X className="h-3.5 w-3.5" aria-hidden="true" />
          </button>
        </li>
      ))}
    </ul>
  );
}
