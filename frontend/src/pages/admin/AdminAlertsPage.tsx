import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BellRing, Check, Clock, ExternalLink, SlidersHorizontal } from 'lucide-react';
import {
  adminAlertService,
  type AlertSeverityFilter,
  type AlertStatusFilter,
  type AlertThreshold,
} from '../../api/adminAlertService';
import { notificationService, type NotificationSeverity } from '../../api/notificationService';
import { PageHeader } from '../../components/common/PageHeader';
import { EmptyState } from '../../components/common/EmptyState';
import { useToast } from '../../context/ToastContext';

const SEV_BADGE: Record<NotificationSeverity, string> = {
  INFO: 'bg-blue-100 text-blue-700',
  SUCCESS: 'bg-green-100 text-green-700',
  WARNING: 'bg-amber-100 text-amber-700',
  CRITICAL: 'bg-red-100 text-red-700',
};

const SEV_OPTIONS: { value: AlertSeverityFilter; label: string }[] = [
  { value: 'ALERT', label: 'Alertes (Warning + Critical)' },
  { value: 'CRITICAL', label: 'Critical uniquement' },
  { value: 'WARNING', label: 'Warning uniquement' },
  { value: 'ALL', label: 'Toutes sévérités' },
];

const STATUS_OPTIONS: { value: AlertStatusFilter; label: string }[] = [
  { value: 'open', label: 'Ouvertes' },
  { value: 'acknowledged', label: 'Acquittées' },
  { value: 'all', label: 'Toutes' },
];

