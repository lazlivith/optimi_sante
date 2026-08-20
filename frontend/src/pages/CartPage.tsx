import { useState } from 'react';
import { useCart } from '../context/CartContext';
import { orderService, type CheckoutRequestDto, type QuoteRequestDto } from '../api/orderService';
import { Link } from 'react-router-dom';
import { Loader2, Box, Trash2, ArrowLeft, Lock } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export function CartPage() {
  const { items, removeFromCart, clearCart, totalPrice, addToCart } = useCart();
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successQuote, setSuccessQuote] = useState(false);
  const { isAuthenticated } = useAuth();

  const hasQuoteOnlyItems = items.some(i => i.isQuoteOnly);

  const handleStripeCheckout = async () => {
    if (!isAuthenticated) {
      window.location.href = '/login';
      return;
    }
    
    setIsProcessing(true);
    setError(null);
    try {
      const request: CheckoutRequestDto = {
        items: items.map(item => ({ productId: item.id, quantity: item.cartQuantity })),
        paymentMethod: 'STRIPE_CARD'
      };
      const response = await orderService.checkout(request);
      if (response.paymentUrl) {
        clearCart();
        window.location.href = response.paymentUrl;
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Erreur lors du checkout.');
      setIsProcessing(false);
    }
  };

  const handleQuoteRequest = async () => {
    setIsProcessing(true);
    setError(null);
    try {
      const request: QuoteRequestDto = {
        items: items.map(item => ({ productId: item.id, quantity: item.cartQuantity })),
        notes: "Demande de devis depuis le site web."
      };
      await orderService.quoteRequest(request);
      clearCart();
      setSuccessQuote(true);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Erreur lors de la demande de devis.');
    } finally {
      setIsProcessing(false);
    }
  };

  const updateQuantity = (product: any, delta: number) => {
    const newQuantity = product.cartQuantity + delta;
    if (newQuantity < 1) return;
    // We add the delta. addToCart handles logic if it exists
    addToCart(product, delta);
  };

  if (successQuote) {
    return (
      <div className="max-w-2xl mx-auto py-20 text-center space-y-6 bg-white rounded-2xl shadow-sm border p-8">
        <h2 className="text-3xl font-bold text-brand-dark">Devis envoyé avec succès !</h2>
        <p className="text-slate-600 text-lg">Nos équipes reviendront vers vous dans les 24h ouvrées.</p>
        <Link to="/catalog" className="inline-block mt-4 text-brand-green font-medium hover:underline">
          Retour au catalogue
        </Link>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="max-w-4xl mx-auto py-12">
        <Link to="/catalog" className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-8 transition-colors">
          <ArrowLeft className="w-4 h-4 mr-2" />
          Continuer mes achats
        </Link>
        <div className="text-center py-20 bg-white rounded-2xl border border-slate-200">
          <h2 className="text-2xl font-bold text-brand-dark mb-4">Votre panier est vide.</h2>
          <Link to="/catalog" className="text-brand-green font-medium hover:underline">Parcourir le catalogue</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto px-6 py-12 max-w-5xl">
      <Link to="/catalog" className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-8 transition-colors">
        <ArrowLeft className="w-4 h-4 mr-2" />
        Continuer mes achats
      </Link>

      <div className="mb-8">
        <span className="text-xs font-semibold text-slate-400 tracking-widest uppercase mb-2 block">
          PANIER ({isAuthenticated ? 'CONNECTÉ' : 'INVITÉ'})
        </span>
        <h1 className="text-3xl md:text-4xl font-bold text-brand-dark">Votre panier</h1>
      </div>

      {error && (
        <div className="mb-6 p-4 bg-red-50 text-red-600 rounded-lg border border-red-200 font-medium">
          {error}
        </div>
      )}

      <div className="grid lg:grid-cols-3 gap-8 items-start">
        {/* Cart Items (Left side - 2 columns width) */}
        <div className="lg:col-span-2 space-y-4">
          {items.map((item) => (
            <div key={item.id} className="bg-white rounded-2xl border border-slate-200 p-4 sm:p-6 flex flex-col sm:flex-row items-center gap-6 shadow-sm">
              <div className="w-12 h-12 rounded-xl bg-brand-light flex-shrink-0 flex items-center justify-center text-brand-green hidden sm:flex">
                <Box className="w-6 h-6" />
              </div>
              
              <div className="flex-1 text-center sm:text-left w-full sm:w-auto">
                <h3 className="font-bold text-lg text-brand-dark leading-tight">{item.name}</h3>
                <span className="text-xs font-mono text-slate-400">{item.sku}</span>
                {item.isQuoteOnly && <div className="text-xs font-bold text-brand-orange mt-1">SUR DEVIS</div>}
              </div>
              
              <div className="flex items-center gap-6 w-full sm:w-auto justify-between sm:justify-end">
                <div className="flex items-center border border-slate-200 rounded-lg overflow-hidden">
                  <button onClick={() => updateQuantity(item, -1)} className="px-3 py-1.5 hover:bg-slate-50 text-slate-500 font-medium transition-colors">-</button>
                  <span className="px-3 py-1.5 font-semibold text-brand-dark bg-white min-w-[2.5rem] text-center">{item.cartQuantity}</span>
                  <button onClick={() => updateQuantity(item, 1)} className="px-3 py-1.5 hover:bg-slate-50 text-slate-500 font-medium transition-colors">+</button>
                </div>
                
                <div className="w-24 text-right font-bold text-lg text-brand-dark">
                  {item.isQuoteOnly ? '--' : `${(item.finalPrice * item.cartQuantity).toFixed(0)} €`}
                </div>
                
                <button onClick={() => removeFromCart(item.id)} className="p-2 text-red-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors">
                  <Trash2 className="w-5 h-5" />
                </button>
              </div>
            </div>
          ))}
        </div>

        {/* Summary (Right side - 1 column width) */}
        <div className="lg:col-span-1 bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 shadow-sm sticky top-28">
          <div className="flex justify-between items-center mb-8">
            <span className="font-bold text-lg text-brand-dark">Total</span>
            <span className="text-2xl font-bold text-brand-dark">
              {hasQuoteOnlyItems ? 'Sur devis' : `${totalPrice.toFixed(0)} €`}
            </span>
          </div>

          {hasQuoteOnlyItems ? (
             <button 
                onClick={handleQuoteRequest}
                disabled={isProcessing}
                className="w-full flex items-center justify-center px-6 py-4 bg-brand-orange text-white font-bold rounded-xl hover:bg-orange-600 disabled:opacity-50 transition-colors shadow-sm"
              >
                {isProcessing ? <Loader2 className="animate-spin mr-2 w-5 h-5" /> : null}
                Demander un devis
              </button>
          ) : (
             <button 
                onClick={handleStripeCheckout}
                disabled={isProcessing}
                className="w-full flex items-center justify-center px-6 py-4 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] disabled:opacity-50 transition-colors shadow-sm"
              >
                {isProcessing ? (
                  <Loader2 className="animate-spin mr-2 w-5 h-5" />
                ) : !isAuthenticated ? (
                  <Lock className="w-4 h-4 mr-2" />
                ) : null}
                {isAuthenticated ? 'Payer la commande' : 'Se connecter pour commander'}
              </button>
          )}

          {!isAuthenticated && !hasQuoteOnlyItems && (
            <p className="text-[10px] text-center text-slate-400 font-mono tracking-widest uppercase mt-4">
              Un compte est requis pour finaliser un achat
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
