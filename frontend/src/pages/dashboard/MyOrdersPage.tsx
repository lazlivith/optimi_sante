import { useEffect, useState } from 'react';
import { orderService } from '../../api/orderService';
import type { OrderResponseDto } from '../../api/orderService';
import { vaultService } from '../../api/vaultService';
import { Package, Loader2, FileText, ExternalLink } from 'lucide-react';

export function MyOrdersPage() {
  const [orders, setOrders] = useState<OrderResponseDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchOrders = async () => {
      try {
        const data = await orderService.getMyOrders();
        setOrders(data.content ?? []);
      } catch (error) {
        console.error('Failed to fetch orders', error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchOrders();
  }, []);

  const handleOpenDocument = async (order: OrderResponseDto) => {
    try {
      const url = await vaultService.getPresignedUrl(order.isQuote ? 'QUOTE' : 'INVOICE', order.id);
      window.open(url, '_blank');
    } catch {
      alert('Impossible d\'ouvrir le document.');
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'UNPAID':
        return <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-amber-100 text-amber-700">NON PAYÉ</span>;
      case 'PAID':
        return <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-emerald-100 text-emerald-700">PAYÉ</span>;
      case 'PENDING_APPROVAL':
        return <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-blue-100 text-blue-700">EN VALIDATION</span>;
      case 'QUOTE_SENT':
        return <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-amber-100 text-amber-700">DEVIS ENVOYÉ</span>;
      case 'QUOTE_REJECTED':
        return <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-rose-100 text-rose-700">DEVIS REJETÉ</span>;
      default:
        return <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-slate-100 text-slate-700">{status}</span>;
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 p-8">
      <div className="max-w-7xl mx-auto space-y-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Mes Commandes & Devis</h1>
          <p className="text-sm text-slate-500 mt-1">Historique de vos commandes et demandes de devis.</p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
          {isLoading ? (
            <div className="p-12 flex justify-center items-center">
              <Loader2 className="w-8 h-8 text-emerald-600 animate-spin" />
            </div>
          ) : orders.length === 0 ? (
            <div className="p-12 text-center text-slate-500">
              <Package className="w-12 h-12 text-slate-300 mx-auto mb-3" />
              <p>Aucune commande pour le moment.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                  <tr>
                    <th className="px-6 py-4">Référence & Date</th>
                    <th className="px-6 py-4">Type</th>
                    <th className="px-6 py-4">Montant Total</th>
                    <th className="px-6 py-4">Statut</th>
                    <th className="px-6 py-4">Document</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {orders.map((order) => (
                    <tr key={order.id} className="hover:bg-slate-50/50 transition-colors">
                      <td className="px-6 py-4">
                        <div className="font-semibold text-slate-900">{order.orderNumber}</div>
                        <div className="text-xs text-slate-500 mt-1">
                          {new Date(order.createdAt).toLocaleDateString('fr-FR', {
                            year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit'
                          })}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-slate-700">
                        {order.isQuote ? 'Demande de devis' : 'Commande'}
                      </td>
                      <td className="px-6 py-4 font-semibold text-slate-900">
                        {order.totalAmount.toFixed(2)} €
                      </td>
                      <td className="px-6 py-4">
                        {getStatusBadge(order.paymentStatus)}
                      </td>
                      <td className="px-6 py-4">
                        {order.documentS3Key ? (
                          <button
                            onClick={() => handleOpenDocument(order)}
                            className="inline-flex items-center text-xs font-medium text-emerald-600 hover:text-emerald-700 hover:underline"
                          >
                            <ExternalLink className="w-3 h-3 mr-1" />
                            Ouvrir le PDF
                          </button>
                        ) : (
                          <span className="text-xs text-slate-400 italic inline-flex items-center">
                            <FileText className="w-3 h-3 mr-1" /> Non disponible
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
