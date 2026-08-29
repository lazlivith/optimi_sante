import { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Heart, ShoppingCart, ArrowRight, MessageCircle } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { catalogService } from '../api/catalogService';
import type { Product } from '../api/catalogService';
import { useCart } from '../context/CartContext';
import { ProductImage } from '../components/common/ProductImage';

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
const featuredGradients = [
  { bg: 'from-purple-200 to-purple-100', textColor: 'text-purple-900' },
  { bg: 'from-rose-200 to-orange-100', textColor: 'text-rose-900' },
  { bg: 'from-sky-200 to-sky-100', textColor: 'text-sky-900' },
  { bg: 'from-teal-200 to-cyan-100', textColor: 'text-teal-900' },
];

function FeaturedSection({ products }: { products: Product[] }) {
  if (products.length === 0) return null;

  return (
    <section className="container mx-auto px-4 md:px-8 py-8">
      <h2 className="text-2xl font-bold text-gray-800 mb-5">Incontournables</h2>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {products.slice(0, 4).map((item, index) => {
          const style = featuredGradients[index % featuredGradients.length];
          return (
            <Link
              to={`/catalog`}
              key={item.id}
              className={`group bg-gradient-to-b ${style.bg} rounded-2xl p-5 flex flex-col relative overflow-hidden hover:shadow-lg transition-all min-h-[200px]`}
            >
              <span className={`text-sm font-bold leading-tight ${style.textColor} line-clamp-3`}>
                {item.name.split(' ').map((word, i) => i === 0
                  ? <strong key={i}>{word} </strong>
                  : <span key={i}>{word} </span>
                )}
              </span>
              <div className="flex-1 flex items-end justify-center pt-4">
                <ProductImage
                  src={item.imageUrl}
                  alt={item.name}
                  className="h-28 w-full drop-shadow-md group-hover:scale-105 transition-transform duration-300"
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

function CategoriesSection({ categories }: { categories: Category[] }) {
  if (!categories || categories.length === 0) return null;

  // Cleanup names & remove html entities
  const fallbacks = [
    'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=400',
    'https://images.unsplash.com/photo-1584820927498-cfe5211fd8bf?w=400',
    'https://images.unsplash.com/photo-1530497610245-94d3c16cda28?w=400',
    'https://images.unsplash.com/photo-1586773860418-d37222d8fce3?w=400',
    'https://images.unsplash.com/photo-1579684385127-1ef15d508118?w=400',
    'https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?w=400',
    'https://images.unsplash.com/photo-1628348070889-cb656235b4eb?w=400',
    'https://images.unsplash.com/photo-1551601651-2a8555f1a136?w=400'
  ];

  const cleanCategories = categories
    .map((c, i) => ({
      ...c,
      cleanName: c.name.replace(/&amp;/g, '&'),
      // Fallback images based on name
      img: c.name.toLowerCase().includes('diagnostic') ? 'https://images.unsplash.com/photo-1584820927498-cfe5211fd8bf?w=400' :
           c.name.toLowerCase().includes('cardio') ? 'https://images.unsplash.com/photo-1628348070889-cb656235b4eb?w=400' :
           c.name.toLowerCase().includes('urgence') ? 'https://images.unsplash.com/photo-1559757148-5c350d0d3c56?w=400' :
           c.name.toLowerCase().includes('instru') ? 'https://images.unsplash.com/photo-1551601651-2a8555f1a136?w=400' :
           c.name.toLowerCase().includes('mobilier') ? 'https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?w=400' :
           c.name.toLowerCase().includes('kiné') ? 'https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=400' :
           fallbacks[i % fallbacks.length]
    }))
    .filter((c, index, self) => {
      const normalize = (name: string) => name.toLowerCase().replace(/s\b/g, '').replace('électropcardiographe', 'électrocardiographe').trim();
      const current = normalize(c.cleanName);
      return self.findIndex(t => normalize(t.cleanName) === current) === index;
    })
    .slice(0, 8); // Display first 8

  return (
    <section className="container mx-auto px-4 md:px-8 py-8">
      <div className="flex items-center justify-between mb-5">
        <h2 className="text-2xl font-bold text-gray-800">Catégories</h2>
        <Link to="/catalog" className="text-sm font-semibold text-brand-green hover:underline flex items-center gap-1">
          Voir tout le catalogue <ArrowRight className="w-4 h-4" />
        </Link>
      </div>
      <div className="grid grid-cols-4 md:grid-cols-8 gap-3">
        {cleanCategories.map((cat) => (
          <Link key={cat.id} to={`/catalog?cat=${cat.id}`} className="flex flex-col items-center gap-2 group">
            <div className="w-full aspect-square bg-white rounded-2xl border border-gray-100 flex items-center justify-center p-3 hover:shadow-md transition-all group-hover:border-brand-green/30">
              <img
                src={cat.img}
                alt={cat.cleanName}
                className="w-full h-full object-cover rounded-xl"
                onError={(e) => { (e.target as HTMLImageElement).style.opacity = '0.3'; }}
              />
            </div>
            <span className="text-xs text-center text-gray-600 font-medium leading-tight">{cat.cleanName}</span>
          </Link>
        ))}
      </div>
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
          <Link to={`/product/${item.slug}`} key={item.id} className="bg-white rounded-xl border border-gray-100 p-4 flex flex-col hover:shadow-lg transition-all group relative">
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
              <span className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-1">
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
      <Link to={`/product/${product.slug}`} className="absolute inset-0 z-0" aria-label={`Voir ${product.name}`}></Link>
      
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
        className="w-full border border-gray-300 rounded-full py-1.5 text-xs font-semibold text-gray-700 hover:bg-brand-green hover:text-white hover:border-brand-green transition-all relative z-10"
      >
        + Acheter
      </button>
    </div>
  );
}

// ──────────────────────────────────────────────────
// Product Slider Section
// ──────────────────────────────────────────────────
function ProductSliderSection({ title, products, bannerContent }: {
  title: string;
  products: Product[];
  bannerContent: React.ReactNode;
}) {
  const [startIdx, setStartIdx] = useState(0);
  const visible = 3;
  const canPrev = startIdx > 0;
  const canNext = startIdx + visible < products.length;

  return (
    <section className="container mx-auto px-4 md:px-8 py-8">
      <div className="grid md:grid-cols-[1fr_340px] gap-6">
        {/* Left: Products */}
        <div>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-bold text-gray-800">{title}</h2>
            <Link to="/catalog" className="flex items-center gap-1 text-xs font-semibold text-gray-500 hover:text-brand-green">
              Voir tout <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
          <div className="relative">
            <div className="grid grid-cols-3 gap-3">
              {products.slice(startIdx, startIdx + visible).map(p => (
                <ProductCardHome key={p.id} product={p} />
              ))}
            </div>
            {(canPrev || canNext) && (
              <div className="flex items-center gap-2 mt-4">
                <button onClick={() => setStartIdx(i => Math.max(0, i - visible))} disabled={!canPrev} className="w-8 h-8 rounded-full border border-gray-200 flex items-center justify-center text-gray-600 hover:bg-gray-50 disabled:opacity-30">
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button onClick={() => setStartIdx(i => Math.min(products.length - visible, i + visible))} disabled={!canNext} className="w-8 h-8 rounded-full border border-gray-200 flex items-center justify-center text-gray-600 hover:bg-gray-50 disabled:opacity-30">
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Right: Banner */}
        <div className="bg-gradient-to-br from-teal-700 to-teal-900 rounded-2xl p-6 flex flex-col justify-center text-white">
          {bannerContent}
        </div>
      </div>
    </section>
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
// Best Sellers (with category tabs)
// ──────────────────────────────────────────────────
function BestSellersSection({ products }: { products: Product[] }) {
  const [activeCategory, setActiveCategory] = useState(0);
  
  // Extraire les noms de catégories des produits
  const tabs = Array.from(new Set(products.filter(p => p.category?.name).map(p => p.category!.name))).slice(0, 4);

  if (products.length === 0 || tabs.length === 0) return null;
  
  const activeProducts = products.filter(p => p.category?.name === tabs[activeCategory]).slice(0, 6);

  return (
    <section className="container mx-auto px-4 md:px-8 py-8">
      <h2 className="text-2xl font-bold text-gray-800 mb-5">Nos best-sellers par catégorie</h2>
      <div className="flex gap-2 flex-wrap mb-5">
        {tabs.map((tab, i) => (
          <button
            key={tab}
            onClick={() => setActiveCategory(i)}
            className={`px-4 py-2 rounded-full text-sm font-semibold transition-all ${activeCategory === i ? 'bg-blue-700 text-white' : 'bg-white border border-gray-200 text-gray-600 hover:border-gray-300'}`}
          >
            {tab.replace(/&amp;/g, '&')}
          </button>
        ))}
      </div>
      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
        {activeProducts.length > 0 ? activeProducts.map(p => (
          <ProductCardHome key={p.id} product={p} />
        )) : (
          <p className="text-gray-500 text-sm col-span-full">Aucun produit pour le moment.</p>
        )}
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
  const { data: productsData } = useQuery({
    queryKey: ['products-home'],
    queryFn: () => catalogService.getProducts({ size: 48 }),
  });
  
  const { data: categories = [] } = useQuery({
    queryKey: ['categories-home'],
    queryFn: () => catalogService.getCategories(),
  });

  const products = productsData?.content ?? [];

  return (
    <div className="flex flex-col bg-gray-50 min-h-screen">
      <HeroSlider />
      <FeaturedSection products={products} />
      <CategoriesSection categories={categories} />
      <EssentialEquipmentSection products={products} />

      {products.length >= 3 && (
        <ProductSliderSection
          title="Dispositifs de pointe"
          products={products}
          bannerContent={
            <>
              <p className="text-teal-200 text-xs font-bold uppercase mb-2">Robustesse & Confort</p>
              <h3 className="text-2xl font-bold leading-tight mb-3">
                Robustesse et <span className="text-teal-300">confort</span> réunis pour vos consultations.
              </h3>
              <Link to="/catalog" className="inline-flex items-center gap-2 bg-white text-teal-800 font-bold px-4 py-2 rounded-full text-sm hover:bg-gray-100 transition-colors mt-2">
                Explorer <ArrowRight className="w-4 h-4" />
              </Link>
            </>
          }
        />
      )}

      <PromosSection products={products.slice(0, 10)} />
      <BestSellersSection products={products.slice(5, 11)} />

      {/* Events Banner */}
      <section className="container mx-auto px-4 md:px-8 py-8">
        <div className="grid md:grid-cols-2 gap-6">
          <div className="bg-gradient-to-br from-blue-600 to-indigo-700 rounded-2xl p-8 text-white">
            <p className="text-blue-200 text-xs font-bold uppercase mb-2">À venir</p>
            <h3 className="text-2xl font-bold mb-2">Congrès National de Médecine</h3>
            <p className="text-sm text-blue-100 mb-4">Bordeaux · 15-18 Octobre 2026</p>
            <Link to="/formations" className="inline-flex items-center gap-2 bg-white text-blue-700 font-bold px-4 py-2 rounded-full text-sm hover:bg-blue-50 transition-colors">
              En savoir plus <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
          <div className="bg-white rounded-2xl border border-gray-100 p-8">
            <p className="text-gray-400 text-xs font-bold uppercase mb-2">Agenda</p>
            <h3 className="text-2xl font-bold text-gray-800 mb-3">Calendrier des Événements Médicaux</h3>
            <p className="text-sm text-gray-500 mb-4">Formations continues, webinaires et conférences pour les professionnels de santé.</p>
            <Link to="/formations" className="inline-flex items-center gap-2 bg-brand-green text-white font-bold px-4 py-2 rounded-full text-sm hover:bg-[#0f3c35] transition-colors">
              Voir les formations <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </section>

      <PartnersSection />

      {/* All Products Grid */}
      <section className="container mx-auto px-4 md:px-8 py-12 bg-white rounded-t-3xl border-t border-gray-100 shadow-sm mt-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h2 className="text-3xl font-bold text-gray-800">Tout notre catalogue</h2>
            <p className="text-gray-500 mt-2">Explorez l'ensemble de nos équipements médicaux importés</p>
          </div>
          <Link to="/catalog" className="hidden md:flex items-center gap-2 bg-brand-green text-white px-5 py-2.5 rounded-full font-bold hover:bg-[#0f3c35] transition-colors shadow-md">
            Voir le catalogue complet <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
        
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          {products.slice(0, HOMEPAGE_CATALOG_PREVIEW_COUNT).map(p => (
            <ProductCardHome key={p.id} product={p} />
          ))}
        </div>
        
        <div className="mt-8 flex justify-center md:hidden">
          <Link to="/catalog" className="inline-flex items-center gap-2 bg-brand-green text-white px-6 py-3 rounded-full font-bold hover:bg-[#0f3c35] transition-colors shadow-md w-full justify-center">
            Voir le catalogue complet <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      </section>

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
