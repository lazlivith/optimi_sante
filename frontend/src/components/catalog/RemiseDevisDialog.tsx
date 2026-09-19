import { useEffect, useState } from 'react';
import { AlertTriangle, Loader2, Percent, X } from 'lucide-react';
import { adminOrderService, type LigneDevis } from '../../api/adminOrderService';
import type { OrderResponseDto } from '../../api/orderService';

interface RemiseDevisDialogProps {
  devis: OrderResponseDto;
  onClose: () => void;
  onApplique: (devis: OrderResponseDto) => void;
}

const euros = (montant: number) => montant.toLocaleString('fr-FR', { style: 'currency', currency: 'EUR' });

/**
 * Remise accordée sur un devis avant validation.
 *
 * <p>Les lignes affichent le stock disponible : un tarif dégressif consenti sur 300 unités dont
 * 40 sont en magasin engage une livraison qu'on ne peut pas tenir. Le devis PDF est réémis avec
 * les montants accordés — c'est lui que le client garde.</p>
 */
export function RemiseDevisDialog({ devis, onClose, onApplique }: RemiseDevisDialogProps) {
  const [lignes, setLignes] = useState<LigneDevis[] | null>(null);
  const [taux, setTaux] = useState(String(devis.quoteDiscountRate ?? 0));
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    adminOrderService.getQuoteLines(devis.id).then(setLignes).catch(() => setLignes([]));
  }, [devis.id]);

  useEffect(() => {
    const clavier = (e: KeyboardEvent) => { if (e.key === 'Escape' && !enCours) onClose(); };
    window.addEventListener('keydown', clavier);
    return () => window.removeEventListener('keydown', clavier);
  }, [enCours, onClose]);

  const brut = (lignes ?? []).reduce((total, l) => total + l.sousTotal, 0);
  const tauxNombre = Number(taux) || 0;
  const remise = Math.round(brut * tauxNombre) / 100;
  const manquants = (lignes ?? []).filter((l) => l.stockDisponible != null && l.stockDisponible < l.quantite);

  const appliquer = async () => {
    setEnCours(true);
    setErreur(null);
    try {
      onApplique(await adminOrderService.applyQuoteDiscount(devis.id, tauxNombre));
    } catch (err: any) {
      setErreur(err.response?.data?.message || "La remise n'a pas pu être appliquée.");
    } finally {
      setEnCours(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center bg-brand-dark/50 backdrop-blur-sm sm:p-4"
      onMouseDown={(e) => { if (e.target === e.currentTarget && !enCours) onClose(); }}
    >
      <div role="dialog" aria-modal="true" aria-labelledby="remise-titre"
        className="bg-white w-full sm:max-w-2xl rounded-t-2xl sm:rounded-2xl shadow-xl max-h-[92vh] flex flex-col">
        <div className="flex items-start justify-between gap-4 px-5 sm:px-6 pt-5 pb-4 border-b border-slate-100">
          <div>
            <h2 id="remise-titre" className="text-lg font-bold text-brand-dark">Ajuster le devis</h2>
            <p className="text-sm text-slate-500 mt-0.5">{devis.orderNumber}</p>
          </div>
          <button type="button" onClick={onClose} disabled={enCours} aria-label="Fermer"
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 disabled:opacity-50">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="overflow-y-auto px-5 sm:px-6 py-5 space-y-5">
          {lignes === null ? (
            <div className="py-6 text-center"><Loader2 className="w-6 h-6 animate-spin mx-auto text-brand" aria-label="Chargement" /></div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[520px] text-sm">
                <thead className="text-left text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="py-2 font-medium">Article</th>
                    <th className="py-2 font-medium text-right">Qté</th>
                    <th className="py-2 font-medium text-right">Stock</th>
                    <th className="py-2 font-medium text-right">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {lignes.map((l, i) => (
                    <tr key={`${l.designation}-${i}`}>
                      <td className="py-2 pr-3 text-slate-700">{l.designation}</td>
                      <td className="py-2 text-right tabular-nums text-slate-700">{l.quantite}</td>
                      <td className={`py-2 text-right tabular-nums ${
                        l.stockDisponible != null && l.stockDisponible < l.quantite ? 'text-danger font-semibold' : 'text-slate-500'}`}>
                        {l.stockDisponible ?? '—'}
                      </td>
                      <td className="py-2 text-right tabular-nums text-slate-700">{euros(l.sousTotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {manquants.length > 0 && (
            <p className="flex gap-2 p-3 rounded-lg bg-warning/10 text-warning text-sm">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" />
              {manquants.length} article(s) demandé(s) au-delà du stock disponible : prévoyez le réapprovisionnement
              avant de vous engager sur un délai.
            </p>
          )}

          <div>
            <label htmlFor="taux-remise" className="block text-xs font-medium text-slate-600 mb-1">
              Remise accordée (%)
            </label>
            <div className="relative sm:max-w-[10rem]">
              <input
                id="taux-remise" type="number" min={0} max={100} step="0.5" value={taux}
                onChange={(e) => setTaux(e.target.value)}
                className="w-full rounded-lg border border-slate-300 p-2.5 pr-9 text-sm focus:border-brand focus:ring-2 focus:ring-brand/20 focus:outline-none"
              />
              <Percent className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
            </div>
          </div>

          <dl className="rounded-xl bg-slate-50 p-4 text-sm space-y-2">
            <div className="flex justify-between gap-4">
              <dt className="text-slate-600">Total des lignes</dt>
              <dd className="tabular-nums text-slate-800">{euros(brut)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-slate-600">Remise {tauxNombre} %</dt>
              <dd className="tabular-nums text-slate-800">− {euros(remise)}</dd>
            </div>
            <div className="flex justify-between gap-4 pt-2 border-t border-slate-200">
              <dt className="font-semibold text-brand-dark">Montant du devis</dt>
              <dd className="tabular-nums text-lg font-bold text-brand-dark">{euros(brut - remise)}</dd>
            </div>
          </dl>

          {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}
        </div>

        <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 px-5 sm:px-6 py-4 border-t border-slate-100">
          <button type="button" onClick={onClose} disabled={enCours}
            className="px-4 py-2.5 rounded-lg border border-slate-300 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60">
            Annuler
          </button>
          <button type="button" onClick={appliquer} disabled={enCours || lignes === null}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg bg-brand text-white text-sm font-semibold hover:bg-brand-fonce disabled:opacity-60">
            {enCours && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
            Appliquer et rééditer le devis
          </button>
        </div>
      </div>
    </div>
  );
}
