import { Link } from 'react-router-dom';
import { ArrowRight, GraduationCap, ShoppingBag, Plane } from 'lucide-react';
import { LEGAL } from '../config/legal';
import { usePageMeta } from '../hooks/usePageMeta';

/**
 * Qui est Optimi Santé, en bref.
 *
 * <p>Le lien « Qui sommes-nous ? » menait à la page des services, qui répond à une autre
 * question : <i>ce que nous faisons pour vous</i>. Un visiteur qui demande qui nous sommes a
 * sa réponse ici, dès la première phrase.</p>
 *
 * <p><b>Une présentation s'ouvre sur ce qu'on est.</b> Une première version décrivait la
 * société par ce qu'elle ne fait pas — ne soigne pas, ne forme pas elle-même, ne fabrique
 * rien. C'était exact et mal orienté : un visiteur lit cette page pour savoir à qui il
 * s'adresse, pas pour une mise au point. L'activité est donc énoncée au positif, sans pour
 * autant avancer d'ancienneté ni de chiffres que le site ne peut pas étayer.</p>
 */
export function QuiSommesNousPage() {
  usePageMeta('Qui sommes-nous ?',
    `${LEGAL.nomCommercial} accompagne les professionnels de santé sur trois terrains : `
    + "l'équipement médical, la formation clinique et la mobilité médicale internationale.");

  const metiers = [
    {
      icon: GraduationCap,
      titre: 'Formation médicale',
      texte: "Des parcours de formation clinique construits avec des établissements de santé "
        + 'français, de la candidature à la première journée en service.',
      lien: { to: '/formations', libelle: 'Voir les formations' },
    },
    {
      icon: Plane,
      titre: 'Mobilité internationale',
      texte: "Le dossier consulaire, l'assurance, l'hébergement et l'accueil à l'arrivée : "
        + 'tout ce qu\'un séjour de formation en France demande, pris en charge.',
      lien: { to: '/services', libelle: 'Nos services' },
    },
    {
      icon: ShoppingBag,
      titre: 'Équipements de santé',
      texte: 'Matériel et consommables médicaux pour les établissements, les professionnels '
        + 'et les particuliers, livrés en France et vers l\'Afrique.',
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
            Trois métiers, un seul interlocuteur.
          </h1>

          {/* La reponse, d'abord et au positif. Un visiteur qui clique sur « Qui sommes-nous ? »
              ne doit ni deduire la reponse d'un catalogue de prestations, ni lire une liste de
              ce que la societe ne fait pas. */}
          <div className="max-w-2xl space-y-4 text-lg leading-relaxed text-slate-300">
            <p>
              <strong className="text-white">{LEGAL.nomCommercial}</strong> est une société
              française, établie en Gironde et immatriculée à Bordeaux. Nous accompagnons les
              professionnels de santé sur trois terrains : l'équipement médical, la formation
              clinique et la mobilité médicale internationale.
            </p>
            <p>
              Notre métier est de réunir ce qui se traite d'ordinaire séparément. Un médecin
              qui vient se former dans un établissement français trouve chez nous sa formation,
              le dossier qui l'accompagne et la logistique de son séjour. Un établissement ou
              un professionnel y trouve son matériel et ses consommables. Un seul
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

      <div className="container mx-auto max-w-5xl px-4 py-12 md:px-8">
        <div className="grid gap-5 md:grid-cols-3">
          {metiers.map(({ icon: Icone, titre, texte, lien }) => (
            <div key={titre} className="flex flex-col rounded-2xl border border-slate-200 bg-white p-6">
              <span className="mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-brand-light text-brand">
                <Icone className="h-5 w-5" aria-hidden="true" />
              </span>
              <h2 className="mb-2 font-bold text-brand-dark">{titre}</h2>
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

        {/* L'identite de l'editeur n'est pas recopiee ici : elle est deja aux mentions
            legales, et deux copies d'une meme information finissent par diverger. */}
        <p className="mt-8 text-sm leading-relaxed text-slate-500">
          {LEGAL.raisonSociale}, {LEGAL.formeJuridique}, présidée par{' '}
          {LEGAL.directeurPublication}. Siège social : {LEGAL.adresseSiege}.{' '}
          <Link to="/mentions-legales" className="font-semibold text-brand hover:underline">
            Mentions légales
          </Link>
          {' · '}
          <Link to="/cgv" className="font-semibold text-brand hover:underline">
            Conditions générales
          </Link>
        </p>
      </div>
    </div>
  );
}
