import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import { orderService, type CheckoutRequestDto } from '../api/orderService';
import { StripeEmbeddedCheckout } from '../components/payment/StripeEmbeddedCheckout';
import { Loader2, ArrowLeft, CreditCard, Building, Tag, X } from 'lucide-react';
import { Toast, type ToastType } from '../components/common/Toast';
import { usePageMeta } from '../hooks/usePageMeta';
import {
  FormulaireLivraison, ADRESSE_VIDE, adresseComplete, type AdresseLivraison,
} from '../components/checkout/FormulaireLivraison';
import type { EstimationLivraison } from '../api/adminShippingService';
import { useDevise } from '../context/DeviseContext';
import { DetailTva } from '../components/catalog/DetailTva';

export const CheckoutPage = () => {
  usePageMeta('Paiement sécurisé');
  const { items, totalPrice, clearCart } = useCart();
  const { devise, prix } = useDevise();
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

  const [adresse, setAdresse] = useState<AdresseLivraison>(ADRESSE_VIDE);
  const [frais, setFrais] = useState<EstimationLivraison | null>(null);

  // Une demande de devis ne part pas en colis : le transport se chiffre avec le devis, pas
  // dans le tunnel. Partout ailleurs, la marchandise doit aller quelque part.
  const livraisonRequise = paymentMethod !== 'QUOTE_REQUEST';
  const fraisPort = livraisonRequise ? (frais?.montant ?? 0) : 0;
  const totalAPayer = discountedTotal + fraisPort;
  // Tant que la destination n'est pas desservie, `frais` reste nul : le bouton doit rester
  // bloque, sinon le client valide une commande que personne ne peut expedier.
  const livraisonPrete = !livraisonRequise || (adresseComplete(adresse) && frais !== null);

  // Le nom du titulaire du compte est le destinataire le plus probable ; il reste modifiable.
  useEffect(() => {
    if (!user) return;
    // Un professionnel se fait livrer au nom de sa structure, un particulier au sien.
    const nom = (user.companyName
      || [user.firstName, user.lastName].filter(Boolean).join(' ')).trim();
    if (nom) setAdresse(prec => (prec.recipient ? prec : { ...prec, recipient: nom }));
  }, [user]);

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

  /**
   * L'adresse, telle que le contrat de l'API l'attend.
   *
   * Rien n'est transmis pour une demande de devis : sans pays, le serveur ne facture aucun
   * frais de port, ce qui est exactement le comportement d'avant cette page.
   */
  const champsLivraison = () => (livraisonRequise ? {
    // La devise dit en quelle monnaie facturer, jamais combien : le montant encaisse est
    // recalcule par le serveur a partir du prix de reference.
    devise: devise.code,
    shippingRecipient: adresse.recipient.trim(),
    shippingLine1: adresse.line1.trim(),
    shippingLine2: adresse.line2.trim() || undefined,
    shippingPostalCode: adresse.postalCode.trim(),
    shippingCity: adresse.city.trim(),
    shippingCountry: adresse.country,
  } : {});

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
          promoCode,
          ...champsLivraison()
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
          promoCode,
          ...champsLivraison()
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
            <StripeEmbeddedCheckout clientSecret={stripeClientSecret} payLabel={`Payer ${prix(totalAPayer)}`} />
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
          <button onClick={() => navigate('/catalog')} className="px-6 py-3 bg-brand text-white font-bold rounded-xl hover:bg-brand-fonce transition-colors">
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
          {/* Left Column: Shipping address, then payment methods */}
          <div className="space-y-6">
            {livraisonRequise && (
              <FormulaireLivraison
                adresse={adresse}
                onAdresseChange={setAdresse}
                montantArticles={discountedTotal}
                onFraisChange={setFrais}
              />
            )}

            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
              <h2 className="text-xl font-semibold text-brand-dark mb-6">Mode de paiement</h2>
              
              <div className="space-y-4 mb-8">
                {forceQuote ? (
                  <label className={`flex items-center p-4 border rounded-xl cursor-pointer transition-colors border-brand bg-brand-light`}>
                    <input type="radio" name="paymentMethod" value="QUOTE_REQUEST" checked readOnly className="mr-4 h-4 w-4 text-brand focus:ring-brand" />
                    <Building className="w-5 h-5 text-slate-500 mr-3" />
                    <span className="font-medium text-slate-800">Demande de devis obligatoire</span>
                  </label>
                ) : (
                  <>
                    <label className={`flex items-center p-4 border rounded-xl cursor-pointer transition-colors ${paymentMethod === 'STRIPE_CARD' ? 'border-brand bg-brand-light' : 'border-slate-200 hover:bg-slate-50'}`}>
                      <input type="radio" name="paymentMethod" value="STRIPE_CARD" checked={paymentMethod === 'STRIPE_CARD'} onChange={() => setPaymentMethod('STRIPE_CARD')} className="mr-4 h-4 w-4 text-brand focus:ring-brand" />
                      <CreditCard className="w-5 h-5 text-slate-500 mr-3" />
                      <span className="font-medium text-slate-800">Carte Bancaire (Stripe)</span>
                    </label>
                    
                    <label className={`flex items-center p-4 border rounded-xl cursor-pointer transition-colors ${paymentMethod === 'BANK_TRANSFER' ? 'border-brand bg-brand-light' : 'border-slate-200 hover:bg-slate-50'}`}>
                      <input type="radio" name="paymentMethod" value="BANK_TRANSFER" checked={paymentMethod === 'BANK_TRANSFER'} onChange={() => setPaymentMethod('BANK_TRANSFER')} className="mr-4 h-4 w-4 text-brand focus:ring-brand" />
                      <Building className="w-5 h-5 text-slate-500 mr-3" />
                      <span className="font-medium text-slate-800">Virement Bancaire</span>
                    </label>

                    {isB2B && (
                      <label className={`flex items-center p-4 border rounded-xl cursor-pointer transition-colors ${paymentMethod === 'QUOTE_REQUEST' ? 'border-brand bg-brand-light' : 'border-slate-200 hover:bg-slate-50'}`}>
                        <input type="radio" name="paymentMethod" value="QUOTE_REQUEST" checked={paymentMethod === 'QUOTE_REQUEST'} onChange={() => setPaymentMethod('QUOTE_REQUEST')} className="mr-4 h-4 w-4 text-brand focus:ring-brand" />
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
                    <button onClick={handleDirectCheckout} disabled={isProcessing || !livraisonPrete} title={livraisonPrete ? undefined : 'Renseignez une adresse de livraison desservie pour continuer.'} className="flex-1 flex justify-center items-center px-6 py-3 bg-brand text-white font-bold rounded-xl hover:bg-brand-fonce transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
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
                    <span className="font-semibold">{item.isQuoteOnly ? '--' : prix(item.finalPrice * item.cartQuantity)}</span>
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
                        className="flex-1 min-w-0 px-3 py-2 text-sm border border-slate-200 rounded-lg uppercase focus:outline-none focus:ring-2 focus:ring-brand/20 focus:border-brand"
                      />
                      <button
                        onClick={handleApplyPromoCode}
                        disabled={isValidatingPromo || !promoCodeInput.trim()}
                        className="px-4 py-2 text-sm font-semibold text-brand border border-brand rounded-lg hover:bg-brand-light transition-colors disabled:opacity-50 shrink-0"
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
                    <span>− {prix(appliedPromo.discountAmount)}</span>
                  </div>
                )}
                {livraisonRequise && (
                  <div className="flex justify-between items-center text-sm text-slate-600">
                    <span>Livraison{frais ? ` — ${frais.zoneLibelle}` : ''}</span>
                    {frais
                      ? (frais.offerte
                        ? <span className="font-semibold text-success">Offerte</span>
                        : <span>{prix(frais.montant)}</span>)
                      : <span className="text-slate-400">À calculer</span>}
                  </div>
                )}
                <div className="flex justify-between items-center text-lg font-bold text-brand-dark">
                  <span>Total à payer</span>
                  <span>{prix(totalAPayer)}</span>
                </div>

                {/* Apres le total, et non avant : la taxe est DEJA dans les prix annonces.
                    La placer parmi les lignes qui s'additionnent laisserait croire qu'elle
                    s'ajoute au montant a regler. */}
                <DetailTva items={items} remise={appliedPromo?.discountAmount} />
                {frais?.droitsALArrivee && (
                  // Mention DAP : hors de France, les droits et taxes locales restent a la
                  // charge du destinataire. Le dire ici, pas a la livraison.
                  <p className="pt-2 text-xs leading-relaxed text-slate-500">
                    Livraison DAP : les droits de douane et taxes locales sont à régler par le
                    destinataire à l'arrivée, et ne sont pas inclus dans ce total.
                  </p>
                )}
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
