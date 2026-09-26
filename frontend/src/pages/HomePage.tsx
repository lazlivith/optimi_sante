import { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Heart, ShoppingCart, ArrowRight, MessageCircle } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { catalogService, cheminProduit } from '../api/catalogService';
import type { Product } from '../api/catalogService';
import { useCart } from '../context/CartContext';
import { ProductImage } from '../components/common/ProductImage';
import { usePageMeta } from '../hooks/usePageMeta';
import { blogService } from '../api/blogService';
import { formaterPeriode } from '../lib/dates';
import { PromoHeroSlider } from '../components/home/PromoHeroSlider';
import { CabinetSection } from '../components/home/CabinetSection';
import { RayonsCarousel } from '../components/home/RayonsCarousel';

const HOMEPAGE_CATALOG_PREVIEW_COUNT = 8;

// ──────────────────────────────────────────────────
// Hero Slider
// ──────────────────────────────────────────────────
const heroSlides = [
  {
    id: 1,
    badge: 'NOUVEAUTÉ 2026',
    title: 'Votre allié pour un diagnostic\nprécis et efficace',
    subtitle: 'Dispositifs médicaux certifiés CE · Livraison rapide',
    bgFrom: '#C8E6F5',
    bgTo: '#E8F4FD',
    accentColor: '#154D44',
    imageSrc: 'https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/default-medical-equipment.jpg',
  },
  {
    id: 2,
    badge: 'MOBILITÉ MÉDICALE',
    title: 'Formations cliniques\ndans les CHU partenaires',
    subtitle: 'Programme Afrique → France · Accompagnement visa inclus',
    bgFrom: '#D4EDD6',
    bgTo: '#E8F5E9',
    accentColor: '#154D44',
    imageSrc: 'https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/stetho.jpg',
  },
  {
    id: 3,
    badge: 'OFFRE B2B',
    title: 'Équipez votre cabinet\nou votre structure de soin',
    subtitle: 'Devis personnalisé · Remises professionnelles négociables',
    bgFrom: '#F5E6D4',
    bgTo: '#FDF3E8',
    accentColor: '#D98A3C',
    imageSrc: 'https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/microscope.jpg',
  },
];

function HeroSlider() {
  const [current, setCurrent] = useState(0);
  const total = heroSlides.length;
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const startInterval = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    intervalRef.current = setInterval(() => setCurrent(c => (c + 1) % total), 5000);
  };

  useEffect(() => {
    startInterval();
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, []);

  const goTo = (idx: number) => { setCurrent(idx); startInterval(); };
  const prev = () => goTo((current - 1 + total) % total);
  const next = () => goTo((current + 1) % total);
  const slide = heroSlides[current];

  return (
    <section
      className="relative overflow-hidden rounded-2xl mx-4 md:mx-8 mt-4"
      style={{ background: `linear-gradient(135deg, ${slide.bgFrom}, ${slide.bgTo})`, minHeight: 280 }}
    >
      <div className="container mx-auto px-8 py-12 grid lg:grid-cols-2 gap-8 items-center min-h-[280px]">
        {/* Text */}
        <div className="space-y-4">
          <span className="inline-block text-[10px] font-bold tracking-widest uppercase px-3 py-1 rounded-full bg-white/60 text-gray-600">
            {slide.badge}
          </span>
          <h1
            className="text-3xl md:text-4xl font-bold leading-tight"
            style={{ color: slide.accentColor }}
          >
            {slide.title.split('\n').map((line, i) => (
              <span key={i}>{line}{i < slide.title.split('\n').length - 1 && <br />}</span>
            ))}
          </h1>
          <p className="text-sm text-gray-500">{slide.subtitle}</p>
          <div className="flex gap-3 pt-2">
            <Link
              to="/catalog"
              className="inline-flex items-center gap-2 px-6 py-2.5 rounded-full text-sm font-semibold text-white transition-colors shadow-md"
              style={{ backgroundColor: slide.accentColor }}
            >
              Voir les produits <ArrowRight className="w-4 h-4" />
            </Link>
            <Link to="/formations" className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full text-sm font-semibold text-gray-700 bg-white/80 hover:bg-white transition-colors">
              Formations
            </Link>
          </div>
        </div>

        {/* Image */}
        <div className="flex justify-center items-end h-52">
          <img
            src={slide.imageSrc}
            alt="produit médical"
            className="h-full object-contain drop-shadow-xl transition-all duration-500"
            onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
          />
        </div>
      </div>

      {/* Arrow nav */}
      <button onClick={prev} className="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-white shadow-md flex items-center justify-center text-gray-700 hover:bg-gray-50 transition-colors z-10">
        <ChevronLeft className="w-5 h-5" />
      </button>
      <button onClick={next} className="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-white shadow-md flex items-center justify-center text-gray-700 hover:bg-gray-50 transition-colors z-10">
        <ChevronRight className="w-5 h-5" />
      </button>

      {/* Dots */}
      <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2">
        {heroSlides.map((_, i) => (
          <button key={i} onClick={() => goTo(i)} className={`w-2 h-2 rounded-full transition-all ${i === current ? 'bg-brand-dark w-5' : 'bg-gray-300'}`} />
        ))}
      </div>
    </section>
  );
}

