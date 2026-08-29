import { NavLink, Outlet, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  LayoutDashboard, Users, FileText, GraduationCap, Store, LogOut, ShieldCheck, Package, Building2, TrendingUp, Tag, ShoppingBag, BookOpen
} from 'lucide-react';

const NAV_ITEMS = [
  { to: '/admin', label: 'Tableau de bord', icon: LayoutDashboard, end: true },
  { to: '/admin/finance', label: 'Finance', icon: TrendingUp },
  { to: '/admin/orders', label: 'Commandes', icon: ShoppingBag },
  { to: '/admin/users', label: 'Utilisateurs', icon: Users },
  { to: '/admin/catalog', label: 'Catalogue', icon: Package },
  { to: '/admin/promo-codes', label: 'Codes Promo', icon: Tag },
  { to: '/admin/quotes', label: 'Devis B2B', icon: FileText },
  { to: '/admin/trainings', label: 'Formations', icon: BookOpen },
  { to: '/admin/enrollments', label: 'Dossiers CHU', icon: GraduationCap },
  { to: '/admin/partnership-requests', label: 'Demandes Partenariat', icon: Building2 },
];

export function AdminLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen flex bg-slate-100">
      {/* Sidebar */}
      <aside className="w-64 shrink-0 bg-brand-dark text-slate-300 flex flex-col">
        <div className="h-16 flex items-center gap-2 px-6 border-b border-white/10">
          <div className="bg-brand-green text-white font-bold rounded-lg flex items-center justify-center w-8 h-8 text-xs">
            OS
          </div>
          <div>
            <div className="text-sm font-bold text-white leading-none">Optimi Santé</div>
            <div className="text-[10px] text-slate-400 leading-none mt-1">Espace Administration</div>
          </div>
        </div>

        <nav className="flex-1 py-6 px-3 space-y-1">
          {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-brand-green text-white'
                    : 'text-slate-400 hover:bg-white/5 hover:text-white'
                }`
              }
            >
              <Icon className="w-4 h-4" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="p-3 border-t border-white/10 space-y-1">
          <Link
            to="/"
            className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-slate-400 hover:bg-white/5 hover:text-white transition-colors"
          >
            <Store className="w-4 h-4" />
            Retour à la boutique
          </Link>
          <button
            onClick={logout}
            className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-slate-400 hover:bg-red-900/40 hover:text-red-300 transition-colors"
          >
            <LogOut className="w-4 h-4" />
            Se déconnecter
          </button>
        </div>

        <div className="p-4 border-t border-white/10 flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-white/10 flex items-center justify-center text-brand-green">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-semibold text-white truncate">{user?.email}</div>
            <div className="text-[10px] font-bold text-brand-green uppercase tracking-wide">
              {user?.role === 'SUPER_ADMIN' ? 'Super Admin' : 'Admin'}
            </div>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 min-w-0 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  );
}
