import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Loader2, Link2, CalendarClock, Hourglass, Rocket, History, RotateCcw, Lock, Eye,
} from 'lucide-react';
import {
  adminExercerEnFranceService as api,
  type SectionAdmin, type PeriodeAdmin, type LienAdmin, type SpecialiteAdmin, type OffreAdmin,
  type IndicateursEvc, type VersionPubliee,
} from '../../api/adminExercerEnFranceService';
import {
  exercerEnFranceService, type PageExercerEnFrance,
} from '../../api/exercerEnFranceService';
import {
  OngletSections, OngletPeriodes, OngletLiens, OngletSpecialites, OngletOffres,
} from '../../components/exercerenfrance/admin/OngletsEvc';
import { useAuth } from '../../context/AuthContext';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';
import { EmptyState } from '../../components/common/EmptyState';

type Onglet = 'sections' | 'periodes' | 'liens' | 'specialites' | 'offres' | 'historique';

const ONGLETS: { cle: Onglet; titre: string }[] = [
  { cle: 'sections', titre: 'Sections' },
  { cle: 'periodes', titre: 'Périodes' },
  { cle: 'liens', titre: 'Liens' },
  { cle: 'specialites', titre: 'Spécialités' },
  { cle: 'offres', titre: 'Offres & tarifs' },
  { cle: 'historique', titre: 'Historique' },
];