// ──────────────────────────────────────────────────
// Featured Cards ("Incontournables")
// ──────────────────────────────────────────────────
/**
 * Teintes de fond des cartes « Incontournables ».
 *
 * <p>Une par carte, pour que la rangee se lise comme quatre univers et non comme une grille
 * uniforme. Chaque teinte est assez pale pour porter un titre en encre : c'est ce qui permet
 * d'ecrire par-dessus sans cartouche ni voile.</p>
 */
const TEINTES_INCONTOURNABLES = [
  'from-violet-100 to-violet-50',
  'from-rose-100 to-amber-50',
  'from-slate-100 to-slate-50',
  'from-lime-100 to-emerald-50',
];

/**
 * Les quatre produits mis en avant.
 *
 * <p>Les quatre PREMIERS du catalogue etaient pris tels quels : un produit de test sans
 * categorie et un produit sans visuel se retrouvaient ainsi en page d'accueil — l'un menait
 * au catalogue entier, l'autre affichait un pictogramme de repli. On ne met en avant que ce
 * qui est presentable : une categorie pour savoir ou mener, et une vraie photo.</p>
 *
 * <p>Le repli garde l'ordre d'origine : si moins de quatre produits satisfont ces conditions,
 * mieux vaut une rangee imparfaite qu'une rangee incomplete.</p>
 */
function selectionnerVedettes(products: Product[]): Product[] {
  const presentables = products.filter(
    (p) => p.category?.id && !visuelAFaire(p.imageUrl));
  return presentables.length >= 4 ? presentables.slice(0, 4) : products.slice(0, 4);
}

