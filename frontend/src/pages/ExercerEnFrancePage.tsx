import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, ArrowRight, CalendarDays, ExternalLink, Info, Loader2 } from 'lucide-react';
import {
  exercerEnFranceService,
  type OffreEvc, type PageExercerEnFrance, type PeriodeEvc, type SectionEvc,
} from '../api/exercerEnFranceService';
import { badgeDePeriode, OFFRE_ALERTE_AEP } from '../lib/exercerEnFrance';

/**
 * « Exercer en France » : ce qu'un praticien diplômé hors UE doit comprendre avant de se
 * lancer, et ce que nous proposons pour l'y aider.
 *
 * <p><b>Aucun texte métier n'est écrit ici.</b> Tout vient de l'API, parce que cette page
 * décrit une procédure administrative qui change : des dates de dépôt, un décret, une liste
 * de spécialités. Figés dans le composant, ces textes auraient exigé un déploiement à chaque
 * annonce du CNG — c'est-à-dire qu'ils auraient vieilli.</p>
 *
 * <p>Le composant ne connaît que des <b>clés</b> de section, stables par construction, et la
 * forme dans laquelle chacune se présente.</p>
 */
export function ExercerEnFrancePage() {
  const [page, setPage] = useState<PageExercerEnFrance | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState(false);

  useEffect(() => {
    document.title = 'Exercer en France avec un diplôme hors UE · Optimi Santé';
    const description = document.querySelector('meta[name="description"]');
    description?.setAttribute('content',
      'Médecins, chirurgiens-dentistes, sages-femmes, pharmaciens diplômés hors Union '
      + 'européenne : comprenez les épreuves de vérification des connaissances, '
      + "l'attestation d'exercice provisoire et la commission d'autorisation d'exercice.");

    exercerEnFranceService.page()
      .then(setPage)
      .catch(() => setErreur(true))
      .finally(() => setChargement(false));
  }, []);

  const sections = useMemo(() => {
    const parCle = new Map<string, SectionEvc>();
    page?.sections.forEach(s => parCle.set(s.cle, s));
    return parCle;
  }, [page]);

  const lien = (cle: string) => page?.liens.find(l => l.cle === cle) ?? null;

  if (chargement) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center" role="status">
        <Loader2 className="w-6 h-6 animate-spin text-brand" aria-hidden="true" />
        <span className="sr-only">Chargement de la page</span>
      </div>
    );
  }

  if (erreur || !page) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-20 text-center">
        <h1 className="text-2xl font-bold text-brand-dark">Page momentanément indisponible</h1>
        <p className="mt-3 text-slate-600">
          Les informations n'ont pas pu être chargées. Réessayez dans un instant.
        </p>
        <Link to="/" className="mt-6 inline-block text-brand underline underline-offset-2">
          Retour à l'accueil
        </Link>
      </div>
    );
  }

  return (
    <div className="bg-white">
      <FilDAriane />
      <Banniere sections={sections} />
      <BandeauNonOfficiel section={sections.get('bandeau_non_officiel')} lien={lien('site_cng')} />

      <div className="max-w-6xl mx-auto px-4 sm:px-6 space-y-16 sm:space-y-20 py-12 sm:py-16">
        <SectionEvcEpreuves section={sections.get('evc')} lienSession={lien('session_evc')} />
        <SectionAep
          section={sections.get('aep')}
          periodes={page.periodes}
          specialites={page.specialites}
          lienRenouvellement={lien('formulaire_renouvellement_aep')}
        />
        <SectionDossier section={sections.get('dossier')} />
        <SectionCae
          section={sections.get('cae')}
          lienCalendrier={lien('calendrier_commissions')}
          calendrierActualiseLe={page.calendrierActualiseLe}
        />
        <SectionAutresVoies
          section={sections.get('autres_voies')}
          lienFellowship={lien('fellowship')}
          lienRefugies={lien('refugies')}
        />
        <SectionAccompagnements section={sections.get('accompagnements')} offres={page.offres} />
      </div>

      <BandeauRendezVous />
      {page.misAJourLe && (
        <p className="max-w-6xl mx-auto px-4 sm:px-6 pb-10 text-xs text-slate-500">
          Informations mises à jour le{' '}
          <time dateTime={page.misAJourLe}>{dateCourte(page.misAJourLe)}</time>
          {' · sources officielles CNG et ministère de la Santé'}
        </p>
      )}
    </div>
  );
}

