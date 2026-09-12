import { useCallback, useEffect, useState } from 'react';
import { Plus, Loader2, CheckCircle2, XCircle, Clock, Ban, AlertTriangle, X } from 'lucide-react';
import {
  documentRequestService, documentTypeLabel, DOCUMENT_TYPES,
  type DossierSummary, type DocumentRequestView, type DocumentTypeValue,
} from '../../api/documentRequestService';
import { vaultService } from '../../api/vaultService';
import { DossierProgress } from './DossierProgress';
import { Toast, type ToastType } from '../common/Toast';
import { DocumentButton } from '../../components/documents/DocumentButton';

/** Rendu d'un état de demande : couleur, icône et libellé au même endroit. */
const ETATS: Record<string, { label: string; classe: string; Icone: typeof Clock }> = {
  PENDING:   { label: 'En attente du médecin', classe: 'bg-amber-50 text-amber-700 border-amber-200', Icone: Clock },
  SUBMITTED: { label: 'À vérifier',            classe: 'bg-blue-50 text-blue-700 border-blue-200',    Icone: Clock },
  ACCEPTED:  { label: 'Validée',               classe: 'bg-emerald-50 text-emerald-700 border-emerald-200', Icone: CheckCircle2 },
  REJECTED:  { label: 'Refusée',               classe: 'bg-rose-50 text-rose-700 border-rose-200',    Icone: XCircle },
  CANCELLED: { label: 'Retirée',               classe: 'bg-slate-100 text-slate-500 border-slate-200', Icone: Ban },
};

/**
 * Espace de constitution du dossier, côté administration : réclamer une pièce, voir ce que le
 * candidat a déposé, valider ou refuser.
 *
 * Distinct du « coffre-fort documentaire » de la même page, qui liste les fichiers présents.
 * Ici on suit ce qui est **attendu** — la différence entre « voici ce qu'on a » et « voici ce
 * qu'il manque » est précisément ce qui manquait pour préparer une demande de visa.
 */
