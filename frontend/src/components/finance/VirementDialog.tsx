import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, CheckCircle2, Download, Landmark, Loader2, X } from 'lucide-react';
import {
  financeService, formatMoney,
  type CoordonneesPartenaire, type DossierReversement, type PayoutDto, type TrancheReversement,
} from '../../api/financeService';
import { DocumentButton } from '../documents/DocumentButton';
import { telechargerOrdreSepa } from './sepa';

/** Une tranche retenue pour le virement, avec le dossier qui la porte. */
export interface LigneVirement {
  dossier: DossierReversement;
  tranche: TrancheReversement;
}

interface VirementDialogProps {
  partnerProfileId: string;
  coordonnees: CoordonneesPartenaire;
  lignes: LigneVirement[];
  onClose: () => void;
  /** Appelé dès que le virement existe côté serveur, pour rafraîchir l'écran derrière. */
  onEmis: (payout: PayoutDto) => void;
}

const somme = (lignes: LigneVirement[], champ: 'grossAmount' | 'commissionAmount' | 'netAmount') =>
  Math.round(lignes.reduce((total, l) => total + l.tranche[champ], 0) * 100) / 100;

/**
 * Référence annoncée avant validation. Elle suit la règle du serveur (une tranche : dossier et
 * tranche ; plusieurs : date et nombre de dossiers). Le serveur peut y ajouter un suffixe si la
 * même existe déjà : la référence définitive est celle affichée une fois l'ordre émis.
 */