function FeaturedSection({ products }: { products: Product[] }) {
  const vedettes = selectionnerVedettes(products);
  if (vedettes.length === 0) return null;

  return (
    <section className="container mx-auto px-4 md:px-8 py-10">
      <h2 className="mb-6 text-3xl font-extrabold tracking-tight text-brand-dark">
        Incontournables
      </h2>

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4 md:gap-5">
        {vedettes.map((item, index) => {
          const remise = item.isOnPromo && item.basePrice > item.finalPrice
            ? Math.round((1 - item.finalPrice / item.basePrice) * 100)
            : 0;

          return (
            <Link
              // Vers les produits SIMILAIRES, et non vers le catalogue entier : depuis une
              // vignette « Fauteuils releveurs », on veut les fauteuils releveurs. La page de
              // rayon les presente ; le filtre du catalogue ne faisait que les lister. A
              // defaut de categorie, on retombe sur le catalogue plutot que sur une impasse.
              to={item.category?.slug ? `/category/${item.category.slug}` : '/catalog'}
              key={item.id}
              className={`group relative flex flex-col overflow-hidden rounded-2xl
                          bg-gradient-to-b ${TEINTES_INCONTOURNABLES[index % 4]}
                          transition-shadow hover:shadow-xl`}
            >
              {/* Le titre occupe le haut de la carte, sur la teinte : c'est ce qui donne a la
                  rangee son allure d'affiche. L'image vient dessous, au lieu d'etre posee a
                  cote d'un texte en petit corps. */}
              <div className="px-5 pt-5 pb-1">
                <h3 className="line-clamp-2 min-h-[2.75rem] text-sm font-bold leading-snug
                               text-brand-dark sm:min-h-[3.25rem] sm:text-base lg:text-lg">
                  {item.name}
                </h3>
                {/* Remise et prix sur la meme ligne, sous le titre. Le prix etait auparavant
                    place en absolu au bas de la carte : depuis que celle-ci est courte, il s'y
                    serait superpose a l'image. */}
                <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1.5">
                  {remise > 0 && (
                    <span className="rounded-full bg-brand-accent px-3 py-1 text-xs font-bold text-white">
                      Jusqu’à −{remise} %
                    </span>
                  )}
                  {!item.isQuoteOnly && (
                    <span className="text-sm font-bold text-brand-dark/70">
                      {item.finalPrice.toFixed(0)} €
                    </span>
                  )}
                </div>
              </div>

              {/* Hauteur FIXE, et non un ratio. Un ratio se calcule sur la largeur de la
                  colonne : sur un ecran large, chaque colonne fait plus de 400 pixels, et
                  « aspect-[3/4] » en reclamait donc 600 rien que pour l'image — la carte
                  depassait l'ecran. Une hauteur en rem ne depend pas de la largeur. */}
              <div className="flex h-36 items-center justify-center px-4 pb-5 sm:h-44 lg:h-52">
                <ProductImage
                  src={item.imageUrl}
                  alt={item.name}
                  objectFit="contain"
                  className="h-full w-full drop-shadow-[0_12px_20px_rgba(11,36,48,0.15)]
                             transition-transform duration-300 group-hover:scale-105"
                  iconClassName="w-10 h-10 opacity-40"
                />
              </div>

            </Link>
          );
        })}
      </div>
    </section>
  );
}

// ──────────────────────────────────────────────────
// Category Grid
// ──────────────────────────────────────────────────
import type { Category } from '../api/catalogService';
import { visuelAFaire } from '../api/productMediaService';

/** Le catalogue importé contient encore des entités HTML dans les noms : « Diagnostic &amp; Secours ». */
const nomLisible = (nom: string) => nom.replace(/&amp;/g, '&');

/** Rayons mis en avant sur l'accueil : deux rangées de sept, comme la maquette. */
const RAYONS_AFFICHES = 14;

/**
 * Les rayons du catalogue.
 *
 * <p><b>Les images sont désormais celles du catalogue.</b> Cette section illustrait chaque
 * catégorie avec une photo Unsplash choisie par mot-clé dans son nom — « cardio », « mobilier »,
 * « instru » — et, quand aucun mot ne tombait, avec la photo suivante d'un tableau de huit.
 * Le rayon « Sparadrap » héritait donc d'une photo de bloc opératoire, et deux catégories sans
 * rapport partageaient la même. Le serveur remonte maintenant, pour chaque rayon, la photo d'un
 * produit qu'il contient réellement.</p>
 *
 * <p><b>On n'affiche que des rayons qui mènent quelque part.</b> 85 des 211 catégories ne
 * contiennent aucun produit, et 16 autres n'ont encore aucune photo : une vignette vers l'une
 * d'elles serait une impasse. Les rayons sont classés par nombre de produits — c'est le seul
 * classement que les données permettent, et il place devant ce que la boutique vend le plus.</p>
 */
