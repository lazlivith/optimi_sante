import { useCallback, useEffect, useState } from 'react';
import { Loader2, TrendingUp, Undo2, History } from 'lucide-react';
import {
  adminFiscaliteService, type ApercuRevision, type RevisionPrix,
} from '../../api/adminFiscaliteService';
import { adminCatalogService, type AdminCategoryDto } from '../../api/adminCatalogService';

const euros = (v: number) => v.toLocaleString('fr-FR', { style: 'currency', currency: 'EUR' });

/**
 * Les révisions tarifaires, appliquées depuis l'administration.
 *
 * <p>La hausse de 10 % d'octobre a demandé une migration SQL écrite à la main. Rejouer cela à
 * chaque révision ferait dépendre une décision commerciale d'un déploiement — et d'un
 * développeur disponible le jour où le fournisseur annonce ses nouveaux tarifs.</p>
 *
 * <p><b>L'aperçu n'est pas un confort.</b> Une révision touche quinze cents produits d'un seul
 * geste ; voir le prix moyen, le plus bas et le plus haut avant de valider est ce qui
 * transforme une saisie en décision. Il est calculé par le serveur, avec l'expression qui sera
 * réellement appliquée : une estimation faite autrement finirait par annoncer un chiffre que
 * l'opération ne produit pas.</p>
 */
