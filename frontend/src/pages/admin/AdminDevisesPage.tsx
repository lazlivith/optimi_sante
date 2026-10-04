import { useEffect, useState } from 'react';
import { Loader2, Save, Coins, Lock } from 'lucide-react';
import { adminDevisesService, type DeviseTaux } from '../../api/adminDevisesService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';

/** Le montant d'exemple de l'aperçu : assez gros pour que l'effet du palier se voie. */
const EXEMPLE_EUROS = 500;

/**
 * Les devises dans lesquelles la plateforme peut afficher ses prix.
 *
 * <p>Un prix n'existe qu'une fois, en euros. Les autres devises s'en déduisent par le taux
 * saisi ici : il n'y a donc pas de second catalogue à tenir, et aucun produit ne peut devenir
 * invendable dans une zone parce qu'on a oublié de lui donner un prix.</p>
 *
 * <p>L'aperçu n'est pas un ornement. Un palier mal choisi ne se voit pas sur un taux — il se
 * voit sur un prix. L'écran montre donc en permanence ce que donnerait une vente de 500 €.</p>
 */
export function AdminDevisesPage() {
  const [grille, setGrille] = useState<DeviseTaux[]>([]);
  const [chargement, setChargement] = useState(true);
  const [enCours, setEnCours] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const charger = async () => {
    setChargement(true);
    try {
      setGrille(await adminDevisesService.grille());
    } catch {
      setToast({ message: 'Impossible de charger les devises.', type: 'error' });
    } finally {
      setChargement(false);
    }
  };

  useEffect(() => { charger(); }, []);

  const modifier = (devise: string, champ: Partial<DeviseTaux>) =>
    setGrille(prev => prev.map(d => (d.devise === devise ? { ...d, ...champ } : d)));

  const enregistrer = async (ligne: DeviseTaux) => {
    if (ligne.taux <= 0 || ligne.palierArrondi <= 0) {
      setToast({ message: 'Le taux et le palier doivent être strictement positifs.', type: 'error' });
      return;
    }
    setEnCours(ligne.devise);
    try {
      const enregistre = await adminDevisesService.enregistrer(ligne.devise, {
        taux: ligne.taux, palierArrondi: ligne.palierArrondi, actif: ligne.actif,
      });
      setGrille(prev => prev.map(d => (d.devise === enregistre.devise ? enregistre : d)));
      setToast({ message: `Taux enregistré — ${enregistre.devise}.`, type: 'success' });
    } catch (err: any) {
      setToast({
        message: err.response?.data?.message || "Erreur lors de l'enregistrement.",
        type: 'error',
      });
    } finally {
      setEnCours(null);
    }
  };

  /* L'apercu est calcule ici, avec la meme regle que le serveur : le montrer apres un
     aller-retour reseau le rendrait inerte pendant la saisie, au moment ou il sert. */
  const apercu = (ligne: DeviseTaux) => {
    const converti = EXEMPLE_EUROS * ligne.taux;
    const palier = ligne.palierArrondi;
    const arrondi = Math.ceil(converti / palier) * palier;
    return arrondi.toLocaleString('fr-FR', {
      minimumFractionDigits: ligne.decimales, maximumFractionDigits: ligne.decimales,
    });
  };

  const champ = 'w-full rounded-md border border-slate-300 p-2.5 disabled:bg-slate-50 '
    + 'disabled:text-slate-400';

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-4xl">
      <PageHeader
        title="Devises et taux de change"
        subtitle="Les devises dans lesquelles la plateforme affiche ses prix, et le taux qui les déduit du prix en euros."
      />

      <div className="mb-6 rounded-2xl border border-brand-light bg-brand-cream p-5 text-sm text-brand-dark">
        <p className="leading-relaxed">
          <strong>Un prix n'existe qu'une fois, en euros.</strong> Les autres devises s'en
          déduisent par le taux ci-dessous : il n'y a pas de second catalogue à tenir, et aucun
          produit ne peut devenir invendable dans une zone faute d'un prix saisi. L'arrondi
          remonte toujours au palier supérieur — on n'encaisse jamais moins que le prix de
          référence par un effet d'arrondi.
        </p>
      </div>

      {chargement ? (
        <div className="flex justify-center p-12">
          <Loader2 className="h-8 w-8 animate-spin text-brand" />
        </div>
      ) : (
        <div className="space-y-4">
          {grille.map(ligne => (
            <div
              key={ligne.devise}
              className={`rounded-2xl border bg-white p-5 shadow-sm transition-colors ${
                ligne.actif ? 'border-slate-200' : 'border-slate-200 opacity-70'
              }`}
            >
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2.5">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-light text-brand">
                    <Coins className="h-4 w-4" aria-hidden="true" />
                  </span>
                  <span className="font-bold text-brand-dark">{ligne.devise}</span>
                  {ligne.reference && (
                    <span className="inline-flex items-center gap-1 rounded-lg bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-500">
                      <Lock className="h-3 w-3" aria-hidden="true" /> Devise de référence
                    </span>
                  )}
                </div>

                {!ligne.reference && (
                  <label className="flex items-center gap-2 text-sm text-slate-600">
                    <input
                      type="checkbox" checked={ligne.actif}
                      onChange={e => modifier(ligne.devise, { actif: e.target.checked })}
                      className="h-4 w-4 rounded border-slate-300"
                    />
                    Ouverte à la vente
                  </label>
                )}
              </div>

              {ligne.reference ? (
                <p className="text-sm leading-relaxed text-slate-500">
                  Les prix du catalogue sont libellés en euros. Son taux vaut 1 par définition et
                  ne se modifie pas : le laisser changer rendrait tous les prix de la plateforme
                  dépendants d'une saisie.
                </p>
              ) : (
                <>
                  <div className="grid gap-4 sm:grid-cols-3">
                    <div>
                      <label className="mb-1 block text-xs font-medium text-slate-600">
                        1 EUR vaut
                      </label>
                      <input
                        type="number" step="0.000001" min="0"
                        value={ligne.taux}
                        onChange={e => modifier(ligne.devise, { taux: parseFloat(e.target.value) || 0 })}
                        className={champ}
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-slate-600">
                        Arrondir au multiple de
                      </label>
                      <input
                        type="number" step="0.01" min="0"
                        value={ligne.palierArrondi}
                        onChange={e => modifier(ligne.devise, {
                          palierArrondi: parseFloat(e.target.value) || 0,
                        })}
                        className={champ}
                      />
                    </div>
                    <div className="flex items-end">
                      <button
                        type="button"
                        onClick={() => enregistrer(ligne)}
                        disabled={enCours === ligne.devise}
                        className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 font-bold text-white transition-colors hover:bg-brand-fonce disabled:opacity-60"
                      >
                        {enCours === ligne.devise
                          ? <Loader2 className="h-4 w-4 animate-spin" />
                          : <Save className="h-4 w-4" />}
                        Enregistrer
                      </button>
                    </div>
                  </div>

                  <p className="mt-4 rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-600">
                    Un produit à <strong>{EXEMPLE_EUROS} €</strong> serait affiché{' '}
                    <strong className="text-brand-dark">{apercu(ligne)} {ligne.devise}</strong>.
                  </p>

                  {!ligne.actif && (
                    <p className="mt-2 text-xs text-slate-500">
                      Devise fermée : elle n'est proposée ni à l'affichage, ni au paiement.
                    </p>
                  )}
                </>
              )}
            </div>
          ))}
        </div>
      )}

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}
