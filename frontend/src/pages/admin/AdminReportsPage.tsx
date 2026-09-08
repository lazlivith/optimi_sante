import { useCallback, useEffect, useState } from 'react';
import {
  FileSpreadsheet, Loader2, Play, RefreshCw, Download, Eye, CheckCircle2, XCircle, Server,
} from 'lucide-react';
import {
  adminReportingService,
  type ReportDefinition,
  type ReportPreview,
  type ReportRun,
  type ReportingStatus,
} from '../../api/adminReportingService';
import { downloadApiFile } from '../../lib/download';
import { PageHeader } from '../../components/common/PageHeader';
import { EmptyState } from '../../components/common/EmptyState';

function fmtDate(iso: string | null): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
  } catch {
    return iso;
  }
}

export function AdminReportsPage() {
  const [status, setStatus] = useState<ReportingStatus | null>(null);
  const [reports, setReports] = useState<ReportDefinition[]>([]);
  const [runs, setRuns] = useState<Record<string, ReportRun[]>>({});
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [preview, setPreview] = useState<ReportPreview | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    const st = await adminReportingService.status();
    setStatus(st);
    if (!st.configured || !st.reachable) {
      setReports([]);
      return;
    }
    const defs = await adminReportingService.listReports();
    setReports(defs);
    const runEntries = await Promise.all(
      defs.map(async (d) => [d.key, await adminReportingService.runs(d.key, 5)] as const),
    );
    setRuns(Object.fromEntries(runEntries));
  }, []);

  useEffect(() => {
    load()
      .catch((e) => setError(e?.response?.data?.message ?? e?.message ?? 'Erreur'))
      .finally(() => setIsLoading(false));
  }, [load]);

  const runReport = async (key: string) => {
    setBusyKey(key);
    setError(null);
    try {
      await adminReportingService.run(key);
      setRuns((prev) => ({ ...prev, [key]: [] }));
      const fresh = await adminReportingService.runs(key, 5);
      setRuns((prev) => ({ ...prev, [key]: fresh }));
    } catch (e: unknown) {
      const anyE = e as { response?: { data?: { message?: string } }; message?: string };
      setError(anyE.response?.data?.message ?? anyE.message ?? 'Échec du lancement du rapport.');
    } finally {
      setBusyKey(null);
    }
  };

  const showPreview = async (key: string) => {
    setBusyKey(key);
    try {
      setPreview(await adminReportingService.preview(key, 50));
    } catch (e: unknown) {
      const anyE = e as { response?: { data?: { message?: string } }; message?: string };
      setError(anyE.response?.data?.message ?? anyE.message ?? 'Aperçu indisponible.');
    } finally {
      setBusyKey(null);
    }
  };

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Rapports"
        subtitle="Rapports batch et exports analytiques générés par le worker Python (CSV / Excel)."
        actions={
          <button
            onClick={() => {
              setIsLoading(true);
              load().finally(() => setIsLoading(false));
            }}
            className="inline-flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm font-semibold text-slate-700 hover:bg-slate-50"
          >
            <RefreshCw className="w-4 h-4" />
            Rafraîchir
          </button>
        }
      />

      {status && (
        <div
          className={`flex items-center gap-2 rounded-xl px-4 py-3 mb-6 text-sm border ${
            status.reachable
              ? 'bg-emerald-50 border-emerald-100 text-emerald-800'
              : 'bg-amber-50 border-amber-100 text-amber-800'
          }`}
        >
          <Server className="w-4 h-4" />
          {status.reachable
            ? 'Worker de reporting connecté.'
            : status.configured
              ? "Worker de reporting configuré mais injoignable. Démarrez le conteneur « analytics » (docker compose up -d analytics)."
              : 'Worker de reporting non configuré (REPORTING_WORKER_URL). Fonctionnalité indisponible.'}
        </div>
      )}

      {error && (
        <div className="text-sm text-rose-700 bg-rose-50 border border-rose-100 rounded-lg px-3 py-2 mb-4">{error}</div>
      )}

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="w-8 h-8 text-brand-green animate-spin" />
        </div>
      ) : reports.length === 0 ? (
        <EmptyState
          icon={FileSpreadsheet}
          title="Aucun rapport disponible"
          description="Le worker Python n'est pas accessible pour le moment."
        />
      ) : (
        <div className="space-y-4">
          {reports.map((r) => {
            const history = runs[r.key] ?? [];
            const last = history[0];
            return (
              <div key={r.key} className="bg-white rounded-2xl border border-slate-200 p-5">
                <div className="flex items-start justify-between gap-4 flex-wrap">
                  <div className="min-w-0">
                    <h3 className="text-sm font-bold text-slate-800">{r.title}</h3>
                    <p className="text-xs text-slate-500 mt-0.5">{r.description}</p>
                    <code className="text-[11px] text-slate-400">{r.key}</code>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      onClick={() => showPreview(r.key)}
                      disabled={busyKey === r.key}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-600 border border-slate-200 hover:bg-slate-50 disabled:opacity-60"
                    >
                      <Eye className="w-3.5 h-3.5" /> Aperçu
                    </button>
                    <button
                      onClick={() => runReport(r.key)}
                      disabled={busyKey === r.key}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-brand-green hover:opacity-90 disabled:opacity-60"
                    >
                      {busyKey === r.key ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
                      Lancer
                    </button>
                    {last?.status === 'SUCCESS' && (
                      <>
                        <button
                          onClick={() => downloadApiFile(adminReportingService.downloadUrl(r.key, 'csv'), `${r.key}.csv`)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-600 border border-slate-200 hover:bg-slate-50"
                        >
                          <Download className="w-3.5 h-3.5" /> CSV
                        </button>
                        <button
                          onClick={() => downloadApiFile(adminReportingService.downloadUrl(r.key, 'xlsx'), `${r.key}.xlsx`)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-600 border border-slate-200 hover:bg-slate-50"
                        >
                          <Download className="w-3.5 h-3.5" /> Excel
                        </button>
                      </>
                    )}
                  </div>
                </div>

                {history.length > 0 && (
                  <div className="mt-3 border-t border-slate-100 pt-3">
                    <div className="text-[11px] uppercase tracking-wide text-slate-400 font-semibold mb-1.5">
                      Dernières exécutions
                    </div>
                    <ul className="space-y-1">
                      {history.map((run) => (
                        <li key={run.id} className="flex items-center gap-2 text-xs text-slate-500">
                          {run.status === 'SUCCESS' ? (
                            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
                          ) : run.status === 'FAILED' ? (
                            <XCircle className="w-3.5 h-3.5 text-rose-500" />
                          ) : (
                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          )}
                          <span className="tabular-nums">{fmtDate(run.startedAt)}</span>
                          <span className="text-slate-400">·</span>
                          <span>{run.trigger === 'SCHEDULED' ? 'batch nocturne' : 'manuel'}</span>
                          {run.rowCount != null && (
                            <>
                              <span className="text-slate-400">·</span>
                              <span>{run.rowCount} lignes</span>
                            </>
                          )}
                          {run.error && <span className="text-rose-500 truncate">· {run.error}</span>}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {preview && (
        <div className="fixed inset-0 bg-slate-900/40 flex items-center justify-center z-50 p-4" onClick={() => setPreview(null)}>
          <div className="bg-white rounded-2xl max-w-4xl w-full max-h-[80vh] flex flex-col shadow-xl" onClick={(e) => e.stopPropagation()}>
            <div className="px-5 py-3 border-b border-slate-100 flex items-center justify-between">
              <div className="text-sm font-bold text-slate-800">
                Aperçu · {preview.key}
                <span className="text-slate-400 font-normal"> — {preview.totalRows} lignes au total</span>
              </div>
              <button onClick={() => setPreview(null)} className="text-slate-400 hover:text-slate-600 text-sm">Fermer</button>
            </div>
            <div className="overflow-auto p-4">
              <table className="w-full text-xs">
                <thead className="bg-slate-50 text-slate-500 sticky top-0">
                  <tr>
                    {preview.columns.map((c) => (
                      <th key={c} className="text-left font-semibold px-2 py-1.5 whitespace-nowrap">{c}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {preview.rows.map((row, i) => (
                    <tr key={i}>
                      {preview.columns.map((c) => (
                        <td key={c} className="px-2 py-1.5 whitespace-nowrap text-slate-600">
                          {String(row[c] ?? '')}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
