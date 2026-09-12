import { useCallback, useEffect, useState } from 'react';
import { Loader2, ShieldCheck, Home, Bus, Check, Trash2, CreditCard, Lock } from 'lucide-react';
import {
  serviceOptionService, serviceOptionTypeLabel,
  type ServiceOptionType, type ServiceSummary,
} from '../../api/serviceOptionService';
import { vaultService } from '../../api/vaultService';
import { StripeEmbeddedCheckout } from '../payment/StripeEmbeddedCheckout';
import { formatAmount } from '../training/TuitionPaymentCard';
import { Toast, type ToastType } from '../common/Toast';
import { DocumentButton } from '../../components/documents/DocumentButton';

const ICONE: Record<ServiceOptionType, typeof Home> = {
  INSURANCE: ShieldCheck,
  HOUSING: Home,
  TRANSPORT: Bus,
};

/**
 * « Mes services », côté médecin : assurance, hébergement, transport organisés par Optimi Santé.
 *
 * Le panneau ne s'affiche que lorsqu'il y a quelque chose à montrer — une offre disponible ou
 * un service déjà retenu. Sur un dossier encore en revue, le serveur ne propose rien : afficher
 * un cadre vide donnerait à croire qu'une étape manque, alors qu'une inscription est
 * parfaitement valable sans aucune option.
 */
