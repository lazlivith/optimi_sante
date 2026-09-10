import { useCallback, useEffect, useState } from 'react';
import { Loader2, ShieldCheck, Home, Bus, Plus, XCircle, Wallet } from 'lucide-react';
import {
  serviceOptionService, serviceOptionTypeLabel, SERVICE_OPTION_TYPES,
  type CatalogOption, type ServiceOptionType,
} from '../../api/serviceOptionService';
import { formatAmount } from '../training/TuitionPaymentCard';
import { Toast, type ToastType } from '../common/Toast';

const ICONE: Record<ServiceOptionType, typeof Home> = {
  INSURANCE: ShieldCheck,
  HOUSING: Home,
  TRANSPORT: Bus,
};

/**
 * Catalogue des services d'une formation, côté administration de la mobilité.
 *
 * <p>Ces prestations sont montées et payées par l'agence : le partenaire n'y a aucun accès, et
 * il n'existe aucune route qui le lui donnerait. Même règle que pour les frais de dossier — le
 * CHU ne fixe pas la rémunération d'un travail qu'il n'effectue pas.</p>
 */
export function AdminServiceCatalogPanel({
  trainingId, trainingTitle, onClose,
}: {
  trainingId: string;
  trainingTitle: string;
  onClose: () => void;
}) {
  const [offres, setOffres] = useState<CatalogOption[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [type, setType] = useState<ServiceOptionType>('INSURANCE');
  const [libelle, setLibelle] = useState('');
  const [description, setDescription] = useState('');
  const [prix, setPrix] = useState('');
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const charger = useCallback(async () => {
    try {
      setOffres(await serviceOptionService.catalog(trainingId));
    } catch {
      setToast({ message: 'Catalogue indisponible.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [trainingId]);

  useEffect(() => { charger(); }, [charger]);

  const deposer = async () => {
    const montant = Number(prix.replace(',', '.'));
    if (!libelle.trim()) {
      setToast({ message: 'Décrivez le service : c\'est ce que le médecin lira.', type: 'error' });
      return;
    }
    if (Number.isNaN(montant) || montant < 0) {
      setToast({ message: 'Tarif invalide.', type: 'error' });
      return;
    }
    setBusy(true);
    try {
      await serviceOptionService.upsert(trainingId, {
        optionType: type,
        label: libelle.trim(),
        description: description.trim() || null,
        price: montant,
      });
      setLibelle(''); setDescription(''); setPrix('');
      await charger();
      setToast({ message: 'Service ajouté au catalogue.', type: 'success' });
    } catch (err: any) {
      setToast({ message: err?.response?.data?.message ?? "L'ajout a échoué.", type: 'error' });
    } finally {
      setBusy(false);
    }
  };

  const retirer = async (id: string) => {
    setBusy(true);
    try {
      await serviceOptionService.deactivate(id);
      await charger();
      setToast({ message: 'Service retiré du catalogue.', type: 'success' });
    } catch (err: any) {
      setToast({ message: err?.response?.data?.message ?? 'Le retrait a échoué.', type: 'error' });
    } finally {
      setBusy(false);
    }
  };

  const actives = offres.filter((o) => o.active);
  const retirees = offres.filter((o) => !o.active);
  const aideType = SERVICE_OPTION_TYPES.find((t) => t.value === type)?.exemple ?? '';

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
      <div className="bg-white rounded-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto shadow-xl border border-slate-200">
        <div className="px-6 py-5 border-b border-slate-100 bg-slate-50 sticky top-0">
          <h2 className="inline-flex items-center gap-2 text-lg font-bold text-brand-dark">
            <Wallet className="w-5 h-5 text-brand-green" />
            Services de séjour
          </h2>
          <p className="text-sm text-slate-500 mt-0.5">{trainingTitle}</p>
        </div>

        {isLoading ? (
          <div className="p-12 flex justify-center"><Loader2 className="w-6 h-6 text-brand-green animate-spin" /></div>
        ) : (
          <div className="px-6 py-5 space-y-6">
            <p className="text-sm text-slate-600 bg-sky-50 border border-sky-200 rounded-xl p-3">
              Recette entièrement acquise à Optimi Santé : ces prestations sont montées par
              l'agence, qui règle ses propres prestataires. Le partenaire ne les voit pas et n'en
              fixe pas le prix.
            </p>

            <div>
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-3">
                Offres en vigueur
              </h3>
              {actives.length === 0 ? (
                <p className="text-sm text-slate-500">Aucun service proposé sur cette formation.</p>
              ) : (
                <ul className="space-y-2">
                  {actives.map((o) => {
                    const Icone = ICONE[o.optionType];
                    return (
                      <li key={o.id} className="flex items-start gap-3 p-4 rounded-xl border border-slate-200">
                        <Icone className="w-5 h-5 shrink-0 text-slate-400 mt-0.5" />
                        <div className="flex-1 min-w-0">
                          <p className="font-semibold text-slate-800">{o.label}</p>
                          <p className="text-xs text-slate-500">{serviceOptionTypeLabel(o.optionType)}</p>
                          {o.description && <p className="text-sm text-slate-600 mt-1">{o.description}</p>}
                        </div>
                        <span className="font-bold text-slate-800 whitespace-nowrap">
                          {formatAmount(o.price)}
                        </span>
                        <button
                          type="button" disabled={busy} onClick={() => retirer(o.id)}
                          title="Retirer du catalogue"
                          className="p-2 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 disabled:opacity-40 transition-colors"
                        >
                          <XCircle className="w-4 h-4" />
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>

            <div className="rounded-2xl border border-slate-200 p-4">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-3">
                Proposer un service
              </h3>
              <div className="grid sm:grid-cols-2 gap-3">
                <div>
                  <label htmlFor="type-service" className="block text-sm font-semibold text-slate-700 mb-1">
                    Nature
                  </label>
                  <select
                    id="type-service" value={type}
                    onChange={(e) => setType(e.target.value as ServiceOptionType)}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
                  >
                    {SERVICE_OPTION_TYPES.map((t) => (
                      <option key={t.value} value={t.value}>{t.label}</option>
                    ))}
                  </select>
                  <p className="text-xs text-slate-500 mt-1">{aideType}</p>
                </div>
                <div>
                  <label htmlFor="prix-service" className="block text-sm font-semibold text-slate-700 mb-1">
                    Tarif
                  </label>
                  <div className="flex items-center gap-2">
                    <input
                      id="prix-service" type="number" min="0" step="0.01" value={prix}
                      onChange={(e) => setPrix(e.target.value)}
                      placeholder="0.00"
                      className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
                    />
                    <span className="text-slate-500 font-semibold">€</span>
                  </div>
                </div>
              </div>

              <div className="mt-3">
                <label htmlFor="libelle-service" className="block text-sm font-semibold text-slate-700 mb-1">
                  Intitulé lu par le médecin
                </label>
                <input
                  id="libelle-service" type="text" value={libelle}
                  onChange={(e) => setLibelle(e.target.value)}
                  placeholder="Ex. : studio meublé à 10 min du CHU"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
                />
              </div>

              <div className="mt-3">
                <label htmlFor="desc-service" className="block text-sm font-semibold text-slate-700 mb-1">
                  Ce que la prestation comprend (facultatif)
                </label>
                <textarea
                  id="desc-service" rows={2} value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
                />
              </div>

              {/* Déposer une offre du même type remplace la précédente : le dire évite de
                  découvrir l'effet après coup. */}
              <p className="text-xs text-slate-500 mt-2">
                Une seule offre par nature. Déposer un nouveau tarif retire l'offre en vigueur —
                les souscriptions déjà prises gardent le leur.
              </p>

              <button
                type="button" disabled={busy} onClick={deposer}
                className="mt-3 inline-flex items-center gap-2 px-5 py-2 rounded-xl bg-brand-green text-white text-sm font-bold hover:bg-[#0f3c35] disabled:opacity-50 transition-colors"
              >
                {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                Ajouter au catalogue
              </button>
            </div>

            {retirees.length > 0 && (
              <details>
                <summary className="text-xs font-bold uppercase tracking-wider text-slate-500 cursor-pointer">
                  Offres retirées ({retirees.length})
                </summary>
                <ul className="mt-2 space-y-1 text-sm text-slate-500">
                  {retirees.map((o) => (
                    <li key={o.id}>
                      {serviceOptionTypeLabel(o.optionType)} — {o.label} · {formatAmount(o.price)}
                    </li>
                  ))}
                </ul>
              </details>
            )}
          </div>
        )}

        <div className="px-6 py-4 border-t border-slate-100 bg-slate-50 flex justify-end sticky bottom-0">
          <button
            type="button" onClick={onClose}
            className="px-5 py-2 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100 transition-colors"
          >
            Fermer
          </button>
        </div>
      </div>

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}
