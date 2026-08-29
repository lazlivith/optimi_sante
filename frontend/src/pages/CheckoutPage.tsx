import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import { orderService, type CheckoutRequestDto } from '../api/orderService';
import { StripeEmbeddedCheckout } from '../components/payment/StripeEmbeddedCheckout';
import { Loader2, ArrowLeft, CreditCard, Building, Tag, X } from 'lucide-react';
import { Toast, type ToastType } from '../components/common/Toast';

export const CheckoutPage = () => {
  const { items, totalPrice, clearCart } = useCart();
  const { isAuthenticated, user } = useAuth();
  const navigate = useNavigate();

  const isB2B = user?.role === 'CLIENT_B2B';
  const hasQuoteItem = items.some(i => i.isQuoteOnly);
  const forceQuote = hasQuoteItem;

  const defaultMethod = forceQuote ? 'QUOTE_REQUEST' : (isB2B ? 'QUOTE_REQUEST' : 'STRIPE_CARD');

  const [paymentMethod, setPaymentMethod] = useState<'STRIPE_CARD' | 'BANK_TRANSFER' | 'QUOTE_REQUEST'>(defaultMethod);
  const [isProcessing, setIsProcessing] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);
  const [success, setSuccess] = useState(false);
  // Une fois renseigné, remplace le formulaire de choix de paiement par le Payment Element
  // Stripe intégré (ui_mode "elements") — le panier n'est vidé qu'à la confirmation réelle du
  // paiement sur /checkout/complete, pas avant, pour ne pas le perdre en cas d'échec/abandon.
  const [stripeClientSecret, setStripeClientSecret] = useState<string | null>(null);

  const [promoCodeInput, setPromoCodeInput] = useState('');
  const [appliedPromo, setAppliedPromo] = useState<{ code: string; discountAmount: number } | null>(null);
  const [isValidatingPromo, setIsValidatingPromo] = useState(false);
  const [promoError, setPromoError] = useState<string | null>(null);

  const discountedTotal = Math.max(0, totalPrice - (appliedPromo?.discountAmount ?? 0));

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    if (items.length === 0 && !success) {
      navigate('/cart');
    }
  }, [isAuthenticated, items, navigate, success]);

  const handleApplyPromoCode = async () => {
    if (!promoCodeInput.trim()) return;
    setIsValidatingPromo(true);
    setPromoError(null);
    try {
      const result = await orderService.validatePromoCode(promoCodeInput.trim().toUpperCase(), totalPrice);
      setAppliedPromo({ code: promoCodeInput.trim().toUpperCase(), discountAmount: result.discountAmount });
    } catch (err: any) {
      setAppliedPromo(null);
      setPromoError(err.response?.data?.message || 'Code promo invalide.');
    } finally {
      setIsValidatingPromo(false);
    }
  };

  const handleRemovePromoCode = () => {
    setAppliedPromo(null);
    setPromoCodeInput('');
    setPromoError(null);
  };

  const handleDirectCheckout = async () => {
    setIsProcessing(true);
    try {
      // Les codes promo ne s'appliquent pas aux demandes de devis (négociation individuelle).
      const promoCode = paymentMethod !== 'QUOTE_REQUEST' ? appliedPromo?.code : undefined;

      if (paymentMethod === 'QUOTE_REQUEST') {
        await orderService.quoteRequest({
          items: items.map(item => ({ productId: item.id, quantity: item.cartQuantity })),
          notes: 'Devis demandé depuis le tunnel d\'achat'
        });
        handleSuccess();
      } else if (paymentMethod === 'STRIPE_CARD') {
        const request: CheckoutRequestDto = {
          items: items.map(item => ({ productId: item.id, quantity: item.cartQuantity })),
          paymentMethod: 'STRIPE_CARD',
          promoCode
        };
        const response = await orderService.checkout(request);
        if (response.clientSecret) {
          // Affiche le Payment Element Stripe intégré à la page (ui_mode "elements") — plus de
          // redirection. Le panier n'est vidé qu'à la confirmation réelle du paiement sur
          // /checkout/complete, pas ici, pour ne pas le perdre en cas d'abandon/échec.
          setStripeClientSecret(response.clientSecret);
          setIsProcessing(false);
        } else {
          setToast({ message: 'Erreur: paiement non initialisé par le serveur.', type: 'error' });
          setIsProcessing(false);
        }
      } else {
        await orderService.checkout({
          items: items.map(item => ({ productId: item.id, quantity: item.cartQuantity })),
          paymentMethod: paymentMethod,
          promoCode
        });
        handleSuccess();
      }
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la validation.', type: 'error' });
      setIsProcessing(false);
    }
  };

  const handleSuccess = () => {
    clearCart();
    setSuccess(true);
  };

  if (stripeClientSecret) {
    return (
      <div className="min-h-screen bg-slate-50 py-12 px-4 sm:px-6">
        <div className="max-w-xl mx-auto">
          <button onClick={() => setStripeClientSecret(null)} className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-8 transition-colors">
            <ArrowLeft className="w-4 h-4 mr-2" /> Changer de mode de paiement
          </button>
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
            <h1 className="text-xl font-bold text-brand-dark mb-6">Paiement par carte</h1>
            <StripeEmbeddedCheckout clientSecret={stripeClientSecret} payLabel={`Payer ${discountedTotal.toFixed(0)} €`} />
          </div>
        </div>
      </div>
    );
  }

  if (success) {
    return (
      <div className="max-w-2xl mx-auto py-20 px-6 text-center">
        <div className="bg-white p-10 rounded-3xl shadow-sm border border-slate-200">
          <div className="w-20 h-20 bg-green-100 text-green-600 rounded-full flex items-center justify-center mx-auto mb-6">
            <svg className="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" /></svg>
          </div>
          <h1 className="text-3xl font-bold text-brand-dark mb-4">Commande confirmée !</h1>
          <p className="text-slate-600 mb-8">
            Merci pour votre confiance. Vous recevrez un email de confirmation contenant les détails de votre commande.
          </p>
          <button onClick={() => navigate('/catalog')} className="px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors">
            Continuer mes achats
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 py-12 px-4 sm:px-6">
      <div className="max-w-4xl mx-auto">
        <button onClick={() => navigate('/cart')} className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-8 transition-colors">
          <ArrowLeft className="w-4 h-4 mr-2" /> Retour au panier
        </button>

        <h1 className="text-3xl font-bold text-brand-dark mb-8">Finalisation de la commande</h1>

        <div className="grid md:grid-cols-2 gap-8">
          {/* Left Column: Payment Methods */}
          <div className="space-y-6">
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
              <h2 className="text-xl font-semibold text-brand-dark mb-6">Mode de paiement</h2>
              
              <div className="space-y-4 mb-8">
                {forceQuote ? (
                  <label className={`flex items-center p-4 border rounded-xl cursor-pointer transition-colors border-brand-green bg-brand-light`}>
                    <input type="radio" name="paymentMethod" value="QUOTE_REQUEST" checked readOnly className="mr-4 h-4 w-4 text-brand-green focus:ring-brand-green" />
                    <Building className="w-5 h-5 text-slate-500 mr-3" />
                    <span className="font-medium text-slate-800">Demande de devis obligatoire</span>
                  </label>
                ) : (
                  <>
                    <label className={`flex items-center p-4 border rounded-xl cursor-pointer transition-colors ${paymentMethod === 'STRIPE_CARD' ? 'border-brand-green bg-brand-light' : 'border-slate-200 hover:bg-slate-50'}`}>
                      <input type="radio" name="paymentMethod" value="STRIPE_CARD" checked={paymentMethod === 'STRIPE_CARD'} onChange={() => setPaymentMethod('STRIPE_CARD')} className="mr-4 h-4 w-4 text-brand-green focus:ring-brand-green" />
                      <CreditCard className="w-5 h-5 text-slate-500 mr-3" />
                      <span className="font-medium text-slate-800">Carte Bancaire (Stripe)</span>
                    </label>
                    
                    <label className={`flex items-center p-4 border rounded-xl cursor-pointer transition-colors ${paymentMethod === 'BANK_TRANSFER' ? 'border-brand-green bg-brand-light' : 'border-slate-200 hover:bg-slate-50'}`}>
                      <input type="radio" name="paymentMethod" value="BANK_TRANSFER" checked={paymentMethod === 'BANK_TRANSFER'} onChange={() => setPaymentMethod('BANK_TRANSFER')} className="mr-4 h-4 w-4 text-brand-green focus:ring-brand-green" />
                      <Building className="w-5 h-5 text-slate-500 mr-3" />
                      <span className="font-medium text-slate-800">Virement Bancaire</span>
                    </label>

                    {isB2B && (
                      <label className={`flex items-center p-4 border rounded-xl cursor-pointer transition-colors ${paymentMethod === 'QUOTE_REQUEST' ? 'border-brand-green bg-brand-light' : 'border-slate-200 hover:bg-slate-50'}`}>
                        <input type="radio" name="paymentMethod" value="QUOTE_REQUEST" checked={paymentMethod === 'QUOTE_REQUEST'} onChange={() => setPaymentMethod('QUOTE_REQUEST')} className="mr-4 h-4 w-4 text-brand-green focus:ring-brand-green" />
                        <Building className="w-5 h-5 text-slate-500 mr-3" />
                        <span className="font-medium text-slate-800">Demande de devis</span>
                      </label>
                    )}
                  </>
                )}
              </div>

              <div className="pt-4 border-t border-slate-100">
                <div className="space-y-6">
                  <p className="text-sm text-slate-600 bg-blue-50 p-4 rounded-lg border border-blue-100">
                    {paymentMethod === 'QUOTE_REQUEST'
                      ? 'Votre demande de devis sera transmise à notre équipe. Un conseiller vous contactera dans les plus brefs délais.'
                      : paymentMethod === 'STRIPE_CARD'
                      ? 'Le formulaire de paiement sécurisé Stripe s\'affichera directement sur cette page.'
                      : 'En choisissant le virement bancaire, votre commande sera mise en attente jusqu\'à réception des fonds. Nos coordonnées bancaires vous seront envoyées par email.'}
                  </p>
                  <div className="flex gap-4">
                    <button onClick={() => navigate('/cart')} className="px-6 py-3 border border-slate-300 text-slate-600 font-medium rounded-xl hover:bg-slate-50 transition-colors">
                      Annuler
                    </button>
                    <button onClick={handleDirectCheckout} disabled={isProcessing} className="flex-1 flex justify-center items-center px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors disabled:opacity-50">
                      {isProcessing ? <Loader2 className="animate-spin w-5 h-5 mr-2" /> : null}
                      {paymentMethod === 'QUOTE_REQUEST' ? 'Demander un devis' : paymentMethod === 'STRIPE_CARD' ? 'Payer par carte' : 'Confirmer la commande'}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Right Column: Order Summary */}
          <div>
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm sticky top-28">
              <h2 className="text-xl font-semibold text-brand-dark mb-6">Récapitulatif</h2>
              
              <div className="space-y-4 mb-6 max-h-64 overflow-y-auto pr-2">
                {items.map(item => (
                  <div key={item.id} className="flex justify-between text-sm">
                    <div className="flex flex-col">
                      <span className="font-medium text-slate-800">{item.name}</span>
                      <span className="text-slate-500">Qté: {item.cartQuantity}</span>
                    </div>
                    <span className="font-semibold">{item.isQuoteOnly ? '--' : `${(item.finalPrice * item.cartQuantity).toFixed(0)} €`}</span>
                  </div>
                ))}
              </div>
              
              {paymentMethod !== 'QUOTE_REQUEST' && (
                <div className="border-t border-slate-100 pt-4 mb-4">
                  {appliedPromo ? (
                    <div className="flex items-center justify-between bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-2">
                      <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-emerald-700">
                        <Tag className="w-3.5 h-3.5" /> {appliedPromo.code}
                      </span>
                      <button onClick={handleRemovePromoCode} className="text-emerald-700 hover:text-emerald-900" aria-label="Retirer le code promo">
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  ) : (
                    <div className="flex gap-2">
                      <input
                        type="text"
                        placeholder="Code promo"
                        value={promoCodeInput}
                        onChange={(e) => setPromoCodeInput(e.target.value)}
                        className="flex-1 min-w-0 px-3 py-2 text-sm border border-slate-200 rounded-lg uppercase focus:outline-none focus:ring-2 focus:ring-brand-green/20 focus:border-brand-green"
                      />
                      <button
                        onClick={handleApplyPromoCode}
                        disabled={isValidatingPromo || !promoCodeInput.trim()}
                        className="px-4 py-2 text-sm font-semibold text-brand-green border border-brand-green rounded-lg hover:bg-brand-light transition-colors disabled:opacity-50 shrink-0"
                      >
                        {isValidatingPromo ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Appliquer'}
                      </button>
                    </div>
                  )}
                  {promoError && <p className="text-xs text-rose-600 mt-1.5">{promoError}</p>}
                </div>
              )}

              <div className="border-t border-slate-200 pt-4 mt-2 space-y-2">
                {appliedPromo && (
                  <div className="flex justify-between items-center text-sm text-emerald-700">
                    <span>Remise ({appliedPromo.code})</span>
                    <span>− {appliedPromo.discountAmount.toFixed(2)} €</span>
                  </div>
                )}
                <div className="flex justify-between items-center text-lg font-bold text-brand-dark">
                  <span>Total à payer</span>
                  <span>{discountedTotal.toFixed(0)} €</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      {toast && (
        <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  );
};
