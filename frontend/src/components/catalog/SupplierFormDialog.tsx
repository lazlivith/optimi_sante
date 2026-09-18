import { useEffect, useState } from 'react';
import { Loader2, X } from 'lucide-react';
import type { Supplier, SupplierRequest } from '../../api/adminSupplierService';

interface SupplierFormDialogProps {
  /** Nul pour une création. */
  fournisseur: Supplier | null;
  onClose: () => void;
  onEnregistrer: (body: SupplierRequest) => Promise<void>;
}

/** Fiche fournisseur : raison sociale et contact suffisent, le reste se complète plus tard. */
export function SupplierFormDialog({ fournisseur, onClose, onEnregistrer }: SupplierFormDialogProps) {
  const [form, setForm] = useState<SupplierRequest>({
    code: fournisseur?.code ?? '',
    companyName: fournisseur?.companyName ?? '',
    taxId: fournisseur?.taxId ?? '',
    contactName: fournisseur?.contactName ?? '',
    contactEmail: fournisseur?.contactEmail ?? '',
    contactPhone: fournisseur?.contactPhone ?? '',
    commissionRate: fournisseur?.commissionRate ?? 0,
    notes: fournisseur?.notes ?? '',
  });
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    const clavier = (e: KeyboardEvent) => { if (e.key === 'Escape' && !enCours) onClose(); };
    window.addEventListener('keydown', clavier);
    return () => window.removeEventListener('keydown', clavier);
  }, [enCours, onClose]);

  const champ = (cle: keyof SupplierRequest) => ({
    value: (form[cle] ?? '') as string | number,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      setForm((f) => ({ ...f, [cle]: cle === 'commissionRate' ? Number(e.target.value) : e.target.value })),
    className: 'w-full rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand focus:ring-2 focus:ring-brand/20 focus:outline-none',
  });

  const soumettre = async (e: React.FormEvent) => {
    e.preventDefault();
    setEnCours(true);
    setErreur(null);
    try {
      await onEnregistrer(form);
    } catch (err: any) {
      setErreur(err.response?.data?.message || "Le fournisseur n'a pas pu être enregistré.");
    } finally {
      setEnCours(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center bg-brand-dark/50 backdrop-blur-sm sm:p-4"
      onMouseDown={(e) => { if (e.target === e.currentTarget && !enCours) onClose(); }}
    >
      <form
        onSubmit={soumettre}
        role="dialog" aria-modal="true" aria-labelledby="fournisseur-titre"
        className="bg-white w-full sm:max-w-2xl rounded-t-2xl sm:rounded-2xl shadow-xl max-h-[92vh] flex flex-col"
      >
        <div className="flex items-start justify-between gap-4 px-5 sm:px-6 pt-5 pb-4 border-b border-slate-100">
          <h2 id="fournisseur-titre" className="text-lg font-bold text-brand-dark">
            {fournisseur ? `Modifier ${fournisseur.companyName}` : 'Nouveau fournisseur'}
          </h2>
          <button type="button" onClick={onClose} disabled={enCours} aria-label="Fermer"
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 disabled:opacity-50">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="overflow-y-auto px-5 sm:px-6 py-5 grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <label htmlFor="companyName" className="block text-xs font-medium text-slate-600 mb-1">Raison sociale</label>
            <input id="companyName" required maxLength={255} {...champ('companyName')} />
          </div>
          <div>
            <label htmlFor="code" className="block text-xs font-medium text-slate-600 mb-1">
              Code <span className="text-slate-400">(laissé vide : dérivé du nom)</span>
            </label>
            <input id="code" maxLength={40} placeholder="FOURN-001" {...champ('code')} />
          </div>
          <div>
            <label htmlFor="taxId" className="block text-xs font-medium text-slate-600 mb-1">
              SIRET / NIU <span className="text-slate-400">(facultatif)</span>
            </label>
            <input id="taxId" maxLength={50} {...champ('taxId')} />
          </div>
          <div>
            <label htmlFor="contactName" className="block text-xs font-medium text-slate-600 mb-1">Contact</label>
            <input id="contactName" maxLength={150} {...champ('contactName')} />
          </div>
          <div>
            <label htmlFor="contactEmail" className="block text-xs font-medium text-slate-600 mb-1">E-mail</label>
            <input id="contactEmail" type="email" maxLength={255} {...champ('contactEmail')} />
          </div>
          <div>
            <label htmlFor="contactPhone" className="block text-xs font-medium text-slate-600 mb-1">Téléphone</label>
            <input id="contactPhone" maxLength={30} {...champ('contactPhone')} />
          </div>
          <div>
            <label htmlFor="commissionRate" className="block text-xs font-medium text-slate-600 mb-1">
              Commission négociée (%)
            </label>
            <input id="commissionRate" type="number" min={0} max={100} step="0.01" {...champ('commissionRate')} />
          </div>
          <div className="sm:col-span-2">
            <label htmlFor="notes" className="block text-xs font-medium text-slate-600 mb-1">
              Notes <span className="text-slate-400">(conditions, délais de livraison…)</span>
            </label>
            <textarea id="notes" rows={3} {...champ('notes')} />
          </div>
          {erreur && <p role="alert" className="sm:col-span-2 text-sm text-danger">{erreur}</p>}
        </div>

        <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 px-5 sm:px-6 py-4 border-t border-slate-100">
          <button type="button" onClick={onClose} disabled={enCours}
            className="px-4 py-2.5 rounded-lg border border-slate-300 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60">
            Annuler
          </button>
          <button type="submit" disabled={enCours}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg bg-brand text-white text-sm font-semibold hover:bg-brand-fonce disabled:opacity-60">
            {enCours && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
            {fournisseur ? 'Enregistrer' : 'Créer le fournisseur'}
          </button>
        </div>
      </form>
    </div>
  );
}