function CategoriesSection({ categories }: { categories: Category[] }) {
  const rayons = categories
    // Un rayon s'écarte s'il est CONNU vide, pas si son compteur est absent. La nuance a
    // coûté une section entière : tant que le serveur n'était pas redéployé, aucune
    // catégorie ne portait de compteur, `(undefined ?? 0) > 0` était faux partout, et la
    // section disparaissait de l'accueil sans rien signaler.
    .filter((c) => c.productCount !== 0 && !visuelAFaire(c.imageUrl))
    .sort((a, b) => (b.productCount ?? 0) - (a.productCount ?? 0))
    .slice(0, RAYONS_AFFICHES);

  if (rayons.length === 0) return null;

  return (
    <section className="container mx-auto px-4 md:px-8 py-10">
      <div className="mb-7 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-2xl font-bold text-brand-dark lg:text-3xl">Catégories</h2>
        <Link
          to="/catalog"
          className="flex items-center gap-1.5 text-sm font-semibold text-brand hover:underline"
        >
          Voir tout le catalogue <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
      </div>

      <ul className="grid grid-cols-3 gap-x-3 gap-y-6 sm:grid-cols-4 lg:grid-cols-7">
        {rayons.map((cat) => (
          <li key={cat.id}>
            <Link
              to={`/category/${cat.slug}`}
              className="group flex h-full flex-col items-center gap-2 rounded-2xl p-2
                         transition-all hover:bg-white hover:shadow-md
                         hover:ring-1 hover:ring-brand/20
                         focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand"
            >
              {/* Hauteur fixe, jamais un ratio : un ratio se calcule sur la largeur de la
                  colonne, et la vignette enflerait avec l'écran. */}
              <div className="flex h-24 w-full items-center justify-center sm:h-28">
                <img
                  src={cat.imageUrl ?? undefined}
                  alt=""
                  loading="lazy"
                  className="max-h-full max-w-full object-contain
                             transition-transform duration-300 group-hover:scale-105"
                />
              </div>
              {/* L'image porte alt="" : elle illustre le rayon, elle ne le nomme pas. Le nom
                  juste dessous est le vrai libellé du lien, et le répéter ferait entendre
                  deux fois la même chose à un lecteur d'écran. */}
              <span className="text-center text-sm font-medium leading-tight text-brand-dark
                               group-hover:text-brand">
                {nomLisible(cat.name)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

// ──────────────────────────────────────────────────
// Essential Medical Equipment Section
// ──────────────────────────────────────────────────
function EssentialEquipmentSection({ products }: { products: Product[] }) {
  if (!products || products.length === 0) return null;
  
  // Get top 4 most expensive products
  const essentials = [...products]
    .sort((a, b) => b.finalPrice - a.finalPrice)
    .slice(0, 4);

  return (
    <section className="container mx-auto px-4 md:px-8 py-8">
      <h2 className="text-2xl font-bold text-gray-800 mb-5">Équipements médicaux essentiels</h2>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {essentials.map(item => (
          <Link to={cheminProduit(item.slug)} key={item.id} className="bg-white rounded-xl border border-gray-100 p-4 flex flex-col hover:shadow-lg transition-all group relative">
            {/* Image */}
            <div className="flex items-center justify-center h-48 mb-4 bg-gray-50 rounded-lg p-2">
              <ProductImage
                src={item.imageUrl}
                alt={item.name}
                className="max-h-full w-full h-full group-hover:scale-105 transition-transform duration-300"
                iconClassName="w-12 h-12 opacity-40"
              />
            </div>
            
            {/* Content */}
            <div className="flex flex-col flex-1">
              <span className="text-[11px] text-gray-600 font-bold uppercase tracking-wider mb-1">
                {(item.category?.name || 'Équipement').replace(/&amp;/g, '&')}
              </span>
              <h3 className="text-sm font-medium text-gray-800 line-clamp-2 h-10 mb-2">
                {item.name}
              </h3>
              <div className="mt-auto pt-2 border-t border-gray-50 flex items-center justify-between">
                <span className="text-brand-dark font-bold text-lg">{item.finalPrice.toFixed(2)} €</span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}

// ──────────────────────────────────────────────────
// Product Card
// ──────────────────────────────────────────────────
function ProductCardHome({ product }: { product: Product }) {
  const { addToCart } = useCart();
  const [liked, setLiked] = useState(false);

  return (
    <div className="bg-white rounded-xl border border-gray-100 p-4 flex flex-col hover:shadow-lg transition-all group relative">
      <Link to={cheminProduit(product.slug)} className="absolute inset-0 z-0" aria-label={`Voir ${product.name}`}></Link>
      
      {/* Badge */}
      <div className="relative z-10 pointer-events-none">
        {product.isQuoteOnly ? (
          <span className="absolute top-3 left-3 bg-blue-500 text-white text-[9px] font-bold px-2 py-0.5 rounded uppercase">Devis</span>
        ) : product.b2bDiscountRate > 0 ? (
          <span className="absolute top-3 left-3 bg-red-500 text-white text-[9px] font-bold px-2 py-0.5 rounded uppercase">{product.b2bDiscountRate}% off</span>
        ) : product.stockQuantity < 5 && product.stockQuantity > 0 ? (
          <span className="absolute top-3 left-3 bg-orange-500 text-white text-[9px] font-bold px-2 py-0.5 rounded uppercase">Bon Plan</span>
        ) : null}
      </div>

      {/* Wishlist */}
      <button
        onClick={() => setLiked(!liked)}
        className="absolute top-3 right-3 text-gray-300 hover:text-red-400 transition-colors z-10"
        aria-label="Ajouter aux favoris"
      >
        <Heart className={`w-4 h-4 ${liked ? 'fill-red-400 text-red-400' : ''}`} />
      </button>

      {/* Image */}
      <div className="flex items-center justify-center h-36 mb-3 pointer-events-none">
        <ProductImage
          src={product.imageUrl}
          alt={product.name}
          className="max-h-full w-full h-full group-hover:scale-105 transition-transform duration-300"
          iconClassName="w-10 h-10 opacity-40"
        />
      </div>

      {/* Name */}
      <p className="text-xs font-medium text-gray-700 line-clamp-2 mb-2 flex-1 pointer-events-none">{product.name}</p>

      {/* Price */}
      <div className="mb-3 pointer-events-none">
        {product.isQuoteOnly ? (
          <span className="text-xs text-gray-500 flex items-center gap-1">
            <ShoppingCart className="w-3 h-3" /> Prix sur devis
          </span>
        ) : (
          <>
            {product.b2bDiscountRate > 0 && (
              <span className="text-xs text-gray-400 line-through block">{product.basePrice.toFixed(0)} €</span>
            )}
            <span className="text-sm font-bold text-red-600">{product.finalPrice.toFixed(0)} €</span>
          </>
        )}
      </div>

      {/* CTA */}
      <button
        onClick={() => addToCart(product, 1)}
        className="w-full border border-gray-300 rounded-full py-1.5 text-xs font-semibold text-gray-700 hover:bg-brand hover:text-white hover:border-brand transition-all relative z-10"
      >
        + Acheter
      </button>
    </div>
  );
}

// ──────────────────────────────────────────────────
// Promos Section (Horizontal Scroll)
// ──────────────────────────────────────────────────
function PromosSection({ products }: { products: Product[] }) {
  const [start, setStart] = useState(0);
  const visible = 5;

  if (products.length === 0) return null;

  return (
    <section className="container mx-auto px-4 md:px-8 py-8">
      <h2 className="text-2xl font-bold text-gray-800 mb-5">Nos promos du mois</h2>
      <div className="relative">
        <div className="grid grid-cols-5 gap-3">
          {products.slice(start, start + visible).map(p => (
            <ProductCardHome key={p.id} product={p} />
          ))}
        </div>
        <div className="flex items-center gap-2 mt-4">
          <button onClick={() => setStart(i => Math.max(0, i - visible))} disabled={start === 0} className="w-8 h-8 rounded-full border border-gray-200 flex items-center justify-center text-gray-600 hover:bg-gray-50 disabled:opacity-30">
            <ChevronLeft className="w-4 h-4" />
          </button>
          <button onClick={() => setStart(i => Math.min(products.length - 1, i + visible))} disabled={start + visible >= products.length} className="w-8 h-8 rounded-full border border-gray-200 flex items-center justify-center text-gray-600 hover:bg-gray-50 disabled:opacity-30">
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </section>
  );
}

// ──────────────────────────────────────────────────
// Partners Slider
// ──────────────────────────────────────────────────
const partners = [
  'CHU de Bordeaux', 'CHU de Lyon', 'AP-HP Paris', 'CHU de Nantes',
  'CHU de Montpellier', 'CHU de Toulouse', 'CHU de Lille', 'CHU de Strasbourg',
];

function PartnersSection() {
  return (
    <section className="container mx-auto px-4 md:px-8 py-8">
      <h2 className="text-xl font-bold text-gray-800 mb-5 text-center">Ils nous font confiance</h2>
      <div className="flex gap-4 overflow-x-auto pb-2 scrollbar-hide">
        {partners.map(p => (
          <div key={p} className="shrink-0 bg-white rounded-xl border border-gray-100 shadow-sm px-6 py-3 flex items-center justify-center h-16 min-w-[160px]">
            <span className="text-xs font-semibold text-gray-600 text-center">{p}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

// ──────────────────────────────────────────────────
// Main Page
// ──────────────────────────────────────────────────
export function HomePage() {
  usePageMeta('');
  const { data: productsData } = useQuery({
    queryKey: ['products-home'],
    queryFn: () => catalogService.getProducts({ size: 48 }),
  });
  
  const { data: categories = [] } = useQuery({
    queryKey: ['categories-home'],
    queryFn: () => catalogService.getCategories(),
  });

  const products = productsData?.content ?? [];

  // Les promotions actives, servies par le meme filtre que la page « Promotions » et que le
  // prix reellement facture : les trois ne peuvent pas diverger.
  const { data: promoData } = useQuery({
    queryKey: ['products-promo-home'],
    queryFn: () => catalogService.getProducts({ promo: true, size: 8 }),
  });
  const promos = promoData?.content ?? [];

  // Le prochain evenement du blog, s'il y en a un : la banniere ci-dessous s'y adapte.
  // Requete a part du reste, et sans blocage — le serveur repond 204 quand l'agenda est
  // vide, et la page d'accueil ne doit pas attendre le blog pour s'afficher.
  const { data: prochainEvenement } = useQuery({
    queryKey: ['blog-prochain-evenement'],
    queryFn: () => blogService.prochainEvenement(),
  });

  return (
    <div className="flex flex-col bg-gray-50 min-h-screen">
      {/* Le carrousel montre les vraies promotions. Sans aucune promotion active, il
          retomberait sur un cadre vide : on garde alors les bannieres statiques, qui restent
          une presentation valable de l'offre. */}
      {promos.length > 0 ? <PromoHeroSlider products={promos} /> : <HeroSlider />}
      <FeaturedSection products={products} />
      <CategoriesSection categories={categories} />
      <EssentialEquipmentSection products={products} />

      {/* Remplace « Dispositifs de pointe », qui affichait les trois premiers produits du
          catalogue sous un titre annonçant une sélection. */}
      <CabinetSection categories={categories} />

      {/* Auparavant : products.slice(0, 10), c'est-a-dire les dix premiers produits du
          catalogue — la section « Nos promos du mois » n'affichait aucune promotion. */}
      <PromosSection products={promos} />
      {/* Remplace « Nos best-sellers par catégorie », qui refiltrait six produits pris au
          rang 5 du catalogue et n'en montrait qu'un ou deux par onglet. */}
      <RayonsCarousel categories={categories} />

      {/* Events Banner */}
      <section className="container mx-auto px-4 md:px-8 py-8">
        <div className="grid md:grid-cols-2 gap-6">
          {/* Le prochain evenement annonce sur le blog. Cette carte affichait auparavant un
              congres ecrit en dur, avec ses dates : une annonce inventee sur la page
              d'accueil d'un site en production. Sans evenement au programme, on invite au
              blog plutot que d'en inventer un. */}
          <div className="bg-gradient-to-br from-brand to-brand-dark rounded-2xl p-8 text-white">
            <p className="text-brand-light/80 text-xs font-bold uppercase mb-2">
              {prochainEvenement ? 'À venir' : 'Le blog'}
            </p>
            <h3 className="text-2xl font-bold mb-2">
              {prochainEvenement ? prochainEvenement.title : 'Actualités & événements'}
            </h3>
            <p className="text-sm text-brand-light mb-4">
              {prochainEvenement
                ? [prochainEvenement.eventLocation,
                   formaterPeriode(prochainEvenement.eventStartsOn, prochainEvenement.eventEndsOn)]
                    .filter(Boolean).join(' · ')
                : 'Congrès, salons et nouvelles de la plateforme.'}
            </p>
            <Link
              to={prochainEvenement ? `/blog/${prochainEvenement.slug}` : '/blog'}
              className="inline-flex items-center gap-2 bg-white text-brand font-bold px-4 py-2 rounded-full text-sm hover:bg-brand-light transition-colors"
            >
              En savoir plus <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
          <div className="bg-white rounded-2xl border border-gray-100 p-8">
            <p className="text-gray-400 text-xs font-bold uppercase mb-2">Agenda</p>
            <h3 className="text-2xl font-bold text-gray-800 mb-3">Calendrier des Événements Médicaux</h3>
            <p className="text-sm text-gray-500 mb-4">Formations continues, webinaires et conférences pour les professionnels de santé.</p>
            <Link to="/formations" className="inline-flex items-center gap-2 bg-brand text-white font-bold px-4 py-2 rounded-full text-sm hover:bg-brand-fonce transition-colors">
              Voir les formations <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </section>

      {/* All Products Grid */}
      <section className="container mx-auto px-4 md:px-8 py-12 bg-white rounded-t-3xl border-t border-gray-100 shadow-sm mt-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h2 className="text-3xl font-bold text-gray-800">Tout notre catalogue</h2>
            <p className="text-gray-500 mt-2">Explorez l'ensemble de nos équipements médicaux importés</p>
          </div>
          <Link to="/catalog" className="hidden md:flex items-center gap-2 bg-brand text-white px-5 py-2.5 rounded-full font-bold hover:bg-brand-fonce transition-colors shadow-md">
            Voir le catalogue complet <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
        
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          {products.slice(0, HOMEPAGE_CATALOG_PREVIEW_COUNT).map(p => (
            <ProductCardHome key={p.id} product={p} />
          ))}
        </div>
        
        <div className="mt-8 flex justify-center md:hidden">
          <Link to="/catalog" className="inline-flex items-center gap-2 bg-brand text-white px-6 py-3 rounded-full font-bold hover:bg-brand-fonce transition-colors shadow-md w-full justify-center">
            Voir le catalogue complet <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      </section>

      {/* Placee apres le catalogue : la preuve sociale a plus de poids une fois l'offre vue
          qu'avant, ou elle interrompait le parcours entre deux blocs de produits. */}
      <PartnersSection />

      {/* WhatsApp Floating Button */}
      <a
        href="https://wa.me/33600000000"
        target="_blank"
        rel="noopener noreferrer"
        className="fixed bottom-6 right-6 z-50 w-14 h-14 bg-green-500 rounded-full flex items-center justify-center shadow-lg hover:bg-green-600 transition-colors hover:scale-110"
        aria-label="Contacter via WhatsApp"
      >
        <MessageCircle className="w-6 h-6 text-white" />
      </a>
    </div>
  );
}
