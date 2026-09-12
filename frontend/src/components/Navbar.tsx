import { useState, useRef, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { adminHomeFor, ALL_ADMIN_ROLES } from '../lib/adminUniverses';
import { ShoppingCart, LogOut, User as UserIcon, ChevronDown, Shield, FileText, Settings, Search, Home, Briefcase, Database as DatabaseIcon, ShieldCheck } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import { NotificationBell } from './common/NotificationBell';
import { LogoOptimi } from '../components/marque/LogoOptimi';


export const Navbar = () => {
  const { totalItems } = useCart();
  const { user, isAuthenticated, logout } = useAuth();
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [clientType, setClientType] = useState<'particulier' | 'professionnel'>('particulier');
  const dropdownRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/catalog?search=${encodeURIComponent(searchQuery)}`);
    }
  };

  const ADMIN_ROLES: string[] = ALL_ADMIN_ROLES;

  // Deposer le fichier dans frontend/public/ sous ce nom suffit a l'activer.

  const getRoleLabel = (role: string) => {
    switch (role) {
      case 'CLIENT_B2C': return 'PARTICULIER';
      case 'CLIENT_B2B': return 'PROFESSIONNEL';
      case 'MEDECIN': return 'MÉDECIN';
      case 'ADMIN':
      case 'SUPER_ADMIN': return 'ADMIN';
      // Sans ces deux cas, le `default` renvoyait l'enum brute et l'ecran affichait
      // « ADMIN_MOBILITE » a l'utilisateur.
      case 'ADMIN_ECOMMERCE': return 'ADMIN NÉGOCE';
      case 'ADMIN_MOBILITE': return 'ADMIN MOBILITÉ';
      case 'CENTRE_FORMATION': return 'PARTENAIRE';
      default: return role;
    }
  };

  const getRoleColor = (role: string) => {
    switch (role) {
      case 'CLIENT_B2C': return 'text-blue-600';
      case 'CLIENT_B2B': return 'text-purple-600';
      case 'MEDECIN': return 'text-emerald-600';
      case 'ADMIN':
      case 'SUPER_ADMIN':
      case 'ADMIN_ECOMMERCE':
      case 'ADMIN_MOBILITE': return 'text-red-600';
      default: return 'text-gray-600';
    }
  };

  return (
    <header className="bg-white sticky top-0 z-50 border-b border-gray-100 shadow-sm">
      {/* Main Header Row */}
      <div className="container mx-auto px-4 h-20 flex items-center gap-5">

        <Link to="/" className="flex items-center gap-2.5 shrink-0" aria-label="Optimi Santé — accueil">
          {/* Sans la signature : a 48 pixels de haut elle n'est plus lisible, et une mention
              illisible ne fait que rapetisser le logotype. */}
          <LogoOptimi fond="clair" signature={false} className="h-12 w-auto max-w-[190px]" />
        </Link>

        {/* Search Bar — Central */}
        <form onSubmit={handleSearch} className="flex-1 max-w-2xl mx-auto">
          <div className="relative">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="search"
              placeholder="Quel produit recherchez-vous ?"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 bg-gray-100/80 border border-gray-200 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-brand/30 focus:bg-white transition-all"
            />
          </div>
        </form>

        {/* Right Zone */}
        <div className="flex items-center gap-2 shrink-0">
          {/* Cart */}
          <Link to="/cart" className="relative p-2.5 text-gray-600 hover:text-brand-dark transition-colors rounded-full hover:bg-gray-100">
            <ShoppingCart className="w-5 h-5" />
            {totalItems > 0 && (
              <span className="absolute -top-0.5 -right-0.5 inline-flex items-center justify-center w-4 h-4 text-[9px] font-bold text-white bg-red-500 rounded-full">
                {totalItems}
              </span>
            )}
          </Link>

          {/* Notifications */}
          {isAuthenticated && user && <NotificationBell />}

          {/* User */}
          {isAuthenticated && user ? (
            <div className="relative" ref={dropdownRef}>
              <button
                onClick={() => setIsDropdownOpen(!isDropdownOpen)}
                className="flex items-center gap-2 px-3 py-1.5 rounded-full hover:bg-gray-100 transition-colors"
              >
                <div className="w-8 h-8 bg-brand-light rounded-full flex items-center justify-center">
                  <UserIcon className="w-4 h-4 text-brand" />
                </div>
                <div className="hidden sm:block text-left">
                  <p className="text-xs text-gray-500 leading-none">Bonjour</p>
                  <p className="text-sm font-semibold text-gray-800 leading-none mt-0.5">
                    {user.firstName || user.email.split('@')[0]}
                  </p>
                  <p className={`text-[10px] font-bold leading-none mt-0.5 ${getRoleColor(user.role)}`}>
                    {getRoleLabel(user.role)}
                  </p>
                </div>
                <ChevronDown className="w-3 h-3 text-gray-400 hidden sm:block" />
              </button>

              {isDropdownOpen && (
                <div className="absolute right-0 mt-2 w-56 bg-white rounded-xl shadow-lg border border-gray-100 py-2 z-50">
                  <div className="px-4 py-2 border-b border-gray-100 mb-1">
                    <p className="text-sm font-semibold text-gray-800">{user.firstName ? `${user.firstName} ${user.lastName}` : 'Mon compte'}</p>
                    <p className="text-xs text-gray-500 truncate">{user.email}</p>
                  </div>
                  <Link to="/profile" onClick={() => setIsDropdownOpen(false)} className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                    <UserIcon className="w-4 h-4 mr-3 text-gray-400" /> Mon Profil
                  </Link>
                  {/* ADMIN_ECOMMERCE et ADMIN_MOBILITE manquaient ici : la barre laterale
                      d'administration propose « Retour a la boutique », mais aucun chemin ne
                      ramenait ensuite vers l'espace — l'administrateur restait bloque sur la
                      vitrine. `adminHomeFor` renvoie chacun vers le tableau de bord de son
                      metier, la meme source que la redirection de connexion. */}
                  {ADMIN_ROLES.includes(user.role) && (
                    <Link to={adminHomeFor(user.role)} onClick={() => setIsDropdownOpen(false)} className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                      <Settings className="w-4 h-4 mr-3 text-gray-400" /> Espace Admin
                    </Link>
                  )}
                  {user.role === 'MEDECIN' && (
                    <Link to="/doctor" onClick={() => setIsDropdownOpen(false)} className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                      <Shield className="w-4 h-4 mr-3 text-gray-400" /> Mon Espace Médecin
                    </Link>
                  )}
                  {(user.role === 'CLIENT_B2B' || user.role === 'CLIENT_B2C' || user.role === 'MEDECIN' || user.role === 'CENTRE_FORMATION') && (
                    <Link to="/my-orders" onClick={() => setIsDropdownOpen(false)} className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                      {/* « Mes Devis » seul faisait croire aux comptes professionnels qu'aucun
                          historique de commande n'existait : la page destinataire s'intitule
                          « Mes Commandes & Devis » et contient bien les deux. */}
                      <FileText className="w-4 h-4 mr-3 text-gray-400" /> {user.role === 'CLIENT_B2B' ? 'Mes commandes et devis' : 'Mes commandes'}
                    </Link>
                  )}
                  {user.role === 'CENTRE_FORMATION' && (
                    <Link to="/partner" onClick={() => setIsDropdownOpen(false)} className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                      <Briefcase className="w-4 h-4 mr-3 text-gray-400" /> Espace Partenaire
                    </Link>
                  )}
                  {/* Droit d'acces (RGPD art. 15) : propose a tout compte authentifie, pas
                      seulement aux clients — le droit ne depend pas du role. */}
                  <Link to="/mes-donnees" onClick={() => setIsDropdownOpen(false)} className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                    <DatabaseIcon className="w-4 h-4 mr-3 text-gray-400" /> Mes données personnelles
                  </Link>
                  <Link to="/politique-confidentialite" onClick={() => setIsDropdownOpen(false)} className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                    <ShieldCheck className="w-4 h-4 mr-3 text-gray-400" /> Confidentialité
                  </Link>
                  <div className="border-t border-gray-100 mt-1 pt-1">
                    <button
                      onClick={() => { setIsDropdownOpen(false); logout(); }}
                      className="flex items-center w-full px-4 py-2 text-sm text-red-600 hover:bg-red-50"
                    >
                      <LogOut className="w-4 h-4 mr-3" /> Se déconnecter
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <Link to="/login" className="bg-brand text-white px-4 py-2 rounded-full text-sm font-semibold hover:bg-[#0f3c35] transition-colors">
              Se connecter
            </Link>
          )}
        </div>
      </div>

      {/* Secondary Navigation */}
      <div className="border-t border-gray-100 bg-white">
        <div className="container mx-auto px-4 flex items-center h-12 gap-1">
          {/* Client Type Switcher */}
          <div className="flex items-center gap-1 bg-gray-100 rounded-lg p-1 mr-3">
            <button
              onClick={() => setClientType('particulier')}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-semibold transition-all ${clientType === 'particulier' ? 'bg-white shadow-sm text-gray-800' : 'text-gray-500 hover:text-gray-700'}`}
            >
              <Home className="w-3.5 h-3.5" />
              Particulier
            </button>
            <button
              onClick={() => setClientType('professionnel')}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-semibold transition-all ${clientType === 'professionnel' ? 'bg-white shadow-sm text-gray-800' : 'text-gray-500 hover:text-gray-700'}`}
            >
              <Briefcase className="w-3.5 h-3.5" />
              Professionnel
            </button>
          </div>

          <div className="w-px h-5 bg-gray-200 mx-1" />

          {/* Nav Links */}
          <nav className="flex items-center gap-0.5 overflow-x-auto scrollbar-hide">
            <Link to="/" className="whitespace-nowrap px-3 py-1.5 text-xs font-medium text-gray-600 hover:text-brand-dark transition-colors rounded-md hover:bg-gray-50">
              Accueil
            </Link>
            <Link to="/formations" className="whitespace-nowrap px-3 py-1.5 text-xs font-medium text-gray-600 hover:text-brand-dark transition-colors rounded-md hover:bg-gray-50">
              Formations Médicales
            </Link>
            {/* Place juste apres les formations : c'est en hesitant devant une formation qu'on
                veut savoir ce qui est pris en charge autour. */}
            <Link to="/services" className="whitespace-nowrap px-3 py-1.5 text-xs font-medium text-gray-600 hover:text-brand-dark transition-colors rounded-md hover:bg-gray-50">
              Nos Services
            </Link>
            <Link to="/devenir-partenaire" className="whitespace-nowrap px-3 py-1.5 text-xs font-medium text-gray-600 hover:text-brand-dark transition-colors rounded-md hover:bg-gray-50">
              Devenir Partenaire
            </Link>
            <Link to="/catalog" className="whitespace-nowrap px-3 py-1.5 text-xs font-medium text-gray-600 hover:text-brand-dark transition-colors rounded-md hover:bg-gray-50">
              Catalogue
            </Link>
            <Link to="/catalog?promo=true" className="whitespace-nowrap flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-gray-600 hover:text-brand-dark transition-colors rounded-md hover:bg-gray-50">
              Promotions
              <span className="bg-red-500 text-white text-[9px] font-bold px-1.5 py-0.5 rounded uppercase">New</span>
            </Link>
          </nav>
        </div>
      </div>
    </header>
  );
};
