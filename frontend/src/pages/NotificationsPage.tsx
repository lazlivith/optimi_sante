import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bell, BellOff, Check, CheckCheck, Clock, Settings2 } from 'lucide-react';
import { notificationService, type AppNotification, type NotificationSeverity } from '../api/notificationService';
import { useNotificationActions } from '../hooks/useNotifications';
import { EmptyState } from '../components/common/EmptyState';

const DOT: Record<NotificationSeverity, string> = {
  INFO: 'bg-blue-500',
  SUCCESS: 'bg-green-500',
  WARNING: 'bg-amber-500',
  CRITICAL: 'bg-red-500',
};

function fmt(iso: string): string {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
}

export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const navigate = useNavigate();
  const { markRead, markAllRead, snooze } = useNotificationActions();

  const { data, isLoading } = useQuery({
    queryKey: ['notifications', 'page', page, unreadOnly],
    queryFn: () => notificationService.list(page, 20, unreadOnly),
  });

  const items = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  const open = (n: AppNotification) => {
    if (!n.read) markRead.mutate(n.id);
    if (n.linkUrl) navigate(n.linkUrl);
  };

  return (
    <div className="max-w-3xl mx-auto px-4 py-10">
      <div className="flex items-end justify-between gap-4 flex-wrap mb-6">
        <div>
          <h1 className="text-2xl font-bold text-brand-dark">Notifications</h1>
          <p className="text-sm text-slate-500 mt-1">Historique de toutes vos notifications.</p>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to="/notifications/settings"
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-600 hover:bg-slate-50"
          >
            <Settings2 className="w-4 h-4" /> Préférences
          </Link>
          <button
            onClick={() => markAllRead.mutate()}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-brand-green text-white text-sm font-medium hover:bg-[#0f3c35]"
          >
            <CheckCheck className="w-4 h-4" /> Tout marquer lu
          </button>
        </div>
      </div>

      <label className="inline-flex items-center gap-2 text-sm text-slate-600 mb-4 cursor-pointer">
        <input
          type="checkbox"
          checked={unreadOnly}
          onChange={(e) => {
            setUnreadOnly(e.target.checked);
            setPage(0);
          }}
          className="rounded border-slate-300"
        />
        Afficher uniquement les non lues
      </label>

      <div className="bg-white rounded-xl border border-slate-200 divide-y divide-slate-100 overflow-hidden">
        {isLoading && <p className="p-8 text-center text-sm text-slate-400">Chargement…</p>}

        {!isLoading && items.length === 0 && (
          <EmptyState
            icon={unreadOnly ? BellOff : Bell}
            title={unreadOnly ? 'Aucune notification non lue' : 'Aucune notification'}
            description="Vous serez prévenu ici des événements qui vous concernent."
          />
        )}

        {items.map((n) => (
          <div key={n.id} className={`flex gap-3 px-4 py-3.5 ${n.read ? 'bg-white' : 'bg-brand-green/5'}`}>
            <span className={`mt-1.5 w-2 h-2 rounded-full shrink-0 ${DOT[n.severity] ?? DOT.INFO}`} />
            <button onClick={() => open(n)} className="min-w-0 flex-1 text-left">
              <span className="flex items-center gap-1.5">
                <span className="text-sm font-medium text-slate-800">{n.title}</span>
                {n.groupCount > 1 && (
                  <span className="text-[10px] font-bold text-slate-500 bg-slate-100 rounded px-1.5 py-0.5">
                    ×{n.groupCount}
                  </span>
                )}
              </span>
              {n.body && <span className="block text-xs text-slate-500 mt-0.5">{n.body}</span>}
              <span className="block text-[11px] text-slate-400 mt-1">{fmt(n.createdAt)}</span>
            </button>
            <div className="flex items-start gap-1 shrink-0">
              {!n.read && (
                <button
                  onClick={() => markRead.mutate(n.id)}
                  className="p-1.5 rounded hover:bg-slate-100 text-slate-400"
                  title="Marquer comme lu"
                >
                  <Check className="w-4 h-4" />
                </button>
              )}
              <button
                onClick={() => snooze.mutate({ id: n.id, hours: 24 })}
                className="p-1.5 rounded hover:bg-slate-100 text-slate-400"
                title="Mettre en veille 24 h"
              >
                <Clock className="w-4 h-4" />
              </button>
            </div>
          </div>
        ))}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 mt-5 text-sm">
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="px-3 py-1.5 rounded border border-slate-200 disabled:opacity-40"
          >
            Précédent
          </button>
          <span className="text-slate-500">
            Page {page + 1} / {totalPages}
          </span>
          <button
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="px-3 py-1.5 rounded border border-slate-200 disabled:opacity-40"
          >
            Suivant
          </button>
        </div>
      )}
    </div>
  );
}
