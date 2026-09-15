import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle, Building2, CheckCircle2, Download, FileText, Inbox, Landmark, Loader2, Pencil, Search, Send,
} from 'lucide-react';
import {
  financeService, formatDate, formatMoney,
  type CoordonneesPartenaire, type DossierReversement, type EtatTranche, type PartnerDueRow, type PayoutDto,
} from '../../api/financeService';
import { StatusBadge, type BadgeTone } from '../common/StatusBadge';
import { EmptyState } from '../common/EmptyState';
import type { ToastType } from '../common/Toast';
import { DocumentButton } from '../documents/DocumentButton';
import { VirementDialog, type LigneVirement } from './VirementDialog';
import { telechargerOrdreSepa } from './sepa';

const ETATS: Record<EtatTranche, { label: string; tone: BadgeTone }> = {
  A_REVERSER: { label: 'À reverser', tone: 'amber' },
  EN_ATTENTE_MEDECIN: { label: 'Attendu du médecin', tone: 'slate' },
  VIREMENT_INITIE: { label: 'Virement initié', tone: 'purple' },
  VIRE: { label: 'Viré', tone: 'emerald' },
  ANNULE: { label: 'Annulé', tone: 'rose' },
};

interface ReversementsChuPanelProps {
  partners: PartnerDueRow[];
  /** Recharge les indicateurs de la page (montants dus, déjà viré). */
  onDataChanged: () => Promise<void> | void;
  notify: (message: string, type: ToastType) => void;
}

/** L'établissement à qui l'on doit de l'argent d'abord : c'est pour lui qu'on ouvre l'écran. */
const partenaireParDefaut = (partners: PartnerDueRow[]) =>
  (partners.find((p) => p.pendingAmount > 0) ?? partners.find((p) => p.awaitingTransfer > 0) ?? partners[0])
    ?.partnerProfileId ?? '';

/**
 * Reversement au CHU, dossier par dossier.
 *
 * <p><b>Pourquoi par dossier et non par période.</b> Un virement global mêlait les acomptes et
 * les soldes de plusieurs candidats : le CHU recevait une somme qu'il ne savait pas rapprocher
 * de ses dossiers. Chaque tranche se vire ici séparément, avec une référence qui nomme le
 * dossier et la tranche ; la sélection groupée reste possible, avec un bordereau nominatif.</p>
 *
 * <p>Le reversement par période existe toujours côté serveur ; il n'est plus proposé à l'écran.</p>
 */
