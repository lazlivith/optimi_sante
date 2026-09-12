import { useCallback, useEffect, useRef, useState } from 'react';
import { Loader2, CheckCircle2, Clock, UploadCloud, AlertTriangle, FolderCheck } from 'lucide-react';
import {
  documentRequestService, documentTypeLabel,
  type DossierSummary, type DocumentRequestView,
} from '../../api/documentRequestService';
import { vaultService } from '../../api/vaultService';
import { DossierProgress } from './DossierProgress';
import { Toast, type ToastType } from '../common/Toast';
import { DocumentButton } from '../../components/documents/DocumentButton';

/**
 * « Mon dossier visa », côté médecin : ce qu'on lui réclame, ce qu'il a déposé, ce qui a été
 * validé.
 *
 * Placé sur le détail de la candidature et non dans une entrée de menu à part : les pièces
 * appartiennent à un dossier précis, et un médecin peut en avoir plusieurs. Une page
 * « Mon dossier visa » au niveau du menu poserait aussitôt la question « lequel ? ».
 */
export function MyVisaDossierPanel({ enrollmentId }: { enrollmentId: string }) {
  const [summary, setSummary] = useState<DossierSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);
  const inputsRef = useRef<Record<string, HTMLInputElement | null>>({});

  const charger = useCallback(async () => {
    try {
      setSummary(await documentRequestService.listForDoctor(enrollmentId));
    } catch (err) {
      console.error('Chargement du dossier de pièces impossible', err);
      setToast({ message: 'Impossible de charger vos pièces.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [enrollmentId]);

  useEffect(() => { charger(); }, [charger]);

  const deposer = async (demande: DocumentRequestView, file: File) => {
    setBusyId(demande.id);
    try {
      await documentRequestService.fulfil(demande.id, file);
      setToast({ message: 'Pièce envoyée. Elle sera vérifiée par OptimiSanté.', type: 'success' });
      await charger();
    } catch (err: any) {
      setToast({
        message: err?.response?.data?.message ?? "L'envoi du fichier a échoué.",
        type: 'error',
      });
    } finally {
      setBusyId(null);
    }
  };

  if (isLoading) {
    return (
      <div className="bg-white rounded-3xl shadow-sm border border-slate-200 p-8 flex justify-center">
        <Loader2 className="w-6 h-6 animate-spin text-brand" />
      </div>
    );
  }

  // Rien n'est réclamé : plutôt qu'une section vide, on le dit — l'absence de demande est une
  // information rassurante, pas un écran qui n'a pas fini de charger.
  if (!summary || summary.total === 0) {
    return (
      <div className="bg-white rounded-3xl shadow-sm border border-slate-200 p-8">
        <h2 className="text-xl font-bold text-brand-dark mb-2">Mon dossier visa</h2>
        <p className="text-sm text-slate-500">
          Aucune pièce ne vous est demandée pour l'instant. Les documents nécessaires à votre
          demande de visa vous seront réclamés ici au fur et à mesure de la procédure.
        </p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-3xl shadow-sm border border-slate-200 overflow-hidden">
      <div className="px-8 py-6 border-b border-slate-100">
        <h2 className="text-xl font-bold text-brand-dark flex items-center gap-2">
          <FolderCheck className="w-5 h-5 text-brand" /> Mon dossier visa
        </h2>
        <p className="text-sm text-slate-500 mt-0.5">
          Les pièces réunies ici constitueront votre dossier de demande de visa.
        </p>
      </div>

      <div className="px-8 py-6">
        <DossierProgress summary={summary} />

        <ul className="mt-6 space-y-3">
          {summary.requests.map((d) => {
            const enCours = busyId === d.id;
            // Le médecin doit agir tant que la pièce n'est ni déposée ni validée. Un refus le
            // ramène ici : c'est le seul état où l'on réaffiche un bouton d'envoi.
            const aFournir = d.status === 'PENDING' || d.status === 'REJECTED';

            return (
              <li key={d.id} className={`rounded-2xl border p-4 ${
                d.status === 'ACCEPTED' ? 'border-emerald-200 bg-emerald-50/40'
                : d.status === 'REJECTED' ? 'border-rose-200 bg-rose-50/40'
                : 'border-slate-200'}`}>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-semibold text-brand-dark">{d.label}</span>
                      <span className="text-[10px] font-bold uppercase tracking-wide text-slate-400">
                        {documentTypeLabel(d.documentType)}
                      </span>
                    </div>
                    {d.instructions && <p className="text-xs text-slate-600 mt-1">{d.instructions}</p>}
                    {d.dueDate && aFournir && (
                      <p className="text-xs text-amber-700 font-medium mt-1">
                        À fournir avant le {new Date(d.dueDate).toLocaleDateString('fr-FR')}
                      </p>
                    )}
                    {d.status === 'REJECTED' && d.rejectionReason && (
                      <p className="mt-2 text-xs text-rose-800 bg-rose-100 border border-rose-200 rounded-lg px-3 py-2 flex items-start gap-1.5">
                        <AlertTriangle className="w-3.5 h-3.5 mt-0.5 shrink-0" />
                        <span><strong>À refaire :</strong> {d.rejectionReason}</span>
                      </p>
                    )}
                  </div>

                  <div className="shrink-0">
                    {d.status === 'ACCEPTED' && (
                      <span className="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-700">
                        <CheckCircle2 className="w-4 h-4" /> Validée
                      </span>
                    )}
                    {d.status === 'SUBMITTED' && (
                      <span className="inline-flex items-center gap-1.5 text-xs font-bold text-blue-700">
                        <Clock className="w-4 h-4" /> En cours de vérification
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2 mt-3">
                  {aFournir && (
                    <>
                      <input
                        type="file"
                        ref={(el) => { inputsRef.current[d.id] = el; }}
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (file) deposer(d, file);
                          e.target.value = '';   // permet de redéposer le même fichier
                        }}
                        className="hidden"
                        aria-label={`Déposer le fichier pour ${d.label}`}
                      />
                      <button
                        type="button" disabled={enCours}
                        onClick={() => inputsRef.current[d.id]?.click()}
                        className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-brand text-white text-xs font-bold hover:bg-[#0f3c35] disabled:opacity-50 transition-colors"
                      >
                        {enCours ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <UploadCloud className="w-3.5 h-3.5" />}
                        {d.status === 'REJECTED' ? 'Déposer un nouveau fichier' : 'Déposer le fichier'}
                      </button>
                    </>
                  )}
                  {d.documentId && (
                    <DocumentButton
                      libelle="Voir mon envoi"
                      obtenirLien={() => vaultService.getPresignedUrl(d.documentType, d.documentId!)}
                      className="px-3 py-1.5 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50"
                    />
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      </div>

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
