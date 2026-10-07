import { useEffect, useState } from 'react';
import { Loader2, Receipt } from 'lucide-react';
import { adminFiscaliteService } from '../../api/adminFiscaliteService';
import { TvaCategoriesPanel } from '../../components/catalog/TvaCategoriesPanel';
import { RevisionsPrixPanel } from '../../components/catalog/RevisionsPrixPanel';
import { PageHeader } from '../../components/common/PageHeader';
import { Toast, type ToastType } from '../../components/common/Toast';

/**
 * Ce que l'administration pilote elle-même : l'affichage de la TVA, sa grille, et les prix.
 *
 * <p>Trois leviers qui demandaient jusqu'ici un développeur — une variable d'environnement
 * pour le premier, une migration SQL écrite à la main pour le dernier. Ce sont pourtant des
 * décisions commerciales, prises par des gens qui n'ont ni les accès de l'hébergeur ni de
 * raison d'attendre un déploiement.</p>
 */
export function AdminFiscalitePage() {
  const [affichageTva, setAffichageTva] = useState<boolean | null>(null);
  const [bascule, setBascule] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const erreur = (message: string) => setToast({ message, type: 'error' });
  const succes = (message: string) => setToast({ message, type: 'success' });

  useEffect(() => {
    adminFiscaliteService.lireAffichageTva()
      .then(setAffichageTva)
      .catch(() => erreur("Impossible de lire l'état de l'affichage de la TVA."));
  }, []);

  const basculer = async () => {
    if (affichageTva === null) return;
    const cible = !affichageTva;

    if (cible && !window.confirm(
      "Afficher la ventilation de la TVA sur les reçus, devis et factures ?\n\n"
      + "Les montants ne changent pas : les prix sont annoncés TTC, la taxe est extraite dans "
      + "les deux cas.\n\nMais chaque document portera alors le taux de sa catégorie. "
      + "Assurez-vous que les catégories signalées « à confirmer » ont été arbitrées par votre "
      + "comptable : une mention de taux erronée figure sur une pièce comptable.")) return;

    setBascule(true);
    try {
      setAffichageTva(await adminFiscaliteService.definirAffichageTva(cible));
      succes(cible
        ? 'La ventilation de la TVA apparaît désormais sur les documents.'
        : 'La ventilation de la TVA est retirée des documents.');
    } catch (err: any) {
      erreur(err.response?.data?.message || "Le réglage n'a pas pu être enregistré.");
    } finally {
      setBascule(false);
    }
  };

  return (
    <div className="max-w-5xl p-4 sm:p-6 lg:p-8">
      <PageHeader
        title="Fiscalité & prix"
        subtitle="L'affichage de la TVA, sa grille par famille de produits, et les révisions tarifaires."
      />

      {/* L'interrupteur d'abord : c'est la decision qui conditionne tout ce que les documents
          annoncent, et elle doit se lire avant la grille qui la nourrit. */}
      <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-light text-brand">
              <Receipt className="h-5 w-5" aria-hidden="true" />
            </span>
            <div>
              <h2 className="font-bold text-brand-dark">Ventilation de la TVA sur les documents</h2>
              <p className="mt-1 max-w-xl text-sm leading-relaxed text-slate-600">
                Fermée, les reçus et devis affichent le montant réglé sans détail de taxe.
                Ouverte, ils portent « dont TVA », par taux. <strong>Aucun montant ne change</strong> :
                les prix sont annoncés TTC, la taxe est extraite dans les deux cas.
              </p>
            </div>
          </div>

          {affichageTva === null ? (
            <Loader2 className="h-5 w-5 animate-spin text-brand" aria-hidden="true" />
          ) : (
            <button
              type="button" onClick={basculer} disabled={bascule}
              role="switch" aria-checked={affichageTva}
              className={`inline-flex shrink-0 items-center gap-2 rounded-xl px-4 py-2.5 font-bold transition-colors disabled:opacity-60 ${
                affichageTva
                  ? 'bg-brand text-white hover:bg-brand-fonce'
                  : 'border border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
            >
              {bascule && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
              <span
                aria-hidden="true"
                className={`h-2.5 w-2.5 rounded-full ${affichageTva ? 'bg-white' : 'bg-slate-300'}`}
              />
              {affichageTva ? 'Affichée' : 'Masquée'}
            </button>
          )}
        </div>
      </section>

      <div className="space-y-6">
        <TvaCategoriesPanel onErreur={erreur} />
        <RevisionsPrixPanel onErreur={erreur} onSucces={succes} />
      </div>

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}
