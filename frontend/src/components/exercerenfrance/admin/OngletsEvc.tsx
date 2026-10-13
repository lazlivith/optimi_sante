import { useState } from 'react';
import {
  ArrowUp, ArrowDown, Eye, EyeOff, Pencil, Trash2, Plus, Check, ExternalLink, Lock,
} from 'lucide-react';
import {
  adminExercerEnFranceService as api,
  type SectionAdmin, type PeriodeAdmin, type LienAdmin, type SpecialiteAdmin, type OffreAdmin,
} from '../../../api/adminExercerEnFranceService';
import { verifierAdresseDeLien } from '../../../lib/exercerEnFrance';
import { EmptyState } from '../../common/EmptyState';

/**
 * Ce qu'une suppression demande avant d'être faite.
 *
 * <p><b>Le mot « définitive » est le message.</b> Ces contenus n'ont pas de corbeille : une
 * section supprimée ne se retrouve pas. Une confirmation qui dirait seulement « Supprimer ? »
 * laisserait croire le contraire.</p>
 */
export function confirmerSuppression(quoi: string): boolean {
  return window.confirm(
    `Supprimer ${quoi} ?\n\nCette suppression est définitive : ce contenu ne pourra pas être `
    + 'récupéré. La page publique sera modifiée à la prochaine publication.',
  );
}

const CHAMP = 'w-full px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-800';
const BOUTON = 'inline-flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium';
const PRIMAIRE = `${BOUTON} bg-brand-dark text-white hover:opacity-90 disabled:opacity-40`;
const SECONDAIRE = `${BOUTON} border border-slate-200 text-slate-700 hover:bg-slate-50`;

interface CommunProps {
  onErreur: (m: string) => void;
  onSucces: (m: string) => void;
  onChange: () => void;
}

// ══ Sections ═════════════════════════════════════════════════════════════════════════

interface SectionsProps extends CommunProps {
  sections: SectionAdmin[];
  onApercu: () => void;
}