export function ReversementsChuPanel({ partners, onDataChanged, notify }: ReversementsChuPanelProps) {
  const [partnerId, setPartnerId] = useState(() => partenaireParDefaut(partners));
  const [dossiers, setDossiers] = useState<DossierReversement[]>([]);
  const [coordonnees, setCoordonnees] = useState<CoordonneesPartenaire | null>(null);
  const [payouts, setPayouts] = useState<PayoutDto[]>([]);
  const [chargement, setChargement] = useState(true);
  const [filtre, setFiltre] = useState<'a_reverser' | 'tous'>('a_reverser');
  const [recherche, setRecherche] = useState('');
  const [selection, setSelection] = useState<Set<string>>(new Set());
  const [virement, setVirement] = useState<LigneVirement[] | null>(null);
  const [aConfirmer, setAConfirmer] = useState<PayoutDto | null>(null);
  const [edition, setEdition] = useState(false);
  const [actionEnCours, setActionEnCours] = useState<string | null>(null);

  useEffect(() => {
    if (!partnerId && partners.length > 0) setPartnerId(partenaireParDefaut(partners));
  }, [partners, partnerId]);

  const charger = useCallback(async () => {
    if (!partnerId) { setChargement(false); return; }
    try {
      const [d, c, p] = await Promise.all([
        financeService.getPayoutDossiers(partnerId),
        financeService.getBankDetails(partnerId),
        financeService.getPartnerPayouts(partnerId),
      ]);
      setDossiers(d);
      setCoordonnees(c);
      setPayouts(p);
    } catch {
      notify('Impossible de charger les dossiers de cet établissement.', 'error');
    } finally {
      setChargement(false);
    }
  }, [partnerId, notify]);

  useEffect(() => {
    setChargement(true);
    setSelection(new Set());
    setEdition(false);
    charger();
  }, [charger]);

  const partner = partners.find((p) => p.partnerProfileId === partnerId);
  const virable = coordonnees?.complet ?? false;

  const lignesParPaiement = useMemo(() => {
    const index = new Map<string, LigneVirement>();
    dossiers.forEach((dossier) => dossier.tranches.forEach((tranche) => {
      if (tranche.paymentId) index.set(tranche.paymentId, { dossier, tranche });
    }));
    return index;
  }, [dossiers]);

  const aReverser = dossiers.filter((d) => d.tranches.some((t) => t.etat === 'A_REVERSER'));
  const visibles = (filtre === 'a_reverser' ? aReverser : dossiers).filter((d) => {
    if (!recherche) return true;
    const aiguille = recherche.toLowerCase();
    return [d.doctorName, d.dossierCode, d.trainingTitle].some((v) => v?.toLowerCase().includes(aiguille));
  });

  const selectionnees = [...selection].map((id) => lignesParPaiement.get(id)).filter(Boolean) as LigneVirement[];
  const netSelection = Math.round(selectionnees.reduce((t, l) => t + l.tranche.netAmount, 0) * 100) / 100;
  const dossiersSelection = new Set(selectionnees.map((l) => l.dossier.enrollmentId)).size;

  const basculer = (paymentId: string) => setSelection((avant) => {
    const apres = new Set(avant);
    if (apres.has(paymentId)) apres.delete(paymentId); else apres.add(paymentId);
    return apres;
  });

  const apresEmission = async () => {
    setSelection(new Set());
    await Promise.all([charger(), onDataChanged()]);
  };

  const telecharger = async (payout: { id: string; reference: string | null }) => {
    setActionEnCours(`sepa-${payout.id}`);
    const message = await telechargerOrdreSepa(payout.id, payout.reference);
    setActionEnCours(null);
    if (message) notify(message, 'error');
  };

  const marquerVire = async (payout: PayoutDto) => {
    setActionEnCours(`vire-${payout.id}`);
    try {
      await financeService.markPayoutPaid(payout.id);
      notify(`Virement ${payout.reference} marqué comme exécuté.`, 'success');
      setAConfirmer(null);
      await Promise.all([charger(), onDataChanged()]);
    } catch (err: any) {
      notify(err.response?.data?.message || 'Action impossible.', 'error');
    } finally {
      setActionEnCours(null);
    }
  };

  const regenererReleve = async (payoutId: string) => {
    setActionEnCours(`releve-${payoutId}`);
    try {
      await financeService.generateStatement(payoutId);
      notify('Bordereau régénéré et mis à disposition du partenaire.', 'success');
      setPayouts(await financeService.getPartnerPayouts(partnerId));
    } catch (err: any) {
      notify(err.response?.data?.message || "Le bordereau n'a pas pu être généré.", 'error');
    } finally {
      setActionEnCours(null);
    }
  };

  if (partners.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-slate-200">
        <EmptyState icon={Building2} title="Aucun établissement partenaire"
          description="Les CHU apparaîtront ici dès qu'une formation partenaire aura été réglée." />
      </div>
    );
  }

  const tranchesPayout = (payoutId: string) =>
    dossiers.flatMap((d) => d.tranches).filter((t) => t.payoutId === payoutId).length;

  return (
    <div className="space-y-6">
      {/* ── Établissement et compte crédité ─────────────────────────────────── */}
      <div className="bg-white rounded-xl border border-slate-200 p-4 sm:p-6">
        <div className="flex flex-col lg:flex-row gap-6">
          <div className="flex-1 min-w-0">
            <label htmlFor="partenaire" className="block text-sm font-medium text-slate-700 mb-2">
              Établissement partenaire
            </label>
            <select
              id="partenaire" value={partnerId} onChange={(e) => setPartnerId(e.target.value)}
              className="w-full sm:max-w-md rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand focus:outline-none"
            >
              {partners.map((p) => (
                <option key={p.partnerProfileId} value={p.partnerProfileId}>
                  {p.institutionName} — {formatMoney(p.pendingAmount)} à reverser
                </option>
              ))}
            </select>

            {partner && (
              <dl className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-5">
                {[
                  { label: 'À reverser', valeur: partner.pendingAmount, couleur: 'text-warning', sous: `${partner.pendingPaymentsCount} tranche(s)` },
                  { label: 'Virement initié', valeur: partner.awaitingTransfer, couleur: 'text-brand', sous: 'Ordre émis' },
                  { label: 'Déjà viré', valeur: partner.paidOut, couleur: 'text-success', sous: 'Exécuté' },
                ].map((k) => (
                  <div key={k.label} className="rounded-lg bg-slate-50 px-3 py-2.5 min-w-0">
                    <dt className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">{k.label}</dt>
                    <dd className={`text-base sm:text-xl font-bold tabular-nums mt-0.5 ${k.couleur}`}>{formatMoney(k.valeur)}</dd>
                    <dd className="text-xs text-slate-500 truncate">{k.sous}</dd>
                  </div>
                ))}
              </dl>
            )}
          </div>

          <div className="lg:w-96 shrink-0">
            {coordonnees && (edition || !coordonnees.complet) ? (
              <FormulaireCoordonnees
                partnerId={partnerId}
                coordonnees={coordonnees}
                annulable={coordonnees.complet}
                onAnnuler={() => setEdition(false)}
                onEnregistre={(c) => { setCoordonnees(c); setEdition(false); notify('Coordonnées bancaires enregistrées.', 'success'); }}
              />
            ) : coordonnees && (
              <div className="rounded-xl border border-slate-200 p-4 h-full">
                <div className="flex items-center justify-between gap-2">
                  <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-500">
                    <Landmark className="w-3.5 h-3.5" aria-hidden="true" /> Compte crédité
                  </p>
                  <button type="button" onClick={() => setEdition(true)}
                    className="inline-flex items-center gap-1 text-xs font-medium text-brand hover:text-brand-fonce hover:underline">
                    <Pencil className="w-3 h-3" aria-hidden="true" /> Modifier
                  </button>
                </div>
                <p className="mt-2 font-semibold text-brand-dark">{coordonnees.titulaire}</p>
                <p className="font-mono text-sm text-slate-700 mt-1">{coordonnees.ibanMasque}</p>
                <p className="text-xs text-slate-500 mt-1">
                  {coordonnees.bic ? `BIC ${coordonnees.bic}` : 'BIC non renseigné'} · frais de gestion {coordonnees.commissionRate} %
                </p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ── Dossiers ─────────────────────────────────────────────────────────── */}
      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="p-4 border-b border-slate-200 flex flex-col md:flex-row gap-3 md:items-center">
          <div className="flex gap-1 p-1 bg-slate-100 rounded-lg w-fit" role="group" aria-label="Filtrer les dossiers">
            {([['a_reverser', `À reverser (${aReverser.length})`], ['tous', `Tous les dossiers (${dossiers.length})`]] as const).map(([valeur, libelle]) => (
              <button
                key={valeur} type="button" onClick={() => setFiltre(valeur)} aria-pressed={filtre === valeur}
                className={`px-3 py-1.5 text-sm font-medium rounded-md transition-all ${
                  filtre === valeur ? 'bg-white text-brand shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}
              >
                {libelle}
              </button>
            ))}
          </div>
          <div className="relative flex-1 md:max-w-sm md:ml-auto">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
            <input
              type="search" value={recherche} onChange={(e) => setRecherche(e.target.value)}
              placeholder="Candidat, n° de dossier, formation…" aria-label="Rechercher un dossier"
              className="w-full pl-9 rounded-lg border border-slate-300 p-2.5 text-sm focus:border-brand focus:ring-2 focus:ring-brand/20 focus:outline-none"
            />
          </div>
        </div>

        {!virable && !chargement && (
          <p className="flex gap-2 px-4 py-3 bg-warning/10 text-warning text-sm border-b border-slate-200">
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" />
            Renseignez l'IBAN et le titulaire du compte du CHU : aucun virement ne peut partir sans compte à créditer.
          </p>
        )}

        {chargement ? (
          <div className="p-12 text-center text-slate-500 text-sm">
            <Loader2 className="w-6 h-6 animate-spin mx-auto mb-3 text-brand" aria-hidden="true" />
            Chargement des dossiers…
          </div>
        ) : visibles.length === 0 ? (
          <EmptyState
            icon={Inbox}
            title={recherche ? 'Aucun dossier' : filtre === 'a_reverser' ? 'Rien à reverser pour cet établissement' : 'Aucun dossier réglé'}
            description={recherche
              ? 'Aucun dossier ne correspond à la recherche.'
              : filtre === 'a_reverser' && dossiers.length > 0
                ? 'Toutes les tranches réglées ont déjà été virées ou ordonnancées.'
                : "Les dossiers apparaîtront ici dès qu'un candidat aura réglé une tranche de sa formation."}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[920px] text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="w-10 px-4 py-3"><span className="sr-only">Sélection</span></th>
                  <th className="px-4 py-3 font-medium">Candidat & dossier</th>
                  <th className="px-4 py-3 font-medium">Tranche</th>
                  <th className="px-4 py-3 font-medium text-right">Encaissé</th>
                  <th className="px-4 py-3 font-medium text-right">Frais de gestion</th>
                  <th className="px-4 py-3 font-medium text-right">Net CHU</th>
                  <th className="px-4 py-3 font-medium">État</th>
                  <th className="px-4 py-3 font-medium text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {visibles.map((dossier) => (
                  <Fragment key={dossier.enrollmentId}>
                    {dossier.tranches.map((tranche, i) => {
                      const payout = payouts.find((p) => p.id === tranche.payoutId);
                      const cochable = tranche.etat === 'A_REVERSER' && virable && tranche.paymentId;
                      const coche = tranche.paymentId ? selection.has(tranche.paymentId) : false;
                      return (
                        <tr key={tranche.paymentId ?? `${dossier.enrollmentId}-${tranche.tranche}`}
                          className={`${i === 0 ? 'border-t border-slate-200' : ''} ${coche ? 'bg-brand-light/60' : 'hover:bg-slate-50'} transition-colors`}>
                          <td className="px-4 py-3 align-top">
                            {tranche.etat === 'A_REVERSER' && (
                              <input
                                type="checkbox" checked={coche} disabled={!cochable}
                                onChange={() => tranche.paymentId && basculer(tranche.paymentId)}
                                aria-label={`Sélectionner ${tranche.trancheLabel} de ${dossier.doctorName}`}
                                className="w-4 h-4 mt-0.5 rounded border-slate-300 accent-brand disabled:opacity-40"
                              />
                            )}
                          </td>
                          {i === 0 && (
                            <td className="px-4 py-3 align-top" rowSpan={dossier.tranches.length}>
                              <p className="font-semibold text-brand-dark">{dossier.doctorName}</p>
                              <p className="font-mono text-xs text-slate-500 whitespace-nowrap">{dossier.dossierCode}</p>
                              <p className="text-xs text-slate-600 mt-1 max-w-[16rem]">{dossier.trainingTitle}</p>
                              {dossier.optionsAmount > 0 && (
                                <p className="text-xs text-slate-500 mt-1" title="Visible de l'administration uniquement : jamais reversé ni inscrit au bordereau">
                                  Options {formatMoney(dossier.optionsAmount)} · conservées
                                </p>
                              )}
                            </td>
                          )}
                          <td className="px-4 py-3 align-top whitespace-nowrap">
                            <p className="text-slate-700">{tranche.trancheLabel}</p>
                            <p className="text-xs text-slate-400">
                              {tranche.paidAt ? `Réglé le ${formatDate(tranche.paidAt)}` : 'Non encore réglé'}
                            </p>
                          </td>
                          <td className="px-4 py-3 align-top text-right tabular-nums text-slate-700 whitespace-nowrap">
                            {formatMoney(tranche.grossAmount)}
                          </td>
                          <td className="px-4 py-3 align-top text-right tabular-nums text-slate-500 whitespace-nowrap">
                            − {formatMoney(tranche.commissionAmount)}
                            <span className="block text-xs text-slate-400">{tranche.commissionRate} %</span>
                          </td>
                          <td className="px-4 py-3 align-top text-right tabular-nums font-semibold text-brand-dark whitespace-nowrap">
                            {formatMoney(tranche.netAmount)}
                          </td>
                          <td className="px-4 py-3 align-top">
                            <StatusBadge status={tranche.etat} label={ETATS[tranche.etat].label} tone={ETATS[tranche.etat].tone} />
                            {tranche.payoutReference && (
                              <p className="font-mono text-[11px] text-slate-400 mt-1 whitespace-nowrap">{tranche.payoutReference}</p>
                            )}
                          </td>
                          <td className="px-4 py-3 align-top text-right">
                            {tranche.etat === 'A_REVERSER' && tranche.paymentId && (
                              <button
                                type="button" disabled={!virable}
                                onClick={() => setVirement([{ dossier, tranche }])}
                                title={virable ? undefined : 'Renseignez d\'abord le compte du CHU'}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-brand text-white text-xs font-semibold hover:bg-brand-fonce disabled:opacity-50 whitespace-nowrap"
                              >
                                <Send className="w-3.5 h-3.5" aria-hidden="true" /> Effectuer le virement
                              </button>
                            )}
                            {tranche.etat === 'VIREMENT_INITIE' && tranche.payoutId && (
                              <div className="flex flex-col items-end gap-1.5">
                                <button
                                  type="button" disabled={actionEnCours === `sepa-${tranche.payoutId}`}
                                  onClick={() => telecharger({ id: tranche.payoutId!, reference: tranche.payoutReference })}
                                  className="inline-flex items-center gap-1.5 text-xs font-medium text-brand hover:text-brand-fonce hover:underline disabled:opacity-60 whitespace-nowrap"
                                >
                                  {actionEnCours === `sepa-${tranche.payoutId}`
                                    ? <Loader2 className="w-3 h-3 animate-spin" aria-hidden="true" />
                                    : <Download className="w-3 h-3" aria-hidden="true" />}
                                  Ordre SEPA
                                </button>
                                {payout && (
                                  <button
                                    type="button" onClick={() => setAConfirmer(payout)}
                                    className="inline-flex items-center gap-1.5 text-xs font-medium text-success hover:underline whitespace-nowrap"
                                  >
                                    <CheckCircle2 className="w-3 h-3" aria-hidden="true" /> Marquer viré
                                  </button>
                                )}
                              </div>
                            )}
                            {(tranche.etat === 'VIRE' || tranche.etat === 'VIREMENT_INITIE') && tranche.statementAvailable && tranche.payoutId && (
                              <DocumentButton
                                libelle="Bordereau"
                                obtenirLien={() => financeService.getStatementUrl(tranche.payoutId!)}
                                className={tranche.etat === 'VIREMENT_INITIE' ? 'mt-1.5' : ''}
                              />
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {selectionnees.length > 0 && (
          <div className="sticky bottom-0 flex flex-col sm:flex-row sm:items-center gap-3 px-4 py-3 bg-brand-dark text-white">
            <p className="text-sm">
              <span className="font-semibold">{selectionnees.length} tranche{selectionnees.length > 1 ? 's' : ''}</span>
              {' '}sur {dossiersSelection} dossier{dossiersSelection > 1 ? 's' : ''} · net{' '}
              <span className="font-semibold tabular-nums">{formatMoney(netSelection)}</span>
            </p>
            <div className="flex gap-2 sm:ml-auto">
              <button type="button" onClick={() => setSelection(new Set())}
                className="px-3 py-2 rounded-lg text-sm font-medium text-white/80 hover:text-white hover:bg-white/10">
                Désélectionner
              </button>
              <button type="button" onClick={() => setVirement(selectionnees)}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-white text-brand-dark text-sm font-semibold hover:bg-brand-light">
                <Send className="w-4 h-4" aria-hidden="true" />
                Reverser la sélection ({dossiersSelection} dossier{dossiersSelection > 1 ? 's' : ''})
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ── Historique ───────────────────────────────────────────────────────── */}
      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-200">
          <h2 className="text-sm font-semibold text-brand-dark">Historique des virements</h2>
        </div>
        {payouts.length === 0 ? (
          <EmptyState icon={Building2} title="Aucun virement pour cet établissement"
            description="Les virements émis depuis les dossiers apparaîtront ici." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Référence</th>
                  <th className="px-4 py-3 font-medium">Émis le</th>
                  <th className="px-4 py-3 font-medium text-right">Montant net</th>
                  <th className="px-4 py-3 font-medium">Statut</th>
                  <th className="px-4 py-3 font-medium">Viré le</th>
                  <th className="px-4 py-3 font-medium text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {payouts.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-4 py-3 font-mono text-xs text-brand-dark whitespace-nowrap">{p.reference}</td>
                    <td className="px-4 py-3 text-slate-500 whitespace-nowrap">{formatDate(p.createdAt)}</td>
                    <td className="px-4 py-3 text-right tabular-nums font-medium text-brand-dark whitespace-nowrap">
                      {formatMoney(p.totalAmount, p.currency)}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={p.status}
                        label={p.status === 'PENDING' ? 'Virement initié' : p.status === 'PAID' ? 'Viré' : undefined}
                        tone={p.status === 'PENDING' ? 'purple' : undefined} />
                    </td>
                    <td className="px-4 py-3 text-slate-500 whitespace-nowrap">{formatDate(p.paidAt)}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-3 whitespace-nowrap">
                        {p.statementAvailable && (
                          <DocumentButton libelle="Bordereau" obtenirLien={() => financeService.getStatementUrl(p.id)} />
                        )}
                        <button
                          type="button" onClick={() => regenererReleve(p.id)} disabled={actionEnCours !== null}
                          className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-600 hover:text-brand hover:underline disabled:opacity-60"
                          title={p.statementAvailable ? 'Régénérer le bordereau PDF' : 'Générer le bordereau PDF remis au partenaire'}
                        >
                          <FileText className="w-3 h-3" aria-hidden="true" />
                          {p.statementAvailable ? 'Régénérer' : 'Générer le bordereau'}
                        </button>
                        {p.status === 'PENDING' && (
                          <>
                            <button
                              type="button" onClick={() => telecharger(p)} disabled={actionEnCours === `sepa-${p.id}`}
                              className="inline-flex items-center gap-1.5 text-xs font-medium text-brand hover:text-brand-fonce hover:underline disabled:opacity-60"
                            >
                              <Download className="w-3 h-3" aria-hidden="true" /> Ordre SEPA
                            </button>
                            <button
                              type="button" onClick={() => setAConfirmer(p)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-medium text-slate-700 hover:bg-slate-50"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" aria-hidden="true" /> Marquer viré
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {virement && coordonnees && (
        <VirementDialog
          partnerProfileId={partnerId}
          coordonnees={coordonnees}
          lignes={virement}
          onClose={() => setVirement(null)}
          onEmis={apresEmission}
        />
      )}

      {aConfirmer && (
        <ConfirmationExecution
          payout={aConfirmer}
          tranches={tranchesPayout(aConfirmer.id)}
          enCours={actionEnCours === `vire-${aConfirmer.id}`}
          onAnnuler={() => setAConfirmer(null)}
          onConfirmer={() => marquerVire(aConfirmer)}
        />
      )}
    </div>
  );
}

/** Saisie du compte du CHU. L'IBAN est contrôlé par le serveur (clé ISO 13616). */
function FormulaireCoordonnees({ partnerId, coordonnees, annulable, onAnnuler, onEnregistre }: {
  partnerId: string;
  coordonnees: CoordonneesPartenaire;
  annulable: boolean;
  onAnnuler: () => void;
  onEnregistre: (c: CoordonneesPartenaire) => void;
}) {
  const [titulaire, setTitulaire] = useState(coordonnees.titulaire ?? coordonnees.institutionName);
  const [iban, setIban] = useState(coordonnees.iban ?? '');
  const [bic, setBic] = useState(coordonnees.bic ?? '');
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  const enregistrer = async (e: React.FormEvent) => {
    e.preventDefault();
    setEnCours(true);
    setErreur(null);
    try {
      onEnregistre(await financeService.saveBankDetails(partnerId, { iban, bic, accountHolder: titulaire }));
    } catch (err: any) {
      setErreur(err.response?.data?.message || "Les coordonnées n'ont pas pu être enregistrées.");
    } finally {
      setEnCours(false);
    }
  };

  const champ = 'w-full rounded-lg border border-slate-300 p-2 text-sm focus:border-brand focus:ring-2 focus:ring-brand/20 focus:outline-none';

  return (
    <form onSubmit={enregistrer} className="rounded-xl border border-slate-200 p-4 space-y-3">
      <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-500">
        <Landmark className="w-3.5 h-3.5" aria-hidden="true" /> Compte à créditer
      </p>
      <div>
        <label htmlFor="titulaire" className="block text-xs font-medium text-slate-600 mb-1">Titulaire du compte</label>
        <input id="titulaire" required value={titulaire} onChange={(e) => setTitulaire(e.target.value)} className={champ} />
      </div>
      <div>
        <label htmlFor="iban" className="block text-xs font-medium text-slate-600 mb-1">IBAN</label>
        <input id="iban" required value={iban} onChange={(e) => setIban(e.target.value)}
          placeholder="FR76 3000 6000 0112 3456 7890 189" autoComplete="off" spellCheck={false}
          className={`${champ} font-mono`} />
      </div>
      <div>
        <label htmlFor="bic" className="block text-xs font-medium text-slate-600 mb-1">BIC <span className="text-slate-400">(facultatif)</span></label>
        <input id="bic" value={bic} onChange={(e) => setBic(e.target.value)} placeholder="AGRIFRPP"
          autoComplete="off" spellCheck={false} className={`${champ} font-mono`} />
      </div>
      {erreur && <p role="alert" className="text-xs text-danger">{erreur}</p>}
      <div className="flex justify-end gap-2">
        {annulable && (
          <button type="button" onClick={onAnnuler}
            className="px-3 py-2 rounded-lg border border-slate-300 text-xs font-medium text-slate-700 hover:bg-slate-50">
            Annuler
          </button>
        )}
        <button type="submit" disabled={enCours}
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-brand text-white text-xs font-semibold hover:bg-brand-fonce disabled:opacity-60">
          {enCours && <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />}
          Enregistrer
        </button>
      </div>
    </form>
  );
}

/** Marquer un virement comme exécuté est définitif : on le fait confirmer, montant à l'appui. */
function ConfirmationExecution({ payout, tranches, enCours, onAnnuler, onConfirmer }: {
  payout: PayoutDto;
  tranches: number;
  enCours: boolean;
  onAnnuler: () => void;
  onConfirmer: () => void;
}) {
  useEffect(() => {
    const clavier = (e: KeyboardEvent) => { if (e.key === 'Escape' && !enCours) onAnnuler(); };
    window.addEventListener('keydown', clavier);
    return () => window.removeEventListener('keydown', clavier);
  }, [enCours, onAnnuler]);

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-brand-dark/50 backdrop-blur-sm p-4"
      onMouseDown={(e) => { if (e.target === e.currentTarget && !enCours) onAnnuler(); }}>
      <div role="alertdialog" aria-modal="true" aria-labelledby="execution-titre"
        className="bg-white w-full max-w-md rounded-2xl shadow-xl p-6">
        <h2 id="execution-titre" className="text-lg font-bold text-brand-dark">Le virement a-t-il été exécuté ?</h2>
        <p className="text-sm text-slate-600 mt-2">
          Confirmez uniquement si la banque a débité le compte d'Optimi Santé. Le virement{' '}
          <span className="font-mono font-semibold text-brand-dark">{payout.reference}</span> de{' '}
          <span className="font-semibold tabular-nums">{formatMoney(payout.totalAmount, payout.currency)}</span>
          {tranches > 1 ? ` (${tranches} tranches)` : ''} passera à « Viré », et son ordre SEPA ne pourra plus être téléchargé.
        </p>
        <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 mt-6">
          <button type="button" onClick={onAnnuler} disabled={enCours}
            className="px-4 py-2.5 rounded-lg border border-slate-300 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60">
            Annuler
          </button>
          <button type="button" onClick={onConfirmer} disabled={enCours} autoFocus
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg bg-success text-white text-sm font-semibold hover:opacity-90 disabled:opacity-60">
            {enCours && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
            Oui, marquer comme viré
          </button>
        </div>
      </div>
    </div>
  );
}
