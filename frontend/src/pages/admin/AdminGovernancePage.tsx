import { useCallback, useEffect, useState } from 'react';
import {
  ShieldCheck, ShieldAlert, Loader2, RefreshCw, Search, Download, UserX,
  Database, Clock, FileText, CheckCircle2, AlertTriangle,
} from 'lucide-react';
import {
  adminGovernanceService,
  type RetentionReport,
  type RgpdRequestRow,
  type RgpdSubjectSummary,
  type SoftDeleteReport,
} from '../../api/adminGovernanceService';
import type { Page } from '../../api/adminAuditService';
import { downloadJson } from '../../lib/download';
import { useAuth } from '../../context/AuthContext';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard } from '../../components/common/StatCard';
import { EmptyState } from '../../components/common/EmptyState';

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
  } catch {
    return iso;
  }
}

export function AdminGovernancePage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';

  const [softDelete, setSoftDelete] = useState<SoftDeleteReport | null>(null);
  const [retention, setRetention] = useState<RetentionReport | null>(null);
  const [requests, setRequests] = useState<Page<RgpdRequestRow> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const [email, setEmail] = useState('');
  const [subject, setSubject] = useState<RgpdSubjectSummary | null>(null);
  const [subjectError, setSubjectError] = useState<string | null>(null);
  const [lookupBusy, setLookupBusy] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [confirmAnon, setConfirmAnon] = useState(false);

  const fetchReports = useCallback(async () => {
    const [sd, rt, rq] = await Promise.all([
      adminGovernanceService.softDeleteReport(),
      adminGovernanceService.retentionReport(24),
      adminGovernanceService.rgpdRequests(0, 20),
    ]);
    setSoftDelete(sd);
    setRetention(rt);
    setRequests(rq);
  }, []);

  useEffect(() => {
    fetchReports().finally(() => setIsLoading(false));
  }, [fetchReports]);

  const refresh = async () => {
    setIsRefreshing(true);
    try {
      await fetchReports();
    } finally {
      setIsRefreshing(false);
    }
  };

  const lookup = async () => {
    if (!email.trim()) return;
    setLookupBusy(true);
    setSubject(null);
    setSubjectError(null);
    setNotice(null);
    try {
      setSubject(await adminGovernanceService.lookupSubject(email.trim()));
    } catch (e: unknown) {
      setSubjectError(extractMessage(e));
    } finally {
      setLookupBusy(false);
    }
  };

  const doExport = async () => {
    setActionBusy(true);
    setNotice(null);
    try {
      const payload = await adminGovernanceService.exportSubject(email.trim());
      downloadJson(payload, `export-rgpd-${email.trim().replace(/[^a-z0-9]/gi, '_')}.json`);
      setNotice('Export généré et téléchargé. La demande est tracée dans l’historique.');
      await refresh();
    } catch (e: unknown) {
      setSubjectError(extractMessage(e));
    } finally {
      setActionBusy(false);
    }
  };

  const doAnonymize = async () => {
    setActionBusy(true);
    setConfirmAnon(false);
    setNotice(null);
    try {
      const res = await adminGovernanceService.anonymizeSubject(email.trim());
      setNotice(`Anonymisation effectuée. ${res.summary}`);
      setSubject(null);
      setEmail('');
      await refresh();
    } catch (e: unknown) {
      setSubjectError(extractMessage(e));
    } finally {
      setActionBusy(false);
    }
  };

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Gouvernance / RGPD"
        subtitle="Conformité des données : étanchéité du soft delete, rétention, droit d'accès et droit à l'effacement."
        actions={
          <button
            onClick={refresh}
            disabled={isRefreshing}
            className="inline-flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin' : ''}`} />
            Rafraîchir
          </button>
        }
      />

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="w-8 h-8 text-brand animate-spin" />
        </div>
      ) : (
        <div className="space-y-8">
          {/* ---------- Droit d'accès / effacement ---------- */}
          <section className="bg-white rounded-2xl border border-slate-200 p-6">
            <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2 mb-1">
              <Search className="w-4 h-4" /> Personne concernée
            </h2>
            <p className="text-xs text-slate-500 mb-4">
              Recherchez un compte par email pour exporter ses données (droit d'accès) ou l'anonymiser
              irréversiblement (droit à l'effacement — réservé au Super Admin).
            </p>

            <div className="flex flex-wrap items-center gap-2">
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && lookup()}
                placeholder="email@exemple.fr"
                className="border border-slate-200 rounded-lg px-3 py-2 text-sm text-slate-700 w-72"
              />
              <button
                onClick={lookup}
                disabled={lookupBusy || !email.trim()}
                className="inline-flex items-center gap-2 px-4 py-2 bg-brand text-white rounded-lg text-sm font-semibold hover:opacity-90 disabled:opacity-60"
              >
                {lookupBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
                Rechercher
              </button>
            </div>

            {subjectError && (
              <div className="mt-3 text-sm text-rose-700 bg-rose-50 border border-rose-100 rounded-lg px-3 py-2">
                {subjectError}
              </div>
            )}
            {notice && (
              <div className="mt-3 text-sm text-emerald-800 bg-emerald-50 border border-emerald-100 rounded-lg px-3 py-2">
                {notice}
              </div>
            )}

            {subject && (
              <div className="mt-4 border border-slate-200 rounded-xl p-4">
                <div className="grid sm:grid-cols-4 gap-3 text-sm">
                  <Fact label="Commandes" value={subject.ordersCount} />
                  <Fact label="Inscriptions" value={subject.enrollmentsCount} />
                  <Fact label="Leads" value={subject.leadsCount} />
                  <Fact label="Candidatures" value={subject.applicationsCount} />
                </div>
                {subject.alreadyAnonymized && (
                  <div className="mt-3 text-xs text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
                    Ce compte est déjà anonymisé.
                  </div>
                )}
                <div className="mt-4 flex flex-wrap gap-2">
                  <button
                    onClick={doExport}
                    disabled={actionBusy}
                    className="inline-flex items-center gap-2 px-3 py-2 border border-slate-200 rounded-lg text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                  >
                    {actionBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
                    Exporter les données (JSON)
                  </button>
                  <button
                    onClick={() => setConfirmAnon(true)}
                    disabled={actionBusy || !isSuperAdmin || subject.alreadyAnonymized}
                    title={!isSuperAdmin ? 'Action réservée au Super Admin' : undefined}
                    className="inline-flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-semibold text-white bg-rose-600 hover:bg-rose-700 disabled:opacity-40"
                  >
                    <UserX className="w-4 h-4" />
                    Anonymiser
                  </button>
                  {!isSuperAdmin && (
                    <span className="text-xs text-slate-400 self-center">Anonymisation réservée au Super Admin.</span>
                  )}
                </div>
              </div>
            )}
          </section>

          {/* ---------- Soft delete ---------- */}
          <section>
            <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2 mb-3">
              <Database className="w-4 h-4" /> Étanchéité du Soft Delete
            </h2>
            {softDelete && (
              <>
                <div
                  className={`flex items-start gap-2 rounded-xl px-4 py-3 mb-3 text-sm border ${
                    softDelete.healthy
                      ? 'bg-emerald-50 border-emerald-100 text-emerald-800'
                      : 'bg-rose-50 border-rose-100 text-rose-800'
                  }`}
                >
                  {softDelete.healthy ? <CheckCircle2 className="w-4 h-4 mt-0.5" /> : <AlertTriangle className="w-4 h-4 mt-0.5" />}
                  <span>{softDelete.verdict}</span>
                </div>
                <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead className="bg-slate-50 text-slate-500 text-xs uppercase tracking-wide">
                        <tr>
                          <th className="text-left font-semibold px-4 py-3">Table</th>
                          <th className="text-left font-semibold px-4 py-3">Filtre global</th>
                          <th className="text-right font-semibold px-4 py-3">Total</th>
                          <th className="text-right font-semibold px-4 py-3">Actives</th>
                          <th className="text-right font-semibold px-4 py-3">Effacées</th>
                          <th className="text-right font-semibold px-4 py-3">Anonymisées</th>
                          <th className="text-left font-semibold px-4 py-3">Note</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {softDelete.tables.map((t) => (
                          <tr key={t.table}>
                            <td className="px-4 py-3 font-medium text-slate-800">
                              {t.table}
                              <span className="text-slate-400 font-normal"> · {t.entity}</span>
                            </td>
                            <td className="px-4 py-3">
                              {t.globalFilterEnforced ? (
                                <span className="inline-flex items-center gap-1 text-emerald-700 text-xs font-semibold">
                                  <ShieldCheck className="w-3.5 h-3.5" /> appliqué
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 text-slate-400 text-xs font-semibold">
                                  <ShieldAlert className="w-3.5 h-3.5" /> n/a
                                </span>
                              )}
                            </td>
                            <td className="px-4 py-3 text-right tabular-nums text-slate-600">{t.totalRows}</td>
                            <td className="px-4 py-3 text-right tabular-nums text-slate-600">{t.activeRows}</td>
                            <td className="px-4 py-3 text-right tabular-nums text-slate-600">{t.softDeletedRows}</td>
                            <td className="px-4 py-3 text-right tabular-nums text-slate-600">{t.anonymizedRows}</td>
                            <td className="px-4 py-3 text-xs text-slate-500 max-w-sm">{t.note}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </>
            )}
          </section>

          {/* ---------- Rétention ---------- */}
          <section>
            <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2 mb-3">
              <Clock className="w-4 h-4" /> Rétention des données ({retention?.staleThresholdMonths ?? 24} mois)
            </h2>
            {retention && (
              <>
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                  <StatCard label="Comptes inactifs" value={retention.staleActiveUsers} sub="Sans commande ni dossier" icon={UserX} tone="amber" />
                  <StatCard label="Leads anciens" value={retention.oldLeads} sub="Au-delà du seuil" icon={Clock} tone="slate" />
                  <StatCard label="Comptes anonymisés" value={retention.anonymizedUsers} icon={ShieldCheck} tone="emerald" />
                  <StatCard label="Sans consentement" value={retention.usersWithoutConsent} sub="gdpr_consent_at manquant" icon={AlertTriangle} tone="rose" />
                </div>
                {retention.staleUserSample.length > 0 && (
                  <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                    <div className="px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide border-b border-slate-100">
                      Échantillon de comptes inactifs (candidats à anonymisation)
                    </div>
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <tbody className="divide-y divide-slate-100">
                          {retention.staleUserSample.map((c) => (
                            <tr key={c.userId} className="hover:bg-slate-50/60">
                              <td className="px-4 py-2.5 text-slate-800">{c.email}</td>
                              <td className="px-4 py-2.5 text-slate-400 text-xs">{c.role}</td>
                              <td className="px-4 py-2.5 text-slate-500 text-xs">créé le {fmtDate(c.createdAt)}</td>
                              <td className="px-4 py-2.5 text-right">
                                <button
                                  onClick={() => {
                                    setEmail(c.email);
                                    setSubject(null);
                                    window.scrollTo({ top: 0, behavior: 'smooth' });
                                  }}
                                  className="text-xs text-brand font-semibold underline"
                                >
                                  Traiter
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </>
            )}
          </section>

          {/* ---------- Historique des demandes ---------- */}
          <section>
            <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2 mb-3">
              <FileText className="w-4 h-4" /> Historique des demandes RGPD
            </h2>
            {!requests || requests.content.length === 0 ? (
              <EmptyState icon={FileText} title="Aucune demande traitée" description="Les exports et anonymisations apparaîtront ici." />
            ) : (
              <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-slate-500 text-xs uppercase tracking-wide">
                      <tr>
                        <th className="text-left font-semibold px-4 py-3">Date</th>
                        <th className="text-left font-semibold px-4 py-3">Type</th>
                        <th className="text-left font-semibold px-4 py-3">Personne</th>
                        <th className="text-left font-semibold px-4 py-3">Demandé par</th>
                        <th className="text-left font-semibold px-4 py-3">Résultat</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {requests.content.map((r) => (
                        <tr key={r.id}>
                          <td className="px-4 py-3 whitespace-nowrap text-slate-500">{fmtDate(r.createdAt)}</td>
                          <td className="px-4 py-3">
                            <span className="inline-flex px-2 py-0.5 rounded-md bg-slate-100 text-slate-700 text-xs font-semibold">
                              {r.requestType}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-slate-700">{r.subjectEmail}</td>
                          <td className="px-4 py-3 text-slate-500">{r.requestedByEmail ?? '—'}</td>
                          <td className="px-4 py-3 text-slate-500 max-w-md truncate" title={r.resultSummary ?? ''}>
                            {r.resultSummary ?? r.status}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </section>
        </div>
      )}

      {confirmAnon && (
        <div className="fixed inset-0 bg-slate-900/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl">
            <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <AlertTriangle className="w-5 h-5 text-rose-600" /> Anonymisation irréversible
            </h3>
            <p className="text-sm text-slate-600 mt-3">
              Toutes les données personnelles de <strong>{email}</strong> seront brouillées définitivement
              (identité, contacts, profil, leads, candidatures). Les commandes et inscriptions sont
              conservées mais rattachées à un compte anonyme. Cette action ne peut pas être annulée.
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                onClick={() => setConfirmAnon(false)}
                className="px-4 py-2 rounded-lg border border-slate-200 text-sm font-semibold text-slate-600 hover:bg-slate-50"
              >
                Annuler
              </button>
              <button
                onClick={doAnonymize}
                className="px-4 py-2 rounded-lg bg-rose-600 text-white text-sm font-semibold hover:bg-rose-700"
              >
                Confirmer l'anonymisation
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function Fact({ label, value }: { label: string; value: number | string }) {
  return (
    <div>
      <div className="text-lg font-bold text-slate-900">{value}</div>
      <div className="text-xs text-slate-500">{label}</div>
    </div>
  );
}

function extractMessage(e: unknown): string {
  if (typeof e === 'object' && e !== null) {
    const anyE = e as { response?: { data?: { message?: string } }; message?: string };
    return anyE.response?.data?.message ?? anyE.message ?? 'Erreur inconnue.';
  }
  return 'Erreur inconnue.';
}
