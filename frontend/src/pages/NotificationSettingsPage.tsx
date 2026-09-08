import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { ArrowLeft, Mail, Bell } from 'lucide-react';
import {
  notificationService,
  NOTIFICATION_TYPE_LABELS,
  type NotificationPreference,
} from '../api/notificationService';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

/** Types proposés selon le rôle (les types « admin » ne concernent que ADMIN / SUPER_ADMIN). */
const ADMIN_TYPES = ['PARTNERSHIP_REQUEST', 'DOCTOR_APPLICATION', 'REPORT_FAILURE', 'ORDER_UNPAID_STALE', 'ENROLLMENT_STALE'];
const COMMON_TYPES = ['ORDER_PAID', 'ORDER_STATUS', 'ENROLLMENT_STATUS', 'ACCOUNT_VALIDATED'];

export function NotificationSettingsPage() {
  const { user } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();

  const isAdmin = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
  const types = useMemo(() => (isAdmin ? [...COMMON_TYPES, ...ADMIN_TYPES] : COMMON_TYPES), [isAdmin]);

  const { data: prefs = [], isLoading } = useQuery({
    queryKey: ['notifications', 'preferences'],
    queryFn: notificationService.getPreferences,
  });

  const byType = useMemo(() => {
    const m = new Map<string, NotificationPreference>();
    prefs.forEach((p) => m.set(p.type, p));
    return m;
  }, [prefs]);

  const save = useMutation({
    mutationFn: ({ type, body }: { type: string; body: { inAppEnabled: boolean; emailEnabled: boolean } }) =>
      notificationService.setPreference(type, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications', 'preferences'] });
      notify('Préférence enregistrée.', 'success');
    },
    onError: () => notify("Échec de l'enregistrement.", 'error'),
  });

  const toggle = (type: string, field: 'inAppEnabled' | 'emailEnabled') => {
    const current = byType.get(type) ?? { type, inAppEnabled: true, emailEnabled: true };
    save.mutate({
      type,
      body: {
        inAppEnabled: field === 'inAppEnabled' ? !current.inAppEnabled : current.inAppEnabled,
        emailEnabled: field === 'emailEnabled' ? !current.emailEnabled : current.emailEnabled,
      },
    });
  };

  return (
    <div className="max-w-2xl mx-auto px-4 py-10">
      <Link to="/notifications" className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 mb-4">
        <ArrowLeft className="w-4 h-4" /> Retour aux notifications
      </Link>
      <h1 className="text-2xl font-bold text-brand-dark">Préférences de notification</h1>
      <p className="text-sm text-slate-500 mt-1 mb-6">
        Choisissez comment vous souhaitez être prévenu pour chaque type d'événement. Par défaut, tout est activé.
      </p>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="grid grid-cols-[1fr_auto_auto] items-center gap-4 px-4 py-2.5 bg-slate-50 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
          <span>Type</span>
          <span className="flex items-center gap-1"><Bell className="w-3.5 h-3.5" /> In-app</span>
          <span className="flex items-center gap-1"><Mail className="w-3.5 h-3.5" /> E-mail</span>
        </div>

        {isLoading && <p className="p-6 text-center text-sm text-slate-400">Chargement…</p>}

        {!isLoading &&
          types.map((type) => {
            const pref = byType.get(type) ?? { type, inAppEnabled: true, emailEnabled: true };
            return (
              <div
                key={type}
                className="grid grid-cols-[1fr_auto_auto] items-center gap-4 px-4 py-3 border-t border-slate-100"
              >
                <span className="text-sm text-slate-700">{NOTIFICATION_TYPE_LABELS[type] ?? type}</span>
                <input
                  type="checkbox"
                  checked={pref.inAppEnabled}
                  disabled={save.isPending}
                  onChange={() => toggle(type, 'inAppEnabled')}
                  className="w-4 h-4 rounded border-slate-300 justify-self-center"
                />
                <input
                  type="checkbox"
                  checked={pref.emailEnabled}
                  disabled={save.isPending}
                  onChange={() => toggle(type, 'emailEnabled')}
                  className="w-4 h-4 rounded border-slate-300 justify-self-center"
                />
              </div>
            );
          })}
      </div>
    </div>
  );
}