export function OngletSections({ sections, onApercu, onErreur, onSucces, onChange }: SectionsProps) {
  const [edition, setEdition] = useState<Partial<SectionAdmin> | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const enregistrer = async () => {
    if (!edition?.titre?.trim()) {
      onErreur('Le titre est obligatoire.');
      return;
    }
    setEnvoi(true);
    try {
      if (edition.id) await api.modifierSection(edition.id, edition);
      else await api.creerSection(edition);
      setEdition(null);
      onChange();
      onSucces('Section enregistrée. Elle sera visible en ligne après publication.');
    } catch (e: any) {
      onErreur(e.response?.data?.message || "La section n'a pas pu être enregistrée.");
    } finally {
      setEnvoi(false);
    }
  };

  const deplacer = async (s: SectionAdmin, versLeHaut: boolean) => {
    try {
      await api.deplacerSection(s.id, versLeHaut);
      onChange();
    } catch {
      onErreur('Le déplacement a échoué.');
    }
  };

  const supprimer = async (s: SectionAdmin) => {
    if (!confirmerSuppression(`la section « ${s.titre} »`)) return;
    try {
      await api.supprimerSection(s.id);
      onChange();
      onSucces('Section supprimée.');
    } catch {
      onErreur('La suppression a échoué.');
    }
  };

  const basculerVisible = async (s: SectionAdmin) => {
    try {
      await api.modifierSection(s.id, { ...s, visible: !s.visible });
      onChange();
    } catch {
      onErreur("La visibilité n'a pas pu être changée.");
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <button type="button" className={PRIMAIRE}
                onClick={() => setEdition({ titre: '', contenu: '', visible: true })}>
          <Plus className="w-4 h-4" /> Nouvelle section
        </button>
        <button type="button" className={SECONDAIRE} onClick={onApercu}>
          <Eye className="w-4 h-4" /> Aperçu
        </button>
      </div>

      {edition && (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 space-y-3">
          <h3 className="font-semibold text-brand-dark">
            {edition.id ? 'Modifier la section' : 'Nouvelle section'}
          </h3>
          <label className="block text-xs font-semibold text-slate-500">
            Sur-titre (facultatif)
            <input className={CHAMP} value={edition.surTitre ?? ''}
                   onChange={e => setEdition({ ...edition, surTitre: e.target.value })} />
          </label>
          <label className="block text-xs font-semibold text-slate-500">
            Titre
            <input className={CHAMP} value={edition.titre ?? ''}
                   onChange={e => setEdition({ ...edition, titre: e.target.value })} />
          </label>
          <label className="block text-xs font-semibold text-slate-500">
            Contenu
            <textarea className={CHAMP} rows={10} value={edition.contenu ?? ''}
                      onChange={e => setEdition({ ...edition, contenu: e.target.value })} />
          </label>

          {/*
            L'aide est ici et pas dans une documentation : c'est au moment où l'on tape que
            la règle sert, et le découpage en encadrés est la seule chose que l'éditeur fait
            qui ne se devine pas.
          */}
          <p className="text-xs text-slate-600 rounded-lg bg-brand-light/60 p-3">
            <strong>Un titre commençant par <code>##</code> crée un nouvel encadré.</strong> Tout
            ce qui suit, jusqu'au prochain <code>##</code>, est le contenu de cet encadré. Le
            reste du texte s'affiche tel qu'il est écrit — aucune autre mise en forme n'est
            interprétée. Utilisez l'<strong>Aperçu</strong> pour voir le résultat avant de
            publier.
          </p>

          <div className="flex gap-2">
            <button type="button" className={PRIMAIRE} disabled={envoi} onClick={enregistrer}>
              Enregistrer
            </button>
            <button type="button" className={SECONDAIRE} onClick={() => setEdition(null)}>
              Annuler
            </button>
          </div>
        </div>
      )}

      {sections.length === 0 ? (
        <EmptyState icon={Pencil} title="Aucune section." />
      ) : (
        <ul className="space-y-2">
          {sections.map((s, i) => (
            <li key={s.id}
                className="rounded-xl border border-slate-200 bg-white p-3 flex items-start gap-3">
              <div className="flex flex-col gap-1">
                <button type="button" aria-label="Monter" disabled={i === 0}
                        onClick={() => deplacer(s, true)}
                        className="p-1 rounded text-slate-500 hover:bg-slate-100 disabled:opacity-30">
                  <ArrowUp className="w-4 h-4" />
                </button>
                <button type="button" aria-label="Descendre" disabled={i === sections.length - 1}
                        onClick={() => deplacer(s, false)}
                        className="p-1 rounded text-slate-500 hover:bg-slate-100 disabled:opacity-30">
                  <ArrowDown className="w-4 h-4" />
                </button>
              </div>
              <div className="flex-1 min-w-0">
                {s.surTitre && <div className="text-xs text-slate-500">{s.surTitre}</div>}
                <div className="font-semibold text-slate-800 break-words">{s.titre}</div>
                <div className="text-xs text-slate-500">
                  clé « {s.cle} » · {(s.contenu ?? '').length} caractères
                  {!s.visible && ' · masquée'}
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button type="button" onClick={() => basculerVisible(s)}
                        aria-label={s.visible ? 'Masquer' : 'Afficher'}
                        className="p-2 rounded-lg text-slate-500 hover:bg-slate-100">
                  {s.visible ? <Eye className="w-4 h-4" /> : <EyeOff className="w-4 h-4" />}
                </button>
                <button type="button" onClick={() => setEdition(s)} aria-label="Modifier"
                        className="p-2 rounded-lg text-slate-500 hover:bg-slate-100">
                  <Pencil className="w-4 h-4" />
                </button>
                <button type="button" onClick={() => supprimer(s)} aria-label="Supprimer"
                        className="p-2 rounded-lg text-danger hover:bg-danger/10">
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ══ Périodes ═════════════════════════════════════════════════════════════════════════

interface PeriodesProps extends CommunProps {
  periodes: PeriodeAdmin[];
  calendrierActualiseLe: string | null;
}

export function OngletPeriodes({
  periodes, calendrierActualiseLe, onErreur, onSucces, onChange,
}: PeriodesProps) {
  const [edition, setEdition] = useState<Partial<PeriodeAdmin> | null>(null);
  const [dateCalendrier, setDateCalendrier] = useState(calendrierActualiseLe ?? '');
  const [envoi, setEnvoi] = useState(false);

  const enregistrer = async () => {
    if (!edition?.libelle?.trim()) {
      onErreur('Le libellé est obligatoire.');
      return;
    }
    // La source est exigée ICI, avant l'appel : une date d'ouverture sans sa source
    // officielle est une rumeur, et c'est la page publique qui la porterait.
    const erreurSource = verifierAdresseDeLien(edition.sourceUrl);
    if (erreurSource) {
      onErreur(erreurSource);
      return;
    }
    setEnvoi(true);
    try {
      if (edition.id) await api.modifierPeriode(edition.id, edition);
      else await api.creerPeriode(edition);
      setEdition(null);
      onChange();
      onSucces('Période enregistrée.');
    } catch (e: any) {
      onErreur(e.response?.data?.message || "La période n'a pas pu être enregistrée.");
    } finally {
      setEnvoi(false);
    }
  };

  const supprimer = async (p: PeriodeAdmin) => {
    if (!confirmerSuppression(`la période « ${p.libelle} »`)) return;
    try {
      await api.supprimerPeriode(p.id);
      onChange();
      onSucces('Période supprimée.');
    } catch {
      onErreur('La suppression a échoué.');
    }
  };

  const enregistrerCalendrier = async () => {
    if (!dateCalendrier) {
      onErreur('Indiquez la date à laquelle vous avez consulté le calendrier.');
      return;
    }
    try {
      await api.definirCalendrier(dateCalendrier);
      onChange();
      onSucces('Date du calendrier enregistrée.');
    } catch {
      onErreur("La date n'a pas pu être enregistrée.");
    }
  };

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-slate-200 bg-white p-4">
        <h3 className="font-semibold text-brand-dark mb-1">
          Calendrier des commissions, constaté le
        </h3>
        <p className="text-xs text-slate-600 mb-3">
          La date à laquelle vous avez vérifié le calendrier officiel. Elle est affichée sur la
          page publique : elle dit au praticien de quand date notre information, et non quand
          le site a été modifié.
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <input type="date" className={`${CHAMP} max-w-48`} value={dateCalendrier}
                 onChange={e => setDateCalendrier(e.target.value)} />
          <button type="button" className={PRIMAIRE} onClick={enregistrerCalendrier}>
            Enregistrer la date
          </button>
        </div>
      </div>

      <button type="button" className={PRIMAIRE}
              onClick={() => setEdition({ libelle: '', sourceUrl: '', visible: true })}>
        <Plus className="w-4 h-4" /> Nouvelle période
      </button>

      {edition && (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 space-y-3">
          <label className="block text-xs font-semibold text-slate-500">
            Libellé
            <input className={CHAMP} value={edition.libelle ?? ''}
                   onChange={e => setEdition({ ...edition, libelle: e.target.value })} />
          </label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <label className="block text-xs font-semibold text-slate-500">
              Ouverture
              <input type="date" className={CHAMP} value={edition.dateDebut ?? ''}
                     onChange={e => setEdition({ ...edition, dateDebut: e.target.value })} />
            </label>
            <label className="block text-xs font-semibold text-slate-500">
              Clôture
              <input type="date" className={CHAMP} value={edition.dateFin ?? ''}
                     onChange={e => setEdition({ ...edition, dateFin: e.target.value })} />
            </label>
          </div>
          <label className="block text-xs font-semibold text-slate-500">
            Spécialités non ouvertes (facultatif)
            <input className={CHAMP} value={edition.specialitesNonOuvertes ?? ''}
                   onChange={e => setEdition({ ...edition, specialitesNonOuvertes: e.target.value })} />
          </label>
          <label className="block text-xs font-semibold text-slate-500">
            Source officielle (obligatoire)
            <input className={CHAMP} placeholder="https://..." value={edition.sourceUrl ?? ''}
                   onChange={e => setEdition({ ...edition, sourceUrl: e.target.value })} />
          </label>
          <p className="text-xs text-slate-600">
            Une date d'ouverture engage le praticien à préparer un dossier : la page publique
            cite donc toujours la source où elle a été lue.
          </p>
          <div className="flex gap-2">
            <button type="button" className={PRIMAIRE} disabled={envoi} onClick={enregistrer}>
              Enregistrer
            </button>
            <button type="button" className={SECONDAIRE} onClick={() => setEdition(null)}>
              Annuler
            </button>
          </div>
        </div>
      )}

      {periodes.length === 0 ? (
        <EmptyState icon={Plus} title="Aucune période enregistrée." />
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
              <tr>
                <th className="px-4 py-3">Période</th>
                <th className="px-4 py-3">Ouverture</th>
                <th className="px-4 py-3">Clôture</th>
                <th className="px-4 py-3">Source</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {periodes.map(p => (
                <tr key={p.id}>
                  <td className="px-4 py-3 font-medium text-slate-800">
                    {p.libelle}
                    {!p.visible && <span className="text-xs text-slate-400"> · masquée</span>}
                  </td>
                  <td className="px-4 py-3 text-slate-600">{p.dateDebut || '—'}</td>
                  <td className="px-4 py-3 text-slate-600">{p.dateFin || '—'}</td>
                  <td className="px-4 py-3">
                    {p.sourceUrl ? (
                      <a href={p.sourceUrl} target="_blank" rel="noopener noreferrer"
                         className="inline-flex items-center gap-1 text-brand hover:underline">
                        <ExternalLink className="w-3.5 h-3.5" /> Voir
                      </a>
                    ) : <span className="text-danger text-xs">manquante</span>}
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <button type="button" onClick={() => setEdition(p)} aria-label="Modifier"
                            className="p-2 rounded-lg text-slate-500 hover:bg-slate-100">
                      <Pencil className="w-4 h-4" />
                    </button>
                    <button type="button" onClick={() => supprimer(p)} aria-label="Supprimer"
                            className="p-2 rounded-lg text-danger hover:bg-danger/10">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ══ Liens ════════════════════════════════════════════════════════════════════════════

export function OngletLiens({
  liens, onErreur, onSucces, onChange,
}: CommunProps & { liens: LienAdmin[] }) {
  const [edition, setEdition] = useState<Partial<LienAdmin> | null>(null);
  const [erreurUrl, setErreurUrl] = useState<string | null>(null);

  const enregistrer = async () => {
    if (!edition?.libelle?.trim()) {
      onErreur('Le libellé est obligatoire.');
      return;
    }
    // Le refus est prononcé AVANT l'appel, avec le message exact de la règle : une adresse
    // « javascript: » placée là s'exécuterait chez le visiteur qui clique.
    const probleme = verifierAdresseDeLien(edition.url);
    if (probleme) {
      setErreurUrl(probleme);
      return;
    }
    setErreurUrl(null);
    try {
      if (edition.id) await api.modifierLien(edition.id, edition);
      else await api.creerLien(edition);
      setEdition(null);
      onChange();
      onSucces('Lien enregistré.');
    } catch (e: any) {
      onErreur(e.response?.data?.message || "Le lien n'a pas pu être enregistré.");
    }
  };

  const marquerVerifie = async (l: LienAdmin) => {
    try {
      await api.marquerLienVerifie(l.id);
      onChange();
      onSucces(`« ${l.libelle} » marqué comme vérifié aujourd'hui.`);
    } catch {
      onErreur("Le lien n'a pas pu être marqué.");
    }
  };

  const jours = (iso: string | null) => {
    if (!iso) return null;
    return Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
  };

  return (
    <div className="space-y-4">
      <button type="button" className={PRIMAIRE} onClick={() => setEdition({ libelle: '', url: '' })}>
        <Plus className="w-4 h-4" /> Nouveau lien
      </button>

      {edition && (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 space-y-3">
          <label className="block text-xs font-semibold text-slate-500">
            Libellé
            <input className={CHAMP} value={edition.libelle ?? ''}
                   onChange={e => setEdition({ ...edition, libelle: e.target.value })} />
          </label>
          <label className="block text-xs font-semibold text-slate-500">
            Adresse
            <input className={CHAMP} placeholder="https://..." value={edition.url ?? ''}
                   aria-invalid={erreurUrl ? true : undefined}
                   onChange={e => { setEdition({ ...edition, url: e.target.value }); setErreurUrl(null); }} />
          </label>
          {erreurUrl && (
            <p role="alert" className="text-sm text-danger">{erreurUrl}</p>
          )}
          <div className="flex gap-2">
            <button type="button" className={PRIMAIRE} onClick={enregistrer}>Enregistrer</button>
            <button type="button" className={SECONDAIRE}
                    onClick={() => { setEdition(null); setErreurUrl(null); }}>
              Annuler
            </button>
          </div>
        </div>
      )}

      {liens.length === 0 ? (
        <EmptyState icon={ExternalLink} title="Aucun lien officiel." />
      ) : (
        <ul className="space-y-2">
          {liens.map(l => {
            const age = jours(l.verifieLe);
            const vieux = age === null || age > 90;
            return (
              <li key={l.id}
                  className="rounded-xl border border-slate-200 bg-white p-3 flex flex-wrap items-center gap-3">
                <div className="flex-1 min-w-0">
                  <div className="font-semibold text-slate-800">{l.libelle}</div>
                  <a href={l.url} target="_blank" rel="noopener noreferrer"
                     className="text-xs text-brand hover:underline break-all">{l.url}</a>
                  <div className={`text-xs ${vieux ? 'text-warning font-semibold' : 'text-slate-500'}`}>
                    {age === null
                      ? 'jamais vérifié'
                      : `vérifié il y a ${age} jour${age > 1 ? 's' : ''}`}
                  </div>
                </div>
                <div className="flex items-center gap-1">
                  <button type="button" onClick={() => marquerVerifie(l)} className={SECONDAIRE}>
                    <Check className="w-4 h-4" /> Marquer comme vérifié
                  </button>
                  <button type="button" onClick={() => setEdition(l)} aria-label="Modifier"
                          className="p-2 rounded-lg text-slate-500 hover:bg-slate-100">
                    <Pencil className="w-4 h-4" />
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

// ══ Spécialités ══════════════════════════════════════════════════════════════════════

const GROUPES: { cle: SpecialiteAdmin['groupe']; titre: string }[] = [
  { cle: 'profession', titre: 'Professions' },
  { cle: 'medecine', titre: 'Spécialités médicales' },
];

export function OngletSpecialites({
  specialites, onErreur, onSucces, onChange,
}: CommunProps & { specialites: SpecialiteAdmin[] }) {
  const [nouveau, setNouveau] = useState<Record<string, string>>({});

  const ajouter = async (groupe: SpecialiteAdmin['groupe']) => {
    const libelle = (nouveau[groupe] ?? '').trim();
    if (!libelle) return;
    try {
      await api.creerSpecialite({ groupe, libelle });
      setNouveau({ ...nouveau, [groupe]: '' });
      onChange();
      onSucces('Spécialité ajoutée.');
    } catch (e: any) {
      onErreur(e.response?.data?.message || "La spécialité n'a pas pu être ajoutée.");
    }
  };

  const supprimer = async (s: SpecialiteAdmin) => {
    if (!confirmerSuppression(`« ${s.libelle} »`)) return;
    try {
      await api.supprimerSpecialite(s.id);
      onChange();
      onSucces('Spécialité supprimée.');
    } catch {
      onErreur('La suppression a échoué.');
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {GROUPES.map(g => {
        const liste = specialites.filter(s => s.groupe === g.cle);
        return (
          <section key={g.cle} className="rounded-2xl border border-slate-200 bg-white p-4">
            <h3 className="font-semibold text-brand-dark mb-3">
              {g.titre} <span className="text-slate-400 font-normal">({liste.length})</span>
            </h3>
            <div className="flex gap-2 mb-3">
              <input className={CHAMP} value={nouveau[g.cle] ?? ''} placeholder="Ajouter…"
                     onChange={e => setNouveau({ ...nouveau, [g.cle]: e.target.value })}
                     onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); ajouter(g.cle); } }} />
              <button type="button" className={PRIMAIRE} onClick={() => ajouter(g.cle)}>
                <Plus className="w-4 h-4" />
              </button>
            </div>
            {liste.length === 0 ? (
              <p className="text-sm text-slate-500">Aucune entrée.</p>
            ) : (
              <ul className="flex flex-wrap gap-2">
                {liste.map(s => (
                  <li key={s.id}
                      className="inline-flex items-center gap-1.5 pl-3 pr-1.5 py-1 rounded-full bg-slate-100 text-sm text-slate-700">
                    {s.libelle}
                    <button type="button" onClick={() => supprimer(s)}
                            aria-label={`Supprimer ${s.libelle}`}
                            className="p-1 rounded-full text-slate-400 hover:text-danger hover:bg-white">
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        );
      })}
    </div>
  );
}

// ══ Offres & tarifs ══════════════════════════════════════════════════════════════════

export function OngletOffres({
  offres, estSuperAdmin, onErreur, onSucces, onChange,
}: CommunProps & { offres: OffreAdmin[]; estSuperAdmin: boolean }) {
  const [edition, setEdition] = useState<Partial<OffreAdmin> | null>(null);

  const enregistrer = async () => {
    if (!edition?.titre?.trim()) {
      onErreur('Le titre est obligatoire.');
      return;
    }
    try {
      if (edition.id) await api.modifierOffre(edition.id, edition);
      else await api.creerOffre(edition);
      setEdition(null);
      onChange();
      onSucces('Offre enregistrée.');
    } catch (e: any) {
      onErreur(e.response?.status === 403
        ? "Seul un super administrateur peut modifier les prix."
        : e.response?.data?.message || "L'offre n'a pas pu être enregistrée.");
    }
  };

  return (
    <div className="space-y-4">
      {/*
        Le bouton est masqué pour qui n'a pas le droit, et le serveur refuse de toute façon.
        Les deux sont nécessaires : masquer seul laisserait la route ouverte à qui la
        connaît ; refuser seul laisserait cliquer sur un bouton qui échoue toujours.
      */}
      {estSuperAdmin ? (
        <button type="button" className={PRIMAIRE}
                onClick={() => setEdition({ titre: '', devise: 'EUR', actif: true, technique: false })}>
          <Plus className="w-4 h-4" /> Nouvelle offre
        </button>
      ) : (
        <p className="inline-flex items-center gap-2 text-sm text-slate-600 rounded-lg bg-slate-100 px-3 py-2">
          <Lock className="w-4 h-4" />
          Lecture seule : les prix relèvent du super administrateur.
        </p>
      )}

      {edition && estSuperAdmin && (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 space-y-3">
          <label className="block text-xs font-semibold text-slate-500">
            Titre
            <input className={CHAMP} value={edition.titre ?? ''}
                   onChange={e => setEdition({ ...edition, titre: e.target.value })} />
          </label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <label className="block text-xs font-semibold text-slate-500">
              Prix (vide = « Sur devis »)
              <input type="number" className={CHAMP} value={edition.prix ?? ''}
                     onChange={e => setEdition({
                       ...edition, prix: e.target.value === '' ? null : Number(e.target.value),
                     })} />
            </label>
            <label className="block text-xs font-semibold text-slate-500">
              Devise
              <input className={CHAMP} value={edition.devise ?? 'EUR'}
                     onChange={e => setEdition({ ...edition, devise: e.target.value })} />
            </label>
          </div>
          <label className="block text-xs font-semibold text-slate-500">
            Points (une ligne par point)
            <textarea className={CHAMP} rows={5} value={edition.points ?? ''}
                      onChange={e => setEdition({ ...edition, points: e.target.value })} />
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={edition.actif ?? true}
                   onChange={e => setEdition({ ...edition, actif: e.target.checked })} />
            Offre active sur la page publique
          </label>
          <div className="flex gap-2">
            <button type="button" className={PRIMAIRE} onClick={enregistrer}>Enregistrer</button>
            <button type="button" className={SECONDAIRE} onClick={() => setEdition(null)}>
              Annuler
            </button>
          </div>
        </div>
      )}

      {offres.length === 0 ? (
        <EmptyState icon={Lock} title="Aucune offre." />
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
              <tr>
                <th className="px-4 py-3">Offre</th>
                <th className="px-4 py-3">Prix</th>
                <th className="px-4 py-3">État</th>
                {estSuperAdmin && <th className="px-4 py-3" />}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {offres.map(o => (
                <tr key={o.id}>
                  <td className="px-4 py-3">
                    <div className="font-medium text-slate-800">{o.titre}</div>
                    <div className="text-xs text-slate-500">
                      clé « {o.cle} »{o.technique && ' · technique'}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-slate-700 tabular-nums">
                    {o.prix === null ? 'Sur devis' : `${o.prix} ${o.devise}`}
                  </td>
                  <td className="px-4 py-3 text-slate-600">{o.actif ? 'Active' : 'Inactive'}</td>
                  {estSuperAdmin && (
                    <td className="px-4 py-3 text-right">
                      <button type="button" onClick={() => setEdition(o)} aria-label="Modifier"
                              className="p-2 rounded-lg text-slate-500 hover:bg-slate-100">
                        <Pencil className="w-4 h-4" />
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
