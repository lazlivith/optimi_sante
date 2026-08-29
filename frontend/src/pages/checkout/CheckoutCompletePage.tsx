import { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle, Loader2, XCircle } from 'lucide-react';
import { orderService } from '../../api/orderService';
import { useCart } from '../../context/CartContext';

const MAX_POLL_ATTEMPTS = 10;
const POLL_INTERVAL_MS = 2000;

/**
 * Page de retour du paiement Stripe intégré (ui_mode "elements") : le navigateur y est
 * automatiquement redirigé après confirmation. Le webhook (source de vérité serveur) peut
 * arriver quelques instants après — on interroge donc le statut à intervalles courts plutôt
 * que de supposer que le paiement est déjà traité. Le panier n'est vidé qu'ici, une fois le
 * paiement confirmé, jamais avant.
 */
export function CheckoutCompletePage() {
  const [searchParams] = useSearchParams();
  const sessionId = searchParams.get('session_id');
  const navigate = useNavigate();
  const { clearCart } = useCart();

  const [status, setStatus] = useState<'checking' | 'paid' | 'pending' | 'failed'>('checking');
  const [orderNumber, setOrderNumber] = useState<string | null>(null);
  const attemptsRef = useRef(0);
  const clearedRef = useRef(false);

  useEffect(() => {
    if (!sessionId) {
      setStatus('failed');
      return;
    }

    let cancelled = false;

    const poll = async () => {
      try {
        const data = await orderService.getCheckoutSessionStatus(sessionId);
        if (cancelled) return;
        if (data.orderNumber) setOrderNumber(data.orderNumber);

        if (data.status === 'complete' && data.paymentStatus === 'paid') {
          setStatus('paid');
          if (!clearedRef.current) {
            clearedRef.current = true;
            clearCart();
          }
          return;
        }
        if (data.status === 'expired') {
          setStatus('failed');
          return;
        }
      } catch {
        // On retente silencieusement, le webhook peut simplement ne pas être encore passé.
      }

      attemptsRef.current += 1;
      if (attemptsRef.current >= MAX_POLL_ATTEMPTS) {
        if (!cancelled) setStatus('pending');
        return;
      }
      if (!cancelled) setTimeout(poll, POLL_INTERVAL_MS);
    };

    poll();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  return (
    <div className="max-w-2xl mx-auto py-20 px-6 text-center">
      <div className="bg-white p-10 rounded-3xl shadow-sm border border-slate-200">
        {status === 'checking' && (
          <>
            <div className="w-20 h-20 bg-green-100 text-green-600 rounded-full flex items-center justify-center mx-auto mb-6">
              <Loader2 className="w-10 h-10 animate-spin" />
            </div>
            <h1 className="text-3xl font-bold text-brand-dark mb-4">Confirmation du paiement...</h1>
            <p className="text-slate-600 mb-8">Merci de patienter quelques instants.</p>
          </>
        )}

        {status === 'paid' && (
          <>
            <div className="w-20 h-20 bg-green-100 text-green-600 rounded-full flex items-center justify-center mx-auto mb-6">
              <CheckCircle className="w-10 h-10" />
            </div>
            <h1 className="text-3xl font-bold text-brand-dark mb-4">Paiement réussi !</h1>
            <p className="text-slate-600 mb-8">
              Merci pour votre confiance{orderNumber ? ` — commande ${orderNumber}` : ''}. Vous recevrez un email de
              confirmation contenant les détails de votre commande.
            </p>
            <button onClick={() => navigate('/catalog')} className="px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors">
              Continuer mes achats
            </button>
          </>
        )}

        {status === 'pending' && (
          <>
            <div className="w-20 h-20 bg-amber-100 text-amber-600 rounded-full flex items-center justify-center mx-auto mb-6">
              <Loader2 className="w-10 h-10" />
            </div>
            <h1 className="text-3xl font-bold text-brand-dark mb-4">Paiement en cours de confirmation</h1>
            <p className="text-slate-600 mb-8">
              Votre paiement est en cours de traitement. Vous recevrez un email de confirmation dès qu'il sera validé.
            </p>
            <button onClick={() => navigate('/my-orders')} className="px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors">
              Voir mes commandes
            </button>
          </>
        )}

        {status === 'failed' && (
          <>
            <div className="w-20 h-20 bg-rose-100 text-rose-600 rounded-full flex items-center justify-center mx-auto mb-6">
              <XCircle className="w-10 h-10" />
            </div>
            <h1 className="text-3xl font-bold text-brand-dark mb-4">Paiement non abouti</h1>
            <p className="text-slate-600 mb-8">Le paiement n'a pas pu être confirmé. Votre panier n'a pas été modifié.</p>
            <button onClick={() => navigate('/cart')} className="px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors">
              Retour au panier
            </button>
          </>
        )}
      </div>
    </div>
  );
}