function fmt(iso: string): string {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

export function AdminAlertsPage() {
  const [severity, setSeverity] = useState<AlertSeverityFilter>('ALERT');
  const [status, setStatus] = useState<AlertStatusFilter>('open');
  const [page, setPage] = useState(0);
  const [showThresholds, setShowThresholds] = useState(false);
  const navigate = useNavigate();
  const { notify } = useToast();
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['admin-alerts', severity, status, page],
    queryFn: () => adminAlertService.list(severity, status, page, 20),
    refetchInterval: 60_000,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-alerts'] });
    queryClient.invalidateQueries({ queryKey: ['notifications'] });
  };

  const ack = useMutation({
    mutationFn: notificationService.acknowledge,
    onSuccess: () => {
      invalidate();
      notify('Alerte acquittée.', 'success');
    },
  });
  const snooze = useMutation({
    mutationFn: (id: string) => notificationService.snooze(id, 24),
    onSuccess: () => {
      invalidate();
      notify('Alerte mise en veille 24 h.', 'info');
    },
  });

  const rows = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="p-8">
      <PageHeader
        title="Alertes"
        subtitle="Événements et seuils de veille nécessitant l'attention de l'équipe."
        actions={
          <button
            onClick={() => setShowThresholds((v) => !v)}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-600 hover:bg-slate-50"
          >
            <SlidersHorizontal className="w-4 h-4" />
            Seuils de veille
          </button>
        }
      />

      {showThresholds && <ThresholdsEditor />}

      <div className="flex flex-wrap items-center gap-3 mb-4">
        <select
          value={severity}
          onChange={(e) => {
            setSeverity(e.target.value as AlertSeverityFilter);
            setPage(0);
          }}
          className="px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
        >
          {SEV_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as AlertStatusFilter);
            setPage(0);
          }}
          className="px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
        >
          {STATUS_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        {isLoading && <p className="p-8 text-center text-sm text-slate-400">Chargement…</p>}

        {!isLoading && rows.length === 0 && (
          <EmptyState icon={BellRing} title="Aucune alerte" description="Rien ne requiert votre attention pour ce filtre." />
        )}

        {rows.map((a) => (
          <div key={a.id} className="flex gap-3 px-4 py-3.5 border-t border-slate-100 first:border-t-0">
            <span
              className={`shrink-0 h-fit text-[10px] font-bold px-2 py-0.5 rounded ${SEV_BADGE[a.severity] ?? SEV_BADGE.INFO}`}
            >
              {a.severity}
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <span className="text-sm font-medium text-slate-800">{a.title}</span>
                {a.groupCount > 1 && (
                  <span className="text-[10px] font-bold text-slate-500 bg-slate-100 rounded px-1.5 py-0.5">
                    ×{a.groupCount}
                  </span>
                )}
                {a.acknowledged && (
                  <span className="text-[10px] font-semibold text-green-700 bg-green-50 rounded px-1.5 py-0.5">
                    acquittée
                  </span>
                )}
              </div>
              {a.body && <p className="text-xs text-slate-500 mt-0.5">{a.body}</p>}
              <p className="text-[11px] text-slate-400 mt-1">{fmt(a.createdAt)}</p>
            </div>
            <div className="flex items-start gap-1 shrink-0">
              {a.linkUrl && (
                <button
                  onClick={() => navigate(a.linkUrl!)}
                  className="p-1.5 rounded hover:bg-slate-100 text-slate-400"
                  title="Ouvrir l'écran concerné"
                >
                  <ExternalLink className="w-4 h-4" />
                </button>
              )}
              {!a.acknowledged && (
                <>
                  <button
                    onClick={() => snooze.mutate(a.id)}
                    className="p-1.5 rounded hover:bg-slate-100 text-slate-400"
                    title="Mettre en veille 24 h"
                  >
                    <Clock className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => ack.mutate(a.id)}
                    className="p-1.5 rounded hover:bg-green-50 text-green-600"
                    title="Acquitter (traité)"
                  >
                    <Check className="w-4 h-4" />
                  </button>
                </>
              )}
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

function ThresholdsEditor() {
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const { data: thresholds = [], isLoading } = useQuery({
    queryKey: ['alert-thresholds'],
    queryFn: adminAlertService.thresholds,
  });

  const update = useMutation({
    mutationFn: ({ key, body }: { key: string; body: Partial<AlertThreshold> }) =>
      adminAlertService.updateThreshold(key, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alert-thresholds'] });
      notify('Seuil mis à jour.', 'success');
    },
    onError: () => notify('Échec de la mise à jour du seuil.', 'error'),
  });

  return (
    <div className="bg-white rounded-xl border border-slate-200 p-4 mb-6">
      <h2 className="text-sm font-semibold text-slate-800 mb-3">Seuils de veille (vérifiés chaque heure)</h2>
      {isLoading && <p className="text-sm text-slate-400">Chargement…</p>}
      <div className="space-y-3">
        {thresholds.map((t) => (
          <div key={t.key} className="flex flex-wrap items-center gap-3 text-sm border-t border-slate-100 pt-3 first:border-t-0 first:pt-0">
            <label className="inline-flex items-center gap-2 min-w-[240px]">
              <input
                type="checkbox"
                checked={t.enabled}
                onChange={(e) => update.mutate({ key: t.key, body: { enabled: e.target.checked } })}
                className="rounded border-slate-300"
              />
              <span className="text-slate-700">{t.label}</span>
            </label>
            <span className="text-slate-400">déclenche si ≥</span>
            <input
              type="number"
              min={1}
              defaultValue={t.thresholdValue}
              onBlur={(e) => {
                const v = Number(e.target.value);
                if (v && v !== t.thresholdValue) update.mutate({ key: t.key, body: { thresholdValue: v } });
              }}
              className="w-16 px-2 py-1 rounded border border-slate-200"
            />
            <span className="text-slate-400">sur</span>
            <input
              type="number"
              min={1}
              defaultValue={t.windowHours}
              onBlur={(e) => {
                const v = Number(e.target.value);
                if (v && v !== t.windowHours) update.mutate({ key: t.key, body: { windowHours: v } });
              }}
              className="w-20 px-2 py-1 rounded border border-slate-200"
            />
            <span className="text-slate-400">h —</span>
            <select
              value={t.severity}
              onChange={(e) => update.mutate({ key: t.key, body: { severity: e.target.value as NotificationSeverity } })}
              className="px-2 py-1 rounded border border-slate-200 bg-white"
            >
              <option value="WARNING">WARNING</option>
              <option value="CRITICAL">CRITICAL</option>
              <option value="INFO">INFO</option>
            </select>
          </div>
        ))}
      </div>
    </div>
  );
}
