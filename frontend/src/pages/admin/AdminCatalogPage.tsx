import { useCallback, useEffect, useState } from 'react';
import { Loader2, Plus, Pencil, Trash2, Package, X, RotateCcw, Tag, Search, SlidersHorizontal, ChevronLeft, ChevronRight, AlertTriangle, GraduationCap, ImageOff, Images } from 'lucide-react';
import { adminCatalogService, type AdminProductDto, type AdminProductRequestDto, type AdminCategoryDto, type CatalogFilters, type TrainingLookupDto } from '../../api/adminCatalogService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { ProductMediaDialog } from '../../components/catalog/ProductMediaDialog';
import { visuelAFaire } from '../../api/productMediaService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';

const EMPTY_FORM: AdminProductRequestDto = {
  sku: '', name: '', description: '', basePrice: 0, stockQuantity: 0, stockThreshold: 5, isQuoteOnly: false,
  categoryId: undefined, imageUrl: '', promoPrice: undefined, promoStartsAt: undefined, promoEndsAt: undefined,
  trainingId: undefined
};

function isPromoCurrentlyActive(p: AdminProductDto): boolean {
  if (!p.promoPrice) return false;
  const now = new Date();
  if (p.promoStartsAt && now < new Date(p.promoStartsAt)) return false;
  if (p.promoEndsAt && now > new Date(p.promoEndsAt)) return false;
  return true;
}

const PAGE_SIZE = 25;
const NO_FILTER: CatalogFilters = {};

