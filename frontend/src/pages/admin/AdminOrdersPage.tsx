import { useEffect, useState } from 'react';
import { adminOrderService } from '../../api/adminOrderService';
import type { OrderResponseDto } from '../../api/orderService';
import { vaultService } from '../../api/vaultService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { ShoppingBag, Loader2, ExternalLink, Check } from 'lucide-react';

const PAYMENT_METHOD_LABELS: Record<string, string> = {
  STRIPE_CARD: 'Carte (Stripe)',
  BANK_TRANSFER: 'Virement bancaire',
  PURCHASE_ORDER_30D: 'Bon de commande 30j',
  QUOTE_REQUEST: 'Demande de devis',
};

export function AdminOrdersPage() {
  const [orders, setOrders] = useState<OrderResponseDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const fetchOrders = async () => {
    setIsLoading(true);
    try {
      const data = await adminOrderService.getAllOrders(0, 50);
      setOrders(data.content);
    } catch {
      setToast({ message: 'Impossible de charger les commandes.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { fetchOrders(); }, []);

  const handleConfirmPayment = async (order: OrderResponseDto) => {
    if (!window.confirm(`Confirmer la réception du paiement pour la commande ${order.orderNumber} (${order.totalAmount.toFixed(2)} €) ?`)) return;
    setProcessingId(order.id);
    try {
      await adminOrderService.confirmPayment(order.id);
      setToast({ message: 'Paiement confirmé — stock déduit et reçu généré.', type: 'success' });
      fetchOrders();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la confirmation.', type: 'error' });
    } finally {
      setProcessingId(null);
    }
  };

  const handleOpenDocument = async (order: OrderResponseDto) => {
    try {
      const url = await vaultService.getPresignedUrl(order.isQuote ? 'QUOTE' : 'INVOICE', order.id);
      window.open(url, '_blank');
    } catch {
      setToast({ message: "Impossible d'ouvrir le document.", type: 'error' });
    }
  };

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Commandes"
        subtitle="Toutes les commandes de la boutique (carte, virement, devis). Un virement bancaire doit être confirmé manuellement une fois les fonds reçus — sans quoi il ne compte jamais comme une vente."
      />

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center"><Loader2 className="w-8 h-8 text-brand-green animate-spin" /></div>
        ) : orders.length === 0 ? (
          <EmptyState icon={ShoppingBag} title="Aucune commande pour le moment." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Référence & Date</th>
                  <th className="px-6 py-4">Mode de paiement</th>
                  <th className="px-6 py-4">Montant</th>
                  <th className="px-6 py-4">Statut paiement</th>
                  <th className="px-6 py-4">Document</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {orders.map((order) => (
                  <tr key={order.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="font-semibold text-slate-900">{order.orderNumber}</div>
                      <div className="text-xs text-slate-500 mt-1">
                        {new Date(order.createdAt).toLocaleDateString('fr-FR', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-slate-600">{PAYMENT_METHOD_LABELS[order.paymentMethod] || order.paymentMethod}</td>
                    <td className="px-6 py-4 font-semibold text-slate-900">{order.totalAmount.toFixed(2)} €</td>
                    <td className="px-6 py-4">
                      <StatusBadge status={order.paymentStatus} />
                    </td>
                    <td className="px-6 py-4">
                      {order.documentS3Key ? (
                        <button onClick={() => handleOpenDocument(order)} className="inline-flex items-center text-xs font-medium text-emerald-600 hover:text-emerald-700 hover:underline">
                          <ExternalLink className="w-3 h-3 mr-1" /> Ouvrir
                        </button>
                      ) : (
                        <span className="text-xs text-slate-400 italic">—</span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-right">
                      {order.paymentStatus === 'UNPAID' && (
                        <button
                          onClick={() => handleConfirmPayment(order)}
                          disabled={processingId === order.id}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-white bg-emerald-600 rounded-lg hover:bg-emerald-700 transition-colors disabled:opacity-50"
                          title="Confirmer la réception du paiement"
                        >
                          {processingId === order.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Check className="w-3.5 h-3.5" />}
                          Confirmer le paiement
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
