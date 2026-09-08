import { useCallback, useEffect, useState } from 'react';
import { Download, Loader2, RefreshCw, ScrollText, Search, ShieldCheck, Activity } from 'lucide-react';
import {
  adminAuditService,
  type AuditFilters,
  type AuditLogEntry,
  type AuditStats,
  type Page,
} from '../../api/adminAuditService';
import { downloadApiFile } from '../../lib/download';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard } from '../../components/common/StatCard';
import { EmptyState } from '../../components/common/EmptyState';

function fmtDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
  } catch {
    return iso;
  }
}

const PAGE_SIZE = 25;

export function AdminAuditLogPage() {
  const [page, setPage] = useState<Page<AuditLogEntry> | null>(null);
  const [stats, setStats] = useState<AuditStats | null>(null);
  const [pageIndex, setPageIndex] = useState(0);
  const [filters, setFilters] = useState<AuditFilters>({});
  const [draft, setDraft] = useState<AuditFilters>({});
  const [isLoading, setIsLoading] = useState(true);
  const [isBusy, setIsBusy] = useState(false);

  const fetchPage = useCallback(async (idx: number, f: AuditFilters) => {
    const [p, s] = await Promise.all([
      adminAuditService.list({ ...f, page: idx, size: PAGE_SIZE }),
      idx === 0 ? adminAuditService.stats(30) : Promise.resolve(null),
    ]);
    setPage(p);
    if (s) setStats(s);
  }, []);

  useEffect(() => {
    fetchPage(0, {}).finally(() => setIsLoading(false));
  }, [fetchPage]);

  const applyFilters = async () => {
    setIsBusy(true);
    setFilters(draft);
    setPageIndex(0);
    try {
      await fetchPage(0, draft);
    } finally {
      setIsBusy(false);
    }
  };

  const goToPage = async (idx: number) => {
    setIsBusy(true);
    setPageIndex(idx);
    try {
      await fetchPage(idx, filters);
    } finally {
      setIsBusy(false);
    }
  };

  const [exporting, setExporting] = useState(false);
  const doExport = async () => {
    setExporting(true);
    try {
      await downloadApiFile(adminAuditService.exportUrl(filters), 'audit-log.csv');
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Journal d'audit"
        subtitle="Trace horodatée de toutes les actions sensibles du back-office (RGPD, modération, finance)."
        actions={
          <div className="flex items-center gap-2">
            <button
              onClick={doExport}
              disabled={exporting}
              className="inline-flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
            >
              {exporting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
              Exporter CSV
            </button>
            <button
              onClick={() => goToPage(pageIndex)}
              disabled={isBusy}
              className="inline-flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
            >
              <RefreshCw className={`w-4 h-4 ${isBusy ? 'animate-spin' : ''}`} />
              Rafraîchir
            </button>
          </div>
        }
      />

      {stats && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <StatCard label="Entrées (30 j)" value={stats.totalEntries} icon={ScrollText} tone="blue" />
          <StatCard label="Dernières 24 h" value={stats.entriesLast24h} icon={Activity} tone="emerald" />
          <StatCard
            label="Action la plus fréquente"
            value={stats.byAction[0]?.label ?? '—'}
            sub={stats.byAction[0] ? `${stats.byAction[0].total} entrées` : undefined}
            icon={ShieldCheck}
            tone="purple"
          />
          <StatCard
            label="Acteur le plus actif"
            value={stats.topActors[0]?.label ?? '—'}
            sub={stats.topActors[0] ? `${stats.topActors[0].total} actions` : undefined}
            icon={Search}
            tone="amber"
          />
        </div>
      )}

      <div className="bg-white rounded-2xl border border-slate-200 p-4 mb-4 flex flex-wrap items-end gap-3">
        <label className="text-xs text-slate-500 flex flex-col gap-1">
          Action
          <input
            value={draft.action ?? ''}
            onChange={(e) => setDraft((d) => ({ ...d, action: e.target.value }))}
            placeholder="HTTP_WRITE, RGPD_ANONYMIZE…"
            className="border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm text-slate-700 w-56"
          />
        </label>
        <label className="text-xs text-slate-500 flex flex-col gap-1">
          Type d'entité
          <input
            value={draft.entityType ?? ''}
            onChange={(e) => setDraft((d) => ({ ...d, entityType: e.target.value }))}
            placeholder="User, Enrollment…"
            className="border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm text-slate-700 w-40"
          />
        </label>
        <label className="text-xs text-slate-500 flex flex-col gap-1">
          Acteur (email)
          <input
            value={draft.actorEmail ?? ''}
            onChange={(e) => setDraft((d) => ({ ...d, actorEmail: e.target.value }))}
            placeholder="admin@…"
            className="border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm text-slate-700 w-48"
          />
        </label>
        <button
          onClick={applyFilters}
          disabled={isBusy}
          className="inline-flex items-center gap-2 px-4 py-2 bg-brand-green text-white rounded-lg text-sm font-semibold hover:opacity-90 disabled:opacity-60"
        >
          <Search className="w-4 h-4" />
          Filtrer
        </button>
        {(filters.action || filters.entityType || filters.actorEmail) && (
          <button
            onClick={() => {
              setDraft({});
              setFilters({});
              setPageIndex(0);
              fetchPage(0, {});
            }}
            className="text-xs text-slate-500 underline"
          >
            Réinitialiser
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="w-8 h-8 text-brand-green animate-spin" />
        </div>
      ) : !page || page.content.length === 0 ? (
        <EmptyState icon={ScrollText} title="Aucune entrée" description="Aucune action ne correspond à ces filtres." />
      ) : (
        <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-slate-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="text-left font-semibold px-4 py-3">Horodatage</th>
                  <th className="text-left font-semibold px-4 py-3">Acteur</th>
                  <th className="text-left font-semibold px-4 py-3">Action</th>
                  <th className="text-left font-semibold px-4 py-3">Cible</th>
                  <th className="text-left font-semibold px-4 py-3">Détail</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {page.content.map((e) => (
                  <tr key={e.id} className="hover:bg-slate-50/60">
                    <td className="px-4 py-3 whitespace-nowrap text-slate-500">{fmtDate(e.createdAt)}</td>
                    <td className="px-4 py-3">
                      <div className="text-slate-800">{e.actorEmail ?? '(système)'}</div>
                      {e.actorRole && <div className="text-[11px] text-slate-400">{e.actorRole}</div>}
                    </td>
                    <td className="px-4 py-3">
                      <span className="inline-flex px-2 py-0.5 rounded-md bg-slate-100 text-slate-700 text-xs font-semibold">
                        {e.action}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-600">
                      {e.entityType ? `${e.entityType}${e.entityId ? ` #${e.entityId.slice(0, 8)}` : ''}` : '—'}
                    </td>
                    <td className="px-4 py-3 text-slate-500 max-w-md truncate" title={e.summary ?? ''}>
                      {e.summary ?? `${e.httpMethod ?? ''} ${e.path ?? ''}`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between px-4 py-3 border-t border-slate-100 text-sm text-slate-500">
            <span>
              {page.totalElements} entrée{page.totalElements > 1 ? 's' : ''} · page {page.number + 1}/{Math.max(1, page.totalPages)}
            </span>
            <div className="flex gap-2">
              <button
                onClick={() => goToPage(pageIndex - 1)}
                disabled={isBusy || page.number === 0}
                className="px-3 py-1.5 rounded-lg border border-slate-200 disabled:opacity-40 hover:bg-slate-50"
              >
                Précédent
              </button>
              <button
                onClick={() => goToPage(pageIndex + 1)}
                disabled={isBusy || page.number + 1 >= page.totalPages}
                className="px-3 py-1.5 rounded-lg border border-slate-200 disabled:opacity-40 hover:bg-slate-50"
              >
                Suivant
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