export function RevisionsPrixPanel({ onErreur, onSucces }: {
  onErreur: (message: string) => void;
  onSucces: (message: string) => void;
}) {
  const [categories, setCategories] = useState<AdminCategoryDto[]>([]);
  const [pourcentage, setPourcentage] = useState('');
  const [categorieId, setCategorieId] = useState('');
  const [libelle, setLibelle] = useState('');
  const [apercu, setApercu] = useState<ApercuRevision | null>(null);
  const [historique, setHistorique] = useState<RevisionPrix[]>([]);
  const [enCours, setEnCours] = useState(false);
  const [annulation, setAnnulation] = useState<string | null>(null);

  const chargerHistorique = useCallback(() => {
    adminFiscaliteService.historiqueRevisions()
      .then(setHistorique)
      .catch(() => onErreur("Impossible de charger l'historique des révisions."));
  }, [onErreur]);

  useEffect(() => {
    adminCatalogService.listCategories()
      .then(l => setCategories(l.filter(c => c.productCount > 0)))
      .catch(() => { /* la revision reste possible sur tout le catalogue */ });
    chargerHistorique();
  }, [chargerHistorique]);

  /* L'apercu se rafraichit a la frappe, apres une pause : sans elle, chaque chiffre tape
     declencherait un calcul sur quinze cents lignes. */
  useEffect(() => {
    const valeur = Number(pourcentage.replace(',', '.'));
    if (!pourcentage || Number.isNaN(valeur) || valeur === 0) {
      setApercu(null);
      return;
    }
    const minuteur = setTimeout(() => {
      adminFiscaliteService.apercuRevision(valeur, categorieId || undefined)
        .then(setApercu)
        .catch(() => setApercu(null));
    }, 500);
    return () => clearTimeout(minuteur);
  }, [pourcentage, categorieId]);

  const appliquer = async () => {
    const valeur = Number(pourcentage.replace(',', '.'));
    if (!apercu || Number.isNaN(valeur) || valeur === 0) return;

    const portee = categorieId
      ? `la catégorie « ${categories.find(c => c.id === categorieId)?.name} »`
      : 'tout le catalogue';
    const confirmation = window.confirm(
      `Appliquer ${valeur > 0 ? '+' : ''}${valeur} % à ${portee} ?\n\n`
      + `${apercu.produitsTouches} produit(s) seront modifiés.\n`
      + `Prix moyen : ${euros(apercu.prixMoyenActuel)} → ${euros(apercu.prixMoyenApres)}\n\n`
      + `Cette révision reste annulable : les prix d'avant sont conservés.`);
    if (!confirmation) return;

    setEnCours(true);
    try {
      await adminFiscaliteService.appliquerRevision(valeur, categorieId || undefined, libelle);
      onSucces(`Révision appliquée à ${apercu.produitsTouches} produit(s).`);
      setPourcentage('');
      setLibelle('');
      setApercu(null);
      chargerHistorique();
    } catch (err: any) {
      onErreur(err.response?.data?.message || "La révision n'a pas pu être appliquée.");
    } finally {
      setEnCours(false);
    }
  };

  const annuler = async (revision: RevisionPrix) => {
    if (!window.confirm(
      `Restituer les prix d'avant cette révision de ${revision.pourcentage} % ?\n\n`
      + `${revision.produitsTouches} produit(s) retrouveront leur prix exact d'origine.`)) return;

    setAnnulation(revision.id);
    try {
      const restaures = await adminFiscaliteService.annulerRevision(revision.id);
      onSucces(`${restaures} prix restitués.`);
      chargerHistorique();
    } catch (err: any) {
      onErreur(err.response?.data?.message || "L'annulation a échoué.");
    } finally {
      setAnnulation(null);
    }
  };

  const champ = 'w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm '
    + 'focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20';

  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
      <div className="border-b border-slate-200 p-4">
        <h2 className="flex items-center gap-2 text-lg font-bold text-brand-dark">
          <TrendingUp className="h-5 w-5 text-brand" aria-hidden="true" /> Révision tarifaire
        </h2>
        <p className="text-sm text-slate-500">
          Un pourcentage appliqué à tout le catalogue ou à une famille. Les prix d'avant sont
          conservés : la révision reste annulable.
        </p>
      </div>

      <div className="grid gap-4 p-4 sm:grid-cols-3">
        <div>
          <label htmlFor="rev-pct" className="mb-1 block text-xs font-medium text-slate-600">
            Pourcentage
          </label>
          <input
            id="rev-pct" className={champ} placeholder="+10 ou -5"
            value={pourcentage} onChange={e => setPourcentage(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor="rev-cat" className="mb-1 block text-xs font-medium text-slate-600">
            Portée
          </label>
          <select id="rev-cat" className={champ}
                  value={categorieId} onChange={e => setCategorieId(e.target.value)}>
            <option value="">Tout le catalogue</option>
            {categories.map(c => (
              <option key={c.id} value={c.id}>{c.name} ({c.productCount})</option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="rev-lib" className="mb-1 block text-xs font-medium text-slate-600">
            Motif <span className="font-normal text-slate-400">(facultatif)</span>
          </label>
          <input
            id="rev-lib" className={champ} placeholder="Hausse fournisseur de janvier"
            value={libelle} onChange={e => setLibelle(e.target.value)}
          />
        </div>
      </div>

      {apercu && (
        <div className="mx-4 mb-4 rounded-xl border border-brand-light bg-brand-cream p-4 text-sm">
          <p className="font-semibold text-brand-dark">
            {apercu.produitsTouches} produit{apercu.produitsTouches > 1 ? 's' : ''} seraient
            modifié{apercu.produitsTouches > 1 ? 's' : ''}.
          </p>
          {apercu.produitsTouches > 0 && (
            <p className="mt-1 text-slate-600">
              Prix moyen {euros(apercu.prixMoyenActuel)} → <strong className="text-brand-dark">
              {euros(apercu.prixMoyenApres)}</strong>. Après révision, les prix iraient de{' '}
              {euros(apercu.prixMinApres)} à {euros(apercu.prixMaxApres)}.
            </p>
          )}
        </div>
      )}

      <div className="px-4 pb-4">
        <button
          type="button" onClick={appliquer}
          disabled={!apercu || apercu.produitsTouches === 0 || enCours}
          className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 font-bold text-white transition-colors hover:bg-brand-fonce disabled:cursor-not-allowed disabled:opacity-50"
        >
          {enCours ? <Loader2 className="h-4 w-4 animate-spin" /> : <TrendingUp className="h-4 w-4" />}
          Appliquer la révision
        </button>
      </div>

      <div className="border-t border-slate-200">
        <h3 className="flex items-center gap-2 px-4 pb-2 pt-4 text-sm font-bold text-brand-dark">
          <History className="h-4 w-4 text-slate-400" aria-hidden="true" /> Révisions passées
        </h3>
        {historique.length === 0 ? (
          <p className="px-4 pb-4 text-sm text-slate-400">Aucune révision enregistrée.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {historique.map(r => (
              <li key={r.id} className={`flex flex-wrap items-center gap-3 p-4 ${r.annuleeLe ? 'opacity-60' : ''}`}>
                <span className={`rounded-lg px-2 py-1 text-sm font-bold ${
                  r.pourcentage > 0 ? 'bg-brand-light text-brand' : 'bg-slate-100 text-slate-600'
                }`}>
                  {r.pourcentage > 0 ? '+' : ''}{r.pourcentage} %
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-brand-dark">
                    {r.libelle || (r.categorieNom ? `Catégorie « ${r.categorieNom} »` : 'Tout le catalogue')}
                  </p>
                  <p className="text-xs text-slate-500">
                    {r.produitsTouches} produit{r.produitsTouches > 1 ? 's' : ''} ·{' '}
                    {new Date(r.appliqueeLe).toLocaleDateString('fr-FR', {
                      day: 'numeric', month: 'long', year: 'numeric',
                    })}
                    {r.appliqueePar && ` · ${r.appliqueePar}`}
                    {r.annuleeLe && ` · annulée le ${new Date(r.annuleeLe).toLocaleDateString('fr-FR')}`}
                  </p>
                </div>
                {!r.annuleeLe && (
                  <button
                    type="button" onClick={() => annuler(r)} disabled={annulation === r.id}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-60"
                  >
                    {annulation === r.id
                      ? <Loader2 className="h-4 w-4 animate-spin" />
                      : <Undo2 className="h-4 w-4" aria-hidden="true" />}
                    Annuler
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
