import { useState } from 'react';
import { CheckCircle2, CreditCard, Loader2, Lock, ShieldCheck, Building2 } from 'lucide-react';
import { StripeEmbeddedCheckout } from '../payment/StripeEmbeddedCheckout';
import { enrollmentService } from '../../api/enrollmentService';

/** Formatage monétaire unique de l'écran, pour éviter les divergences d'affichage. */
export const formatAmount = (amount: number | null | undefined, currency = 'EUR') =>
  amount == null
    ? '—'
    : new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount);

interface TuitionPaymentCardProps {
  enrollmentId: string;
  trainingTitle: string;
  hostInstitution?: string | null;
  /** Montant de l'acompte, celui qui sera réellement prélevé maintenant. */
  amount?: number | null;
  /** Prix total de la formation, pour situer l'acompte. */
  totalAmount?: number | null;
  balanceAmount?: number | null;
  depositRate?: number | null;
  onError: (message: string) => void;
}

/**
 * Règlement des frais de formation, affiché lorsqu'un dossier est accepté par
 * l'établissement et attend le paiement.
 *
 * Le formulaire Stripe n'est monté qu'après un geste explicite du médecin : ouvrir une
 * session de paiement crée une ligne en base, on ne le fait donc pas au simple affichage
 * de la page. Après confirmation, Stripe redirige vers cette même page, dont le statut
 * aura basculé — il n'y a pas d'état de succès à maintenir côté client.
 */
export function TuitionPaymentCard({
  enrollmentId,
  trainingTitle,
  hostInstitution,
  amount,
  totalAmount,
  balanceAmount,
  depositRate,
  onError,
}: TuitionPaymentCardProps) {
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  const [isPreparing, setIsPreparing] = useState(false);

  const handleOpenPayment = async () => {
    setIsPreparing(true);
    try {
      const { clientSecret: secret } = await enrollmentService.createTuitionCheckout(enrollmentId);
      setClientSecret(secret);
    } catch (err: any) {
      console.error("Échec de l'ouverture du paiement", err);
      onError(
        err.response?.data?.message
          || "Le paiement n'a pas pu être ouvert. Réessayez dans un instant.",
      );
    } finally {
      setIsPreparing(false);
    }
  };

  return (
    <div className="bg-white rounded-3xl shadow-sm border border-slate-200 overflow-hidden">
      <div className="px-8 py-5 bg-emerald-50 border-b border-emerald-100 flex items-start gap-3">
        <CheckCircle2 className="w-5 h-5 text-brand shrink-0 mt-0.5" />
        <div>
          <p className="font-bold text-brand-dark">Candidature validée par l'établissement</p>
          <p className="text-sm text-slate-600 mt-0.5">
            Votre place est réservée. Elle sera définitivement confirmée dès réception de
            l'acompte. Le solde ne vous sera demandé qu'une fois votre visa délivré.
          </p>
        </div>
      </div>

      <div className="p-8">
        <div className="grid sm:grid-cols-2 gap-6 mb-8">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
              Formation
            </p>
            <p className="font-semibold text-brand-dark">{trainingTitle}</p>
            {hostInstitution && (
              <p className="text-sm text-slate-500 mt-1 flex items-center gap-1.5">
                <Building2 className="w-3.5 h-3.5" />
                {hostInstitution}
              </p>
            )}
          </div>
          <div className="sm:text-right">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
              {depositRate ? `Acompte (${depositRate} %)` : 'Montant à régler'}
            </p>
            <p className="text-3xl font-bold text-brand-dark tabular-nums">{formatAmount(amount)}</p>
            {/* Le total et le solde sont rappelés : afficher l'acompte seul laisserait croire
                que la formation coûte ce montant-là. */}
            {totalAmount != null && balanceAmount != null && balanceAmount > 0 && (
              <p className="text-xs text-slate-500 mt-1.5">
                sur {formatAmount(totalAmount)} — solde de {formatAmount(balanceAmount)} à la
                délivrance de votre visa
              </p>
            )}
          </div>
        </div>

        {clientSecret ? (
          <div className="border-t border-slate-100 pt-8">
            <StripeEmbeddedCheckout
              clientSecret={clientSecret}
              payLabel={`Régler ${formatAmount(amount)}`}
            />
          </div>
        ) : (
          <button
            type="button"
            onClick={handleOpenPayment}
            disabled={isPreparing}
            className="w-full flex justify-center items-center py-3.5 px-4 rounded-xl shadow-sm text-sm font-bold text-white bg-brand hover:bg-[#0f3c35] disabled:opacity-70 transition-colors"
          >
            {isPreparing ? (
              <>
                <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                Préparation du paiement sécurisé...
              </>
            ) : (
              <>
                <CreditCard className="w-5 h-5 mr-2" />
                Régler l'acompte
              </>
            )}
          </button>
        )}

        <div className="mt-6 pt-6 border-t border-slate-100 flex flex-wrap gap-x-6 gap-y-2 text-xs text-slate-500">
          <span className="flex items-center gap-1.5">
            <Lock className="w-3.5 h-3.5 text-brand" />
            Paiement sécurisé par Stripe
          </span>
          <span className="flex items-center gap-1.5">
            <ShieldCheck className="w-3.5 h-3.5 text-brand" />
            Fonds sécurisés par Optimi Santé, tiers de confiance
          </span>
        </div>
      </div>
    </div>
  );
}
