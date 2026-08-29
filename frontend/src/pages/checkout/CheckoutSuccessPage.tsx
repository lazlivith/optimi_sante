import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle } from 'lucide-react';
import { useCart } from '../../context/CartContext';

export function CheckoutSuccessPage() {
  const navigate = useNavigate();
  const { clearCart } = useCart();

  useEffect(() => {
    clearCart();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="max-w-2xl mx-auto py-20 px-6 text-center">
      <div className="bg-white p-10 rounded-3xl shadow-sm border border-slate-200">
        <div className="w-20 h-20 bg-green-100 text-green-600 rounded-full flex items-center justify-center mx-auto mb-6">
          <CheckCircle className="w-10 h-10" />
        </div>
        <h1 className="text-3xl font-bold text-brand-dark mb-4">Paiement réussi !</h1>
        <p className="text-slate-600 mb-8">
          Merci pour votre confiance. Votre paiement a été traité par Stripe et votre commande est en cours de préparation.
          Vous recevrez un email de confirmation avec le détail de votre commande.
        </p>
        <button onClick={() => navigate('/catalog')} className="px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors">
          Continuer mes achats
        </button>
      </div>
    </div>
  );
}
