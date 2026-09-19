import { useEffect, useMemo, useState } from 'react';
import { Loader2, Percent, Search } from 'lucide-react';
import { adminCatalogService, type AdminCategoryDto } from '../../api/adminCatalogService';

/**
 * Marges par famille de produits, appliquées aux imports fournisseurs.
 *
 * <p>Rangé avec les fournisseurs, et non dans le catalogue : c'est au moment d'acheter que la
 * marge se décide. Elle prime sur la commission du fournisseur ; laissée vide, c'est cette
 * dernière qui s'applique.</p>
 *
 * <p>Le catalogue compte plus de deux cents catégories : on affiche d'abord celles qui ont une
 * marge ou des produits, et la recherche sert à atteindre les autres.</p>
 */
export function MargesCategoriesPanel({ onErreur }: { onErreur: (message: string) => void }) {
  const [categories, setCategories] = useState<AdminCategoryDto[]>([]);
  const [recherche, setRecherche] = useState('');
  const [chargement, setChargement] = useState(true);
  const [enregistrement, setEnregistrement] = useState<string | null>(null);
  const [saisies, setSaisies] = useState<Record<string, string>>({});

  useEffect(() => {
    adminCatalogService.listCategories()
      .then((liste) => {
        setCategories(liste);
        setSaisies(Object.fromEntries(liste.map((c) => [c.id, c.marginRate == null ? '' : String(c.marginRate)])));
      })
      .catch(() => onErreur('Impossible de charger les catégories.'))
      .finally(() => setChargement(false));
  }, [onErreur]);

  const visibles = useMemo(() => {
    const aiguille = recherche.trim().toLowerCase();
    return categories
      .filter((c) => (aiguille
        ? c.name.toLowerCase().includes(aiguille)
        : c.marginRate != null || c.productCount > 0))
      .slice(0, 40);
  }, [categories, recherche]);

  const enregistrer = async (categorie: AdminCategoryDto) => {
    const saisie = (saisies[categorie.id] ?? '').trim().replace(',', '.');
    const taux = saisie === '' ? null : Number(saisie);
    if (taux !== null && (Number.isNaN(taux) || taux < 0 || taux > 300)) {
      onErreur('La marge doit être un nombre compris entre 0 et 300 %.');
      return;
    }
    if (taux === categorie.marginRate || (taux === null && categorie.marginRate == null)) {
      return;
    }
    setEnregistrement(categorie.id);
    try {
      const maj = await adminCatalogService.setCategoryMargin(categorie.id, taux);
      setCategories((prev) => prev.map((c) => (c.id === maj.id ? maj : c)));
    } catch (err: any) {
      onErreur(err.response?.data?.message || "La marge n'a pas pu être enregistrée.");
    } finally {
      setEnregistrement(null);
    }
  };

  return (
    <section className="bg-white rounded-2xl border border-slate-200 overflow-hidden" aria-labelledby="marges-titre">
      <div className="p-4 border-b border-slate-200 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 id="marges-titre" className="text-lg font-bold text-brand-dark">Marges par catégorie</h2>
          <p className="text-sm text-slate-500">
            Appliquées au prix d'achat lors d'un import. Vide : la commission du fournisseur s'applique.
          </p>
        </div>
        <div className="relative flex-1 sm:flex-none sm:w-64">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
          <input
            type="search" value={recherche} onChange={(e) => setRecherche(e.target.value)}
            placeholder="Chercher une catégorie…" aria-label="Chercher une catégorie"
            className="w-full pl-9 rounded-lg border border-slate-300 p-2 text-sm focus:border-brand focus:ring-2 focus:ring-brand/20 focus:outline-none"
          />
        </div>
      </div>

      {chargement ? (
        <div className="p-8 text-center"><Loader2 className="w-5 h-5 animate-spin mx-auto text-brand" aria-label="Chargement" /></div>
      ) : visibles.length === 0 ? (
        <p className="p-6 text-sm text-slate-500">Aucune catégorie ne correspond.</p>
      ) : (
        <ul className="divide-y divide-slate-100 max-h-96 overflow-y-auto">
          {visibles.map((c) => (
            <li key={c.id} className="px-4 py-2.5 flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="text-sm font-medium text-brand-dark truncate">{c.name.replace(/&amp;/g, '&')}</p>
                <p className="text-xs text-slate-400">{c.productCount} produit(s)</p>
              </div>
              <div className="relative w-28 shrink-0">
                <label htmlFor={`marge-${c.id}`} className="sr-only">Marge de {c.name}</label>
                <input
                  id={`marge-${c.id}`} type="number" min={0} max={300} step="0.5" inputMode="decimal"
                  value={saisies[c.id] ?? ''}
                  onChange={(e) => setSaisies((s) => ({ ...s, [c.id]: e.target.value }))}
                  onBlur={() => enregistrer(c)}
                  placeholder="—"
                  className="w-full rounded-lg border border-slate-300 p-2 pr-8 text-sm text-right focus:border-brand focus:ring-2 focus:ring-brand/20 focus:outline-none"
                />
                {enregistrement === c.id
                  ? <Loader2 className="w-3.5 h-3.5 animate-spin text-brand absolute right-2.5 top-1/2 -translate-y-1/2" aria-label="Enregistrement" />
                  : <Percent className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2" aria-hidden="true" />}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