function dateHeure(iso: string | null): string {
  if (!iso) return 'jamais';
  return new Date(iso).toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

/**
 * L'administration de la page « Exercer en France ».
 *
 * <p><b>Ce que l'écran surveille.</b> Cette page publique annonce à des praticiens étrangers
 * des dates de concours et des démarches administratives. Une information périmée n'y est pas
 * un détail de présentation : elle fait préparer un dossier pour une session qui n'existe
 * plus. Les quatre rappels en tête existent pour cela — ils ne disent pas ce qui est cassé,
 * mais ce qui a vieilli.</p>
 *
 * <p><b>Brouillon et version en ligne sont deux choses.</b> Tout ce qui est modifié ici reste
 * un brouillon jusqu'à « Publier ». C'est ce qui permet de corriger une page à plusieurs
 * mains sans la montrer à moitié faite.</p>
 */
export function AdminExercerEnFrancePage() {
  const { user } = useAuth();
  const estSuperAdmin = user?.role === 'SUPER_ADMIN';

  const [onglet, setOnglet] = useState<Onglet>('sections');
  const [chargement, setChargement] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const [indicateurs, setIndicateurs] = useState<IndicateursEvc | null>(null);
  const [sections, setSections] = useState<SectionAdmin[]>([]);
  const [periodes, setPeriodes] = useState<PeriodeAdmin[]>([]);
  const [liens, setLiens] = useState<LienAdmin[]>([]);
  const [specialites, setSpecialites] = useState<SpecialiteAdmin[]>([]);
  const [offres, setOffres] = useState<OffreAdmin[]>([]);
  const [versions, setVersions] = useState<VersionPubliee[]>([]);
  const [brouillon, setBrouillon] = useState<PageExercerEnFrance | null>(null);
  const [apercuOuvert, setApercuOuvert] = useState(false);
  const [publication, setPublication] = useState(false);

  const erreur = (message: string) => setToast({ message, type: 'error' });
  const succes = (message: string) => setToast({ message, type: 'success' });

  const charger = useCallback(async () => {
    setChargement(true);
    try {
      const [i, s, p, l, sp, o, v, b] = await Promise.all([
        api.indicateurs(), api.sections(), api.periodes(), api.liens(),
        api.specialites(), api.offres(), api.versions(), api.apercu(),
      ]);
      setIndicateurs(i); setSections(s); setPeriodes(p); setLiens(l);
      setSpecialites(sp); setOffres(o); setVersions(v); setBrouillon(b);
    } catch {
      erreur("Le contenu n'a pas pu être chargé.");
    } finally {
      setChargement(false);
    }
  }, []);

  useEffect(() => { charger(); }, [charger]);

  /**
   * Publier, après avoir montré ce qui va changer.
   *
   * <p>La comparaison est faite entre le BROUILLON et la page RÉELLEMENT EN LIGNE, relue à
   * l'instant. Comparer au dernier numéro de version ne dirait rien : on publie pour corriger
   * ce que le visiteur voit, et c'est donc cela qu'il faut mettre en regard.</p>
   */
  const publier = async () => {
    setPublication(true);
    try {
      const [enLigne, aPublier] = await Promise.all([
        exercerEnFranceService.page().catch(() => null),
        api.apercu(),
      ]);

      const compte = (p: PageExercerEnFrance | null) => ({
        sections: p?.sections.length ?? 0,
        periodes: p?.periodes.length ?? 0,
        liens: p?.liens.length ?? 0,
        specialites: p?.specialites.length ?? 0,
        offres: p?.offres.length ?? 0,
      });
      const avant = compte(enLigne);
      const apres = compte(aPublier);

      const ecarts = (Object.keys(apres) as (keyof typeof apres)[])
        .filter(c => avant[c] !== apres[c])
        .map(c => `  • ${c} : ${avant[c]} → ${apres[c]}`);

      const resume = enLigne === null
        ? "Rien n'est encore publié : cette publication créera la première version en ligne."
        : ecarts.length > 0
          ? `Ce qui change par rapport à la version ${enLigne.version ?? '?'} en ligne :\n`
            + ecarts.join('\n')
            + '\n\nLes textes modifiés ne sont pas listés ici : seuls les nombres le sont.'
          : `Aucun ajout ni suppression par rapport à la version ${enLigne.version ?? '?'} en `
            + 'ligne. Des textes ont pu être modifiés — utilisez l\'Aperçu pour les relire.';

      if (!window.confirm(`Publier la page « Exercer en France » ?\n\n${resume}\n\n`
                          + 'La page publique sera modifiée immédiatement.')) {
        return;
      }

      const v = await api.publier();
      succes(`Version ${v.version} publiée.`);
      charger();
    } catch (e: any) {
      erreur(e.response?.data?.message || "La publication a échoué.");
    } finally {
      setPublication(false);
    }
  };

  const restaurer = async (v: VersionPubliee) => {
    // Ce que cette confirmation annonçait était FAUX, et dangereusement : elle promettait
    // le remplacement du BROUILLON. La restauration fait l'inverse — elle republie
    // l'ancienne version en ligne, tout de suite, sans toucher au brouillon. La recette
    // l'a montré en remettant en ligne des textes retirés pour raison juridique.
    if (!window.confirm(
      `Restaurer la version ${v.version}, publiée le ${dateHeure(v.publieLe)} ?\n\n`
      + "ATTENTION : cette version sera REMISE EN LIGNE immédiatement, à la place de "
      + "ce que le visiteur voit aujourd'hui. Votre brouillon n'est pas modifié.\n\n"
      + "Une version ancienne porte les textes et les prix de son époque : vérifiez "
      + "par l'Aperçu qu'elle ne remet pas en ligne un contenu retiré depuis.",
    )) return;
    try {
      const r = await api.restaurer(v.version);
      succes(`Version ${v.version} remise en ligne, sous le numéro ${r.version}.`);
      charger();
    } catch (e: any) {
      erreur(e.response?.status === 403
        ? 'Seul un super administrateur peut restaurer une version.'
        : "La restauration a échoué.");
    }
  };

  const rappels = indicateurs ? [
    {
      actif: indicateurs.liensNonVerifies > 0,
      icone: Link2,
      texte: `${indicateurs.liensNonVerifies} lien(s) officiel(s) non vérifié(s) depuis plus de 90 jours`,
      vers: 'liens' as Onglet, lien: null,
    },
    {
      actif: indicateurs.periodesSansDates > 0,
      icone: CalendarClock,
      texte: `${indicateurs.periodesSansDates} période(s) sans dates`,
      vers: 'periodes' as Onglet, lien: null,
    },
    {
      actif: indicateurs.calendrierAVerifier,
      icone: CalendarClock,
      texte: "Le calendrier des commissions n'a pas été constaté depuis plus de 90 jours",
      vers: 'periodes' as Onglet, lien: null,
    },
    {
      actif: indicateurs.prestationsSansFin > 0,
      icone: Hourglass,
      texte: `${indicateurs.prestationsSansFin} prestation(s) engagée(s) depuis plus de 12 mois `
             + 'sans date de fin : leur délai de conservation n\'a jamais commencé',
      vers: null,
      // Ces dossiers se corrigent dans la fiche de chaque demande, pas ici : le rappel
      // y conduit, au lieu de signaler un problème sans donner le moyen d'agir.
      lien: '/admin/demandes-accompagnement?statut=en_cours',
    },
  ].filter(r => r.actif) : [];

  if (chargement) {
    return (
      <div className="p-12 flex justify-center">
        <Loader2 className="w-8 h-8 text-brand animate-spin" />
      </div>
    );
  }

  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <PageHeader
        title="Exercer en France"
        subtitle="Le contenu de la page publique, et ce qu'il faut tenir à jour."
        actions={
          <>
            <button type="button" onClick={() => setApercuOuvert(true)}
                    className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 text-sm font-medium text-slate-700 hover:bg-slate-50">
              <Eye className="w-4 h-4" /> Aperçu
            </button>
            <button type="button" onClick={publier} disabled={publication}
                    className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-brand-dark text-white text-sm font-medium hover:opacity-90 disabled:opacity-40">
              <Rocket className="w-4 h-4" /> Publier
            </button>
          </>
        }
      />

      {/* ── Les quatre chiffres ───────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4">
        <Carte titre="Demandes à traiter" valeur={indicateurs?.demandesATraiter ?? 0} />
        <Carte titre="Demandes ce mois" valeur={indicateurs?.demandesCeMois ?? 0} />
        <Carte titre="Demandes payées" valeur={indicateurs?.demandesPayees ?? 0} />
        <Carte
          titre="Version en ligne"
          valeur={indicateurs?.version ?? '—'}
          detail={indicateurs?.dernierePublication
            ? `publiée le ${dateHeure(indicateurs.dernierePublication)}`
            : 'jamais publiée'}
        />
      </div>

      {/* ── Les rappels ───────────────────────────────────────────────────────────── */}
      {rappels.length > 0 && (
        <ul className="mb-4 space-y-2">
          {rappels.map((r, i) => (
            <li key={i}>
              <div className="flex items-start gap-3 rounded-xl border border-warning/30 bg-warning/10 px-4 py-3">
                <r.icone className="w-4 h-4 text-warning shrink-0 mt-0.5" />
                <span className="text-sm text-slate-700 flex-1">{r.texte}</span>
                {r.vers && (
                  <button type="button" onClick={() => setOnglet(r.vers!)}
                          className="text-sm font-semibold text-warning hover:underline shrink-0">
                    Voir
                  </button>
                )}
                {r.lien && (
                  <Link to={r.lien}
                        className="text-sm font-semibold text-warning hover:underline shrink-0">
                    Voir
                  </Link>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* ── Les onglets ───────────────────────────────────────────────────────────── */}
      <div className="border-b border-slate-200 mb-4 overflow-x-auto">
        <nav className="flex gap-1 min-w-max" role="tablist">
          {ONGLETS.map(o => (
            <button
              key={o.cle}
              type="button"
              role="tab"
              aria-selected={onglet === o.cle}
              onClick={() => setOnglet(o.cle)}
              className={`px-4 py-2.5 text-sm font-medium border-b-2 whitespace-nowrap ${
                onglet === o.cle
                  ? 'border-brand-dark text-brand-dark'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              {o.titre}
              {o.cle === 'offres' && !estSuperAdmin && (
                <Lock className="w-3 h-3 inline-block ml-1.5 -mt-0.5" aria-label="lecture seule" />
              )}
            </button>
          ))}
        </nav>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_18rem] gap-6 items-start">
        <div>
          {onglet === 'sections' && (
            <OngletSections sections={sections} onApercu={() => setApercuOuvert(true)}
                            onErreur={erreur} onSucces={succes} onChange={charger} />
          )}
          {onglet === 'periodes' && (
            <OngletPeriodes periodes={periodes}
                            calendrierActualiseLe={brouillon?.calendrierActualiseLe ?? null}
                            onErreur={erreur} onSucces={succes} onChange={charger} />
          )}
          {onglet === 'liens' && (
            <OngletLiens liens={liens} onErreur={erreur} onSucces={succes} onChange={charger} />
          )}
          {onglet === 'specialites' && (
            <OngletSpecialites specialites={specialites}
                               onErreur={erreur} onSucces={succes} onChange={charger} />
          )}
          {onglet === 'offres' && (
            <OngletOffres offres={offres} estSuperAdmin={estSuperAdmin}
                          onErreur={erreur} onSucces={succes} onChange={charger} />
          )}
          {onglet === 'historique' && (
            <Historique versions={versions} estSuperAdmin={estSuperAdmin} onRestaurer={restaurer} />
          )}
        </div>

        {/* ── Qui a le droit de quoi ────────────────────────────────────────────── */}
        <aside className="rounded-2xl border border-slate-200 bg-white p-4">
          <h2 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-3">
            Qui peut quoi
          </h2>
          <dl className="space-y-2 text-sm">
            <Droit quoi="Contenu, périodes, liens, spécialités" qui="Mobilité et super admin" ouvert />
            <Droit quoi="Publier la page" qui="Mobilité et super admin" ouvert />
            <Droit quoi="Offres et prix" qui="Super administrateur seul" ouvert={estSuperAdmin} />
            <Droit quoi="Restaurer une version" qui="Super administrateur seul" ouvert={estSuperAdmin} />
          </dl>
          <p className="text-xs text-slate-500 mt-3">
            Les boutons que vous ne voyez pas sont aussi refusés par le serveur : les masquer ne
            suffirait pas.
          </p>
        </aside>
      </div>

      {apercuOuvert && brouillon && (
        <Apercu page={brouillon} onFermer={() => setApercuOuvert(false)} />
      )}

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}

function Carte({ titre, valeur, detail }: { titre: string; valeur: number | string; detail?: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4">
      <div className="text-xs font-semibold text-slate-500">{titre}</div>
      <div className="text-2xl font-bold text-brand-dark tabular-nums mt-1">{valeur}</div>
      {detail && <div className="text-xs text-slate-500 mt-1">{detail}</div>}
    </div>
  );
}

function Droit({ quoi, qui, ouvert }: { quoi: string; qui: string; ouvert: boolean }) {
  return (
    <div>
      <dt className="font-medium text-slate-800">{quoi}</dt>
      <dd className={`text-xs ${ouvert ? 'text-success' : 'text-slate-500'}`}>
        {qui}{!ouvert && ' — lecture seule pour vous'}
      </dd>
    </div>
  );
}

function Historique({
  versions, estSuperAdmin, onRestaurer,
}: { versions: VersionPubliee[]; estSuperAdmin: boolean; onRestaurer: (v: VersionPubliee) => void }) {
  if (versions.length === 0) {
    return <EmptyState icon={History} title="Aucune version publiée."
                       description="L'historique se remplit à la première publication." />;
  }
  return (
    <ul className="space-y-2">
      {versions.map(v => (
        <li key={v.version}
            className="rounded-xl border border-slate-200 bg-white p-3 flex flex-wrap items-center gap-3">
          <div className="flex-1 min-w-0">
            <div className="font-semibold text-slate-800">
              Version {v.version}
              {v.restaureDe !== null && v.restaureDe !== undefined && (
                <span className="text-xs font-normal text-slate-500"> · restaurée de la {v.restaureDe}</span>
              )}
            </div>
            <div className="text-xs text-slate-500">Publiée le {dateHeure(v.publieLe)}</div>
          </div>
          {estSuperAdmin ? (
            <button type="button" onClick={() => onRestaurer(v)}
                    className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 text-sm font-medium text-slate-700 hover:bg-slate-50">
              <RotateCcw className="w-4 h-4" /> Restaurer
            </button>
          ) : (
            <span className="inline-flex items-center gap-1.5 text-xs text-slate-500">
              <Lock className="w-3.5 h-3.5" /> super admin
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}

/**
 * L'aperçu du brouillon.
 *
 * <p>Il affiche ce que le serveur publierait, et non une reconstitution faite ici : une
 * approximation donnerait confiance dans un rendu qui n'est pas celui du visiteur.</p>
 */
function Apercu({ page, onFermer }: { page: PageExercerEnFrance; onFermer: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-slate-900/40" onClick={onFermer} aria-hidden="true" />
      <aside role="dialog" aria-modal="true" aria-label="Aperçu de la page"
             className="relative w-full max-w-2xl bg-white h-full overflow-y-auto shadow-xl">
        <div className="sticky top-0 bg-white border-b border-slate-200 px-5 py-4 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-brand-dark">Aperçu du brouillon</h2>
            <p className="text-sm text-slate-500">
              Tel qu'il serait publié — ce n'est pas encore ce que le visiteur voit.
            </p>
          </div>
          <button type="button" onClick={onFermer}
                  className="px-3 py-2 rounded-lg text-sm text-slate-600 hover:bg-slate-100">
            Fermer
          </button>
        </div>
        <div className="p-5 space-y-6">
          {page.sections.map(s => (
            <section key={s.cle}>
              {s.surTitre && (
                <div className="text-xs font-semibold uppercase tracking-wider text-brand">
                  {s.surTitre}
                </div>
              )}
              <h3 className="text-lg font-bold text-brand-dark">{s.titre}</h3>
              {(s.contenu ?? '').split(/^##\s+/m).filter(Boolean).map((bloc, i) => {
                const [premiere, ...reste] = bloc.split('\n');
                const estEncadre = i > 0 || (s.contenu ?? '').trimStart().startsWith('##');
                return estEncadre ? (
                  <div key={i} className="mt-3 rounded-xl bg-brand-light/50 p-3">
                    <div className="font-semibold text-brand-dark">{premiere}</div>
                    <p className="text-sm text-slate-700 whitespace-pre-wrap">{reste.join('\n').trim()}</p>
                  </div>
                ) : (
                  <p key={i} className="mt-2 text-sm text-slate-700 whitespace-pre-wrap">{bloc.trim()}</p>
                );
              })}
            </section>
          ))}

          <section>
            <h3 className="text-sm font-bold uppercase tracking-wider text-slate-500 mb-2">
              Périodes ({page.periodes.length})
            </h3>
            <ul className="text-sm text-slate-700 space-y-1">
              {page.periodes.map((p, i) => (
                <li key={i}>{p.libelle} — {p.dateDebut || '?'} → {p.dateFin || '?'} ({p.statut})</li>
              ))}
            </ul>
          </section>

          <section>
            <h3 className="text-sm font-bold uppercase tracking-wider text-slate-500 mb-2">
              Liens ({page.liens.length}) · Spécialités ({page.specialites.length}) ·
              Offres ({page.offres.length})
            </h3>
            <p className="text-sm text-slate-600">
              Calendrier des commissions constaté le {page.calendrierActualiseLe || '—'}.
            </p>
          </section>
        </div>
      </aside>
    </div>
  );
}
