import { useEffect, useRef, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { catalogService, cheminProduit } from '../api/catalogService';
import type { Product } from '../api/catalogService';
import { useCart } from '../context/CartContext';
import {
  ArrowRight, GraduationCap, Loader2, Minus, Plus, ShieldCheck, Truck, X,
} from 'lucide-react';
import { CountdownTimer } from '../components/common/CountdownTimer';
import { Toast, type ToastType } from '../components/common/Toast';
import { ProductGallery } from '../components/catalog/ProductGallery';
import { ProductImage } from '../components/common/ProductImage';
import { lireFiche } from '../api/ficheProduit';
import { orderService, type QuoteRequestDto } from '../api/orderService';
import { usePageMeta } from '../hooks/usePageMeta';

const nomLisible = (nom: string) => nom.replace(/&amp;/g, '&');

/**
 * Fiche produit.
 *
 * <p><b>Deux colonnes, dont une qui suit.</b> Le panneau d'achat — prix, disponibilité,
 * quantité, bouton — reste à l'écran pendant qu'on lit la description. Sur une fiche de deux
 * mille mots, l'ancienne mise en page laissait le bouton en haut : il fallait remonter pour
 * acheter ce qu'on venait de se décider à acheter.</p>
 *
 * <p><b>La fiche technique vient du texte.</b> Le produit ne porte aucun champ de
 * caractéristiques en base, mais 668 descriptions sur 1 459 en contiennent une, saisie en
 * deux lignes. {@link lireFiche} la sépare du texte courant ; voir ce module pour le détail
 * et pour ce qui a été écarté.</p>
 *
 * <p><b>Ce que les maquettes de référence portent et qui reste absent.</b> Le cœur « favori »
 * (aucune liste d'envies côté serveur), la ligne « Marques » (le produit n'a pas de marque),
 * les encadrés de frais de livraison et de retrait en magasin (nous n'avons ni les tarifs ni
 * les conditions réelles d'Optimi Santé — les inventer afficherait un engagement commercial
 * qui n'existe pas). Le fil d'Ariane s'arrête à trois marches faute d'arborescence de
 * catégories.</p>
 */
export function ProductDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { addToCart } = useCart();
  // La quantité est retenue AVEC la fiche à laquelle elle appartient, et déduite au rendu.
  // La remettre à 1 depuis un effet déclencherait un second rendu, le temps duquel on
  // afficherait encore les « 4 » choisis sur la fiche précédente.
  const [saisieQuantite, setSaisieQuantite] = useState<{ slug?: string; n: number }>(
    { slug, n: 1 });
  const [isQuoteModalOpen, setIsQuoteModalOpen] = useState(false);
  const [isSubmittingQuote, setIsSubmittingQuote] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);
  const [quoteForm, setQuoteForm] = useState({ companyName: '', siretIce: '', message: '' });
  // La barre du bas surveille le vrai bouton d'achat. Sur grand écran le panneau est collant :
  // le bouton ne quitte jamais l'écran, et la barre ne s'affiche donc pas — deux appels à
  // l'achat côte à côte se concurrenceraient. Sur téléphone, le panneau défile et la barre
  // prend le relais.
  const ancreAchat = useRef<HTMLDivElement>(null);

  const { data: product, isLoading, error } = useQuery({
    queryKey: ['product', slug],
    queryFn: () => catalogService.getProductBySlug(slug as string),
    enabled: !!slug,
  });

  const { data: memeRayon } = useQuery({
    queryKey: ['produits-apparentes', product?.category?.id],
    queryFn: () => catalogService.getProducts({ categoryId: product!.category!.id, size: 6 }),
    enabled: Boolean(product?.category?.id),
  });

  usePageMeta(product?.name ?? 'Produit', product?.description || undefined);

  const quantity = saisieQuantite.slug === slug ? saisieQuantite.n : 1;
  const setQuantity = (n: number) => setSaisieQuantite({ slug, n });

  if (isLoading) return <div className="py-20 text-center">Chargement...</div>;
  if (error || !product) return <div className="py-20 text-center text-red-500">Produit introuvable.</div>;

  const handleAddToCart = () => {
    addToCart(product, quantity);
    setToast({ message: 'Produit ajouté au panier', type: 'success' });
  };

  const handleQuoteSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmittingQuote(true);
    try {
      const request: QuoteRequestDto = {
        items: [{ productId: product.id, quantity }],
        notes: `Société: ${quoteForm.companyName} | SIRET/ICE: ${quoteForm.siretIce}\n\nMessage: ${quoteForm.message}`,
      };
      await orderService.quoteRequest(request);
      setIsQuoteModalOpen(false);
      setToast({ message: 'Demande de devis envoyée avec succès.', type: 'success' });
      setQuoteForm({ companyName: '', siretIce: '', message: '' });
    } catch (err) {
      console.error('Erreur lors de l\'envoi de la demande de devis', err);
      setToast({ message: 'Erreur lors de l\'envoi de la demande.', type: 'error' });
    } finally {
      setIsSubmittingQuote(false);
    }
  };

  const agir = () => (product.isQuoteOnly ? setIsQuoteModalOpen(true) : handleAddToCart());
  /**
   * Optimi Santé vend en gros : au-delà du seuil, le prix se négocie. Le bouton d'achat direct
   * reste là — le devis s'ajoute, il ne remplace rien.
   */
  const seuilDevis = product.quoteThreshold ?? 50;
  const grosVolume = !product.isQuoteOnly && quantity >= seuilDevis;
  const fiche = lireFiche(product.description);
  const apparentes = (memeRayon?.content ?? []).filter((p) => p.id !== product.id).slice(0, 4);

  return (
    <div className="min-h-screen bg-slate-50">
      <nav aria-label="Fil d'Ariane" className="border-b border-slate-200 bg-white">
        <ol className="container mx-auto flex max-w-6xl flex-wrap items-center gap-2 px-6 py-4
                       text-sm text-slate-600">
          <li><Link to="/" className="underline-offset-2 hover:underline">Accueil</Link></li>
          <li aria-hidden="true" className="text-slate-400">›</li>
          <li><Link to="/catalog" className="underline-offset-2 hover:underline">Catalogue</Link></li>
          {product.category && (
            <>
              <li aria-hidden="true" className="text-slate-400">›</li>
              <li>
                <Link to={`/category/${product.category.slug}`} className="underline-offset-2 hover:underline">
                  {nomLisible(product.category.name)}
                </Link>
              </li>
            </>
          )}
          <li aria-hidden="true" className="text-slate-400">›</li>
          {/* aria-current : la marche courante est annoncée comme telle, et non comme un
              lien de plus. line-clamp-1 parce que certains noms font deux lignes à eux seuls. */}
          <li aria-current="page" className="line-clamp-1 font-semibold text-brand-dark">
            {product.name}
          </li>
        </ol>
      </nav>

      <div className="container mx-auto grid max-w-6xl items-start gap-6 px-6 py-8
                      lg:grid-cols-[minmax(0,1fr)_23rem]">
        <div className="flex flex-col gap-6">
          <div className="rounded-2xl border border-slate-200 bg-white p-6">
            <ProductGallery product={product} />
          </div>

          {fiche.paragraphes.length > 0 && (
            <section className="rounded-2xl border border-slate-200 bg-white p-6 lg:p-8">
              <h2 className="mb-5 text-xl font-bold text-brand-dark">Description</h2>
              <div className="flex flex-col gap-4">
                {/* whitespace-pre-line : les descriptions sont saisies une idée par ligne,
                    sans ligne vide entre elles. Les réduire à un bloc unique collerait
                    dix caractéristiques en une seule phrase illisible. */}
                {fiche.paragraphes.map((p, i) => (
                  <p key={i} className="whitespace-pre-line leading-relaxed text-slate-700">
                    {p}
                  </p>
                ))}
              </div>
            </section>
          )}

          {fiche.caracteristiques.length > 0 && (
            <section className="rounded-2xl border border-slate-200 bg-white p-6 lg:p-8">
              <h2 className="mb-5 text-xl font-bold text-brand-dark">Fiche technique</h2>
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <tbody>
                    {fiche.caracteristiques.map((c, i) => (
                      <tr key={i} className="border-b border-slate-100 last:border-0">
                        <th scope="row"
                            className="w-2/5 py-3 pr-4 text-left font-semibold text-brand-dark">
                          {c.libelle}
                        </th>
                        <td className="py-3 text-slate-700">{c.valeur}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </div>

        {/* La colonne d'achat suit le défilement. `top-[146px]` = les 130 px de l'en-tête
            collante, plus une respiration. */}
        <div className="flex flex-col gap-6 lg:sticky lg:top-[146px]">
          <PanneauAchat
            product={product}
            quantity={quantity}
            setQuantity={setQuantity}
            agir={agir}
            ancre={ancreAchat}
            grosVolume={grosVolume}
            demanderDevis={() => setIsQuoteModalOpen(true)}
          />

          {product.relatedTraining && (
            <Link
              to={`/formations/${product.relatedTraining.id}`}
              className="group block rounded-2xl border border-brand/25 bg-brand/5 p-5 transition-colors hover:bg-brand/10"
            >
              <div className="flex items-start gap-4">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand text-white">
                  <GraduationCap className="h-5 w-5" aria-hidden="true" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="mb-1 text-[11px] font-bold uppercase tracking-wider text-brand">
                    Formez-vous à cet équipement
                  </p>
                  <p className="font-bold leading-snug text-brand-dark">
                    {product.relatedTraining.title}
                  </p>
                  <p className="mt-1 text-sm text-slate-600">
                    {product.relatedTraining.durationDays} jour
                    {product.relatedTraining.durationDays > 1 ? 's' : ''} ·{' '}
                    {product.relatedTraining.price.toFixed(0)} €
                  </p>
                </div>
                <ArrowRight className="mt-1 h-5 w-5 shrink-0 text-brand transition-transform group-hover:translate-x-1" aria-hidden="true" />
              </div>
            </Link>
          )}

          {apparentes.length > 0 && (
            <section className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="mb-4 text-sm font-bold uppercase tracking-wider text-brand-dark">
                Produits apparentés
              </h2>
              <ul className="flex flex-col divide-y divide-slate-100">
                {apparentes.map((p) => <LigneApparentee key={p.id} product={p} />)}
              </ul>
              {product.category && (
                <Link
                  to={`/category/${product.category.slug}`}
                  className="mt-4 flex items-center gap-1.5 text-sm font-semibold text-brand hover:underline"
                >
                  Tout le rayon <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </Link>
              )}
            </section>
          )}
        </div>
      </div>

      <BarreAchatCollante product={product} agir={agir} ancre={ancreAchat} />

      {isQuoteModalOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-lg overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 p-6">
              <h2 className="text-xl font-bold text-brand-dark">Demande de Devis B2B</h2>
              <button onClick={() => setIsQuoteModalOpen(false)} className="text-slate-400 hover:text-brand-dark">
                <X className="h-6 w-6" />
                <span className="sr-only">Fermer</span>
              </button>
            </div>
            <form onSubmit={handleQuoteSubmit} className="space-y-4 p-6">
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">Nom de la Société</label>
                <input
                  type="text" required value={quoteForm.companyName}
                  onChange={(e) => setQuoteForm({ ...quoteForm, companyName: e.target.value })}
                  className="w-full rounded-md border border-slate-300 p-2 shadow-sm focus:border-brand focus:ring-brand"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">SIRET / ICE</label>
                <input
                  type="text" required value={quoteForm.siretIce}
                  onChange={(e) => setQuoteForm({ ...quoteForm, siretIce: e.target.value })}
                  className="w-full rounded-md border border-slate-300 p-2 shadow-sm focus:border-brand focus:ring-brand"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">Message spécifique (optionnel)</label>
                <textarea
                  rows={3} value={quoteForm.message}
                  onChange={(e) => setQuoteForm({ ...quoteForm, message: e.target.value })}
                  className="w-full rounded-md border border-slate-300 p-2 shadow-sm focus:border-brand focus:ring-brand"
                />
              </div>
              <div className="flex justify-end gap-3 border-t border-slate-100 pt-4">
                <button type="button" onClick={() => setIsQuoteModalOpen(false)} className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50">
                  Annuler
                </button>
                <button type="submit" disabled={isSubmittingQuote} className="flex items-center rounded-lg bg-brand-accent px-4 py-2 text-sm font-bold text-white hover:bg-brand-accent-fonce disabled:opacity-70">
                  {isSubmittingQuote && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Envoyer la demande
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}

/** Prix, disponibilité, quantité, achat. Tout ce qu'il faut pour décider. */
function PanneauAchat({ product, quantity, setQuantity, agir, ancre, grosVolume, demanderDevis }: {
  product: Product;
  quantity: number;
  setQuantity: (n: number) => void;
  agir: () => void;
  ancre: React.RefObject<HTMLDivElement | null>;
  /** Vrai au-delà du seuil de gros volume : le prix se négocie alors par devis. */
  grosVolume: boolean;
  demanderDevis: () => void;
}) {
  const remise = product.isOnPromo && product.basePrice > product.finalPrice
    ? Math.round((1 - product.finalPrice / product.basePrice) * 100)
    : 0;
  const enRupture = !product.isQuoteOnly && product.stockQuantity < 1;

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6">
      {product.category && (
        <Link
          to={`/category/${product.category.slug}`}
          className="mb-2 block text-xs font-bold uppercase tracking-widest text-brand hover:underline"
        >
          {nomLisible(product.category.name)}
        </Link>
      )}
      <h1 className="text-2xl font-extrabold leading-tight text-brand-dark lg:text-3xl">
        {product.name}
      </h1>
      <p className="mt-2 font-mono text-xs text-slate-500">Réf. {product.sku}</p>

      <div className="mt-5 flex flex-wrap items-baseline gap-x-3 gap-y-1">
        {product.isQuoteOnly ? (
          <span className="text-2xl font-extrabold text-brand-dark">Tarif sur demande</span>
        ) : (
          <>
            <span className={`text-3xl font-extrabold ${remise > 0 ? 'text-brand-accent' : 'text-brand-dark'}`}>
              {product.finalPrice.toFixed(0)} €
            </span>
            {(remise > 0 || product.b2bDiscountRate > 0) && (
              <span className="text-lg text-slate-500 line-through">
                {product.basePrice.toFixed(0)} €
              </span>
            )}
            {remise > 0 && (
              // Encre sur l'orange de la charte : 5,35:1. Du blanc n'y tiendrait pas.
              <span className="rounded-full bg-brand-orange px-2.5 py-1 text-xs font-bold text-brand-dark">
                −{remise} %
              </span>
            )}
          </>
        )}
      </div>

      <Disponibilite product={product} />

      {!product.isQuoteOnly && product.stockQuantity > 0 && product.stockQuantity < 5 && (
        <div className="mt-4 flex flex-wrap items-center gap-3 rounded-xl border border-danger/20 bg-danger/5 p-3">
          <span className="text-sm font-medium text-danger">Réservation temporaire :</span>
          <CountdownTimer initialMinutes={15} />
        </div>
      )}

      <div ref={ancre} className="mt-5 flex gap-3">
        {!product.isQuoteOnly && (
          <div className="flex h-12 items-center overflow-hidden rounded-xl border border-slate-200">
            <button
              type="button" onClick={() => setQuantity(Math.max(1, quantity - 1))}
              disabled={quantity <= 1}
              className="flex h-full w-11 items-center justify-center text-slate-600 transition-colors hover:bg-slate-50 disabled:opacity-30"
            >
              <Minus className="h-4 w-4" aria-hidden="true" />
              <span className="sr-only">Retirer un</span>
            </button>
            <span aria-live="polite" className="w-10 text-center font-semibold text-brand-dark">
              {quantity}
            </span>
            <button
              type="button" onClick={() => setQuantity(quantity + 1)}
              className="flex h-full w-11 items-center justify-center text-slate-600 transition-colors hover:bg-slate-50"
            >
              <Plus className="h-4 w-4" aria-hidden="true" />
              <span className="sr-only">Ajouter un</span>
            </button>
          </div>
        )}
        <button
          type="button" onClick={agir} disabled={enRupture}
          className="h-12 flex-1 rounded-xl bg-brand font-bold text-white transition-colors hover:bg-brand-fonce disabled:cursor-not-allowed disabled:opacity-50"
        >
          {product.isQuoteOnly ? 'Demander un devis' : enRupture ? 'Indisponible' : 'Ajouter au panier'}
        </button>
      </div>

      {/* Gros volume : l'achat direct reste possible, le devis s'ajoute. Optimi Santé approvisionne
          des hôpitaux et des pharmacies, et à partir de ce volume le tarif se négocie. */}
      {grosVolume && (
        <div className="mt-3 rounded-xl border border-brand/30 bg-brand-light/40 p-4">
          <p className="text-sm font-semibold text-brand-dark">Commande en gros volume</p>
          <p className="mt-1 text-sm text-slate-600">
            À partir de {product.quoteThreshold ?? 50} unités, demandez un devis : nos équipes
            étudient un tarif dégressif et les délais de livraison.
          </p>
          <button
            type="button" onClick={demanderDevis}
            className="mt-3 inline-flex h-11 items-center justify-center rounded-xl border border-brand px-5 font-bold text-brand transition-colors hover:bg-brand hover:text-white"
          >
            Demander un devis B2B
          </button>
        </div>
      )}

      <ul className="mt-6 flex flex-col gap-3 border-t border-slate-100 pt-5">
        <li className="flex items-center gap-3">
          <ShieldCheck className="h-5 w-5 shrink-0 text-brand" aria-hidden="true" />
          <span className="text-sm text-slate-700">Garantie 2 ans incluse</span>
        </li>
        <li className="flex items-center gap-3">
          <Truck className="h-5 w-5 shrink-0 text-brand" aria-hidden="true" />
          <span className="text-sm text-slate-700">Livraison chiffrée au devis</span>
        </li>
      </ul>
    </div>
  );
}

/** L'état du stock, dit en toutes lettres plutôt que par une couleur seule. */
function Disponibilite({ product }: { product: Product }) {
  if (product.isQuoteOnly) return null;

  const [fond, texte, libelle] = product.stockQuantity < 1
    ? ['bg-danger/10', 'text-danger', 'Rupture de stock']
    : product.stockQuantity < 5
      ? ['bg-warning/10', 'text-warning', `Plus que ${product.stockQuantity} en stock`]
      : ['bg-success/10', 'text-success', 'En stock'];

  return (
    <p className={`mt-4 flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-bold ${fond} ${texte}`}>
      <span aria-hidden="true" className="h-2 w-2 rounded-full bg-current" />
      {libelle}
    </p>
  );
}

function LigneApparentee({ product }: { product: Product }) {
  return (
    <li>
      <Link to={cheminProduit(product.slug)} className="group flex items-center gap-3 py-3">
        <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-lg border border-slate-100">
          <ProductImage
            src={product.imageUrl} alt=""
            className="h-full w-full p-1.5" iconClassName="w-5 h-5"
          />
        </div>
        <div className="min-w-0 flex-1">
          <p className="line-clamp-2 text-sm font-semibold leading-snug text-brand-dark group-hover:text-brand">
            {product.name}
          </p>
          <p className="mt-0.5 text-sm font-bold text-brand-dark">
            {product.isQuoteOnly ? 'Sur devis' : `${product.finalPrice.toFixed(0)} €`}
          </p>
        </div>
      </Link>
    </li>
  );
}

/**
 * Rappel d'achat en bas d'écran, une fois le panneau principal dépassé.
 *
 * <p>Elle ne se montre pas tant que le vrai bouton est visible : deux appels à l'achat côte à
 * côte se concurrencent, et la barre masquerait le bas de la fiche sans rien apporter.</p>
 */
function BarreAchatCollante({ product, agir, ancre }: {
  product: Product;
  agir: () => void;
  ancre: React.RefObject<HTMLDivElement | null>;
}) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const cible = ancre.current;
    if (!cible) return;
    // `top < 0` : la barre n'apparaît que si le bouton est sorti par le HAUT. Sans ce test
    // elle se montrerait aussi à l'ouverture, tant que le bouton est encore plus bas que
    // l'écran sur un petit téléphone.
    const guetteur = new IntersectionObserver(
      ([e]) => setVisible(!e.isIntersecting && e.boundingClientRect.top < 0),
      { threshold: 0 },
    );
    guetteur.observe(cible);
    return () => guetteur.disconnect();
  }, [ancre]);

  const enRupture = !product.isQuoteOnly && product.stockQuantity < 1;

  return (
    <div
      className={`fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white/95 backdrop-blur
                    transition-transform duration-200 ${visible ? 'translate-y-0' : 'translate-y-full'}`}
      >
        <div className="container mx-auto flex max-w-6xl items-center gap-4 px-6 py-3">
          <div className="hidden h-12 w-12 shrink-0 items-center justify-center rounded-lg border border-slate-100 sm:flex">
            <ProductImage src={product.imageUrl} alt="" className="h-full w-full p-1" iconClassName="w-4 h-4" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="line-clamp-1 text-sm font-bold text-brand-dark">{product.name}</p>
            <p className="text-sm font-bold text-brand-dark">
              {product.isQuoteOnly ? 'Tarif sur demande' : `${product.finalPrice.toFixed(0)} €`}
            </p>
          </div>
          <button
            type="button" onClick={agir} disabled={enRupture}
            // tabIndex=-1 quand la barre est cachée : une barre hors écran garde ses boutons
            // dans l'ordre de tabulation, et le clavier tombe sur une commande invisible.
            tabIndex={visible ? 0 : -1}
            className="h-11 shrink-0 rounded-xl bg-brand px-6 font-bold text-white transition-colors hover:bg-brand-fonce disabled:opacity-50"
          >
          {product.isQuoteOnly ? 'Demander un devis' : enRupture ? 'Indisponible' : 'Ajouter au panier'}
        </button>
      </div>
    </div>
  );
}