export function AdminCatalogPage() {
  const [products, setProducts] = useState<AdminProductDto[]>([]);
  const [categories, setCategories] = useState<AdminCategoryDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  // Filtrage et pagination cote serveur. La page chargeait auparavant les 100 premiers
  // produits d'un catalogue qui en compte plus de 1 500 : 93 % du catalogue etait
  // inatteignable depuis cet ecran, sans que rien ne l'indique.
  const [filters, setFilters] = useState<CatalogFilters>(NO_FILTER);

  // Nombre de fiches dont le visuel reste à faire. Rechargé après chaque mise à jour d'image :
  // voir le compteur descendre est ce qui rend le chantier tenable.
  const [visuelsAFaire, setVisuelsAFaire] = useState<number | null>(null);

  // Produit dont on édite les médias. Ouvert depuis la liste : c'est en parcourant les fiches
  // incomplètes qu'on les traite, sans passer par le formulaire complet.
  const [mediasDe, setMediasDe] = useState<AdminProductDto | null>(null);
  const [searchInput, setSearchInput] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<AdminProductRequestDto>(EMPTY_FORM);
  const [isSaving, setIsSaving] = useState(false);

  const hasActiveFilter = Boolean(
    filters.search || filters.categoryId || filters.activeState || filters.lowStock
    || filters.needsVisual);

  // La saisie est temporisee : sans cela, taper « compresse » declencherait neuf requetes
  // et la reponse de la plus lente pourrait ecraser celle de la plus recente.
  useEffect(() => {
    const timer = setTimeout(() => {
      setFilters((prev) => (prev.search === searchInput ? prev : { ...prev, search: searchInput }));
      setPage(0);
    }, 350);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const fetchProducts = useCallback(async () => {
    setIsLoading(true);
    try {
      const productsPage = await adminCatalogService.listProducts(page, PAGE_SIZE, filters);
      setProducts(productsPage.content);
      setTotalPages(productsPage.totalPages);
      setTotalElements(productsPage.totalElements);
    } catch (error) {
      console.error('Failed to fetch catalog data', error);
      setToast({ message: "Impossible de charger le catalogue.", type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [page, filters]);

  useEffect(() => { fetchProducts(); }, [fetchProducts]);

  // Le compteur se recharge separement de la liste : il doit rester juste meme quand on
  // parcourt une page filtree autrement, et il ne depend d'aucun filtre.
  const rafraichirCompteur = useCallback(async () => {
    try {
      setVisuelsAFaire(await adminCatalogService.countNeedingVisual());
    } catch {
      // Un compteur indisponible ne doit pas empecher de travailler : le badge disparait,
      // le filtre reste utilisable.
      setVisuelsAFaire(null);
    }
  }, []);

  useEffect(() => { rafraichirCompteur(); }, [rafraichirCompteur]);

  // Formations proposées au rattachement. Chargées une fois : elles ne dépendent d'aucun
  // filtre, et la liste est courte.
  const [formations, setFormations] = useState<TrainingLookupDto[]>([]);
  useEffect(() => {
    adminCatalogService.listTrainingsForLinking()
      .then(setFormations)
      .catch((error) => console.error('Chargement des formations impossible', error));
  }, []);

  // Les categories ne changent pas au fil du filtrage : une seule fois suffit.
  useEffect(() => {
    adminCatalogService.listCategories()
      .then(setCategories)
      .catch((error) => console.error('Failed to fetch categories', error));
  }, []);

  const updateFilter = (patch: CatalogFilters) => {
    setFilters((prev) => ({ ...prev, ...patch }));
    setPage(0);
  };

  const resetFilters = () => {
    setSearchInput('');
    setFilters(NO_FILTER);
    setPage(0);
  };

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
      promoPrice: p.promoPrice ?? undefined, promoStartsAt: p.promoStartsAt ?? undefined, promoEndsAt: p.promoEndsAt ?? undefined,
      trainingId: p.trainingId ?? undefined
    });
    setIsModalOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {
      if (editingId) {
        await adminCatalogService.updateProduct(editingId, form);
        setToast({ message: 'Produit mis à jour.', type: 'success' });
      } else {
        await adminCatalogService.createProduct(form);
        setToast({ message: 'Produit créé.', type: 'success' });
      }
      setIsModalOpen(false);
      // Rechargement plutot que mutation locale : la liste est filtree et paginee par le
      // serveur, un produit insere en tete du tableau y apparaitrait meme s'il ne
      // correspond pas au filtre courant.
      fetchProducts();
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
      setToast({ message: updated.isActive ? 'Produit réactivé.' : 'Produit désactivé.', type: 'success' });
      // Le statut fait partie des filtres : garder la ligne en place la ferait apparaitre
      // dans une liste « Actifs » alors qu'elle vient d'etre desactivee.
      fetchProducts();
    } catch (error) {
      console.error('Erreur lors de la mise à jour du produit', error);
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

      {/* Barre de filtres. Tout est applique par le serveur : filtrer dans le navigateur
          n'aurait porte que sur la page affichee, donnant l'illusion d'un catalogue vide
          des que le produit cherche se trouve plus loin. */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 mb-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[220px]">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
            <input
              type="search"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Rechercher par nom ou référence…"
              aria-label="Rechercher un produit par nom ou référence"
              className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
            />
          </div>

          <select
            value={filters.categoryId ?? ''}
            onChange={(e) => updateFilter({ categoryId: e.target.value || undefined })}
            aria-label="Filtrer par catégorie"
            className="py-2 px-3 text-sm rounded-lg border border-slate-300 bg-white focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none max-w-[260px]"
          >
            <option value="">Toutes les catégories</option>
            {categories.map((cat) => (
              /* Le compteur evite de choisir une categorie vide et de croire a un bug. */
              <option key={cat.id} value={cat.id}>
                {cat.name} ({cat.productCount})
              </option>
            ))}
          </select>

          <select
            value={filters.activeState ?? ''}
            onChange={(e) => updateFilter({ activeState: (e.target.value || undefined) as CatalogFilters['activeState'] })}
            aria-label="Filtrer par statut"
            className="py-2 px-3 text-sm rounded-lg border border-slate-300 bg-white focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
          >
            <option value="">Tous les statuts</option>
            <option value="ACTIVE">Actifs</option>
            <option value="INACTIVE">Désactivés</option>
          </select>

          <select
            value={filters.sort ?? ''}
            onChange={(e) => updateFilter({ sort: (e.target.value || undefined) as CatalogFilters['sort'] })}
            aria-label="Trier les résultats"
            className="py-2 px-3 text-sm rounded-lg border border-slate-300 bg-white focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
          >
            <option value="">Tri par défaut</option>
            <option value="name_asc">Nom (A → Z)</option>
            <option value="price_asc">Prix croissant</option>
            <option value="price_desc">Prix décroissant</option>
          </select>

          {/* Les deux filtres à bascule restent groupés : séparés, l'un retombe seul à la
              ligne dès que la barre se resserre. */}
          <div className="flex items-center gap-2">
          {/* Compteur visible en permanence : le chantier des fiches incomplètes reste
              sous les yeux de l'équipe et se décompte à mesure, au lieu de disparaître dans
              un menu déroulant. */}
          <button
            type="button"
            onClick={() => updateFilter({ needsVisual: !filters.needsVisual })}
            aria-pressed={Boolean(filters.needsVisual)}
            title="Fiches sans visuel propre : image absente, générique ou temporaire"
            className={`inline-flex items-center gap-1.5 py-2 px-3 text-sm font-medium rounded-lg border transition-colors ${
              filters.needsVisual
                ? 'bg-amber-50 border-amber-300 text-amber-800'
                : 'bg-white border-slate-300 text-slate-600 hover:bg-slate-50'
            }`}
          >
            <ImageOff className="w-4 h-4" />
            Visuel à faire
            {visuelsAFaire !== null && (
              <span
                className={`ml-0.5 rounded-full px-1.5 py-0.5 text-[11px] font-bold tabular-nums ${
                  visuelsAFaire === 0
                    ? 'bg-emerald-100 text-emerald-700'
                    : filters.needsVisual
                      ? 'bg-amber-200 text-amber-900'
                      : 'bg-slate-100 text-slate-600'
                }`}
              >
                {visuelsAFaire}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => updateFilter({ lowStock: !filters.lowStock })}
            aria-pressed={Boolean(filters.lowStock)}
            className={`inline-flex items-center gap-1.5 py-2 px-3 text-sm font-medium rounded-lg border transition-colors ${
              filters.lowStock
                ? 'bg-rose-50 border-rose-300 text-rose-700'
                : 'bg-white border-slate-300 text-slate-600 hover:bg-slate-50'
            }`}
          >
            <AlertTriangle className="w-4 h-4" />
            Stock bas
          </button>
          </div>

          {hasActiveFilter && (
            <button
              type="button"
              onClick={resetFilters}
              className="inline-flex items-center gap-1.5 py-2 px-3 text-sm text-slate-500 hover:text-brand-dark transition-colors"
            >
              <X className="w-4 h-4" />
              Réinitialiser
            </button>
          )}
        </div>

        <div className="mt-3 flex items-center gap-2 text-xs text-slate-500">
          <SlidersHorizontal className="w-3.5 h-3.5" />
          {isLoading
            ? 'Chargement…'
            : hasActiveFilter
              ? `${totalElements} produit${totalElements > 1 ? 's' : ''} correspondent aux filtres`
              : `${totalElements} produit${totalElements > 1 ? 's' : ''} au catalogue`}
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center items-center">
            <Loader2 className="w-8 h-8 text-emerald-600 animate-spin" />
          </div>
        ) : products.length === 0 ? (
          hasActiveFilter ? (
            <EmptyState
              icon={Search}
              title="Aucun produit ne correspond à ces filtres."
              description="Élargissez la recherche ou réinitialisez les filtres."
            />
          ) : (
            <EmptyState icon={Package} title="Aucun produit. Créez-en un pour commencer." />
          )
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
                    <td className="px-6 py-4">
                      <div className="flex items-center justify-end gap-2">
                      {/* Accessible même sur un produit désactivé : on désactive souvent une
                          fiche PARCE QUE son visuel manque, et la corriger est justement ce
                          qui permettra de la réactiver. */}
                      <button
                        onClick={() => setMediasDe(p)}
                        className={`inline-flex items-center justify-center p-2 rounded-lg transition ${
                          visuelAFaire(p.imageUrl)
                            ? 'bg-amber-100 text-amber-800 hover:bg-amber-200'
                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                        }`}
                        title={visuelAFaire(p.imageUrl)
                          ? 'Visuel à faire — déposer la vraie photo'
                          : 'Médias : visuel, galerie, vidéo'}
                      >
                        <Images className="w-4 h-4" />
                      </button>
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
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-4 border-t border-slate-200 bg-slate-50">
            <span className="text-sm text-slate-600">
              Page <span className="font-semibold text-brand-dark">{page + 1}</span> sur {totalPages}
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                disabled={page === 0 || isLoading}
                className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-lg border border-slate-300 bg-white text-slate-700 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronLeft className="w-4 h-4" /> Précédent
              </button>
              <button
                type="button"
                onClick={() => setPage((prev) => Math.min(totalPages - 1, prev + 1))}
                disabled={page >= totalPages - 1 || isLoading}
                className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-lg border border-slate-300 bg-white text-slate-700 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Suivant <ChevronRight className="w-4 h-4" />
              </button>
            </div>
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

              {/* Offre liée : l'équipement et la formation qui apprend à s'en servir.
                  La relation est un à un — une formation déjà rattachée reste visible mais
                  désactivée, plutôt que masquée : la faire disparaître laisserait chercher une
                  formation qu'on sait exister. */}
              <div className="border border-slate-200 rounded-xl p-4 bg-slate-50">
                <div className="flex items-center gap-2 mb-3">
                  <GraduationCap className="w-4 h-4 text-brand-green" />
                  <h3 className="text-sm font-bold text-slate-700">Offre liée (facultatif)</h3>
                </div>
                <label htmlFor="form-training" className="block text-xs font-medium text-slate-600 mb-1">
                  Formation à l'utilisation de cet équipement
                </label>
                <select
                  id="form-training"
                  value={form.trainingId ?? ''}
                  onChange={e => setForm({ ...form, trainingId: e.target.value || undefined })}
                  className="w-full rounded-md border-slate-300 shadow-sm p-2 border bg-white"
                >
                  <option value="">Aucune formation liée</option>
                  {formations.map(f => (
                    <option
                      key={f.id}
                      value={f.id}
                      disabled={f.alreadyLinked && f.id !== form.trainingId}
                    >
                      {f.title}
                      {f.institutionName ? ` — ${f.institutionName}` : ''}
                      {f.alreadyLinked && f.id !== form.trainingId ? ' (déjà rattachée)' : ''}
                    </option>
                  ))}
                </select>
                <p className="text-xs text-slate-400 mt-2">
                  Le client verra cette formation proposée sur la fiche produit. Une formation ne
                  peut accompagner qu'un seul équipement.
                </p>
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

      {mediasDe && (
        <ProductMediaDialog
          productId={mediasDe.id}
          productName={mediasDe.name}
          onClose={() => setMediasDe(null)}
          onChanged={() => { fetchProducts(); rafraichirCompteur(); }}
        />
      )}

      {toast && (
        <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  );
}
