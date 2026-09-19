import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Building2, ChevronRight, Loader2, Plus, Power, Search, Truck } from 'lucide-react';
import { adminSupplierService, type Supplier, type SupplierRequest } from '../../api/adminSupplierService';
import { PageHeader } from '../../components/common/PageHeader';
import { EmptyState } from '../../components/common/EmptyState';
import { Toast, type ToastType } from '../../components/common/Toast';
import { SupplierFormDialog } from '../../components/catalog/SupplierFormDialog';
import { MargesCategoriesPanel } from '../../components/catalog/MargesCategoriesPanel';

/**
 * Fournisseurs du catalogue : qui livre quoi, et par où passent les imports.
 *
 * <p>Rangée dans l'univers Négoce, avec le catalogue : qui gère les produits gère leurs
 * fournisseurs. Un fournisseur désactivé n'accepte plus de dépôt de catalogue, mais ses produits
 * restent en vente — désactiver n'est pas supprimer.</p>
 */
export function AdminSuppliersPage() {
  const [fournisseurs, setFournisseurs] = useState<Supplier[]>([]);
  const [chargement, setChargement] = useState(true);
  const [recherche, setRecherche] = useState('');
  const [enEdition, setEnEdition] = useState<Supplier | 'nouveau' | null>(null);
  const [actionEnCours, setActionEnCours] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const charger = useCallback(async () => {
    try {
      setFournisseurs(await adminSupplierService.list());
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Impossible de charger les fournisseurs.', type: 'error' });
    } finally {
      setChargement(false);
    }
  }, []);

  useEffect(() => { charger(); }, [charger]);

  const enregistrer = async (body: SupplierRequest) => {
    const existant = enEdition !== 'nouveau' ? enEdition : null;
    const enregistre = existant
      ? await adminSupplierService.update(existant.id, body)
      : await adminSupplierService.create(body);
    setToast({ message: existant ? 'Fournisseur mis à jour.' : `Fournisseur ${enregistre.code} créé.`, type: 'success' });
    setEnEdition(null);
    await charger();
  };

  const basculer = async (fournisseur: Supplier) => {
    setActionEnCours(fournisseur.id);
    try {
      await adminSupplierService.setActive(fournisseur.id, !fournisseur.active);
      await charger();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Action impossible.', type: 'error' });
    } finally {
      setActionEnCours(null);
    }
  };

  const visibles = fournisseurs.filter((f) => {
    if (!recherche) return true;
    const aiguille = recherche.toLowerCase();
    return [f.companyName, f.code, f.contactName, f.contactEmail, f.taxId]
      .some((v) => v?.toLowerCase().includes(aiguille));
  });

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-6xl">
      <PageHeader
        title="Fournisseurs"
        subtitle="Qui livre les produits du catalogue, et par où passent leurs imports"
        actions={
          <button
            type="button" onClick={() => setEnEdition('nouveau')}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-brand text-white text-sm font-semibold hover:bg-brand-fonce"
          >
            <Plus className="w-4 h-4" aria-hidden="true" /> Nouveau fournisseur
          </button>
        }
      />

      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
        <div className="p-4 border-b border-slate-200">
          <div className="relative sm:max-w-sm">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
            <input
              type="search" value={recherche} onChange={(e) => setRecherche(e.target.value)}
              placeholder="Raison sociale, code, contact…" aria-label="Rechercher un fournisseur"
              className="w-full pl-9 rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand focus:ring-2 focus:ring-brand/20 focus:outline-none"
            />
          </div>
        </div>

        {chargement ? (
          <div className="p-12 text-center text-slate-500 text-sm">
            <Loader2 className="w-6 h-6 animate-spin mx-auto mb-3 text-brand" aria-hidden="true" />
            Chargement des fournisseurs…
          </div>
        ) : visibles.length === 0 ? (
          <EmptyState
            icon={Truck}
            title={recherche ? 'Aucun fournisseur ne correspond' : 'Aucun fournisseur enregistré'}
            description={recherche
              ? 'Essayez un autre terme.'
              : 'Créez un fournisseur pour lui rattacher des produits et importer son catalogue.'}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Fournisseur</th>
                  <th className="px-4 py-3 font-medium">Contact</th>
                  <th className="px-4 py-3 font-medium text-right">Produits</th>
                  <th className="px-4 py-3 font-medium text-right">Commission</th>
                  <th className="px-4 py-3 font-medium">État</th>
                  <th className="px-4 py-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {visibles.map((f) => (
                  <tr key={f.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-4 py-3">
                      <Link to={`/admin/suppliers/${f.id}`} className="font-semibold text-brand-dark hover:text-brand">
                        {f.companyName}
                      </Link>
                      <p className="font-mono text-xs text-slate-500">{f.code}{f.taxId ? ` · ${f.taxId}` : ''}</p>
                    </td>
                    <td className="px-4 py-3 text-slate-600">
                      {f.contactName || '—'}
                      {f.contactEmail && <span className="block text-xs text-slate-400">{f.contactEmail}</span>}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums text-slate-700">{f.productCount}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-slate-600">{f.commissionRate} %</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${
                        f.active ? 'bg-success/10 text-success' : 'bg-slate-100 text-slate-600'}`}>
                        {f.active ? 'Actif' : 'Inactif'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-3 whitespace-nowrap">
                        <button
                          type="button" onClick={() => setEnEdition(f)}
                          className="text-xs font-medium text-slate-600 hover:text-brand hover:underline"
                        >
                          Modifier
                        </button>
                        <button
                          type="button" onClick={() => basculer(f)} disabled={actionEnCours === f.id}
                          className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-600 hover:text-brand disabled:opacity-60"
                        >
                          <Power className="w-3.5 h-3.5" aria-hidden="true" />
                          {f.active ? 'Désactiver' : 'Réactiver'}
                        </button>
                        <Link
                          to={`/admin/suppliers/${f.id}`}
                          className="inline-flex items-center text-xs font-semibold text-brand hover:text-brand-fonce"
                        >
                          Catalogue <ChevronRight className="w-3.5 h-3.5" aria-hidden="true" />
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="mt-6">
        <MargesCategoriesPanel onErreur={(message) => setToast({ message, type: 'error' })} />
      </div>

      <p className="mt-4 text-sm text-slate-500 flex items-start gap-2">
        <Building2 className="w-4 h-4 mt-0.5 shrink-0 text-slate-400" aria-hidden="true" />
        Les {fournisseurs.reduce((t, f) => t + f.productCount, 0)} produits rattachés à un fournisseur sont les seuls
        qu'un import peut modifier. Les références historiques, sans fournisseur, ne sont jamais touchées.
      </p>

      {enEdition && (
        <SupplierFormDialog
          fournisseur={enEdition === 'nouveau' ? null : enEdition}
          onClose={() => setEnEdition(null)}
          onEnregistrer={enregistrer}
        />
      )}
      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
