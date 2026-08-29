import { useEffect, useState } from 'react';
import { Loader2, Search, UserCheck, UserX } from 'lucide-react';
import { adminUserService, type AdminUserSummaryDto } from '../../api/adminUserService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Avatar } from '../../components/common/Avatar';

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super Admin',
  ADMIN: 'Admin',
  CLIENT_B2C: 'Particulier',
  CLIENT_B2B: 'Professionnel',
  MEDECIN: 'Médecin',
  CENTRE_FORMATION: 'CHU / Partenaire',
};

const ROLE_FILTERS = ['', 'CLIENT_B2C', 'CLIENT_B2B', 'MEDECIN', 'CENTRE_FORMATION', 'ADMIN', 'SUPER_ADMIN'];

export function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUserSummaryDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [roleFilter, setRoleFilter] = useState('');
  const [search, setSearch] = useState('');
  const [processingId, setProcessingId] = useState<string | null>(null);

  const fetchUsers = async () => {
    setIsLoading(true);
    try {
      const data = await adminUserService.listUsers(0, 100, roleFilter || undefined);
      setUsers(data.content);
    } catch (error) {
      console.error('Failed to fetch users', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roleFilter]);

  const handleToggleActive = async (u: AdminUserSummaryDto) => {
    setProcessingId(u.id);
    try {
      const updated = await adminUserService.setUserActive(u.id, !u.isActive);
      setUsers(prev => prev.map(user => user.id === u.id ? updated : user));
    } catch (error) {
      console.error('Failed to update user status', error);
    } finally {
      setProcessingId(null);
    }
  };

  const filteredUsers = users.filter(u =>
    u.email.toLowerCase().includes(search.toLowerCase()) ||
    (u.displayName || '').toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Utilisateurs"
        subtitle="Gestion des comptes de la plateforme (activation, rôles)."
        actions={
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              type="text"
              placeholder="Rechercher par email ou nom..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9 pr-4 py-2 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-emerald-500 outline-none w-72"
            />
          </div>
        }
      />

      <div className="flex flex-wrap gap-2 mb-6">
        {ROLE_FILTERS.map((role) => (
          <button
            key={role || 'ALL'}
            onClick={() => setRoleFilter(role)}
            className={`px-3 py-1.5 rounded-full text-xs font-bold transition-colors ${
              roleFilter === role
                ? 'bg-brand-green text-white'
                : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            {role ? ROLE_LABELS[role] : 'Tous'}
          </button>
        ))}
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center items-center">
            <Loader2 className="w-8 h-8 text-emerald-600 animate-spin" />
          </div>
        ) : filteredUsers.length === 0 ? (
          <EmptyState icon={Search} title="Aucun utilisateur trouvé." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Utilisateur</th>
                  <th className="px-6 py-4">Rôle</th>
                  <th className="px-6 py-4">Inscrit le</th>
                  <th className="px-6 py-4">Statut</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredUsers.map((u) => (
                  <tr key={u.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <Avatar name={u.displayName || u.email} size="sm" />
                        <div className="min-w-0">
                          <div className="font-semibold text-slate-900 truncate">{u.displayName || '—'}</div>
                          <div className="text-xs text-slate-500 truncate">{u.email}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-slate-100 text-slate-700">
                        {ROLE_LABELS[u.role] || u.role}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-slate-500">
                      {new Date(u.createdAt).toLocaleDateString('fr-FR')}
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={u.isActive ? 'ACTIVE' : 'INACTIVE'} label={u.isActive ? 'Actif' : 'Désactivé'} />
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => handleToggleActive(u)}
                        disabled={processingId === u.id}
                        className={`inline-flex items-center px-3 py-1.5 text-xs font-bold rounded-lg transition-colors disabled:opacity-50 ${
                          u.isActive
                            ? 'bg-rose-50 text-rose-700 hover:bg-rose-100'
                            : 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100'
                        }`}
                      >
                        {processingId === u.id ? (
                          <Loader2 className="w-3.5 h-3.5 mr-1.5 animate-spin" />
                        ) : u.isActive ? (
                          <UserX className="w-3.5 h-3.5 mr-1.5" />
                        ) : (
                          <UserCheck className="w-3.5 h-3.5 mr-1.5" />
                        )}
                        {u.isActive ? 'Désactiver' : 'Activer'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
