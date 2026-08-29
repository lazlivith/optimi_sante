import { useState } from 'react';
import { loadStripe } from '@stripe/stripe-js';
import { CheckoutProvider, PaymentElement, useCheckout } from '@stripe/react-stripe-js';
import { Loader2, CreditCard } from 'lucide-react';

// À appeler une seule fois hors composant pour ne pas recréer l'objet Stripe à chaque rendu.
const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY);

function PayForm({ payLabel }: { payLabel: string }) {
  const checkout = useCheckout();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handlePay = async () => {
    setIsSubmitting(true);
    setError(null);
    // En cas de succès, le navigateur est automatiquement redirigé vers le return_url fourni
    // par le backend à la création de la Checkout Session — pas de gestion manuelle ici.
    const result = await checkout.confirm();
    if (result.type === 'error') {
      setError(result.error.message);
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      <PaymentElement id="payment-element" />
      {error && <p className="text-sm text-rose-600">{error}</p>}
      <button
        type="button"
        onClick={handlePay}
        disabled={isSubmitting || !checkout.canConfirm}
        className="w-full flex justify-center items-center py-3 px-4 rounded-xl shadow-sm text-sm font-bold text-white bg-brand-green hover:bg-[#0f3c35] disabled:opacity-70 transition-colors"
      >
        {isSubmitting ? <Loader2 className="w-5 h-5 mr-2 animate-spin" /> : <CreditCard className="w-5 h-5 mr-2" />}
        {isSubmitting ? 'Traitement en cours...' : payLabel}
      </button>
    </div>
  );
}

/**
 * Formulaire de paiement Stripe intégré (Checkout Sessions API, ui_mode "elements", via
 * @stripe/react-stripe-js `CheckoutProvider`) : carte + moyens de paiement sauvegardés,
 * directement dans la page — plus de redirection vers checkout.stripe.com. L'Adaptive Pricing
 * (devise locale du client) s'applique automatiquement une fois activée côté Dashboard Stripe,
 * sans configuration supplémentaire côté client dans cette version du SDK.
 */
export function StripeEmbeddedCheckout({ clientSecret, payLabel = 'Payer' }: { clientSecret: string; payLabel?: string }) {
  return (
    <CheckoutProvider
      stripe={stripePromise}
      options={{
        fetchClientSecret: async () => clientSecret,
        elementsOptions: {
          appearance: {
            theme: 'stripe',
            variables: { colorPrimary: '#14532d' },
          },
          savedPaymentMethod: { enableSave: 'auto', enableRedisplay: 'auto' },
        },
      }}
    >
      <PayForm payLabel={payLabel} />
    </CheckoutProvider>
  );
}
