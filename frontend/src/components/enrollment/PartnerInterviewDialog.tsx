import { useEffect, useState } from 'react';
import { Loader2, CalendarPlus, Plus, Trash2, X, Video } from 'lucide-react';
import {
  interviewService, formatCreneau, INTERVIEW_STATUS_LABELS, type InterviewSchedule,
} from '../../api/interviewService';
import { Toast, type ToastType } from '../common/Toast';

/** Un créneau en cours de saisie : dates locales, converties en instants à l'envoi. */
interface CreneauSaisi {
  jour: string;
  debut: string;
  fin: string;
}

const CRENEAU_VIDE: CreneauSaisi = { jour: '', debut: '09:00', fin: '10:00' };

/**
 * Proposition d'un entretien en visioconférence, côté établissement.
 *
 * <p>Le CHU dépose ici ses disponibilités. <b>Elles ne partent pas directement au médecin</b> :
 * Optimi Santé les transmet. Le texte de la fenêtre le dit explicitement, faute de quoi
 * l'établissement croirait avoir convoqué le candidat et s'étonnerait de son silence.</p>
 */
export function PartnerInterviewDialog({
  enrollmentId, doctorName, onClose,
}: {
  enrollmentId: string;
  doctorName: string;
  onClose: () => void;
}) {
  const [existants, setExistants] = useState<InterviewSchedule[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [lienReunion, setLienReunion] = useState('');
  const [note, setNote] = useState('');
  // Deux créneaux d'emblée : proposer un seul n'est pas un choix, et le serveur le refuse.
  const [creneaux, setCreneaux] = useState<CreneauSaisi[]>([{ ...CRENEAU_VIDE }, { ...CRENEAU_VIDE }]);
  const [envoiEnCours, setEnvoiEnCours] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  useEffect(() => {
    interviewService.historyForPartner(enrollmentId)
      .then(setExistants)
      .catch(() => setToast({ message: 'Historique des entretiens indisponible.', type: 'error' }))
      .finally(() => setIsLoading(false));
  }, [enrollmentId]);

  const actif = existants.find((e) => e.status !== 'CANCELLED');

  const majCreneau = (i: number, champ: keyof CreneauSaisi, valeur: string) =>
    setCreneaux((prev) => prev.map((c, j) => (j === i ? { ...c, [champ]: valeur } : c)));

  const envoyer = async () => {
    const remplis = creneaux.filter((c) => c.jour && c.debut && c.fin);
    if (remplis.length < 2) {
      setToast({ message: 'Proposez au moins deux créneaux complets.', type: 'error' });
      return;
    }
    if (!lienReunion.trim()) {
      setToast({
        message: "Indiquez le lien de la réunion en ligne (Teams, Meet, Zoom…).",
        type: 'error',
      });
      return;
    }

    setEnvoiEnCours(true);
    try {
      // Les champs de saisie donnent une heure locale ; `toISOString` la convertit en instant,
      // seule forme qui garde son sens une fois relue depuis un autre fuseau.
      const slots = remplis.map((c) => ({
        startsAt: new Date(`${c.jour}T${c.debut}`).toISOString(),
        endsAt: new Date(`${c.jour}T${c.fin}`).toISOString(),
      }));
      const cree = await interviewService.propose(enrollmentId, {
        meetingLink: lienReunion.trim(),
        partnerNote: note.trim() || null,
        slots,
      });
      setExistants((prev) => [cree, ...prev]);
      setToast({
        message: 'Créneaux et lien déposés. Optimi Santé les transmettra au médecin.',
        type: 'success',
      });
    } catch (err: any) {
      setToast({
        message: err?.response?.data?.message ?? 'La proposition a échoué.',
        type: 'error',
      });
    } finally {
      setEnvoiEnCours(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
      <div className="bg-white rounded-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto shadow-xl border border-slate-200">
        <div className="px-6 py-5 border-b border-slate-100 bg-slate-50 flex items-start justify-between gap-4 sticky top-0">
          <div>
            <h2 className="text-lg font-bold text-brand-dark">
              Proposer un entretien en visioconférence
            </h2>
            <p className="text-sm text-slate-500 mt-0.5">
              Candidature de {doctorName}
            </p>
          </div>
          <button type="button" onClick={onClose} className="p-1 rounded-lg hover:bg-slate-200 transition-colors">
            <X className="w-5 h-5 text-slate-500" />
          </button>
        </div>

        {isLoading ? (
          <div className="p-12 flex justify-center"><Loader2 className="w-6 h-6 text-brand-green animate-spin" /></div>
        ) : actif ? (
          // Le serveur refuse un second entretien vivant ; le dire ici évite de faire remplir
          // un formulaire dont on sait déjà qu'il sera rejeté.
          <div className="px-6 py-8 space-y-4">
            <p className="text-sm text-slate-700">
              Un entretien est déjà en cours sur ce dossier —{' '}
              <strong>{INTERVIEW_STATUS_LABELS[actif.status].toLowerCase()}</strong>.
            </p>
            <ul className="space-y-1 text-sm text-slate-600">
              {actif.slots.map((s) => (
                <li key={s.id} className={s.confirmed ? 'font-semibold text-emerald-700' : ''}>
                  {formatCreneau(s)}{s.confirmed && ' — retenu par le médecin'}
                </li>
              ))}
            </ul>
            <p className="text-sm text-slate-500">
              Pour en proposer un autre, demandez à Optimi Santé d'annuler celui-ci.
            </p>
          </div>
        ) : (
          <div className="px-6 py-5 space-y-5">
            <p className="inline-flex items-start gap-2 text-sm text-slate-600 bg-sky-50 border border-sky-200 rounded-xl p-3">
              <Video className="w-4 h-4 mt-0.5 shrink-0 text-sky-600" />
              <span>
                L'entretien se tient à distance. Vos créneaux et votre lien de réunion ne
                partent pas directement au médecin : Optimi Santé les lui transmet, puis vous
                informe du créneau retenu.
              </span>
            </p>

            <div>
              <label htmlFor="lien-reunion" className="block text-sm font-semibold text-slate-700 mb-1">
                Lien de la réunion en ligne
              </label>
              <input
                id="lien-reunion" type="url" value={lienReunion}
                onChange={(e) => setLienReunion(e.target.value)}
                placeholder="https://teams.microsoft.com/… · https://meet.google.com/… · https://zoom.us/j/…"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
              />
              <p className="text-xs text-slate-500 mt-1">
                Teams, Google Meet, Zoom — le lien que le médecin ouvrira le jour de l'entretien.
                Le même pour tous les créneaux proposés.
              </p>
            </div>

            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-semibold text-slate-700">
                  Créneaux proposés
                </label>
                <button
                  type="button"
                  onClick={() => setCreneaux((p) => [...p, { ...CRENEAU_VIDE }])}
                  className="inline-flex items-center gap-1 text-sm font-semibold text-brand-green hover:underline"
                >
                  <Plus className="w-4 h-4" /> Ajouter
                </button>
              </div>
              <div className="space-y-2">
                {creneaux.map((c, i) => (
                  <div key={i} className="flex items-center gap-2">
                    <input
                      type="date" value={c.jour}
                      onChange={(e) => majCreneau(i, 'jour', e.target.value)}
                      className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
                    />
                    <input
                      type="time" value={c.debut}
                      onChange={(e) => majCreneau(i, 'debut', e.target.value)}
                      className="rounded-lg border border-slate-300 px-2 py-2 text-sm focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
                    />
                    <span className="text-slate-400">–</span>
                    <input
                      type="time" value={c.fin}
                      onChange={(e) => majCreneau(i, 'fin', e.target.value)}
                      className="rounded-lg border border-slate-300 px-2 py-2 text-sm focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
                    />
                    <button
                      type="button"
                      // Deux créneaux au minimum : le bouton disparaît plutôt que de proposer
                      // une suppression que le serveur refuserait.
                      disabled={creneaux.length <= 2}
                      onClick={() => setCreneaux((p) => p.filter((_, j) => j !== i))}
                      className="p-2 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-slate-400 transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            </div>

            <div>
              <label htmlFor="note-chu" className="block text-sm font-semibold text-slate-700 mb-1">
                Consignes pour le candidat (facultatif)
              </label>
              <textarea
                id="note-chu" rows={3} value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="Documents à préparer, composition du jury, durée prévue…"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
              />
            </div>

            <div className="flex justify-end gap-2 pt-1">
              <button
                type="button" onClick={onClose}
                className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100 transition-colors"
              >
                Fermer
              </button>
              <button
                type="button" disabled={envoiEnCours} onClick={envoyer}
                className="inline-flex items-center gap-2 px-5 py-2 rounded-xl bg-brand-green text-white text-sm font-bold hover:bg-[#0f3c35] disabled:opacity-50 transition-colors"
              >
                {envoiEnCours
                  ? <Loader2 className="w-4 h-4 animate-spin" />
                  : <CalendarPlus className="w-4 h-4" />}
                Déposer mes créneaux
              </button>
            </div>
          </div>
        )}
      </div>

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}
