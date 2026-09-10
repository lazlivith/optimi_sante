import { useCallback, useEffect, useState } from 'react';
import {
  Loader2, CalendarClock, Send, XCircle, CheckCircle2, Video, Info,
} from 'lucide-react';
import {
  interviewService, formatCreneau, estPasse,
  INTERVIEW_STATUS_LABELS, type InterviewSchedule,
} from '../../api/interviewService';
import { Toast, type ToastType } from '../common/Toast';

const COULEUR_STATUT: Record<string, string> = {
  PROPOSED: 'bg-amber-100 text-amber-800',
  TRANSMITTED: 'bg-sky-100 text-sky-800',
  CONFIRMED: 'bg-emerald-100 text-emerald-800',
  CANCELLED: 'bg-slate-100 text-slate-600',
};

/**
 * Entretien de sélection en visioconférence, côté administration : transmettre le lien et les
 * créneaux au médecin, ou annuler.
 *
 * <p>La transmission est le geste central du circuit — c'est lui, et lui seul, qui rend les
 * créneaux visibles au médecin. Tant qu'il n'a pas eu lieu, la proposition du CHU reste un
 * échange interne entre l'établissement et l'agence.</p>
 */
export function AdminInterviewPanel({ enrollmentId }: { enrollmentId: string }) {
  const [entretiens, setEntretiens] = useState<InterviewSchedule[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [noteAdmin, setNoteAdmin] = useState<Record<string, string>>({});
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const charger = useCallback(async () => {
    try {
      setEntretiens(await interviewService.historyForAdmin(enrollmentId));
    } catch (err) {
      console.error('Chargement des entretiens impossible', err);
    } finally {
      setIsLoading(false);
    }
  }, [enrollmentId]);

  useEffect(() => { charger(); }, [charger]);

  const transmettre = async (e: InterviewSchedule) => {
    setBusyId(e.id);
    try {
      const maj = await interviewService.transmit(e.id, noteAdmin[e.id]?.trim() || null);
      setEntretiens((prev) => prev.map((x) => (x.id === e.id ? maj : x)));
      setToast({ message: 'Créneaux et lien transmis au médecin. Il a été prévenu par email.', type: 'success' });
    } catch (err: any) {
      setToast({ message: err?.response?.data?.message ?? 'La transmission a échoué.', type: 'error' });
    } finally {
      setBusyId(null);
    }
  };

  const annuler = async (e: InterviewSchedule) => {
    const motif = window.prompt(
      "Motif de l'annulation de cet entretien.\n\n"
      + "Il reste dans l'historique du dossier, et l'établissement pourra proposer de "
      + 'nouveaux créneaux.',
    );
    if (!motif || !motif.trim()) return;

    setBusyId(e.id);
    try {
      const maj = await interviewService.cancel(e.id, motif.trim());
      setEntretiens((prev) => prev.map((x) => (x.id === e.id ? maj : x)));
      setToast({ message: 'Entretien annulé.', type: 'success' });
    } catch (err: any) {
      setToast({ message: err?.response?.data?.message ?? "L'annulation a échoué.", type: 'error' });
    } finally {
      setBusyId(null);
    }
  };

  if (isLoading) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 p-8 flex justify-center">
        <Loader2 className="w-6 h-6 text-brand-green animate-spin" />
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      <div className="px-6 py-5 border-b border-slate-100 bg-slate-50">
        <h2 className="inline-flex items-center gap-2 font-bold text-brand-dark">
          <CalendarClock className="w-5 h-5 text-brand-green" />
          Entretien de sélection
        </h2>
        <p className="text-sm text-slate-500 mt-0.5">
          L'établissement propose le lien et les créneaux, vous transmettez, le médecin choisit.
        </p>
      </div>

      {entretiens.length === 0 ? (
        <p className="px-6 py-8 text-sm text-slate-500 text-center">
          Aucune visioconférence proposée sur ce dossier.
        </p>
      ) : (
        <div className="divide-y divide-slate-100">
          {entretiens.map((e) => {
            const retenu = e.slots.find((s) => s.confirmed);
            const enCours = busyId === e.id;

            return (
              <div key={e.id} className="px-6 py-5 space-y-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="text-sm">
                    <span className="inline-flex items-center gap-2 font-semibold text-slate-800">
                      <Video className="w-4 h-4 text-slate-400" />
                      Visioconférence
                    </span>
                    {/* Le lien est montré en entier : c'est ce que l'administration vérifie
                        avant de transmettre — un lien tronqué ne se contrôle pas. */}
                    <a
                      href={e.meetingLink}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="block text-brand-green underline mt-1 break-all"
                    >
                      {e.meetingLink}
                    </a>
                    <p className="text-xs text-slate-400 mt-1">
                      Proposé par {e.partnerInstitutionName} le{' '}
                      {new Date(e.proposedAt).toLocaleDateString('fr-FR')}
                    </p>
                  </div>
                  <span className={`px-3 py-1 rounded-full text-xs font-bold whitespace-nowrap ${COULEUR_STATUT[e.status]}`}>
                    {INTERVIEW_STATUS_LABELS[e.status]}
                  </span>
                </div>

                {e.partnerNote && (
                  <div className="rounded-xl bg-slate-50 border border-slate-200 p-3">
                    <p className="inline-flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                      <Info className="w-3 h-3" /> Consignes de l'établissement
                    </p>
                    <p className="text-sm text-slate-700 mt-1 whitespace-pre-line">{e.partnerNote}</p>
                  </div>
                )}

                <ul className="space-y-1.5">
                  {e.slots.map((s) => {
                    const retenuEtAnnule = s.confirmed && e.status === 'CANCELLED';
                    return (
                      <li
                        key={s.id}
                        className={`text-sm flex items-center gap-2 ${
                          retenuEtAnnule ? 'text-slate-500'
                            : s.confirmed ? 'font-semibold text-emerald-700'
                            : estPasse(s) ? 'text-slate-400 line-through' : 'text-slate-700'
                        }`}
                      >
                        {s.confirmed && !retenuEtAnnule && <CheckCircle2 className="w-4 h-4 shrink-0" />}
                        {formatCreneau(s)}
                        {/* Un créneau dépassé reste affiché : il dit ce que le CHU avait offert. */}
                        {!s.confirmed && estPasse(s) && (
                          <span className="text-xs no-underline">(dépassé)</span>
                        )}
                        {retenuEtAnnule && (
                          <span className="text-xs italic">— avait été retenu</span>
                        )}
                      </li>
                    );
                  })}
                </ul>

                {e.status === 'PROPOSED' && (
                  <div className="space-y-3 pt-1">
                    <div>
                      <label htmlFor={`note-${e.id}`} className="block text-xs font-semibold text-slate-600 mb-1">
                        Précision à ajouter pour le médecin (facultatif)
                      </label>
                      <textarea
                        id={`note-${e.id}`}
                        rows={2}
                        value={noteAdmin[e.id] ?? ''}
                        onChange={(ev) => setNoteAdmin((p) => ({ ...p, [e.id]: ev.target.value }))}
                        placeholder="Ex. : connectez-vous 5 minutes à l'avance."
                        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
                      />
                    </div>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        disabled={enCours}
                        onClick={() => transmettre(e)}
                        className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-brand-green text-white text-sm font-bold hover:bg-[#0f3c35] disabled:opacity-50 transition-colors"
                      >
                        {enCours ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                        Transmettre au médecin
                      </button>
                      <BoutonAnnuler onClick={() => annuler(e)} disabled={enCours} />
                    </div>
                  </div>
                )}

                {e.status === 'TRANSMITTED' && (
                  <div className="flex items-center justify-between gap-4 pt-1">
                    <p className="text-sm text-slate-500">
                      Transmis le {new Date(e.transmittedAt!).toLocaleDateString('fr-FR')} —
                      en attente du choix du médecin.
                    </p>
                    <BoutonAnnuler onClick={() => annuler(e)} disabled={enCours} />
                  </div>
                )}

                {e.status === 'CONFIRMED' && retenu && (
                  <div className="flex items-center justify-between gap-4 pt-1">
                    <p className="text-sm text-emerald-700">
                      Confirmé par le médecin. Convocation visio déposée au dossier.
                    </p>
                    <BoutonAnnuler onClick={() => annuler(e)} disabled={enCours} />
                  </div>
                )}

                {e.status === 'CANCELLED' && e.cancelledReason && (
                  <p className="text-sm text-slate-500 italic">Motif : {e.cancelledReason}</p>
                )}
              </div>
            );
          })}
        </div>
      )}

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}

function BoutonAnnuler({ onClick, disabled }: { onClick: () => void; disabled: boolean }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-slate-300 text-slate-600 text-sm font-semibold hover:bg-slate-50 disabled:opacity-50 transition-colors"
    >
      <XCircle className="w-4 h-4" />
      Annuler
    </button>
  );
}
