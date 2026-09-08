import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Bell, Check, CheckCheck, Settings2 } from 'lucide-react';
import type { AppNotification, NotificationSeverity } from '../../api/notificationService';
import { useNotificationActions, useNotificationList, useUnreadCount } from '../../hooks/useNotifications';

const DOT: Record<NotificationSeverity, string> = {
  INFO: 'bg-blue-500',
  SUCCESS: 'bg-green-500',
  WARNING: 'bg-amber-500',
  CRITICAL: 'bg-red-500',
};

function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.round(diff / 60000);
  if (min < 1) return "à l'instant";
  if (min < 60) return `il y a ${min} min`;
  const h = Math.round(min / 60);
  if (h < 24) return `il y a ${h} h`;
  const d = Math.round(h / 24);
  return `il y a ${d} j`;
}

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const { data: unread = 0 } = useUnreadCount();
  const { data: page, isLoading } = useNotificationList(open);
  const { markRead, markAllRead } = useNotificationActions();

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  const openItem = (n: AppNotification) => {
    if (!n.read) markRead.mutate(n.id);
    setOpen(false);
    if (n.linkUrl) navigate(n.linkUrl);
  };

  const items = page?.content ?? [];

  return (
    <div className="relative" ref={rootRef}>
      <button
        onClick={() => setOpen((v) => !v)}
        className="relative p-2 rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-800 transition-colors"
        aria-label="Notifications"
      >
        <Bell className="w-5 h-5" />
        {unread > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-red-500 text-white text-[10px] font-bold flex items-center justify-center">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-96 max-w-[calc(100vw-2rem)] bg-white rounded-xl border border-slate-200 shadow-xl z-50 overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100">
            <span className="text-sm font-semibold text-slate-800">Notifications</span>
            {unread > 0 && (
              <button
                onClick={() => markAllRead.mutate()}
                className="flex items-center gap-1 text-xs font-medium text-brand-green hover:underline"
              >
                <CheckCheck className="w-3.5 h-3.5" />
                Tout marquer lu
              </button>
            )}
          </div>

          <div className="max-h-96 overflow-y-auto">
            {isLoading && <p className="px-4 py-6 text-sm text-slate-400 text-center">Chargement…</p>}
            {!isLoading && items.length === 0 && (
              <p className="px-4 py-8 text-sm text-slate-400 text-center">Aucune notification</p>
            )}
            {items.map((n) => (
              <button
                key={n.id}
                onClick={() => openItem(n)}
                className={`w-full text-left px-4 py-3 border-b border-slate-50 hover:bg-slate-50 transition-colors flex gap-3 ${
                  n.read ? 'opacity-60' : ''
                }`}
              >
                <span className={`mt-1.5 w-2 h-2 rounded-full shrink-0 ${DOT[n.severity] ?? DOT.INFO}`} />
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1.5">
                    <span className="text-sm font-medium text-slate-800 truncate">{n.title}</span>
                    {n.groupCount > 1 && (
                      <span className="shrink-0 text-[10px] font-bold text-slate-500 bg-slate-100 rounded px-1.5 py-0.5">
                        ×{n.groupCount}
                      </span>
                    )}
                  </span>
                  {n.body && <span className="block text-xs text-slate-500 line-clamp-2">{n.body}</span>}
                  <span className="block text-[11px] text-slate-400 mt-0.5">{relativeTime(n.createdAt)}</span>
                </span>
                {!n.read && (
                  <span
                    onClick={(e) => {
                      e.stopPropagation();
                      markRead.mutate(n.id);
                    }}
                    className="p-1 rounded hover:bg-slate-200 text-slate-400 shrink-0"
                    title="Marquer comme lu"
                  >
                    <Check className="w-3.5 h-3.5" />
                  </span>
                )}
              </button>
            ))}
          </div>

          <div className="flex items-center justify-between px-4 py-2.5 border-t border-slate-100 bg-slate-50/60">
            <Link
              to="/notifications"
              onClick={() => setOpen(false)}
              className="text-xs font-medium text-brand-green hover:underline"
            >
              Voir toutes les notifications
            </Link>
            <Link
              to="/notifications/settings"
              onClick={() => setOpen(false)}
              className="flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600"
              title="Préférences de notification"
            >
              <Settings2 className="w-3.5 h-3.5" />
              Préférences
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
