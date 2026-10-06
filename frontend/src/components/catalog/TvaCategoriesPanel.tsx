import { useEffect, useMemo, useState } from 'react';
import { Loader2, Receipt, Search, TriangleAlert } from 'lucide-react';
import { adminCatalogService, type AdminCategoryDto } from '../../api/adminCatalogService';

/** Les taux français en vigueur. 2,1 % ne concerne aucune catégorie du catalogue. */
const TAUX = [
  { valeur: '', libelle: 'Non examiné → 20 %' },
  { valeur: '20', libelle: '20 % — normal' },
  { valeur: '10', libelle: '10 % — intermédiaire' },
  { valeur: '5.5', libelle: '5,5 % — réduit' },
  { valeur: '2.1', libelle: '2,1 % — super-réduit' },
  { valeur: '0', libelle: '0 % — exonéré' },
];

/**
 * La grille de TVA, famille par famille.
 *
 * <p><b>Ce taux ne change aucun prix.</b> Les prix du catalogue sont annoncés TTC : le taux
 * sert à extraire la taxe qu'ils contiennent déjà, pas à l'ajouter. Le modifier change donc la
 * part reversée à l'État et celle qui reste à l'entreprise — jamais ce que le client paie, ni
 * les commandes déjà passées, dont le taux est figé à l'encaissement.</p>
 *
 * <p><b>« Non examiné » n'est pas « zéro ».</b> Pour le matériel médical, la règle est que tout
 * relève du taux normal sauf ce qui figure dans une liste précise du Code général des impôts.
 * Une catégorie sans taux retombe donc sur 20 %, ce qui est juste — mais l'écran le dit, pour
 * qu'on ne confonde pas un arbitrage avec un oubli.</p>
 *
 * <p><b>Les rayons mixtes se traitent par produit.</b> Transfert, sièges de douche, bandes de
 * compression contiennent des articles de taux différents. La catégorie porte le taux prudent,
 * et l'exception se saisit sur la fiche article, où elle prime.</p>
 */
export function TvaCategoriesPanel({ onErreur }: { onErreur: (message: string) => void }) {
  const [categories, setCategories] = useState<AdminCategoryDto[]>([]);
  const [recherche, setRecherche] = useState('');
  const [chargement, setChargement] = useState(true);
  const [enregistrement, setEnregistrement] = useState<string | null>(null);

  useEffect(() => {
    adminCatalogService.listCategories()
      .then(setCategories)
      .catch(() => onErreur('Impossible de charger les catégories.'))
      .finally(() => setChargement(false));
  }, [onErreur]);

  /* Comme pour les marges : plus de deux cents catégories, dont beaucoup vides. On montre
     d'abord celles qui portent un taux ou des produits, et la recherche atteint les autres.
     Les « à vérifier » remontent en tête : ce sont celles qui attendent une décision. */
  const visibles = useMemo(() => {
    const aiguille = recherche.trim().toLowerCase();
    return categories
      .filter(c => (aiguille
        ? c.name.toLowerCase().includes(aiguille)
        : c.vatRate != null || c.productCount > 0))
      .sort((a, b) => Number(b.vatRateAVerifier) - Number(a.vatRateAVerifier))
      .slice(0, 60);
  }, [categories, recherche]);

  const aVerifier = categories.filter(c => c.vatRateAVerifier).length;

  const enregistrer = async (categorie: AdminCategoryDto, valeur: string) => {
    const taux = valeur === '' ? null : Number(valeur);
    if (taux === (categorie.vatRate ?? null)) return;

    setEnregistrement(categorie.id);
    try {
      const maj = await adminCatalogService.setCategoryVatRate(categorie.id, taux);
      setCategories(prev => prev.map(c => (c.id === maj.id ? maj : c)));
    } catch (err: any) {
      onErreur(err.response?.data?.message || "Le taux n'a pas pu être enregistré.");
    } finally {
      setEnregistrement(null);
    }
  };

  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white" aria-labelledby="tva-titre">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-4">
        <div>
          <h2 id="tva-titre" className="text-lg font-bold text-brand-dark">TVA par catégorie</h2>
          <p className="text-sm text-slate-500">
            Les prix étant annoncés TTC, ce taux sert à extraire la taxe qu'ils contiennent.
            Le modifier ne change aucun prix, ni aucune commande déjà passée.
          </p>
        </div>
        <label className="relative">
          <span className="sr-only">Rechercher une catégorie</span>
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          <input
            value={recherche}
            onChange={e => setRecherche(e.target.value)}
            placeholder="Rechercher…"
            className="rounded-xl border border-slate-200 py-2 pl-9 pr-3 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
          />
        </label>
      </div>

      {aVerifier > 0 && (
        <p className="flex items-start gap-2 border-b border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
          <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>
            <strong>{aVerifier} catégorie{aVerifier > 1 ? 's' : ''} à confirmer.</strong> Le
            comptable n'a pas pu consulter la liste officielle des aides techniques (CGI, annexe
            IV, art. 30-0 B). Elles portent le taux prudent en attendant — enregistrer un taux
            lève le marqueur.
          </span>
        </p>
      )}

      {chargement ? (
        <div className="flex justify-center p-12"><Loader2 className="h-6 w-6 animate-spin text-brand" /></div>
      ) : (
        <ul className="divide-y divide-slate-100">
          {visibles.map(categorie => (
            <li key={categorie.id} className="flex flex-wrap items-center gap-3 p-4">
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-light text-brand">
                <Receipt className="h-4 w-4" aria-hidden="true" />
              </span>

              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold text-brand-dark">{categorie.name}</p>
                <p className="text-xs text-slate-400">
                  {categorie.productCount} produit{categorie.productCount > 1 ? 's' : ''}
                  {categorie.vatRate == null && ' · aucun taux posé, le taux normal s\'applique'}
                </p>
              </div>

              {categorie.vatRateAVerifier && (
                <span className="inline-flex items-center gap-1 rounded-lg bg-amber-100 px-2 py-1 text-xs font-semibold text-amber-800">
                  <TriangleAlert className="h-3 w-3" aria-hidden="true" /> À confirmer
                </span>
              )}

              <label className="shrink-0">
                <span className="sr-only">Taux de TVA de {categorie.name}</span>
                <select
                  value={categorie.vatRate == null ? '' : String(categorie.vatRate)}
                  disabled={enregistrement === categorie.id}
                  onChange={e => enregistrer(categorie, e.target.value)}
                  className="rounded-xl border border-slate-200 py-2 pl-3 pr-8 text-sm font-semibold focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 disabled:opacity-60"
                >
                  {TAUX.map(t => <option key={t.valeur} value={t.valeur}>{t.libelle}</option>)}
                </select>
              </label>

              {enregistrement === categorie.id && (
                <Loader2 className="h-4 w-4 animate-spin text-brand" aria-hidden="true" />
              )}
            </li>
          ))}
          {visibles.length === 0 && (
            <li className="p-8 text-center text-sm text-slate-400">Aucune catégorie trouvée.</li>
          )}
        </ul>
      )}
    </section>
  );
}