export function AdminDocumentRequestsPanel({ enrollmentId }: { enrollmentId: string }) {
  const [summary, setSummary] = useState<DossierSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [formOuvert, setFormOuvert] = useState(false);
  const [documentType, setDocumentType] = useState<DocumentTypeValue>('OTHER');
  const [label, setLabel] = useState('');
  const [instructions, setInstructions] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const charger = useCallback(async () => {
    try {
      setSummary(await documentRequestService.listForAdmin(enrollmentId));
    } catch (err) {
      console.error('Chargement des pièces réclamées impossible', err);
      setToast({ message: 'Impossible de charger les pièces du dossier.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [enrollmentId]);

  useEffect(() => { charger(); }, [charger]);

  const messageErreur = (err: any, defaut: string) =>
    err?.response?.data?.message ?? defaut;

  const reclamer = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {
      await documentRequestService.create(enrollmentId, {
        documentType,
        label: label.trim(),
        instructions: instructions.trim() || undefined,
        dueDate: dueDate || undefined,
      });
      setLabel(''); setInstructions(''); setDueDate(''); setDocumentType('OTHER');
      setFormOuvert(false);
      setToast({ message: 'Pièce réclamée au candidat.', type: 'success' });
      await charger();
    } catch (err) {
      // Le serveur explique déjà pourquoi (doublon, champ manquant) : son message vaut mieux
      // qu'un texte générique qui obligerait l'utilisateur à deviner.
      setToast({ message: messageErreur(err, 'La demande n\'a pas pu être enregistrée.'), type: 'error' });
    } finally {
      setIsSaving(false);
    }
  };

  const decider = async (demande: DocumentRequestView, accepted: boolean) => {
    let motif: string | undefined;
    if (!accepted) {
      const saisi = window.prompt(
        `Pourquoi refusez-vous « ${demande.label} » ?\n\n` +
        'Le médecin lira ce motif : sans lui, il redéposera la même pièce.');
      if (saisi === null) return;            // annulation, on ne touche à rien
      if (!saisi.trim()) {
        setToast({ message: 'Un motif est obligatoire pour refuser une pièce.', type: 'error' });
        return;
      }
      motif = saisi.trim();
    }
    setBusyId(demande.id);
    try {
      await documentRequestService.review(demande.id, accepted, motif);
      setToast({ message: accepted ? 'Pièce validée.' : 'Pièce refusée, le médecin est informé.', type: 'success' });
      await charger();
    } catch (err) {
      setToast({ message: messageErreur(err, 'La décision n\'a pas pu être enregistrée.'), type: 'error' });
    } finally {
      setBusyId(null);
    }
  };

  const retirer = async (demande: DocumentRequestView) => {
    if (!window.confirm(`Retirer la demande « ${demande.label} » ? Elle ne sera plus attendue.`)) return;
    setBusyId(demande.id);
    try {
      await documentRequestService.cancel(demande.id);
      setToast({ message: 'Demande retirée.', type: 'success' });
      await charger();
    } catch (err) {
      setToast({ message: messageErreur(err, 'La demande n\'a pas pu être retirée.'), type: 'error' });
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="bg-white rounded-3xl shadow-sm border border-slate-200 overflow-hidden">
      <div className="px-8 py-6 border-b border-slate-100 flex items-start justify-between gap-4">
        <div className="min-w-0">
          <h2 className="text-xl font-bold text-brand-dark">Pièces du dossier</h2>
          <p className="text-sm text-slate-500 mt-0.5">
            Ce que vous réclamez au candidat, et ce qu'il a déposé.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setFormOuvert((v) => !v)}
          className="inline-flex items-center gap-1.5 px-4 py-2 bg-brand text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors text-sm shrink-0"
        >
          {formOuvert ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
          {formOuvert ? 'Fermer' : 'Réclamer une pièce'}
        </button>
      </div>

      {formOuvert && (
        <form onSubmit={reclamer} className="px-8 py-6 bg-slate-50 border-b border-slate-100 space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="sm:col-span-2">
              <label htmlFor="dr-label" className="block text-xs font-semibold text-slate-600 mb-1">
                Pièce attendue <span className="text-rose-600">*</span>
              </label>
              {/* Le libellé prime sur le type : c'est ce que le médecin lira. « Autre pièce »
                  ne lui apprend pas qu'on attend un acte de naissance traduit. */}
              <input
                id="dr-label" required maxLength={255} value={label}
                onChange={(e) => setLabel(e.target.value)}
                placeholder="Ex. : Acte de naissance traduit en français"
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-brand focus:ring-1 focus:ring-brand outline-none"
              />
            </div>
            <div>
              <label htmlFor="dr-type" className="block text-xs font-semibold text-slate-600 mb-1">
                Classement
              </label>
              <select
                id="dr-type" value={documentType}
                onChange={(e) => setDocumentType(e.target.value as DocumentTypeValue)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 bg-white focus:border-brand focus:ring-1 focus:ring-brand outline-none"
              >
                {DOCUMENT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="sm:col-span-2">
              <label htmlFor="dr-instr" className="block text-xs font-semibold text-slate-600 mb-1">
                Précisions (facultatif)
              </label>
              <textarea
                id="dr-instr" rows={2} maxLength={4000} value={instructions}
                onChange={(e) => setInstructions(e.target.value)}
                placeholder="Traduction assermentée, datée de moins de 3 mois…"
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-brand focus:ring-1 focus:ring-brand outline-none"
              />
            </div>
            <div>
              <label htmlFor="dr-due" className="block text-xs font-semibold text-slate-600 mb-1">
                À fournir avant le
              </label>
              <input
                id="dr-due" type="date" value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-brand focus:ring-1 focus:ring-brand outline-none"
              />
            </div>
          </div>

          <div className="flex justify-end">
            <button
              type="submit" disabled={isSaving || !label.trim()}
              className="inline-flex items-center gap-2 px-4 py-2 bg-brand-dark text-white font-bold rounded-xl text-sm disabled:opacity-50 transition-colors"
            >
              {isSaving && <Loader2 className="w-4 h-4 animate-spin" />}
              Réclamer cette pièce
            </button>
          </div>
        </form>
      )}

      <div className="px-8 py-6">
        {isLoading ? (
          <div className="py-6 flex justify-center"><Loader2 className="w-6 h-6 animate-spin text-brand" /></div>
        ) : (
          <>
            <DossierProgress summary={summary!} />

            {summary!.requests.length > 0 && (
              <ul className="mt-6 space-y-3">
                {summary!.requests.map((d) => {
                  const etat = ETATS[d.status];
                  const enCours = busyId === d.id;
                  return (
                    <li
                      key={d.id}
                      className={`rounded-2xl border p-4 ${d.status === 'CANCELLED' ? 'border-slate-200 bg-slate-50/60' : 'border-slate-200'}`}
                    >
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className={`font-semibold text-brand-dark ${d.status === 'CANCELLED' ? 'line-through text-slate-400' : ''}`}>
                              {d.label}
                            </span>
                            <span className="text-[10px] font-bold uppercase tracking-wide text-slate-400">
                              {documentTypeLabel(d.documentType)}
                            </span>
                          </div>
                          {d.instructions && (
                            <p className="text-xs text-slate-500 mt-1">{d.instructions}</p>
                          )}
                          {d.dueDate && d.open && (
                            <p className="text-xs text-slate-500 mt-1">
                              À fournir avant le {new Date(d.dueDate).toLocaleDateString('fr-FR')}
                            </p>
                          )}
                          {d.status === 'REJECTED' && d.rejectionReason && (
                            <p className="mt-2 text-xs text-rose-700 bg-rose-50 border border-rose-200 rounded-lg px-3 py-2 flex items-start gap-1.5">
                              <AlertTriangle className="w-3.5 h-3.5 mt-0.5 shrink-0" />
                              <span>Refusée : {d.rejectionReason}</span>
                            </p>
                          )}
                        </div>

                        <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[11px] font-bold shrink-0 ${etat.classe}`}>
                          <etat.Icone className="w-3.5 h-3.5" /> {etat.label}
                        </span>
                      </div>

                      <div className="flex flex-wrap items-center gap-2 mt-3">
                        {d.documentId && (
                          <DocumentButton
                            libelle="Voir la pièce"
                            obtenirLien={() => vaultService.getPresignedUrl(d.documentType, d.documentId!)}
                            className="px-3 py-1.5 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50"
                          />
                        )}
                        {d.status === 'SUBMITTED' && (
                          <>
                            <button
                              type="button" disabled={enCours} onClick={() => decider(d, true)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600 text-white text-xs font-bold hover:bg-emerald-700 disabled:opacity-50 transition-colors"
                            >
                              {enCours ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <CheckCircle2 className="w-3.5 h-3.5" />}
                              Valider
                            </button>
                            <button
                              type="button" disabled={enCours} onClick={() => decider(d, false)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-100 text-rose-700 text-xs font-bold hover:bg-rose-200 disabled:opacity-50 transition-colors"
                            >
                              <XCircle className="w-3.5 h-3.5" /> Refuser
                            </button>
                          </>
                        )}
                        {/* Une pièce validée fait partie du dossier : elle ne se retire plus. */}
                        {(d.status === 'PENDING' || d.status === 'SUBMITTED' || d.status === 'REJECTED') && (
                          <button
                            type="button" disabled={enCours} onClick={() => retirer(d)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-500 hover:text-rose-700 disabled:opacity-50 transition-colors"
                          >
                            <Ban className="w-3.5 h-3.5" /> Retirer la demande
                          </button>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </>
        )}
      </div>

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