/* ── Repères ──────────────────────────────────────────────────────────────────────────── */

function FilDAriane() {
  return (
    <nav aria-label="Fil d'Ariane" className="max-w-6xl mx-auto px-4 sm:px-6 pt-6">
      <ol className="flex flex-wrap items-center gap-2 text-sm text-slate-500">
        <li><Link to="/" className="underline-offset-2 hover:underline">Accueil</Link></li>
        <li aria-hidden="true" className="text-slate-400">›</li>
        <li><Link to="/services" className="underline-offset-2 hover:underline">Nos services</Link></li>
        <li aria-hidden="true" className="text-slate-400">›</li>
        {/* aria-current : la marche courante est annoncée comme telle, et non comme un lien. */}
        <li aria-current="page" className="font-semibold text-brand-dark">Exercer en France</li>
      </ol>
    </nav>
  );
}

/** Les quatre étapes du parcours, dans l'ordre où le praticien les franchit. */
const PARCOURS = [
  { titre: 'Épreuves de vérification des connaissances', texte: 'Concours écrit, places limitées par spécialité' },
  { titre: 'Parcours de consolidation des compétences', texte: "Exercice encadré dans un établissement d'accueil" },
  { titre: "Commission d'autorisation d'exercice", texte: "Avis sur l'autorisation de plein exercice" },
  { titre: "Inscription à l'Ordre", texte: "Exercice à l'hôpital ou en libéral" },
];

function Banniere({ sections }: { sections: Map<string, SectionEvc> }) {
  return (
    <header className="max-w-6xl mx-auto px-4 sm:px-6 pt-6">
      <div className="bg-brand-cream rounded-2xl px-6 sm:px-10 py-10 sm:py-14 grid lg:grid-cols-2 gap-10 items-start">
        <div>
          <p className="text-xs font-semibold tracking-widest text-brand-accent uppercase">
            Mobilité — praticiens diplômés hors UE
          </p>
          <h1 className="mt-4 text-3xl sm:text-4xl font-extrabold text-brand-dark leading-tight text-balance">
            Exercer en France avec un diplôme hors UE
          </h1>
          <p className="mt-4 text-slate-700 leading-relaxed">
            Médecins, chirurgiens-dentistes, sages-femmes, pharmaciens : comprenez chaque étape
            de l'autorisation d'exercice et préparez votre dossier avec Optimi Santé.
          </p>
          <div className="mt-7 flex flex-wrap items-center gap-4">
            <a href="#accompagnements"
               className="inline-flex items-center gap-2 px-6 py-3 rounded-full bg-brand text-white text-sm font-semibold hover:bg-brand-fonce">
              Être accompagné <ArrowRight className="w-4 h-4" aria-hidden="true" />
            </a>
            <a href="#evc" className="text-sm font-semibold text-brand underline-offset-4 hover:underline">
              Comprendre le parcours
            </a>
          </div>
        </div>

        <div className="bg-white rounded-xl border border-slate-200 p-6 sm:p-7">
          <h2 className="text-xs font-bold tracking-widest text-brand-dark uppercase">
            Le parcours en 4 étapes
          </h2>
          {/* Une liste ORDONNÉE : ces étapes se franchissent dans cet ordre, et le numéro
              porte une information — ce n'est pas une puce décorative. */}
          <ol className="mt-5 space-y-4">
            {PARCOURS.map((etape, i) => (
              <li key={etape.titre} className="flex gap-3">
                <span aria-hidden="true"
                      className={`shrink-0 w-6 h-6 rounded-full text-[11px] font-bold flex items-center justify-center ${
                        i === PARCOURS.length - 1
                          ? 'bg-brand-accent text-white' : 'bg-brand text-white'}`}>
                  {i + 1}
                </span>
                <div>
                  <p className="text-sm font-semibold text-brand-dark">{etape.titre}</p>
                  <p className="text-xs text-slate-600 mt-0.5">{etape.texte}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </div>
      {sections.size === 0 && null}
    </header>
  );
}

function BandeauNonOfficiel({ section, lien }: { section?: SectionEvc; lien: { url: string; libelle: string } | null }) {
  if (!section) return null;
  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 pt-8">
      <div className="flex gap-3 rounded-xl border border-slate-200 bg-slate-50 px-4 sm:px-5 py-4">
        <Info className="w-5 h-5 shrink-0 text-brand mt-0.5" aria-hidden="true" />
        <p className="text-sm text-slate-700 leading-relaxed">
          <span className="font-semibold text-brand-dark">{section.titre}.</span>{' '}
          {section.contenu}{' '}
          {lien && <LienOfficiel {...lien} />}
        </p>
      </div>
    </div>
  );
}

/* ── Sections ─────────────────────────────────────────────────────────────────────────── */

const PROFESSIONS_EVC = [
  { titre: 'Médecins', texte: 'Toutes spécialités', fond: 'bg-brand-light' },
  { titre: 'Chirurgiens-dentistes', texte: 'Omnipratique ou spécialité', fond: 'bg-brand-cream' },
  { titre: 'Sages-femmes', texte: 'Diplôme hors UE / EEE', fond: 'bg-brand-light' },
  { titre: 'Pharmaciens', texte: 'Dont biologistes médicaux', fond: 'bg-brand-cream' },
];

function SectionEvcEpreuves({ section, lienSession }:
    { section?: SectionEvc; lienSession: { url: string; libelle: string } | null }) {
  if (!section) return null;
  return (
    <section id="evc" aria-labelledby="titre-evc" className="scroll-mt-24">
      <EnTeteSection section={section} id="titre-evc" />
      <p className="mt-4 max-w-4xl text-slate-700 leading-relaxed">{section.contenu}</p>
      <div className="mt-8 grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {PROFESSIONS_EVC.map(p => (
          <div key={p.titre} className={`${p.fond} rounded-xl p-5`}>
            <p className="text-sm font-bold tracking-wide text-brand-dark uppercase">{p.titre}</p>
            <p className="mt-1.5 text-sm text-slate-600">{p.texte}</p>
          </div>
        ))}
      </div>
      {lienSession && (
        <div className="mt-6">
          <LienOfficiel {...lienSession} bouton />
        </div>
      )}
    </section>
  );
}

