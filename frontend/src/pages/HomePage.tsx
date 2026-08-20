import { Link } from 'react-router-dom';
import { Box } from 'lucide-react';

export function HomePage() {
  return (
    <div className="flex flex-col">
      {/* Hero Section */}
      <section className="bg-brand-green text-white py-24 md:py-32 rounded-b-3xl">
        <div className="container mx-auto px-6 grid lg:grid-cols-2 gap-16 items-center">
          
          <div className="space-y-8">
            <div className="inline-flex items-center rounded-md bg-[#F4E3D1] px-3 py-1 text-xs font-bold text-brand-orange uppercase tracking-widest">
              DOSSIER 05-2026 · FRANCE « CEMAC
            </div>
            
            <h1 className="text-4xl md:text-5xl lg:text-6xl font-bold leading-tight tracking-tight">
              Dispositifs médicaux, formations cliniques, une seule plateforme.
            </h1>
            
            <p className="text-lg text-slate-200 max-w-lg leading-relaxed">
              Achetez du matériel médical, équipez votre officine via la caisse POS, ou candidatez à un stage clinique dans un CHU partenaire en France.
            </p>
            
            <div className="flex flex-wrap items-center gap-4 pt-4">
              <Link 
                to="/catalog" 
                className="inline-flex h-12 items-center justify-center rounded-md bg-brand-orange px-8 text-sm font-semibold text-white transition-colors hover:bg-orange-600 shadow-sm"
              >
                <Box className="w-4 h-4 mr-2" />
                Découvrir le catalogue
              </Link>
              <Link 
                to="/formations" 
                className="inline-flex h-12 items-center justify-center rounded-md border border-white/30 px-8 text-sm font-semibold text-white transition-colors hover:bg-white/10"
              >
                Voir les formations
              </Link>
            </div>
          </div>

          {/* Hero Cards Right */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="bg-white/10 border border-white/10 rounded-xl p-6 backdrop-blur-sm">
              <h3 className="font-medium text-white">Négoce B2B/B2C</h3>
            </div>
            <div className="bg-white/10 border border-white/10 rounded-xl p-6 backdrop-blur-sm">
              <h3 className="font-medium text-white">Caisse POS</h3>
            </div>
            <div className="bg-white/10 border border-white/10 rounded-xl p-6 backdrop-blur-sm">
              <h3 className="font-medium text-white">Formations CHU</h3>
            </div>
            <div className="bg-white/10 border border-white/10 rounded-xl p-6 backdrop-blur-sm">
              <h3 className="font-medium text-white">Mobilité clinique</h3>
            </div>
          </div>
          
        </div>
      </section>

      {/* Popular Products Section Placeholder */}
      <section className="container mx-auto px-6 py-24">
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-12">
          <div>
            <span className="text-xs font-semibold text-slate-400 tracking-widest uppercase mb-2 block">Sélection</span>
            <h2 className="text-3xl font-bold text-brand-dark">Dispositifs médicaux populaires</h2>
          </div>
          <Link to="/catalog" className="text-sm font-medium text-brand-dark hover:text-brand-orange border border-slate-200 rounded-md px-4 py-2 mt-4 md:mt-0 inline-flex items-center transition-colors">
            Tout le catalogue <span className="ml-2">›</span>
          </Link>
        </div>
        
        {/* Grille placeholder pour le moment */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 opacity-50">
           <div className="bg-white border rounded-2xl h-64"></div>
           <div className="bg-white border rounded-2xl h-64"></div>
           <div className="bg-white border rounded-2xl h-64"></div>
        </div>
      </section>
    </div>
  );
}