function referenceAnnoncee(lignes: LigneVirement[]): string {
  if (lignes.length === 1) return lignes[0].tranche.bankReference ?? '—';
  const etablissement = lignes[0].tranche.bankReference?.split('-')[1] ?? 'CHU';
  const d = new Date();
  const date = `${String(d.getFullYear()).slice(2)}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
  const dossiers = new Set(lignes.map((l) => l.dossier.enrollmentId)).size;
  return `REV-${etablissement}-SEL-${date}-${dossiers}D`;
}

/**
 * Confirmation d'un virement au CHU, puis ce qu'il reste à faire une fois l'ordre émis.
 *
 * <p>Tout ce qui permet de vérifier AVANT de valider est à l'écran : le compte crédité en
 * entier, la référence qui arrivera sur le relevé du CHU, et la ventilation brut, commission,
 * net. Une erreur de montant ou de compte se voit ici, pas au retour du virement.</p>
 */
export function VirementDialog({ partnerProfileId, coordonnees, lignes, onClose, onEmis }: VirementDialogProps) {
  const [etape, setEtape] = useState<'confirmation' | 'emis'>('confirmation');
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [payout, setPayout] = useState<PayoutDto | null>(null);
  const [sepa, setSepa] = useState<{ enCours: boolean; message: string | null; ok: boolean }>({
    enCours: false, message: null, ok: false,
  });
  const boutonPrincipal = useRef<HTMLButtonElement>(null);

  useEffect(() => { boutonPrincipal.current?.focus(); }, [etape]);

  useEffect(() => {
    const clavier = (e: KeyboardEvent) => { if (e.key === 'Escape' && !enCours) onClose(); };
    window.addEventListener('keydown', clavier);
    return () => window.removeEventListener('keydown', clavier);
  }, [enCours, onClose]);

  const brut = somme(lignes, 'grossAmount');
  const commission = somme(lignes, 'commissionAmount');
  const net = somme(lignes, 'netAmount');
  const dossiers = new Map(lignes.map((l) => [l.dossier.enrollmentId, l.dossier]));
  const options = Math.round([...dossiers.values()].reduce((t, d) => t + (d.optionsAmount ?? 0), 0) * 100) / 100;
  const taux = new Set(lignes.map((l) => l.tranche.commissionRate));

  const emettre = async () => {
    setEnCours(true);
    setErreur(null);
    try {
      const cree = await financeService.payoutForPayments(
        partnerProfileId, lignes.map((l) => l.tranche.paymentId!).filter(Boolean),
      );
      setPayout(cree);
      setEtape('emis');
      onEmis(cree);
    } catch (err: any) {
      setErreur(err.response?.data?.message || "L'ordre de virement n'a pas pu être émis. Réessayez.");
    } finally {
      setEnCours(false);
    }
  };

  const telecharger = async () => {
    if (!payout) return;
    setSepa({ enCours: true, message: null, ok: false });
    const message = await telechargerOrdreSepa(payout.id, payout.reference);
    setSepa({ enCours: false, message, ok: message === null });
  };

  return (
    <div
      className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center bg-brand-dark/50 backdrop-blur-sm sm:p-4"
      onMouseDown={(e) => { if (e.target === e.currentTarget && !enCours) onClose(); }}
    >
      <div
        role="dialog" aria-modal="true" aria-labelledby="virement-titre"
        className="bg-white w-full sm:max-w-xl rounded-t-2xl sm:rounded-2xl shadow-xl max-h-[92vh] flex flex-col"
      >
        <div className="flex items-start justify-between gap-4 px-5 sm:px-6 pt-5 pb-4 border-b border-slate-100">
          <div>
            <h2 id="virement-titre" className="text-lg font-bold text-brand-dark">
              {etape === 'confirmation' ? 'Confirmer le virement' : 'Ordre de virement émis'}
            </h2>
            <p className="text-sm text-slate-500 mt-0.5">
              {coordonnees.institutionName} · {lignes.length} tranche{lignes.length > 1 ? 's' : ''}
              {dossiers.size > 1 ? ` · ${dossiers.size} dossiers` : ''}
            </p>
          </div>
          <button
            type="button" onClick={onClose} disabled={enCours} aria-label="Fermer"
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 disabled:opacity-50"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="overflow-y-auto px-5 sm:px-6 py-5 space-y-5">
          {etape === 'emis' && payout && (
            <div className="flex gap-3 p-4 rounded-xl bg-success/10 text-success">
              <CheckCircle2 className="w-5 h-5 shrink-0 mt-0.5" aria-hidden="true" />
              <div className="text-sm">
                <p className="font-semibold">Statut : virement initié</p>
                <p className="text-slate-700 mt-1">
                  Importez l'ordre SEPA dans l'espace bancaire d'Optimi Santé. Une fois le virement
                  exécuté par la banque, marquez-le comme viré depuis le tableau.
                </p>
              </div>
            </div>
          )}

          <section aria-label="Compte crédité" className="rounded-xl border border-slate-200 p-4">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-500">
              <Landmark className="w-3.5 h-3.5" aria-hidden="true" /> Compte crédité
            </div>
            <p className="mt-2 font-semibold text-brand-dark">{coordonnees.titulaire}</p>
            <p className="font-mono text-sm text-slate-700 mt-1 break-all">{coordonnees.iban}</p>
            <p className="text-xs text-slate-500 mt-1">BIC {coordonnees.bic || 'non renseigné (retrouvé par la banque depuis l\'IBAN)'}</p>
          </section>

          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Référence du virement</p>
            <p className="font-mono text-sm font-semibold text-brand-dark mt-1 break-all">
              {etape === 'emis' && payout ? payout.reference : referenceAnnoncee(lignes)}
            </p>
            <p className="text-xs text-slate-500 mt-0.5">Elle figurera sur le relevé bancaire du CHU et sur le bordereau.</p>
          </div>

          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-2">
              {lignes.length > 1 ? 'Dossiers réglés' : 'Dossier réglé'}
            </p>
            <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
              {lignes.map(({ dossier, tranche }) => (
                <li key={tranche.paymentId} className="flex items-start justify-between gap-3 px-3 py-2.5 text-sm">
                  <div className="min-w-0">
                    <p className="font-medium text-brand-dark truncate">{dossier.doctorName}</p>
                    <p className="text-xs text-slate-500">
                      <span className="font-mono whitespace-nowrap">{dossier.dossierCode}</span> · {tranche.trancheLabel}
                    </p>
                  </div>
                  <span className="tabular-nums font-medium text-slate-700 whitespace-nowrap">
                    {formatMoney(tranche.netAmount)}
                  </span>
                </li>
              ))}
            </ul>
          </div>

          <dl className="rounded-xl bg-slate-50 p-4 text-sm space-y-2">
            <div className="flex justify-between gap-4">
              <dt className="text-slate-600">Frais de formation encaissés</dt>
              <dd className="tabular-nums text-slate-800">{formatMoney(brut)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-slate-600">
                Frais de gestion plateforme{taux.size === 1 ? ` (${[...taux][0]} %)` : ''}
              </dt>
              <dd className="tabular-nums text-slate-800">− {formatMoney(commission)}</dd>
            </div>
            <div className="flex justify-between gap-4 pt-2 border-t border-slate-200">
              <dt className="font-semibold text-brand-dark">Montant net {etape === 'emis' ? 'ordonnancé' : 'à virer'}</dt>
              <dd className="tabular-nums text-lg font-bold text-brand-dark">{formatMoney(net)}</dd>
            </div>
          </dl>

          {options > 0 && (
            <p className="text-xs text-slate-500">
              Options réglées sur {dossiers.size > 1 ? 'ces dossiers' : 'ce dossier'} ({formatMoney(options)}) :
              conservées par Optimi Santé, elles ne font pas partie du virement ni du bordereau.
            </p>
          )}

          {erreur && (
            <p role="alert" className="flex gap-2 p-3 rounded-lg bg-danger/10 text-danger text-sm">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" /> {erreur}
            </p>
          )}
          {sepa.message && (
            <p role="alert" className="flex gap-2 p-3 rounded-lg bg-warning/10 text-warning text-sm">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" /> {sepa.message}
            </p>
          )}
        </div>

        <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-end gap-2 px-5 sm:px-6 py-4 border-t border-slate-100">
          {etape === 'confirmation' ? (
            <>
              <button
                type="button" onClick={onClose} disabled={enCours}
                className="px-4 py-2.5 rounded-lg border border-slate-300 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
              >
                Annuler
              </button>
              <button
                ref={boutonPrincipal} type="button" onClick={emettre} disabled={enCours}
                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg bg-brand text-white text-sm font-semibold hover:bg-brand-fonce disabled:opacity-60"
              >
                {enCours && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
                Émettre l'ordre de {formatMoney(net)}
              </button>
            </>
          ) : payout && (
            <>
              <button
                type="button" onClick={onClose}
                className="px-4 py-2.5 rounded-lg border border-slate-300 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                Fermer
              </button>
              {payout.statementAvailable && (
                <DocumentButton
                  libelle="Bordereau"
                  variante="bouton"
                  obtenirLien={() => financeService.getStatementUrl(payout.id)}
                  className="justify-center !bg-white !text-brand border border-brand hover:!bg-brand-light"
                />
              )}
              <button
                ref={boutonPrincipal} type="button" onClick={telecharger} disabled={sepa.enCours}
                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg bg-brand text-white text-sm font-semibold hover:bg-brand-fonce disabled:opacity-60"
              >
                {sepa.enCours
                  ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
                  : sepa.ok ? <CheckCircle2 className="w-4 h-4" aria-hidden="true" /> : <Download className="w-4 h-4" aria-hidden="true" />}
                {sepa.ok ? 'Ordre SEPA téléchargé' : 'Télécharger l\'ordre SEPA (XML)'}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