function SectionAep({ section, periodes, specialites, lienRenouvellement }: {
  section?: SectionEvc;
  periodes: PeriodeEvc[];
  specialites: { groupe: string; libelle: string }[];
  lienRenouvellement: { url: string; libelle: string } | null;
}) {
  if (!section) return null;
  const [intro, ...encadres] = decouperEnBlocs(section.contenu);
  const professions = specialites.filter(s => s.groupe === 'profession');
  const medecine = specialites.filter(s => s.groupe === 'medecine');

  return (
    <section id="aep" aria-labelledby="titre-aep" className="scroll-mt-24">
      <EnTeteSection section={section} id="titre-aep" />
      <p className="mt-4 max-w-4xl text-slate-700 leading-relaxed">{intro?.texte}</p>

      <div className="mt-8 grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {periodes.map(p => <CartePeriode key={p.libelle} periode={p} />)}
      </div>

      {encadres.length > 0 && (
        <div className="mt-4 grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {encadres.map(bloc => (
            <div key={bloc.titre} className="rounded-xl border border-slate-200 p-5">
              <p className="text-sm font-semibold text-brand-dark">{bloc.titre}</p>
              <p className="mt-1.5 text-sm text-slate-600 leading-relaxed">{bloc.texte}</p>
              {bloc.titre.toLowerCase().includes('renouvellement') && lienRenouvellement && (
                <p className="mt-2"><LienOfficiel {...lienRenouvellement} /></p>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Trente-trois lignes : repliées, parce qu'un visiteur cherche SA spécialité et non
          la liste. Un <details> natif s'ouvre au clavier sans une ligne de script. */}
      <details className="mt-6 rounded-xl border border-slate-200">
        <summary className="px-5 py-4 text-sm font-semibold text-brand-dark cursor-pointer">
          Professions et spécialités relevant de la commission nationale (CNG)
        </summary>
        <div className="px-5 pb-5 text-sm text-slate-700 space-y-3">
          {professions.length > 0 && (
            <p><span className="font-semibold">Professions :</span>{' '}
              {professions.map(s => s.libelle.toLowerCase()).join(', ')}.</p>
          )}
          {medecine.length > 0 && (
            <p><span className="font-semibold">Médecins :</span>{' '}
              {medecine.map(s => s.libelle.toLowerCase()).join(' · ')}.</p>
          )}
        </div>
      </details>
    </section>
  );
}

function CartePeriode({ periode }: { periode: PeriodeEvc }) {
  const etat = badgeDePeriode(periode.statut);
  const aVenir = periode.statut === 'a_venir';
  return (
    <div className={`rounded-xl border p-5 ${aVenir ? 'border-warning/40' : 'border-slate-200'}`}>
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-bold tracking-widest text-slate-500 uppercase">{periode.libelle}</p>
        <span className={`shrink-0 text-[11px] font-semibold px-2 py-0.5 rounded-full border ${etat.classe}`}>
          {etat.libelle}
        </span>
      </div>
      <p className={`mt-2 text-lg font-bold ${aVenir ? 'text-brand-accent' : 'text-brand-dark'}`}>
        {periode.dateDebut && periode.dateFin
          ? <>{dateCourte(periode.dateDebut)} → {dateCourte(periode.dateFin)}</>
          : 'Dates à confirmer'}
      </p>
      {periode.specialitesNonOuvertes && (
        <p className="mt-2 text-xs text-slate-600 leading-relaxed">
          <span className="font-semibold">Non ouvertes :</span> {periode.specialitesNonOuvertes}.
        </p>
      )}
      {aVenir && (
        // Même formulaire que les accompagnements, rattaché à l'offre technique « alerte_aep » :
        // c'est elle qui permettra de retrouver qui prévenir quand le CNG publiera ses dates.
        <a href="#accompagnements"
           data-offre={OFFRE_ALERTE_AEP}
           className="mt-3 inline-flex items-center gap-1.5 text-sm font-semibold text-brand-accent underline-offset-4 hover:underline">
          Être alerté à l'ouverture <ArrowRight className="w-3.5 h-3.5" aria-hidden="true" />
        </a>
      )}
    </div>
  );
}

function SectionDossier({ section }: { section?: SectionEvc }) {
  if (!section) return null;
  const { blocs, mentions } = decouper(section.contenu);
  const alerte = blocs.find(b => !b.titre);
  const points = blocs.filter(b => b.titre);

  return (
    <section id="dossier" aria-labelledby="titre-dossier" className="scroll-mt-24">
      <EnTeteSection section={section} id="titre-dossier" />
      {alerte && (
        <div className="mt-4 flex gap-3 rounded-xl border border-warning/30 bg-warning/5 px-4 sm:px-5 py-4">
          <AlertTriangle className="w-5 h-5 shrink-0 text-warning mt-0.5" aria-hidden="true" />
          <p className="text-sm text-slate-700 leading-relaxed">{alerte.texte}</p>
        </div>
      )}
      <div className="mt-6 grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {points.map(bloc => {
          const [numero, ...reste] = bloc.titre.split('—');
          return (
            <div key={bloc.titre} className="rounded-xl border border-slate-200 p-5">
              {reste.length > 0 && (
                <p className="text-2xl font-extrabold text-brand" aria-hidden="true">{numero.trim()}</p>
              )}
              <p className="mt-1 text-sm font-semibold text-brand-dark">
                {reste.length > 0 ? reste.join('—').trim() : bloc.titre}
              </p>
              <p className="mt-1.5 text-sm text-slate-600 leading-relaxed">{bloc.texte}</p>
            </div>
          );
        })}
      </div>
      {mentions.map(mention => (
        <p key={mention} className="mt-4 text-xs text-slate-500 leading-relaxed">{mention}</p>
      ))}
    </section>
  );
}

function SectionCae({ section, lienCalendrier, calendrierActualiseLe }: {
  section?: SectionEvc;
  lienCalendrier: { url: string; libelle: string } | null;
  calendrierActualiseLe: string | null;
}) {
  if (!section) return null;
  const [intro, ...encadres] = decouperEnBlocs(section.contenu);
  return (
    <section id="cae" aria-labelledby="titre-cae" className="scroll-mt-24 grid lg:grid-cols-2 gap-8 items-start">
      <div>
        <EnTeteSection section={section} id="titre-cae" />
        <p className="mt-4 text-slate-700 leading-relaxed">{intro?.texte}</p>
      </div>
      <div className="bg-brand-dark text-slate-200 rounded-xl p-6 sm:p-7">
        <h3 className="flex items-center gap-2 text-xs font-bold tracking-widest uppercase text-white">
          <CalendarDays className="w-4 h-4" aria-hidden="true" /> Calendrier des commissions
        </h3>
        {encadres.map(bloc => (
          <p key={bloc.titre} className="mt-3 text-sm leading-relaxed">
            {bloc.titre && <span className="font-semibold text-white">{bloc.titre} : </span>}
            {bloc.texte}
          </p>
        ))}
        {calendrierActualiseLe && (
          // « Constatée », et non « publiée » : la date vient du nom du fichier déposé par le
          // CNG, qui ne date ni sa page ni son document. Écrire « mise à jour le » laisserait
          // croire qu'Optimi Santé édite ce calendrier.
          <p className="mt-3 text-xs text-slate-400">
            Dernière actualisation du calendrier officiel constatée le{' '}
            <time dateTime={calendrierActualiseLe}>{dateLongue(calendrierActualiseLe)}</time>.
          </p>
        )}
        {lienCalendrier && (
          <a href={lienCalendrier.url} target="_blank" rel="noopener noreferrer"
             className="mt-5 inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white text-brand-dark text-sm font-semibold hover:bg-slate-100">
            {lienCalendrier.libelle} <ExternalLink className="w-3.5 h-3.5" aria-hidden="true" />
          </a>
        )}
      </div>
    </section>
  );
}

function SectionAutresVoies({ section, lienFellowship, lienRefugies }: {
  section?: SectionEvc;
  lienFellowship: { url: string; libelle: string } | null;
  lienRefugies: { url: string; libelle: string } | null;
}) {
  if (!section) return null;
  const blocs = decouperEnBlocs(section.contenu).filter(b => b.titre);
  return (
    <section id="autres-voies" aria-labelledby="titre-autres-voies" className="scroll-mt-24">
      <EnTeteSection section={section} id="titre-autres-voies" />
      <div className="mt-6 grid lg:grid-cols-2 gap-4">
        {blocs.map(bloc => {
          const refugies = bloc.titre.toLowerCase().includes('réfugi');
          const lien = refugies ? lienRefugies : lienFellowship;
          return (
            <div key={bloc.titre} className="rounded-xl border border-slate-200 p-5 sm:p-6">
              <p className="text-sm font-bold tracking-wide text-brand-dark uppercase">{bloc.titre}</p>
              <p className="mt-2 text-sm text-slate-600 leading-relaxed whitespace-pre-line">{bloc.texte}</p>
              {lien && <p className="mt-3"><LienOfficiel {...lien} /></p>}
            </div>
          );
        })}
      </div>
    </section>
  );
}

const FONDS_OFFRES = ['bg-brand-light', 'bg-brand-cream', 'bg-brand-light'];

function SectionAccompagnements({ section, offres }: { section?: SectionEvc; offres: OffreEvc[] }) {
  if (!section) return null;
  return (
    <section id="accompagnements" aria-labelledby="titre-accompagnements" className="scroll-mt-24">
      <EnTeteSection section={section} id="titre-accompagnements" />
      <div className="mt-6 grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {offres.map((offre, i) => (
          <div key={offre.cle} className={`${FONDS_OFFRES[i % FONDS_OFFRES.length]} rounded-xl p-5 sm:p-6 flex flex-col`}>
            {offre.surTitre && (
              <p className="text-[11px] font-bold tracking-widest text-slate-500 uppercase">{offre.surTitre}</p>
            )}
            <p className="mt-1.5 text-base font-bold text-brand-dark uppercase">{offre.titre}</p>
            <ul className="mt-4 space-y-1.5 text-sm text-slate-700 flex-1">
              {offre.points.map(point => (
                <li key={point} className="flex gap-2">
                  <span aria-hidden="true" className="text-brand">•</span>{point}
                </li>
              ))}
            </ul>
            <p className="mt-5 text-lg font-extrabold text-brand-accent">
              {offre.prix == null ? 'Sur devis' : prixLisible(offre.prix, offre.devise)}
            </p>
            <a href="#accompagnements" data-offre={offre.cle}
               className="mt-3 inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-full bg-brand text-white text-sm font-semibold hover:bg-brand-fonce">
              {offre.libelleBouton} <ArrowRight className="w-4 h-4" aria-hidden="true" />
            </a>
          </div>
        ))}
      </div>
      {section.contenu && <p className="mt-5 text-xs text-slate-500">{section.contenu}</p>}
    </section>
  );
}

