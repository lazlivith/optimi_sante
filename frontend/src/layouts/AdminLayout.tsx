import { NavLink, Outlet, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { PageTransition } from '../components/common/PageTransition';
import { Store, LogOut, ShieldCheck } from 'lucide-react';
import { universesFor } from '../lib/adminUniverses';

/** Libellé du rattachement, affiché sous l'email en pied de sidebar. */
const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super Admin',
  ADMIN_ECOMMERCE: 'Admin · Négoce',
  ADMIN_MOBILITE: 'Admin · Mobilité',
  ADMIN: 'Admin (rattachement à définir)',
};

export function AdminLayout() {
  const { user, logout } = useAuth();

  // La navigation découle du rôle, elle n'est pas figée : un admin métier ne voit que son
  // univers, le super admin voit les trois, groupés et intitulés.
  const universes = universesFor(user?.role);
  const isMultiUniverse = universes.length > 1;

  return (
    <div className="min-h-screen flex bg-slate-100">
      {/* Sidebar */}
      {/* `sticky top-0 h-screen` : sans hauteur imposee, l'aside s'etire a la hauteur
          TOTALE de la page (le conteneur est en `min-h-screen flex`). Le `flex-1` du menu
          repoussait alors « Se deconnecter » tout en bas de la page — a des milliers de
          pixels — au lieu du bas de l'ecran, et la barre defilait avec le contenu.
          Bornee a la hauteur de la fenetre, elle reste immobile et son pied revient a sa
          place. `overflow-y-auto` sur le menu couvre le cas du super admin, seul role
          dont les entrees peuvent depasser un petit ecran. */}
      <aside className="w-64 shrink-0 bg-brand-dark text-slate-300 flex flex-col sticky top-0 h-screen">
        <div className="h-16 flex items-center gap-2 px-6 border-b border-white/10">
          <div className="bg-brand-green text-white font-bold rounded-lg flex items-center justify-center w-8 h-8 text-xs">
            OS
          </div>
          <div>
            <div className="text-sm font-bold text-white leading-none">Optimi Santé</div>
            <div className="text-[10px] text-slate-400 leading-none mt-1">
              {universes.length === 1 ? `Espace ${universes[0].label}` : 'Espace Administration'}
            </div>
          </div>
        </div>

        <nav className="flex-1 py-6 px-3 space-y-1 overflow-y-auto min-h-0">
          {universes.map((universe) => (
            <div key={universe.id} className={isMultiUniverse ? 'mb-5' : ''}>
              {/* L'intitulé de groupe n'a de sens que si plusieurs univers coexistent :
                  pour un admin métier, il ajouterait du bruit sans rien distinguer. */}
              {isMultiUniverse && (
                <p className="px-3 mb-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-500">
                  {universe.label}
                </p>
              )}
              <div className="space-y-1">
                {universe.items.map(({ to, label, icon: Icon, end }) => (
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
              </div>
            </div>
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
              {ROLE_LABELS[user?.role ?? ''] ?? 'Admin'}
            </div>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 min-w-0 overflow-y-auto">
        <PageTransition>
          <Outlet />
        </PageTransition>
      </main>
    </div>
  );
}
