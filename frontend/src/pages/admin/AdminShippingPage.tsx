import { useEffect, useState } from 'react';
import { Loader2, Truck, Save } from 'lucide-react';
import { adminShippingService, type TarifLivraison } from '../../api/adminShippingService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';

/**
 * Les frais de port, zone par zone.
 *
 * <p>Quatre lignes seulement : France métropolitaine, et trois zones africaines. Un tarif par
 * pays serait quarante lignes à tenir à jour, pour un écart de coût qui, dans une même zone,
 * tient au transporteur plus qu'au pays.</p>
 *
 * <p><b>Une zone à zéro n'est pas une livraison offerte.</b> C'est un tarif non renseigné — et
 * l'écran le dit, parce que la différence se paie en marge perdue sur chaque commande. La
 * gratuité, elle, se déclare par un seuil : au-delà de ce montant de commande, le transport
 * n'est plus facturé.</p>
 *
 * <p><b>Désactiver vaut mieux que mettre zéro.</b> Une zone désactivée refuse la commande avec
 * une invitation à demander un devis ; une zone à zéro l'accepte et offre le transport.</p>
 */
export function AdminShippingPage() {
  const [grille, setGrille] = useState<TarifLivraison[]>([]);
  const [chargement, setChargement] = useState(true);
  const [enCours, setEnCours] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const charger = async () => {
    setChargement(true);
    try {
      setGrille(await adminShippingService.grille());
    } catch {
      setToast({ message: 'Impossible de charger la grille de livraison.', type: 'error' });
    } finally {
      setChargement(false);
    }
  };

  useEffect(() => { charger(); }, []);

  const modifier = (zone: string, champ: Partial<TarifLivraison>) => {
    setGrille(prev => prev.map(t => (t.zone === zone ? { ...t, ...champ } : t)));
  };

  const enregistrer = async (tarif: TarifLivraison) => {
    if (tarif.amount < 0 || (tarif.freeFrom !== null && tarif.freeFrom < 0)) {
      setToast({ message: 'Les montants ne peuvent pas être négatifs.', type: 'error' });
      return;
    }
    setEnCours(tarif.zone);
    try {
      const enregistre = await adminShippingService.enregistrer(tarif.zone, {
        amount: tarif.amount, freeFrom: tarif.freeFrom, active: tarif.active,
      });
      setGrille(prev => prev.map(t => (t.zone === enregistre.zone ? enregistre : t)));
      setToast({ message: `Tarif enregistré — ${enregistre.libelle}.`, type: 'success' });
    } catch (err: any) {
      setToast({
        message: err.response?.data?.message || "Erreur lors de l'enregistrement.",
        type: 'error',
      });
    } finally {
      setEnCours(null);
    }
  };

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-4xl">
      <PageHeader
        title="Frais de livraison"
        subtitle="Le tarif appliqué à chaque zone, et le montant à partir duquel la livraison est offerte."
      />

      <div className="mb-6 rounded-2xl border border-brand-light bg-brand-cream p-5 text-sm text-brand-dark">
        <p className="leading-relaxed">
          <strong>Hors de France, les envois sont livrés DAP</strong> : le destinataire acquitte
          les droits de douane et les taxes locales à l'arrivée. Ces frais ne sont pas inclus
          dans les tarifs ci-dessous, et la mention figure sur les documents de vente.
        </p>
      </div>

      {chargement ? (
        <div className="p-12 flex justify-center">
          <Loader2 className="w-8 h-8 text-brand animate-spin" />
        </div>
      ) : (
        <div className="space-y-4">
          {grille.map(tarif => (
            <div
              key={tarif.zone}
              className={`rounded-2xl border bg-white p-5 shadow-sm transition-colors ${
                tarif.active ? 'border-slate-200' : 'border-slate-200 opacity-70'
              }`}
            >
              <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
                <div className="flex items-center gap-2.5">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-light text-brand">
                    <Truck className="h-4 w-4" aria-hidden="true" />
                  </span>
                  <span className="font-bold text-brand-dark">{tarif.libelle}</span>
                </div>

                <label className="flex items-center gap-2 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked={tarif.active}
                    onChange={e => modifier(tarif.zone, { active: e.target.checked })}
                    className="h-4 w-4 rounded border-slate-300"
                  />
                  Zone desservie
                </label>
              </div>

              <div className="grid gap-4 sm:grid-cols-3">
                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-1">
                    Tarif (€)
                  </label>
                  <input
                    type="number" step="0.01" min="0"
                    value={tarif.amount}
                    onChange={e => modifier(tarif.zone, { amount: parseFloat(e.target.value) || 0 })}
                    className="w-full rounded-md border border-slate-300 p-2.5"
                  />
                  {tarif.amount === 0 && tarif.active && (
                    // Zero se lit comme « gratuit » a l'ecran : le dire evite de livrer a perte
                    // en croyant n'avoir rien decide.
                    <p className="mt-1 text-xs text-brand-accent">
                      À zéro, la livraison est offerte sur toute commande de cette zone.
                    </p>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-1">
                    Offerte à partir de (€)
                  </label>
                  <input
                    type="number" step="0.01" min="0"
                    value={tarif.freeFrom ?? ''}
                    placeholder="Jamais"
                    onChange={e => modifier(tarif.zone, {
                      freeFrom: e.target.value ? parseFloat(e.target.value) : null,
                    })}
                    className="w-full rounded-md border border-slate-300 p-2.5"
                  />
                  <p className="mt-1 text-xs text-slate-500">
                    Vide : la livraison est toujours facturée.
                  </p>
                </div>

                <div className="flex items-end">
                  <button
                    type="button"
                    onClick={() => enregistrer(tarif)}
                    disabled={enCours === tarif.zone}
                    className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 font-bold text-white transition-colors hover:bg-brand-fonce disabled:opacity-60"
                  >
                    {enCours === tarif.zone
                      ? <Loader2 className="h-4 w-4 animate-spin" />
                      : <Save className="h-4 w-4" />}
                    Enregistrer
                  </button>
                </div>
              </div>

              {!tarif.active && (
                <p className="mt-3 text-xs text-slate-500">
                  Zone non desservie : la commande est refusée, avec une invitation à demander
                  un devis de transport.
                </p>
              )}
            </div>
          ))}
        </div>
      )}

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}