function BandeauRendezVous() {
  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 pb-12">
      <div className="bg-brand-cream rounded-2xl px-6 sm:px-10 py-8 flex flex-col sm:flex-row sm:items-center justify-between gap-5">
        <div>
          <p className="text-base sm:text-lg font-extrabold text-brand-dark uppercase">
            Vous préparez votre venue en France ?
          </p>
          <p className="mt-1 text-sm text-slate-600">
            Un conseiller Optimi Santé fait le point sur votre situation en 30 minutes.
          </p>
        </div>
        <a href="#accompagnements"
           className="shrink-0 inline-flex items-center justify-center gap-2 px-6 py-3 rounded-full bg-brand text-white text-sm font-semibold hover:bg-brand-fonce">
          Prendre rendez-vous <ArrowRight className="w-4 h-4" aria-hidden="true" />
        </a>
      </div>
    </div>
  );
}

/* ── Rouages ──────────────────────────────────────────────────────────────────────────── */

function EnTeteSection({ section, id }: { section: SectionEvc; id: string }) {
  return (
    <>
      {section.surTitre && (
        <p className="text-xs font-bold tracking-widest text-brand-accent uppercase">{section.surTitre}</p>
      )}
      <h2 id={id} className="mt-2 text-2xl sm:text-3xl font-extrabold text-brand-dark text-balance">
        {section.titre}
      </h2>
    </>
  );
}