export function MyServiceOptionsPanel({ enrollmentId }: { enrollmentId: string }) {
  const [resume, setResume] = useState<ServiceSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [choix, setChoix] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const charger = useCallback(async () => {
    try {
      setResume(await serviceOptionService.summary(enrollmentId));
    } catch (err) {
      console.error('Chargement des services impossible', err);
    } finally {
      setIsLoading(false);
    }
  }, [enrollmentId]);

  useEffect(() => { charger(); }, [charger]);

  const echec = (err: any, defaut: string) =>
    setToast({ message: err?.response?.data?.message ?? defaut, type: 'error' });

  const souscrire = async () => {
    if (choix.size === 0) return;
    setBusy(true);
    try {
      setResume(await serviceOptionService.select(enrollmentId, [...choix]));
      setChoix(new Set());
      setToast({ message: 'Services retenus. Ils restent à régler.', type: 'success' });
    } catch (err) {
      echec(err, "La souscription n'a pas abouti.");
    } finally {
      setBusy(false);
    }
  };

  const retirer = async (id: string) => {
    setBusy(true);
    try {
      setResume(await serviceOptionService.cancel(id));
      setToast({ message: 'Service retiré.', type: 'success' });
    } catch (err) {
      echec(err, 'Le retrait a échoué.');
    } finally {
      setBusy(false);
    }
  };

  const ouvrirPaiement = async () => {
    setBusy(true);
    try {
      const { clientSecret: secret } = await serviceOptionService.checkout(enrollmentId);
      setClientSecret(secret);
    } catch (err) {
      echec(err, "Le paiement n'a pas pu être ouvert.");
    } finally {
      setBusy(false);
    }
  };

  if (isLoading) {
    return (
      <div className="bg-white rounded-3xl border border-slate-200 p-8 flex justify-center">
        <Loader2 className="w-6 h-6 text-brand animate-spin" />
      </div>
    );
  }

  const vivantes = resume?.subscriptions.filter((s) => s.status !== 'CANCELLED') ?? [];
  if (!resume || (resume.available.length === 0 && vivantes.length === 0)) return null;

  const basculer = (id: string) =>
    setChoix((prev) => {
      const suivant = new Set(prev);
      if (!suivant.delete(id)) suivant.add(id);
      return suivant;
    });

  const totalChoisi = resume.available
    .filter((o) => choix.has(o.id))
    .reduce((somme, o) => somme + o.price, 0);

  return (
    <div className="bg-white rounded-3xl border border-slate-200 shadow-sm overflow-hidden">
      <div className="px-8 py-6 border-b border-slate-100 bg-brand/5">
        <h2 className="text-lg font-bold text-brand-dark">Services de séjour</h2>
        <p className="text-sm text-slate-600 mt-0.5">
          Assurance, hébergement et transport organisés par Optimi Santé. Facultatifs : votre
          inscription reste valable sans eux.
        </p>
      </div>

      <div className="px-8 py-6 space-y-6">
        {vivantes.length > 0 && (
          <div>
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-3">
              Vos services
            </h3>
            <ul className="space-y-2">
              {vivantes.map((s) => {
                const Icone = ICONE[s.optionType];
                const paye = s.status === 'PAID';
                return (
                  <li
                    key={s.id}
                    className={`flex items-center gap-3 p-4 rounded-xl border ${
                      paye ? 'border-emerald-200 bg-emerald-50' : 'border-slate-200'
                    }`}
                  >
                    <Icone className={`w-5 h-5 shrink-0 ${paye ? 'text-emerald-600' : 'text-slate-400'}`} />
                    <div className="flex-1 min-w-0">
                      <p className="font-semibold text-slate-800 truncate">{s.label}</p>
                      <p className="text-xs text-slate-500">
                        {serviceOptionTypeLabel(s.optionType)}
                        {paye && ' · réglé'}
                      </p>
                    </div>
                    <span className="font-bold text-slate-800 whitespace-nowrap">
                      {formatAmount(s.unitPrice)}
                    </span>
                    {paye ? (
                      // Un service réglé ne se retire pas d'un clic : de l'argent a été encaissé,
                      // et l'annulation appelle un remboursement, donc l'administration.
                      <Lock className="w-4 h-4 text-emerald-600 shrink-0" />
                    ) : (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => retirer(s.id)}
                        title="Retirer ce service"
                        className="p-2 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 disabled:opacity-40 transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        )}

        {resume.available.length > 0 && !clientSecret && (
          <div>
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-3">
              {vivantes.length > 0 ? 'Ajouter un service' : 'Services proposés'}
            </h3>
            <div className="space-y-2">
              {resume.available.map((o) => {
                const Icone = ICONE[o.optionType];
                const retenu = choix.has(o.id);
                return (
                  <label
                    key={o.id}
                    className={`flex items-start gap-3 p-4 rounded-xl border cursor-pointer transition-colors ${
                      retenu ? 'border-brand bg-brand/5' : 'border-slate-200 hover:border-slate-300'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={retenu}
                      onChange={() => basculer(o.id)}
                      className="mt-1 accent-brand"
                    />
                    <Icone className="w-5 h-5 shrink-0 text-slate-400 mt-0.5" />
                    <div className="flex-1 min-w-0">
                      <p className="font-semibold text-slate-800">{o.label}</p>
                      <p className="text-xs text-slate-500">{serviceOptionTypeLabel(o.optionType)}</p>
                      {o.description && (
                        <p className="text-sm text-slate-600 mt-1">{o.description}</p>
                      )}
                    </div>
                    <span className="font-bold text-slate-800 whitespace-nowrap">
                      {formatAmount(o.price)}
                    </span>
                  </label>
                );
              })}
            </div>

            {choix.size > 0 && (
              <button
                type="button"
                disabled={busy}
                onClick={souscrire}
                className="mt-4 inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-brand text-white text-sm font-bold hover:bg-[#0f3c35] disabled:opacity-50 transition-colors"
              >
                {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                Retenir ces services — {formatAmount(totalChoisi)}
              </button>
            )}
          </div>
        )}

        {resume.amountDue > 0 && (
          <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5">
            <div className="flex items-center justify-between gap-4 mb-4">
              <p className="text-sm text-amber-900">
                Services retenus, en attente de règlement
              </p>
              <span className="text-lg font-bold text-amber-900 whitespace-nowrap">
                {formatAmount(resume.amountDue)}
              </span>
            </div>
            {/* Le formulaire n'est monté qu'après un geste explicite : ouvrir un paiement crée
                une ligne en base, on ne le fait pas au simple affichage de la page. */}
            {clientSecret ? (
              <StripeEmbeddedCheckout clientSecret={clientSecret} />
            ) : (
              <button
                type="button"
                disabled={busy}
                onClick={ouvrirPaiement}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-brand text-white text-sm font-bold hover:bg-[#0f3c35] disabled:opacity-50 transition-colors"
              >
                {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <CreditCard className="w-4 h-4" />}
                Régler {formatAmount(resume.amountDue)}
              </button>
            )}
          </div>
        )}

        {resume.amountPaid > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
            <p className="text-sm text-slate-600">
              Déjà réglé : <strong className="text-slate-800">{formatAmount(resume.amountPaid)}</strong>
            </p>
            {resume.subscriptionDocumentId && (
              <DocumentButton
                libelle="Mon attestation de souscription"
                variante="bouton"
                obtenirLien={() => vaultService.getPresignedUrl(
                  'SERVICE_SUBSCRIPTION', resume.subscriptionDocumentId!)}
                className="rounded-xl border border-slate-300 !bg-white !text-slate-700 hover:!bg-slate-50"
              />
            )}
          </div>
        )}
      </div>

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}
