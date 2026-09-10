import { useState } from 'react';
import { CreditCard, Loader2, Lock, Plane, ShieldCheck } from 'lucide-react';
import { StripeEmbeddedCheckout } from '../payment/StripeEmbeddedCheckout';
import { enrollmentService } from '../../api/enrollmentService';
import { formatAmount } from './TuitionPaymentCard';

interface TuitionBalanceCardProps {
  enrollmentId: string;
  trainingTitle: string;
  /** Montant du solde restant. */
  amount?: number | null;
  totalAmount?: number | null;
  onError: (message: string) => void;
}

/**
 * Règlement du solde des frais de formation, appelé une fois le visa délivré.
 *
 * Ce moment-là et pas un autre : c'est l'obtention du visa qui rend le séjour certain. Réclamer
 * le solde plus tôt ferait avancer au candidat l'intégralité d'un déplacement qui dépend encore
 * d'une décision consulaire — ce que l'échéancier existe précisément pour éviter.
 *
 * Comme pour l'acompte, le formulaire Stripe n'est monté qu'après un geste explicite : ouvrir
 * une session crée une ligne en base.
 */
export function TuitionBalanceCard({
  enrollmentId,
  trainingTitle,
  amount,
  totalAmount,
  onError,
}: TuitionBalanceCardProps) {
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  const [isPreparing, setIsPreparing] = useState(false);

  const ouvrirPaiement = async () => {
    setIsPreparing(true);
    try {
      const { clientSecret: secret } = await enrollmentService.createBalanceCheckout(enrollmentId);
      setClientSecret(secret);
    } catch (err: any) {
      console.error("Échec de l'ouverture du solde", err);
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
      <div className="px-8 py-5 bg-sky-50 border-b border-sky-100 flex items-start gap-3">
        <Plane className="w-5 h-5 text-sky-700 shrink-0 mt-0.5" />
        <div>
          <p className="font-bold text-brand-dark">Votre visa est délivré</p>
          <p className="text-sm text-slate-600 mt-0.5">
            Le solde de vos frais de formation peut maintenant être réglé. Votre départ est
            subordonné à ce règlement.
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
          </div>
          <div className="sm:text-right">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
              Solde restant
            </p>
            <p className="text-3xl font-bold text-brand-dark tabular-nums">{formatAmount(amount)}</p>
            {totalAmount != null && (
              <p className="text-xs text-slate-500 mt-1.5">
                sur {formatAmount(totalAmount)} — acompte déjà réglé
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
            onClick={ouvrirPaiement}
            disabled={isPreparing}
            className="w-full flex justify-center items-center py-3.5 px-4 rounded-xl shadow-sm text-sm font-bold text-white bg-brand-green hover:bg-[#0f3c35] disabled:opacity-70 transition-colors"
          >
            {isPreparing ? (
              <>
                <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                Préparation du paiement sécurisé...
              </>
            ) : (
              <>
                <CreditCard className="w-5 h-5 mr-2" />
                Régler le solde
              </>
            )}
          </button>
        )}

        <div className="mt-6 pt-6 border-t border-slate-100 flex flex-wrap gap-x-6 gap-y-2 text-xs text-slate-500">
          <span className="flex items-center gap-1.5">
            <Lock className="w-3.5 h-3.5 text-brand-green" />
            Paiement sécurisé par Stripe
          </span>
          <span className="flex items-center gap-1.5">
            <ShieldCheck className="w-3.5 h-3.5 text-brand-green" />
            Fonds sécurisés par Optimi Santé, tiers de confiance
          </span>
        </div>
      </div>
    </div>
  );
}
