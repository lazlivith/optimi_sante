import { NavLink, Outlet, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  FileStack, ShieldCheck, UserCog, Store, LogOut, Stethoscope
} from 'lucide-react';

const NAV_ITEMS = [
  { to: '/doctor', label: 'Mes dossiers', icon: FileStack, end: true },
  { to: '/doctor/vault', label: 'Coffre-fort', icon: ShieldCheck },
  { to: '/doctor/profile', label: 'Mon Profil', icon: UserCog },
];

export function DoctorLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen flex bg-slate-100">
      <aside className="w-64 shrink-0 bg-emerald-950 text-emerald-200 flex flex-col">
        <div className="h-16 flex items-center gap-2 px-6 border-b border-white/10">
          <div className="bg-brand-green text-white font-bold rounded-lg flex items-center justify-center w-8 h-8 text-xs">
            OS
          </div>
          <div>
            <div className="text-sm font-bold text-white leading-none">Optimi Santé</div>
            <div className="text-[10px] text-emerald-400 leading-none mt-1">Espace Médecin</div>
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
                    : 'text-emerald-300/70 hover:bg-white/5 hover:text-white'
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
            className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-emerald-300/70 hover:bg-white/5 hover:text-white transition-colors"
          >
            <Store className="w-4 h-4" />
            Accéder à la boutique
          </Link>
          <button
            onClick={logout}
            className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-emerald-300/70 hover:bg-red-900/40 hover:text-red-300 transition-colors"
          >
            <LogOut className="w-4 h-4" />
            Se déconnecter
          </button>
        </div>

        <div className="p-4 border-t border-white/10 flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-white/10 flex items-center justify-center text-emerald-300">
            <Stethoscope className="w-5 h-5" />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-semibold text-white truncate">{user?.email}</div>
            <div className="text-[10px] font-bold text-emerald-400 uppercase tracking-wide">Médecin</div>
          </div>
        </div>
      </aside>

      <main className="flex-1 min-w-0 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  );
}
