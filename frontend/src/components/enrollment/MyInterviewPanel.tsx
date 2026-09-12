import { useCallback, useEffect, useState } from 'react';
import { Loader2, CalendarCheck, CalendarClock, Video, Info, CheckCircle2, ExternalLink } from 'lucide-react';
import {
  interviewService, formatCreneau, estPasse, type InterviewSchedule,
} from '../../api/interviewService';
import { vaultService } from '../../api/vaultService';
import { Toast, type ToastType } from '../common/Toast';
import { DocumentButton } from '../../components/documents/DocumentButton';

/**
 * « Mon entretien en visioconférence », côté médecin : les créneaux qu'on lui propose, son
 * choix, puis le lien pour se connecter le jour venu.
 *
 * Le panneau ne s'affiche que lorsqu'un entretien lui a été transmis. Tant que le CHU n'a fait
 * que déposer des créneaux, il n'y a rien à montrer — les afficher reviendrait à laisser
 * l'établissement convoquer directement, ce que tout le circuit cherche à éviter.
 */
export function MyInterviewPanel({ enrollmentId }: { enrollmentId: string }) {
  const [entretien, setEntretien] = useState<InterviewSchedule | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [choix, setChoix] = useState<string | null>(null);
  const [envoiEnCours, setEnvoiEnCours] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const charger = useCallback(async () => {
    try {
      setEntretien(await interviewService.current(enrollmentId));
    } catch (err) {
      console.error("Chargement de l'entretien impossible", err);
    } finally {
      setIsLoading(false);
    }
  }, [enrollmentId]);

  useEffect(() => { charger(); }, [charger]);

  const confirmer = async () => {
    if (!entretien || !choix) return;
    setEnvoiEnCours(true);
    try {
      const maj = await interviewService.confirm(entretien.id, choix);
      setEntretien(maj);
      setToast({
        message: 'Visioconférence confirmée. Le lien et votre convocation sont ci-dessous.',
        type: 'success',
      });
    } catch (err: any) {
      setToast({
        message: err?.response?.data?.message ?? 'La confirmation a échoué.',
        type: 'error',
      });
    } finally {
      setEnvoiEnCours(false);
    }
  };

  if (isLoading) {
    return (
      <div className="bg-white rounded-3xl border border-slate-200 p-8 flex justify-center">
        <Loader2 className="w-6 h-6 text-brand animate-spin" />
      </div>
    );
  }

  // Rien à montrer plutôt qu'un cadre vide : la plupart des dossiers n'ont pas d'entretien.
  if (!entretien) return null;

  const confirme = entretien.status === 'CONFIRMED';
  const creneauRetenu = entretien.slots.find((s) => s.confirmed);
  const disponibles = entretien.slots.filter((s) => !estPasse(s));

  return (
    <div className="bg-white rounded-3xl border border-slate-200 shadow-sm overflow-hidden">
      <div className={`px-8 py-6 border-b ${confirme ? 'bg-emerald-50 border-emerald-100' : 'bg-brand/5 border-slate-100'}`}>
        <div className="flex items-start gap-4">
          {confirme
            ? <CalendarCheck className="w-6 h-6 text-emerald-600 shrink-0 mt-0.5" />
            : <CalendarClock className="w-6 h-6 text-brand shrink-0 mt-0.5" />}
          <div>
            <h2 className="text-lg font-bold text-brand-dark">
              {confirme
                ? 'Votre entretien en visioconférence est confirmé'
                : 'Entretien de sélection en visioconférence'}
            </h2>
            <p className="text-sm text-slate-600 mt-0.5">
              {entretien.partnerInstitutionName} · {entretien.trainingTitle}
            </p>
          </div>
        </div>
      </div>

      <div className="px-8 py-6 space-y-5">
        <p className="inline-flex items-center gap-2 text-sm text-slate-700">
          <Video className="w-4 h-4 text-slate-400" />
          Entretien à distance — aucun déplacement n'est nécessaire.
        </p>

        {/* Les deux notes restent séparées et attribuées : le médecin doit pouvoir distinguer
            une consigne de l'établissement d'une précision d'Optimi Santé. */}
        {entretien.partnerNote && (
          <Note titre={`Consignes de ${entretien.partnerInstitutionName}`} texte={entretien.partnerNote} />
        )}
        {entretien.adminNote && (
          <Note titre="Précisions d'Optimi Santé" texte={entretien.adminNote} />
        )}

        {confirme && creneauRetenu ? (
          <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
            <p className="inline-flex items-center gap-2 font-semibold text-emerald-900">
              <CheckCircle2 className="w-5 h-5" />
              {formatCreneau(creneauRetenu)}
            </p>
            <div className="mt-4 rounded-xl bg-white border border-emerald-200 p-4">
              <p className="text-xs font-bold uppercase tracking-wider text-emerald-800">
                Lien de la réunion en ligne
              </p>
              <a
                href={entretien.meetingLink}
                target="_blank"
                rel="noopener noreferrer"
                className="text-sm text-brand underline break-all mt-1 inline-block"
              >
                {entretien.meetingLink}
              </a>
            </div>

            <div className="flex flex-wrap gap-2 mt-4">
              <a
                href={entretien.meetingLink}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-brand text-white text-sm font-bold hover:bg-[#0f3c35] transition-colors"
              >
                <ExternalLink className="w-4 h-4" />
                Rejoindre la visioconférence
              </a>
              {entretien.convocationDocumentId && (
                <DocumentButton
                  libelle="Ma convocation visio"
                  variante="bouton"
                  obtenirLien={() => vaultService.getPresignedUrl(
                    'INTERVIEW_CONVOCATION', entretien.convocationDocumentId!)}
                  className="rounded-xl border border-emerald-300 !bg-white !text-emerald-800 hover:!bg-emerald-100"
                />
              )}
            </div>
            <p className="text-xs text-emerald-800/80 mt-3">
              Connectez-vous quelques minutes à l'avance et vérifiez votre micro et votre caméra.
            </p>
          </div>
        ) : (
          <>
            <p className="text-sm text-slate-600">
              Choisissez le créneau qui vous convient. Votre choix vaut confirmation du
              rendez-vous : vous recevrez alors le lien de connexion, et votre convocation visio
              sera déposée dans vos documents.
            </p>

            {disponibles.length === 0 ? (
              <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-xl p-4">
                Tous les créneaux proposés sont dépassés. Signalez-le à Optimi Santé pour qu'un
                nouveau rendez-vous soit convenu avec l'établissement.
              </p>
            ) : (
              <div className="space-y-2">
                {disponibles.map((slot) => (
                  <label
                    key={slot.id}
                    className={`flex items-center gap-3 p-4 rounded-xl border cursor-pointer transition-colors ${
                      choix === slot.id
                        ? 'border-brand bg-brand/5'
                        : 'border-slate-200 hover:border-slate-300'
                    }`}
                  >
                    <input
                      type="radio"
                      name="creneau"
                      value={slot.id}
                      checked={choix === slot.id}
                      onChange={() => setChoix(slot.id)}
                      className="accent-brand"
                    />
                    <span className="text-sm font-medium text-slate-800">{formatCreneau(slot)}</span>
                  </label>
                ))}
              </div>
            )}

            {disponibles.length > 0 && (
              <button
                type="button"
                disabled={!choix || envoiEnCours}
                onClick={confirmer}
                className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-brand text-white font-bold hover:bg-[#0f3c35] disabled:opacity-50 transition-colors"
              >
                {envoiEnCours
                  ? <Loader2 className="w-4 h-4 animate-spin" />
                  : <CalendarCheck className="w-4 h-4" />}
                Confirmer ce créneau
              </button>
            )}
          </>
        )}
      </div>

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}

function Note({ titre, texte }: { titre: string; texte: string }) {
  return (
    <div className="rounded-2xl bg-slate-50 border border-slate-200 p-4">
      <p className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-slate-500">
        <Info className="w-3.5 h-3.5" />
        {titre}
      </p>
      <p className="text-sm text-slate-700 mt-2 whitespace-pre-line">{texte}</p>
    </div>
  );
}
