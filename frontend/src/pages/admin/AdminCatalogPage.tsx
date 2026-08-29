import { useEffect, useState } from 'react';
import { Loader2, Plus, Pencil, Trash2, Package, X, RotateCcw, Tag } from 'lucide-react';
import { adminCatalogService, type AdminProductDto, type AdminProductRequestDto, type AdminCategoryDto } from '../../api/adminCatalogService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';

const EMPTY_FORM: AdminProductRequestDto = {
  sku: '', name: '', description: '', basePrice: 0, stockQuantity: 0, stockThreshold: 5, isQuoteOnly: false,
  categoryId: undefined, imageUrl: '', promoPrice: undefined, promoStartsAt: undefined, promoEndsAt: undefined
};

function isPromoCurrentlyActive(p: AdminProductDto): boolean {
  if (!p.promoPrice) return false;
  const now = new Date();
  if (p.promoStartsAt && now < new Date(p.promoStartsAt)) return false;
  if (p.promoEndsAt && now > new Date(p.promoEndsAt)) return false;
  return true;
}

export function AdminCatalogPage() {
  const [products, setProducts] = useState<AdminProductDto[]>([]);
  const [categories, setCategories] = useState<AdminCategoryDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<AdminProductRequestDto>(EMPTY_FORM);
  const [isSaving, setIsSaving] = useState(false);

  const fetchData = async () => {
    setIsLoading(true);
    try {
      const [productsPage, categoriesData] = await Promise.all([
        adminCatalogService.listProducts(0, 100),
        adminCatalogService.listCategories(),
      ]);
      setProducts(productsPage.content);
      setCategories(categoriesData);
    } catch (error) {
      console.error('Failed to fetch catalog data', error);
      setToast({ message: "Impossible de charger le catalogue.", type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const openCreateModal = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setIsModalOpen(true);
  };

  const openEditModal = (p: AdminProductDto) => {
    setEditingId(p.id);
    setForm({
      sku: p.sku, name: p.name, description: p.description || '', basePrice: p.basePrice,
      stockQuantity: p.stockQuantity, stockThreshold: p.stockThreshold, isQuoteOnly: p.isQuoteOnly,
      categoryId: p.categoryId || undefined, imageUrl: p.imageUrl || '',
      promoPrice: p.promoPrice ?? undefined, promoStartsAt: p.promoStartsAt ?? undefined, promoEndsAt: p.promoEndsAt ?? undefined
    });
    setIsModalOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {
      if (editingId) {
        const updated = await adminCatalogService.updateProduct(editingId, form);
        setProducts(prev => prev.map(p => p.id === editingId ? updated : p));
        setToast({ message: 'Produit mis à jour.', type: 'success' });
      } else {
        const created = await adminCatalogService.createProduct(form);
        setProducts(prev => [created, ...prev]);
        setToast({ message: 'Produit créé.', type: 'success' });
      }
      setIsModalOpen(false);
    } catch (error: any) {
      setToast({ message: error.response?.data?.message || 'Erreur lors de l\'enregistrement.', type: 'error' });
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleActive = async (p: AdminProductDto) => {
    if (p.isActive && !window.confirm(`Désactiver "${p.name}" ? Il ne sera plus visible sur la boutique (mais restera visible ici, réactivable à tout moment).`)) return;
    try {
      const updated = await adminCatalogService.setProductActive(p.id, !p.isActive);
      setProducts(prev => prev.map(prod => prod.id === p.id ? updated : prod));
      setToast({ message: updated.isActive ? 'Produit réactivé.' : 'Produit désactivé.', type: 'success' });
    } catch (error) {
      setToast({ message: 'Erreur lors de la mise à jour.', type: 'error' });
    }
  };

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Catalogue Produits"
        subtitle="Créez et gérez les produits de la boutique."
        actions={
          <button
            onClick={openCreateModal}
            className="inline-flex items-center px-4 py-2.5 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors text-sm"
          >
            <Plus className="w-4 h-4 mr-2" /> Nouveau produit
          </button>
        }
      />

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center items-center">
            <Loader2 className="w-8 h-8 text-emerald-600 animate-spin" />
          </div>
        ) : products.length === 0 ? (
          <EmptyState icon={Package} title="Aucun produit. Créez-en un pour commencer." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Produit</th>
                  <th className="px-6 py-4">Catégorie</th>
                  <th className="px-6 py-4">Prix</th>
                  <th className="px-6 py-4">Stock</th>
                  <th className="px-6 py-4">Type</th>
                  <th className="px-6 py-4">Statut</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {products.map((p) => (
                  <tr key={p.id} className={`hover:bg-slate-50/50 transition-colors ${!p.isActive ? 'opacity-60' : ''}`}>
                    <td className="px-6 py-4">
                      <div className="font-semibold text-slate-900">{p.name}</div>
                      <div className="text-xs font-mono text-slate-400">{p.sku}</div>
                    </td>
                    <td className="px-6 py-4 text-slate-600">{p.categoryName || '—'}</td>
                    <td className="px-6 py-4">
                      <span className="font-semibold text-slate-900">{p.basePrice.toFixed(2)} €</span>
                      {isPromoCurrentlyActive(p) && (
                        <span className="ml-2 inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-bold rounded-full bg-rose-100 text-rose-700">
                          <Tag className="w-3 h-3" /> {p.promoPrice!.toFixed(2)} €
                        </span>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      <span className={p.stockQuantity <= p.stockThreshold ? 'text-rose-600 font-bold' : 'text-slate-700'}>
                        {p.stockQuantity}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={p.isQuoteOnly ? 'QUOTE_ONLY' : 'CATALOG'} label={p.isQuoteOnly ? 'Sur devis' : 'Catalogue'} tone={p.isQuoteOnly ? 'amber' : 'emerald'} />
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={p.isActive ? 'ACTIVE' : 'INACTIVE'} label={p.isActive ? 'Actif' : 'Désactivé'} />
                    </td>
                    <td className="px-6 py-4 text-right space-x-2">
                      <button
                        onClick={() => openEditModal(p)}
                        disabled={!p.isActive}
                        className="inline-flex items-center justify-center p-2 bg-slate-100 text-slate-600 rounded-lg hover:bg-slate-200 transition disabled:opacity-40 disabled:cursor-not-allowed"
                        title={p.isActive ? 'Modifier' : 'Réactivez le produit pour le modifier'}
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleToggleActive(p)}
                        className={`inline-flex items-center justify-center p-2 rounded-lg transition ${
                          p.isActive ? 'bg-rose-100 text-rose-700 hover:bg-rose-200' : 'bg-emerald-100 text-emerald-700 hover:bg-emerald-200'
                        }`}
                        title={p.isActive ? 'Désactiver' : 'Réactiver'}
                      >
                        {p.isActive ? <Trash2 className="w-4 h-4" /> : <RotateCcw className="w-4 h-4" />}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white rounded-2xl w-full max-w-lg overflow-hidden shadow-xl border border-slate-200 max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center p-6 border-b border-slate-100 bg-slate-50 sticky top-0">
              <h2 className="text-xl font-bold text-brand-dark">{editingId ? 'Modifier le produit' : 'Nouveau produit'}</h2>
              <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-brand-dark">
                <X className="w-6 h-6" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">SKU</label>
                  <input type="text" required value={form.sku} onChange={e => setForm({ ...form, sku: e.target.value })}
                    className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Prix (€)</label>
                  <input type="number" step="0.01" min="0" required value={form.basePrice}
                    onChange={e => setForm({ ...form, basePrice: parseFloat(e.target.value) || 0 })}
                    className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Nom du produit</label>
                <input type="text" required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })}
                  className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Description</label>
                <textarea rows={3} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })}
                  className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Stock</label>
                  <input type="number" min="0" value={form.stockQuantity}
                    onChange={e => setForm({ ...form, stockQuantity: parseInt(e.target.value) || 0 })}
                    className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Seuil d'alerte</label>
                  <input type="number" min="0" value={form.stockThreshold}
                    onChange={e => setForm({ ...form, stockThreshold: parseInt(e.target.value) || 0 })}
                    className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Catégorie</label>
                <select value={form.categoryId || ''} onChange={e => setForm({ ...form, categoryId: e.target.value || undefined })}
                  className="w-full rounded-md border-slate-300 shadow-sm p-2 border bg-white">
                  <option value="">— Aucune —</option>
                  {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>
              <div className="flex items-center gap-2">
                <input type="checkbox" id="isQuoteOnly" checked={!!form.isQuoteOnly}
                  onChange={e => setForm({ ...form, isQuoteOnly: e.target.checked })}
                  className="rounded border-slate-300 text-brand-green focus:ring-brand-green" />
                <label htmlFor="isQuoteOnly" className="text-sm text-slate-700">Produit uniquement sur devis (B2B)</label>
              </div>

              <div className="pt-4 border-t border-slate-100">
                <div className="flex items-center gap-2 mb-3">
                  <Tag className="w-4 h-4 text-rose-500" />
                  <h3 className="text-sm font-bold text-slate-700">Promotion (facultatif)</h3>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1">Prix promo (€)</label>
                    <input type="number" step="0.01" min="0" value={form.promoPrice ?? ''}
                      onChange={e => setForm({ ...form, promoPrice: e.target.value ? parseFloat(e.target.value) : undefined })}
                      className="w-full rounded-md border-slate-300 shadow-sm p-2 border" placeholder="Aucune" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1">Début</label>
                    <input type="date" value={form.promoStartsAt ? form.promoStartsAt.slice(0, 10) : ''}
                      onChange={e => setForm({ ...form, promoStartsAt: e.target.value ? `${e.target.value}T00:00:00Z` : undefined })}
                      className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1">Fin</label>
                    <input type="date" value={form.promoEndsAt ? form.promoEndsAt.slice(0, 10) : ''}
                      onChange={e => setForm({ ...form, promoEndsAt: e.target.value ? `${e.target.value}T23:59:59Z` : undefined })}
                      className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
                  </div>
                </div>
                <p className="text-xs text-slate-400 mt-2">Sans date de début/fin, la promotion est active immédiatement et indéfiniment. Laissez le prix promo vide pour retirer la promotion.</p>
              </div>

              <div className="pt-4 border-t border-slate-100 flex justify-end gap-3">
                <button type="button" onClick={() => setIsModalOpen(false)} className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-300 rounded-lg hover:bg-slate-50">
                  Annuler
                </button>
                <button type="submit" disabled={isSaving} className="flex items-center px-4 py-2 text-sm font-bold text-white bg-brand-green rounded-lg hover:bg-[#0f3c35] disabled:opacity-70">
                  {isSaving && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                  {editingId ? 'Enregistrer' : 'Créer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {toast && (
        <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  );
}
