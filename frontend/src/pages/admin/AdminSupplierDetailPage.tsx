import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  AlertTriangle, ArrowLeft, CheckCircle2, Download, FileSpreadsheet, Loader2, Upload, XCircle,
} from 'lucide-react';
import {
  adminSupplierService, IMPORT_STATUS_LABELS, type CatalogImport, type Supplier,
} from '../../api/adminSupplierService';
import { downloadApiFile } from '../../lib/download';
import { EmptyState } from '../../components/common/EmptyState';
import { Toast, type ToastType } from '../../components/common/Toast';

const formatDate = (valeur: string | null) =>
  valeur ? new Date(valeur).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

/**
 * Fiche d'un fournisseur et import de son catalogue.
 *
 * <p>Le dépôt d'un fichier ne fait qu'analyser : l'écran annonce ce qui serait créé, mis à jour,
 * écarté ou refusé, et attend une confirmation. L'écriture, elle, se poursuit côté serveur — la
 * page suit l'avancement et peut être quittée sans l'interrompre.</p>
 */
export function AdminSupplierDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const [fournisseur, setFournisseur] = useState<Supplier | null>(null);
  const [imports, setImports] = useState<CatalogImport[]>([]);
  const [enCours, setEnCours] = useState<CatalogImport | null>(null);
  const [chargement, setChargement] = useState(true);
  const [envoi, setEnvoi] = useState(false);
  const [action, setAction] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);
  const champFichier = useRef<HTMLInputElement>(null);

  const charger = useCallback(async () => {
    try {
      const [f, liste] = await Promise.all([adminSupplierService.get(id), adminSupplierService.historique(id)]);
      setFournisseur(f);
      setImports(liste);
      // Un import en attente de confirmation ou en cours d'écriture reprend la main sur l'écran.
      setEnCours(liste.find((i) => i.status === 'PRET' || i.status === 'IMPORT') ?? null);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Fournisseur introuvable.', type: 'error' });
    } finally {
      setChargement(false);
    }
  }, [id]);

  useEffect(() => { charger(); }, [charger]);

  // Pendant l'écriture, l'avancement est relu toutes les deux secondes, puis on s'arrête.
  useEffect(() => {
    if (enCours?.status !== 'IMPORT') return;
    const minuteur = setInterval(async () => {
      try {
        const maj = await adminSupplierService.suivre(enCours.id);
        setEnCours(maj);
        if (maj.status !== 'IMPORT') {
          clearInterval(minuteur);
          charger();
        }
      } catch {
        clearInterval(minuteur);
      }
    }, 2000);
    return () => clearInterval(minuteur);
  }, [enCours?.id, enCours?.status, charger]);

  const deposer = async (fichier: File) => {
    setEnvoi(true);
    try {
      setEnCours(await adminSupplierService.analyser(id, fichier));
      setToast({ message: 'Fichier analysé : vérifiez le résumé avant de confirmer.', type: 'success' });
      if (champFichier.current) champFichier.current.value = '';
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || "Le fichier n'a pas pu être analysé.", type: 'error' });
    } finally {
      setEnvoi(false);
    }
  };

  const confirmer = async () => {
    if (!enCours) return;
    setAction(true);
    try {
      setEnCours(await adminSupplierService.confirmer(enCours.id));
      setToast({ message: "L'import est lancé. Vous pouvez quitter cette page.", type: 'success' });
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Import impossible.', type: 'error' });
    } finally {
      setAction(false);
    }
  };

  const abandonner = async () => {
    if (!enCours) return;
    setAction(true);
    try {
      await adminSupplierService.annuler(enCours.id);
      setEnCours(null);
      await charger();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Action impossible.', type: 'error' });
    } finally {
      setAction(false);
    }
  };

  if (chargement) {
    return (
      <div className="p-8 text-center text-slate-500 text-sm">
        <Loader2 className="w-6 h-6 animate-spin mx-auto mb-3 text-brand" aria-label="Chargement" />
      </div>
    );
  }
  if (!fournisseur) {
    return <div className="p-4 sm:p-6 lg:p-8"><EmptyState icon={AlertTriangle} title="Fournisseur introuvable" /></div>;
  }

  const progression = enCours && enCours.totalRows > 0
    ? Math.round((enCours.processedRows / enCours.totalRows) * 100) : 0;

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-5xl">
      <Link to="/admin/suppliers" className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-6">
        <ArrowLeft className="w-4 h-4 mr-2" aria-hidden="true" /> Retour aux fournisseurs
      </Link>

      <header className="bg-white rounded-2xl border border-slate-200 p-4 sm:p-6 mb-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-2xl font-bold text-brand-dark">{fournisseur.companyName}</h1>
            <p className="font-mono text-xs text-slate-500 mt-1">
              {fournisseur.code}{fournisseur.taxId ? ` · ${fournisseur.taxId}` : ''}
            </p>
            <p className="text-sm text-slate-600 mt-2">
              {fournisseur.contactName || 'Contact non renseigné'}
              {fournisseur.contactEmail && ` · ${fournisseur.contactEmail}`}
              {fournisseur.contactPhone && ` · ${fournisseur.contactPhone}`}
            </p>
          </div>
          <dl className="flex gap-6">
            <div>
              <dt className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">Produits</dt>
              <dd className="text-2xl font-bold text-brand-dark tabular-nums">{fournisseur.productCount}</dd>
            </div>
            <div>
              <dt className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">Commission</dt>
              <dd className="text-2xl font-bold text-brand-dark tabular-nums">{fournisseur.commissionRate} %</dd>
            </div>
          </dl>
        </div>
        {fournisseur.notes && <p className="mt-4 text-sm text-slate-600 whitespace-pre-line">{fournisseur.notes}</p>}
      </header>

      {/* ── Dépôt d'un catalogue ─────────────────────────────────────────────── */}
      <section className="bg-white rounded-2xl border border-slate-200 overflow-hidden mb-6" aria-labelledby="import-titre">
        <div className="px-4 sm:px-6 py-4 border-b border-slate-100 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 id="import-titre" className="text-lg font-bold text-brand-dark">Importer un catalogue</h2>
            <p className="text-sm text-slate-500">
              Fichier CSV ou Excel, jusqu'à 20 000 lignes. Les images sont téléchargées depuis les adresses du fichier.
            </p>
          </div>
          <button
            type="button"
            onClick={() => downloadApiFile(adminSupplierService.templateUrl(), 'modele-catalogue-optimi-sante.csv')}
            className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-300 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            <Download className="w-4 h-4" aria-hidden="true" /> Modèle à envoyer au fournisseur
          </button>
        </div>

        {!enCours ? (
          <div className="p-4 sm:p-6">
            <label
              htmlFor="fichier-catalogue"
              className="flex flex-col items-center justify-center gap-2 border-2 border-dashed border-slate-300 rounded-xl p-8 text-center cursor-pointer hover:border-brand hover:bg-brand-light/30 transition-colors"
            >
              {envoi ? (
                <>
                  <Loader2 className="w-8 h-8 text-brand animate-spin" aria-hidden="true" />
                  <span className="text-sm font-semibold text-brand-dark">Analyse du fichier…</span>
                </>
              ) : (
                <>
                  <Upload className="w-8 h-8 text-slate-400" aria-hidden="true" />
                  <span className="text-sm font-semibold text-brand-dark">Choisir un fichier CSV ou Excel</span>
                  <span className="text-xs text-slate-500">
                    Rien n'est écrit au catalogue : le fichier est d'abord analysé.
                  </span>
                </>
              )}
              <input
                id="fichier-catalogue" ref={champFichier} type="file" className="sr-only"
                accept=".csv,.xlsx,.xls,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                disabled={envoi || !fournisseur.active}
                onChange={(e) => { const f = e.target.files?.[0]; if (f) deposer(f); }}
              />
            </label>
            {!fournisseur.active && (
              <p className="mt-3 text-sm text-warning">
                Ce fournisseur est inactif : réactivez-le pour importer son catalogue.
              </p>
            )}
          </div>
        ) : (
          <div className="p-4 sm:p-6 space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2 min-w-0">
                <FileSpreadsheet className="w-5 h-5 text-brand shrink-0" aria-hidden="true" />
                <span className="font-semibold text-brand-dark truncate">{enCours.fileName}</span>
              </div>
              <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-brand-light text-brand">
                {IMPORT_STATUS_LABELS[enCours.status]}
              </span>
            </div>

            <dl className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {[
                { label: 'À créer', valeur: enCours.toCreate, couleur: 'text-success' },
                { label: 'À mettre à jour', valeur: enCours.toUpdate, couleur: 'text-brand' },
                { label: 'Écartées', valeur: enCours.ignoredRows, couleur: 'text-slate-600' },
                { label: 'Refusées', valeur: enCours.errorRows, couleur: enCours.errorRows > 0 ? 'text-danger' : 'text-slate-600' },
              ].map((k) => (
                <div key={k.label} className="rounded-lg bg-slate-50 px-3 py-2.5">
                  <dt className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">{k.label}</dt>
                  <dd className={`text-xl font-bold tabular-nums ${k.couleur}`}>{k.valeur}</dd>
                </div>
              ))}
            </dl>

            {enCours.status === 'IMPORT' && (
              <div>
                <div className="flex justify-between text-sm text-slate-600 mb-1.5">
                  <span>{enCours.processedRows} ligne(s) sur {enCours.totalRows}</span>
                  <span className="tabular-nums">{progression} %</span>
                </div>
                <div className="h-2 rounded-full bg-slate-200 overflow-hidden">
                  <div className="h-full rounded-full bg-brand transition-all duration-500" style={{ width: `${progression}%` }} />
                </div>
                <p className="text-xs text-slate-500 mt-2">
                  {enCours.createdCount} créé(s) · {enCours.updatedCount} mis à jour · {enCours.imageCount} visuel(s).
                  L'import se poursuit même si vous quittez cette page.
                </p>
              </div>
            )}

            {enCours.marginSummary && (
              <p className="text-sm text-slate-600">
                <span className="font-semibold text-brand-dark">Prix de vente :</span> {enCours.marginSummary}.
              </p>
            )}

            {enCours.motifs.length > 0 && (
              <details className="rounded-lg border border-slate-200">
                <summary className="px-3 py-2 text-sm font-medium text-slate-700 cursor-pointer">
                  Lignes écartées ou refusées ({enCours.motifs.length}{enCours.motifsTronques ? '+' : ''})
                </summary>
                <ul className="px-4 py-2 max-h-56 overflow-y-auto text-xs text-slate-600 space-y-1">
                  {enCours.motifs.map((m) => <li key={m}>{m}</li>)}
                  {enCours.motifsTronques && <li className="text-slate-400">… liste tronquée.</li>}
                </ul>
              </details>
            )}

            {enCours.status === 'PRET' && (
              <div className="flex flex-col sm:flex-row gap-2">
                <button
                  type="button" onClick={confirmer} disabled={action || enCours.toCreate + enCours.toUpdate === 0}
                  className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg bg-brand text-white text-sm font-semibold hover:bg-brand-fonce disabled:opacity-60"
                >
                  {action ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" /> : <CheckCircle2 className="w-4 h-4" aria-hidden="true" />}
                  Écrire {enCours.toCreate + enCours.toUpdate} produit(s) au catalogue
                </button>
                <button
                  type="button" onClick={abandonner} disabled={action}
                  className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg border border-slate-300 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                >
                  <XCircle className="w-4 h-4" aria-hidden="true" /> Abandonner ce fichier
                </button>
              </div>
            )}
          </div>
        )}
      </section>

      {/* ── Historique ───────────────────────────────────────────────────────── */}
      <section className="bg-white rounded-2xl border border-slate-200 overflow-hidden" aria-labelledby="historique-titre">
        <div className="px-4 sm:px-6 py-4 border-b border-slate-100">
          <h2 id="historique-titre" className="text-lg font-bold text-brand-dark">Historique des imports</h2>
        </div>
        {imports.length === 0 ? (
          <EmptyState icon={FileSpreadsheet} title="Aucun import" description="Les fichiers déposés apparaîtront ici." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Fichier</th>
                  <th className="px-4 py-3 font-medium">Déposé le</th>
                  <th className="px-4 py-3 font-medium text-right">Lignes</th>
                  <th className="px-4 py-3 font-medium text-right">Résultat</th>
                  <th className="px-4 py-3 font-medium">État</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {imports.map((i) => (
                  <tr key={i.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 text-slate-700 max-w-[16rem] truncate" title={i.fileName}>{i.fileName}</td>
                    <td className="px-4 py-3 text-slate-500 whitespace-nowrap">{formatDate(i.createdAt)}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-slate-600">{i.totalRows}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-slate-600 whitespace-nowrap">
                      {i.status === 'TERMINE'
                        ? `${i.createdCount} créé(s) · ${i.updatedCount} maj`
                        : i.status === 'ECHEC' ? (i.failureReason ?? 'Échec') : '—'}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex px-2.5 py-1 rounded-full text-xs font-semibold ${
                        i.status === 'TERMINE' ? 'bg-success/10 text-success'
                          : i.status === 'ECHEC' ? 'bg-danger/10 text-danger'
                            : i.status === 'IMPORT' || i.status === 'PRET' ? 'bg-brand-light text-brand'
                              : 'bg-slate-100 text-slate-600'}`}>
                        {IMPORT_STATUS_LABELS[i.status]}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
