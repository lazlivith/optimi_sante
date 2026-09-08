import { useCallback, useEffect, useState } from 'react';
import {
  Mail, MailCheck, MailX, Send, RotateCcw, Search, AlertTriangle, Inbox, BarChart3, Globe, ShieldCheck,
} from 'lucide-react';
import {
  adminEmailService, EMAIL_TYPE_LABELS,
  type EmailLog, type EmailStats,
} from '../../api/adminEmailService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard } from '../../components/common/StatCard';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Toast, type ToastType } from '../../components/common/Toast';

const PAGE_SIZE = 25;

export function AdminEmailsPage() {
  const [logs, setLogs] = useState<EmailLog[]>([]);
  const [stats, setStats] = useState<EmailStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [testRecipient, setTestRecipient] = useState('');
  const [isSendingTest, setIsSendingTest] = useState(false);
  const [resendingId, setResendingId] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [logPage, statsData] = await Promise.all([
        adminEmailService.getLogs({
          status: statusFilter || undefined,
          type: typeFilter || undefined,
          search: search || undefined,
          page,
          size: PAGE_SIZE,
        }),
        adminEmailService.getStats(),
      ]);
      setLogs(logPage.content);
      setTotalPages(logPage.totalPages);
      setTotalElements(logPage.totalElements);
      setStats(statsData);
    } catch (err) {
      console.error('Erreur lors du chargement du journal des emails', err);
      setToast({ message: "Impossible de charger le journal des emails.", type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [statusFilter, typeFilter, search, page]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleSendTest = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSendingTest(true);
    try {
      const { message } = await adminEmailService.sendTest(testRecipient);
      setToast({ message, type: 'success' });
      setTestRecipient('');
      await fetchData();
    } catch (err: any) {
      console.error("Échec de l'email de test", err);
      setToast({
        message: err.response?.data?.message || "Échec de l'envoi de l'email de test.",
        type: 'error',
      });
      await fetchData();
    } finally {
      setIsSendingTest(false);
    }
  };

  const handleResend = async (log: EmailLog) => {
    const confirmed = window.confirm(
      `Renvoyer ses identifiants à ${log.recipient} ?\n\n` +
      "Un NOUVEAU mot de passe provisoire sera généré : l'ancien cessera immédiatement de fonctionner."
    );
    if (!confirmed) return;

    setResendingId(log.id);
    try {
      const { message } = await adminEmailService.resend(log.id);
      setToast({ message, type: 'success' });
      await fetchData();
    } catch (err: any) {
      console.error('Échec du renvoi des identifiants', err);
      setToast({
        message: err.response?.data?.message || 'Échec du renvoi des identifiants.',
        type: 'error',
      });
    } finally {
      setResendingId(null);
    }
  };

  const formatDate = (value: string) =>
    new Date(value).toLocaleString('fr-FR', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });

  return (
    <div className="p-8">
      <PageHeader
        title="Emails"
        subtitle="Journal des envois, renvoi d'identifiants et statistiques de délivrabilité"
      />

      {/* Statistiques */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard label="Emails envoyés" value={stats?.totalSent} icon={MailCheck} tone="emerald" />
        <StatCard
          label="Échecs d'envoi"
          value={stats?.totalFailed}
          icon={MailX}
          tone={stats && stats.totalFailed > 0 ? 'rose' : 'slate'}
          sub={stats && stats.totalFailed > 0 ? 'À renvoyer depuis le journal' : undefined}
        />
        <StatCard
          label="Domaine d'envoi"
          value={stats?.sendingDomain ?? '—'}
          icon={Globe}
          tone={stats?.domainVerified ? 'emerald' : 'amber'}
          sub={stats?.domainVerified ? 'Vérifié' : 'Non vérifié'}
        />
        <StatCard
          label="Enregistrements DNS"
          value={
            stats && stats.dnsRecords.length > 0
              ? `${stats.dnsRecords.filter((r) => r.status === 'passed').length}/${stats.dnsRecords.length}`
              : '—'
          }
          icon={ShieldCheck}
          tone={stats?.domainVerified ? 'emerald' : 'amber'}
          sub={stats?.domainVerified ? 'Tous en place' : 'À compléter'}
        />
      </div>

      {/* API Mailtrap injoignable ou non configurée : on l'explique clairement */}
      {stats && !stats.mailtrapConnected && (
        <div className="mb-8 flex gap-3 p-4 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-600">
          <BarChart3 className="w-4 h-4 shrink-0 mt-0.5 text-slate-400" />
          <div>
            <p className="font-medium text-slate-700">Informations Mailtrap non disponibles</p>
            <p className="mt-0.5">{stats.mailtrapError}</p>
            <p className="mt-1 text-xs text-slate-500">
              Les compteurs « envoyés » et « échecs » proviennent du journal local et restent fiables.
            </p>
          </div>
        </div>
      )}

      {/* État de vérification du domaine d'envoi, directement depuis l'API Mailtrap.
          Tant que tous les enregistrements ne sont pas en place, aucun email ne peut
          être livré à une vraie adresse : c'est l'information la plus actionnable ici. */}
      {stats?.mailtrapConnected && !stats.domainVerified && stats.dnsRecords.length > 0 && (
        <div className="mb-8 bg-amber-50 border border-amber-200 rounded-xl p-5">
          <div className="flex gap-3">
            <AlertTriangle className="w-5 h-5 shrink-0 text-amber-600 mt-0.5" />
            <div className="min-w-0 flex-1">
              <h3 className="font-semibold text-amber-900">
                Domaine {stats.sendingDomain} non vérifié — aucun email ne peut être livré
              </h3>
              <p className="text-sm text-amber-800 mt-1">
                Ajoutez les enregistrements ci-dessous chez votre hébergeur de domaine, puis
                lancez « Vérifier les enregistrements DNS » dans Mailtrap. Les valeurs affichées
                ici viennent directement de l'API Mailtrap : elles sont exactes et non traduites.
              </p>

              <div className="mt-4 overflow-x-auto">
                <table className="w-full text-xs">
                  <thead className="text-left text-amber-900/70 uppercase tracking-wider">
                    <tr>
                      <th className="py-2 pr-3 font-medium">État</th>
                      <th className="py-2 pr-3 font-medium">Type</th>
                      <th className="py-2 pr-3 font-medium">Nom</th>
                      <th className="py-2 font-medium">Valeur</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-amber-200/60">
                    {stats.dnsRecords.map((r) => (
                      <tr key={`${r.type}-${r.name}`} className="align-top">
                        <td className="py-2 pr-3 whitespace-nowrap">
                          {r.status === 'passed' ? (
                            <span className="text-emerald-700 font-medium">✔ en place</span>
                          ) : (
                            <span className="text-amber-700 font-medium">● manquant</span>
                          )}
                        </td>
                        <td className="py-2 pr-3 font-mono text-amber-900">{r.type}</td>
                        <td className="py-2 pr-3 font-mono text-amber-900 break-all">{r.name}</td>
                        <td className="py-2 font-mono text-amber-900 break-all">{r.value}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Envoi de test */}
      <div className="bg-white rounded-xl border border-slate-200 p-6 mb-8">
        <h2 className="text-sm font-semibold text-brand-dark mb-1">Envoyer un email de test</h2>
        <p className="text-xs text-slate-500 mb-4">
          Vérifie la configuration d'envoi et le rendu réel dans une vraie boîte de réception.
        </p>
        <form onSubmit={handleSendTest} className="flex flex-col sm:flex-row gap-3">
          <input
            type="email"
            required
            value={testRecipient}
            onChange={(e) => setTestRecipient(e.target.value)}
            placeholder="destinataire@exemple.com"
            className="flex-1 rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand-green focus:ring-2 focus:ring-brand-green/20 focus:outline-none"
          />
          <button
            type="submit"
            disabled={isSendingTest}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg bg-brand-green text-white text-sm font-medium hover:bg-[#0f3c35] disabled:opacity-70 transition-colors"
          >
            <Send className="w-4 h-4" />
            {isSendingTest ? 'Envoi...' : 'Envoyer'}
          </button>
        </form>
      </div>

      {/* Journal */}
      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="p-4 border-b border-slate-200 flex flex-col sm:flex-row gap-3 sm:items-center">
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(0); }}
              placeholder="Rechercher un destinataire..."
              className="w-full pl-9 rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand-green focus:ring-2 focus:ring-brand-green/20 focus:outline-none"
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
            className="rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand-green focus:outline-none"
          >
            <option value="">Tous les statuts</option>
            <option value="SENT">Envoyés</option>
            <option value="FAILED">Échecs</option>
          </select>
          <select
            value={typeFilter}
            onChange={(e) => { setTypeFilter(e.target.value); setPage(0); }}
            className="rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand-green focus:outline-none"
          >
            <option value="">Tous les types</option>
            <option value="CREDENTIALS">Identifiants</option>
            <option value="TEST">Tests</option>
          </select>
        </div>

        {isLoading ? (
          <div className="p-12 text-center text-slate-500 text-sm">Chargement...</div>
        ) : logs.length === 0 ? (
          <EmptyState
            icon={Inbox}
            title="Aucun email dans le journal"
            description="Les emails envoyés par la plateforme apparaîtront ici, qu'ils aboutissent ou échouent."
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-4 py-3 font-medium">Destinataire</th>
                    <th className="px-4 py-3 font-medium">Type</th>
                    <th className="px-4 py-3 font-medium">Statut</th>
                    <th className="px-4 py-3 font-medium">Date</th>
                    <th className="px-4 py-3 font-medium text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {logs.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-50 transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <Mail className="w-4 h-4 text-slate-400 shrink-0" />
                          <div className="min-w-0">
                            <div className="font-medium text-brand-dark truncate">{log.recipient}</div>
                            {log.recipientRole && (
                              <div className="text-xs text-slate-500">{log.recipientRole}</div>
                            )}
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-slate-600">
                        {EMAIL_TYPE_LABELS[log.emailType] ?? log.emailType}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={log.status} />
                        {log.status === 'FAILED' && log.errorMessage && (
                          <div
                            className="text-xs text-rose-600 mt-1 max-w-xs truncate"
                            title={log.errorMessage}
                          >
                            {log.errorMessage}
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-slate-500 whitespace-nowrap">
                        {formatDate(log.sentAt)}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {log.canResend ? (
                          <button
                            onClick={() => handleResend(log)}
                            disabled={resendingId === log.id}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60 transition-colors"
                          >
                            <RotateCcw className="w-3.5 h-3.5" />
                            {resendingId === log.id ? 'Envoi...' : 'Renvoyer'}
                          </button>
                        ) : (
                          <span className="text-xs text-slate-400">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="p-4 border-t border-slate-200 flex items-center justify-between text-sm">
                <span className="text-slate-500">
                  {totalElements} email{totalElements > 1 ? 's' : ''} · page {page + 1} sur {totalPages}
                </span>
                <div className="flex gap-2">
                  <button
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                    className="px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-medium disabled:opacity-50 hover:bg-slate-50 transition-colors"
                  >
                    Précédent
                  </button>
                  <button
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1}
                    className="px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-medium disabled:opacity-50 hover:bg-slate-50 transition-colors"
                  >
                    Suivant
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