/** Un lien vers une source officielle : nouvel onglet, et jamais sans `noopener`. */
function LienOfficiel({ url, libelle, bouton }: { url: string; libelle: string; bouton?: boolean }) {
  const classes = bouton
    ? 'inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-brand text-white text-sm font-semibold hover:bg-brand-fonce'
    : 'inline-flex items-center gap-1 text-sm font-semibold text-brand underline underline-offset-2';
  return (
    <a href={url} target="_blank" rel="noopener noreferrer" className={classes}>
      {libelle} <ExternalLink className="w-3.5 h-3.5" aria-hidden="true" />
    </a>
  );
}

/**
 * Découpe le contenu d'une section en blocs, séparés par des titres en `##`.
 *
 * <p>Le premier bloc n'a pas de titre : c'est le texte d'introduction. Ce découpage très
 * simple permet à l'administration d'ajouter un encadré depuis l'écran d'édition, sans
 * qu'un développeur ait à toucher au composant.</p>
 */
function decouperEnBlocs(contenu: string | null): { titre: string; texte: string }[] {
  return decouper(contenu).blocs;
}

/**
 * Découpe le contenu d'une section : des blocs titrés en `##`, et des mentions en `>`.
 *
 * <p><b>Pourquoi deux marques et non une.</b> Une mention réglementaire — « articles
 * R. 4111-13-8-3… » — n'est pas un bloc de plus : elle commente l'ensemble et court sous les
 * cartes. Sans marque propre, elle se retrouvait enfermée dans la dernière carte, où elle
 * semblait ne concerner que celle-ci.</p>
 */
function decouper(contenu: string | null): {
  blocs: { titre: string; texte: string }[];
  mentions: string[];
} {
  if (!contenu) return { blocs: [], mentions: [] };
  const blocs: { titre: string; texte: string }[] = [];
  const mentions: string[] = [];
  let titre = '';
  let lignes: string[] = [];
  const pousser = () => {
    const texte = lignes.join('\n').trim();
    if (titre || texte) blocs.push({ titre, texte });
  };
  for (const ligne of contenu.split('\n')) {
    if (ligne.startsWith('## ')) {
      pousser();
      titre = ligne.slice(3).trim();
      lignes = [];
    } else if (ligne.startsWith('> ')) {
      mentions.push(ligne.slice(2).trim());
    } else {
      lignes.push(ligne);
    }
  }
  pousser();
  return { blocs, mentions };
}

function dateCourte(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
}

function dateLongue(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

function prixLisible(prix: number, devise: string): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: devise || 'EUR' }).format(prix);
}
