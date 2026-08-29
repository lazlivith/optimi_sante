import { useEffect, useState } from 'react';
import { adminOrderService } from '../../api/adminOrderService';
import type { OrderResponseDto } from '../../api/orderService';
import { vaultService } from '../../api/vaultService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Check, X, FileText, Loader2, Search, ExternalLink } from 'lucide-react';

export function QuotesAdminPage() {
  const [quotes, setQuotes] = useState<OrderResponseDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);

  useEffect(() => {
    fetchQuotes();
  }, []);

  const fetchQuotes = async () => {
    try {
      setIsLoading(true);
      const data = await adminOrderService.getQuotes(0, 50);
      setQuotes(data.content);
    } catch (error) {
      console.error("Failed to fetch quotes", error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleUpdateStatus = async (orderId: string, status: 'VALIDATED' | 'REJECTED') => {
    try {
      setProcessingId(orderId);
      const updatedQuote = await adminOrderService.updateQuoteStatus(orderId, status);
      setQuotes(prev => prev.map(q => q.id === orderId ? updatedQuote : q));
    } catch (error) {
      console.error("Failed to update status", error);
      alert("Une erreur est survenue lors de la mise à jour du devis.");
    } finally {
      setProcessingId(null);
    }
  };

  const handleOpenDocument = async (orderId: string) => {
    try {
      const url = await vaultService.getPresignedUrl('QUOTE', orderId);
      window.open(url, '_blank');
    } catch {
      alert('Impossible d\'ouvrir le document.');
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 p-8">
      <div className="max-w-7xl mx-auto space-y-8">

        <PageHeader
          title="Modération des Devis B2B"
          subtitle="Gérez les demandes de devis (Pro Forma) et générez les devis définitifs."
          actions={
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="text"
                placeholder="Rechercher (ex: OPT-2026...)"
                className="pl-9 pr-4 py-2 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-emerald-500 outline-none w-64"
              />
            </div>
          }
        />

        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
          {isLoading ? (
            <div className="p-12 flex justify-center items-center">
              <Loader2 className="w-8 h-8 text-emerald-600 animate-spin" />
            </div>
          ) : quotes.length === 0 ? (
            <EmptyState icon={FileText} title="Aucun devis trouvé." />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                  <tr>
                    <th className="px-6 py-4">Référence & Date</th>
                    <th className="px-6 py-4">Client</th>
                    <th className="px-6 py-4">Montant Total</th>
                    <th className="px-6 py-4">Statut</th>
                    <th className="px-6 py-4">Document</th>
                    <th className="px-6 py-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {quotes.map((quote) => (
                    <tr key={quote.id} className="hover:bg-slate-50/50 transition-colors">
                      <td className="px-6 py-4">
                        <div className="font-semibold text-slate-900">{quote.orderNumber}</div>
                        <div className="text-xs text-slate-500 mt-1">
                          {new Date(quote.createdAt).toLocaleDateString('fr-FR', {
                            year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute:'2-digit'
                          })}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="font-medium text-slate-700">Client B2B</div>
                        {/* Note: Ideally we'd have the client email/name from the backend in OrderResponseDto */}
                        <div className="text-xs text-slate-500">Profil Acheteur</div>
                      </td>
                      <td className="px-6 py-4 font-semibold text-slate-900">
                        {quote.totalAmount.toFixed(2)} €
                      </td>
                      <td className="px-6 py-4">
                        <StatusBadge status={quote.status} />
                      </td>
                      <td className="px-6 py-4">
                        {quote.documentS3Key ? (
                          <button
                            onClick={() => handleOpenDocument(quote.id)}
                            className="inline-flex items-center text-xs font-medium text-emerald-600 hover:text-emerald-700 hover:underline"
                          >
                            <ExternalLink className="w-3 h-3 mr-1" />
                            Ouvrir le PDF
                          </button>
                        ) : (
                          <span className="text-xs text-slate-400 italic">Non généré</span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-right space-x-2">
                        {quote.status === 'PENDING' && (
                          <>
                            <button
                              onClick={() => handleUpdateStatus(quote.id, 'VALIDATED')}
                              disabled={processingId === quote.id}
                              className="inline-flex items-center justify-center p-2 bg-emerald-100 text-emerald-700 rounded-lg hover:bg-emerald-200 transition disabled:opacity-50"
                              title="Valider et générer le devis"
                            >
                              {processingId === quote.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                            </button>
                            <button
                              onClick={() => handleUpdateStatus(quote.id, 'REJECTED')}
                              disabled={processingId === quote.id}
                              className="inline-flex items-center justify-center p-2 bg-rose-100 text-rose-700 rounded-lg hover:bg-rose-200 transition disabled:opacity-50"
                              title="Rejeter la demande"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          </>
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
