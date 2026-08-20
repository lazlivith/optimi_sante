import { Link } from 'react-router-dom';
import { ShoppingCart, LogOut, User as UserIcon } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';

export const Navbar = () => {
  const { totalItems } = useCart();
  const { user, isAuthenticated, logout } = useAuth();

  const getRoleBadge = (role: string) => {
    switch (role) {
      case 'CLIENT_B2C': return <span className="bg-blue-100 text-blue-800 text-xs px-2 py-0.5 rounded ml-2">B2C</span>;
      case 'CLIENT_B2B': return <span className="bg-purple-100 text-purple-800 text-xs px-2 py-0.5 rounded ml-2">B2B</span>;
      case 'MEDECIN': return <span className="bg-emerald-100 text-emerald-800 text-xs px-2 py-0.5 rounded ml-2">Médecin</span>;
      case 'SUPER_ADMIN': return <span className="bg-red-100 text-red-800 text-xs px-2 py-0.5 rounded ml-2">Admin</span>;
      default: return null;
    }
  };

  return (
    <header className="bg-brand-cream sticky top-0 z-50">
      <div className="container mx-auto px-6 h-20 flex items-center justify-between">
        
        {/* Logo */}
        <Link to="/" className="flex items-center gap-3">
          <div className="bg-brand-green text-white font-bold rounded-lg flex items-center justify-center w-9 h-9 text-sm">
            OS
          </div>
          <span className="text-xl font-semibold text-brand-dark tracking-tight">Optimi Santé</span>
        </Link>

        {/* Navigation */}
        <nav className="hidden md:flex items-center gap-2">
          <Link to="/" className="px-4 py-2 rounded-full text-sm font-medium text-slate-600 hover:text-brand-dark transition-colors">Accueil</Link>
          <Link to="/catalog" className="px-4 py-2 rounded-full text-sm font-medium bg-brand-light text-brand-dark">Produits</Link>
          <Link to="/formations" className="px-4 py-2 rounded-full text-sm font-medium text-slate-600 hover:text-brand-dark transition-colors">Formations</Link>
          <Link to="/partenariat" className="px-4 py-2 rounded-full text-sm font-medium text-slate-600 hover:text-brand-dark transition-colors">Partenariat</Link>
        </nav>

        {/* Actions */}
        <div className="flex items-center gap-4">
          <div className="hidden sm:flex items-center border border-slate-200 rounded-md bg-white px-3 py-1.5 text-sm font-medium text-slate-600">
            FR · EUR
          </div>
          
          <Link to="/cart" className="relative p-2 text-slate-600 hover:text-brand-dark bg-white border border-slate-200 rounded-md flex items-center justify-center">
            <ShoppingCart className="w-5 h-5" />
            {totalItems > 0 && (
              <span className="absolute -top-2 -right-2 inline-flex items-center justify-center w-5 h-5 text-[10px] font-bold text-white bg-brand-orange rounded-full border-2 border-brand-cream">
                {totalItems}
              </span>
            )}
          </Link>

          {isAuthenticated && user ? (
            <div className="flex items-center gap-3">
              <div className="flex items-center bg-white border border-slate-200 rounded-md px-3 py-1.5 text-sm font-medium text-slate-700">
                <UserIcon className="w-4 h-4 mr-2 text-slate-400" />
                {user.email.split('@')[0]}
                {getRoleBadge(user.role)}
              </div>
              <button 
                onClick={logout}
                className="p-2 text-slate-500 hover:text-red-600 bg-white border border-slate-200 rounded-md transition-colors"
                title="Se déconnecter"
              >
                <LogOut className="w-5 h-5" />
              </button>
            </div>
          ) : (
            <Link to="/login" className="bg-brand-green text-white px-5 py-2 rounded-md text-sm font-medium hover:bg-[#0f3c35] transition-colors">
              Se connecter
            </Link>
          )}
        </div>
      </div>
    </header>
  );
};
