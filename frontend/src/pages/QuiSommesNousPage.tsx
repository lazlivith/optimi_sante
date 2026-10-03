import { Link } from 'react-router-dom';
import { ArrowRight, GraduationCap, ShoppingBag, Plane, Building2 } from 'lucide-react';
import { LEGAL } from '../config/legal';
import { usePageMeta } from '../hooks/usePageMeta';

/**
 * Qui est Optimi Santé.
 *
 * <p>Le lien « Qui sommes-nous ? » menait jusqu'ici à la page des services, qui répond à une
 * autre question : <i>ce que nous faisons pour vous</i>. Un visiteur qui demande qui nous
 * sommes veut savoir à qui il confie un dossier consulaire ou une commande — la réponse
 * arrive donc en premier, avant toute offre.</p>
 *
 * <p><b>Rien n'est affirmé ici qui ne soit vérifiable.</b> Pas d'ancienneté, pas de nombre de
 * médecins accompagnés, pas de liste d'établissements : ce sont des arguments qui se
 * contrôlent, et une page de présentation qui en invente décrédibilise le reste du site. Les
 * éléments d'identité viennent de {@link LEGAL}, la même source que les mentions légales : ils
 * ne peuvent donc pas diverger d'une page à l'autre.</p>
 */
export function QuiSommesNousPage() {
  usePageMeta('Qui sommes-nous ?',
    `${LEGAL.nomCommercial} relie équipements médicaux, formations cliniques en France et `
    + "organisation de la mobilité médicale internationale. Qui nous sommes, ce que nous "
    + 'faisons, et qui porte la société.');

  const metiers = [
    {
      icon: GraduationCap,
      titre: 'Formation médicale',
      texte: "Nous construisons des parcours de formation clinique avec des établissements "
        + "de santé français, qui en restent les seuls dispensateurs. Nous en assurons "
        + "l'ingénierie : la mise en relation, le dossier, le suivi jusqu'à la première "
        + 'journée en service.',
      lien: { to: '/formations', libelle: 'Voir les formations' },
    },
    {
      icon: Plane,
      titre: 'Mobilité internationale',
      texte: "Un stage clinique en France suppose un dossier consulaire, une assurance, un "
        + "logement et une arrivée à organiser. Nous prenons en charge cette part "
        + "administrative et logistique, que le médecin assemblerait sinon seul, à distance.",
      lien: { to: '/services', libelle: 'Nos services' },
    },
    {
      icon: ShoppingBag,
      titre: 'Équipements de santé',
      texte: "Nous distribuons du matériel et des consommables médicaux aux établissements, "
        + 'aux professionnels et aux particuliers, avec des conditions propres aux comptes '
        + 'professionnels et une livraison en France et vers une partie du continent africain.',
      lien: { to: '/catalog', libelle: 'Voir le catalogue' },
    },
  ];

  return (
    <div className="bg-slate-50">
      <section className="bg-brand-dark text-white">
        <div className="container mx-auto max-w-4xl px-4 py-14 md:px-8">
          <p className="mb-3 text-[11px] font-bold uppercase tracking-wider text-brand">
            Qui sommes-nous
          </p>
          <h1 className="mb-5 text-3xl font-bold leading-tight md:text-5xl">
            Un intermédiaire, et nous l'assumons.
          </h1>

          {/* La reponse, d'abord. Un visiteur qui clique sur « Qui sommes-nous ? » ne doit pas
              avoir a deduire la reponse d'un catalogue de prestations. */}
          <div className="max-w-2xl space-y-4 text-lg leading-relaxed text-slate-300">
            <p>
              <strong className="text-white">{LEGAL.nomCommercial}</strong> est le nom
              commercial de {LEGAL.raisonSociale}, société immatriculée à Bordeaux et établie
              en Gironde. Nous ne soignons pas, nous ne formons pas nous-mêmes et nous ne
              fabriquons rien : nous relions des professionnels de santé à ceux qui le font.
            </p>
            <p>
              Concrètement, nous réunissons trois choses qu'un médecin ou un établissement
              devrait sinon assembler séparément : des <strong className="text-white">
              équipements médicaux</strong>, des <strong className="text-white">formations
              cliniques</strong> dans des établissements français, et{' '}
              <strong className="text-white">l'organisation d'un séjour de formation</strong>
              {' '}— visa, assurance, hébergement, arrivée. Trois métiers, un seul
              interlocuteur, et des conditions annoncées avant l'engagement.
            </p>
          </div>

          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              to="/formations"
              className="inline-flex items-center gap-2 rounded-xl bg-brand px-6 py-3 font-bold text-white transition-colors hover:bg-brand-fonce"
            >
              Voir les formations <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
            <a
              href={LEGAL.whatsapp}
              target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-2 rounded-xl bg-white/10 px-5 py-3 font-semibold text-white transition-colors hover:bg-white/20"
            >
              Parler à un conseiller
            </a>
          </div>
        </div>
      </section>

      <div className="container mx-auto max-w-5xl space-y-12 px-4 py-12 md:px-8">
        <section>
          <h2 className="mb-6 text-2xl font-bold text-brand-dark">Nos trois métiers</h2>
          <div className="grid gap-5 md:grid-cols-3">
            {metiers.map(({ icon: Icone, titre, texte, lien }) => (
              <div key={titre} className="flex flex-col rounded-2xl border border-slate-200 bg-white p-6">
                <span className="mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-brand-light text-brand">
                  <Icone className="h-5 w-5" aria-hidden="true" />
                </span>
                <h3 className="mb-2 font-bold text-brand-dark">{titre}</h3>
                <p className="mb-4 flex-1 text-sm leading-relaxed text-slate-600">{texte}</p>
                <Link
                  to={lien.to}
                  className="inline-flex items-center gap-1.5 text-sm font-bold text-brand hover:underline"
                >
                  {lien.libelle} <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </Link>
              </div>
            ))}
          </div>
        </section>

        <section>
          <h2 className="mb-4 text-2xl font-bold text-brand-dark">À qui nous nous adressons</h2>
          <div className="grid gap-5 md:grid-cols-2">
            <div className="rounded-2xl border border-slate-200 bg-white p-6">
              <h3 className="mb-2 font-bold text-brand-dark">
                Aux médecins qui se forment en France
              </h3>
              <p className="text-sm leading-relaxed text-slate-600">
                Candidature, convention, dossier consulaire, assurance, logement, accueil à
                l'arrivée. Les frais applicables et ce qu'il advient en cas de refus de visa
                sont annoncés avant le dépôt, pas après.
              </p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-6">
              <h3 className="mb-2 font-bold text-brand-dark">
                Aux établissements et aux professionnels
              </h3>
              <p className="text-sm leading-relaxed text-slate-600">
                Centres de formation qui accueillent des praticiens, acheteurs d'équipements et
                de consommables, structures qui demandent un devis. Un compte professionnel
                ouvre ses propres conditions tarifaires.
              </p>
            </div>
          </div>
        </section>

        <section>
          <h2 className="mb-4 text-2xl font-bold text-brand-dark">Qui porte la société</h2>
          <div className="rounded-2xl border border-slate-200 bg-white p-6">
            <div className="mb-5 flex items-start gap-3">
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-light text-brand">
                <Building2 className="h-5 w-5" aria-hidden="true" />
              </span>
              <p className="text-sm leading-relaxed text-slate-600">
                Savoir à qui l'on confie un dossier ou une commande fait partie de la réponse.
                Voici l'identité de l'éditeur, telle qu'elle figure aux mentions légales.
              </p>
            </div>

            <dl className="grid gap-x-8 gap-y-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Dénomination
                </dt>
                <dd className="font-semibold text-brand-dark">{LEGAL.raisonSociale}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Forme juridique
                </dt>
                <dd className="text-slate-700">{LEGAL.formeJuridique}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Président
                </dt>
                <dd className="text-slate-700">{LEGAL.directeurPublication}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Capital social
                </dt>
                <dd className="text-slate-700">{LEGAL.capitalSocial}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Siège social
                </dt>
                <dd className="text-slate-700">{LEGAL.adresseSiege}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Immatriculation
                </dt>
                <dd className="text-slate-700">{LEGAL.rcs}</dd>
              </div>
            </dl>

            <div className="mt-6 flex flex-wrap gap-4 border-t border-slate-100 pt-4 text-sm">
              <Link to="/mentions-legales" className="font-bold text-brand hover:underline">
                Mentions légales
              </Link>
              <Link to="/cgv" className="font-bold text-brand hover:underline">
                Conditions générales
              </Link>
              <Link to="/politique-confidentialite" className="font-bold text-brand hover:underline">
                Protection des données
              </Link>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
