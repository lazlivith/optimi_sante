import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { LogoOptimi } from '../../components/marque/LogoOptimi';

/**
 * Ce que la plateforme fait réellement, dit en trois lignes.
 *
 * Les trois métiers d'Optimi Santé, et non des promesses génériques : quelqu'un qui arrive sur
 * cet écran sans savoir où il est doit pouvoir le comprendre avant de saisir ses identifiants.
 */
const REPERES = [
  'Mobilité médicale : candidatures, conventions, visas',
  'Équipements de santé et matériel médical',
  'Formations en établissement partenaire',
];

/**
 * Le cadre des pages d'identification : connexion et création de compte.
 *
 * <p><b>Hors du gabarit du site.</b> Ces deux pages étaient rendues avec la barre de
 * navigation, la recherche, le panier et les quatre colonnes du pied de page. Or elles ont
 * une seule chose à faire faire : tout le reste y est une sortie de route. Le retour au site
 * reste possible, en bas, discret pour ne pas concurrencer le bouton principal — sans barre
 * du haut, c'est la seule porte de sortie.</p>
 *
 * <p><b>Un seul cadre pour les deux.</b> La connexion et l'inscription se répondent : on
 * passe de l'une à l'autre par un lien, et deux mises en page distinctes se verraient à ce
 * moment précis. Les décrire à deux endroits garantirait qu'elles divergent.</p>
 *
 * <p>Le panneau de marque ne s'affiche qu'à partir de 1024 px. En dessous il disparaît au
 * profit du seul formulaire, le logotype venant alors au-dessus du titre : sans barre du
 * haut, rien d'autre n'identifierait le site sur cet écran.</p>
 */
export function CadreAuthentification({ titre, sousTitre, largeur = '26rem', children }: {
  titre: string;
  sousTitre: string;
  /** Le formulaire d'inscription porte plus de champs : il respire un peu plus large. */
  largeur?: string;
  children: ReactNode;
}) {
  return (
    <div className="bg-white">
      <div className="grid min-h-screen lg:grid-cols-2">

        {/* -------------------------------------------------------- panneau de marque -- */}
        <div className="relative order-1 hidden items-center overflow-hidden bg-brand px-6 py-10 sm:px-12 lg:flex lg:py-16 lg:pl-16 xl:pl-24">
          {/* Halo diagonal : il reprend la montée de la flèche du logo. Discret, et non un
              dégradé décoratif posé au hasard. */}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 bg-[radial-gradient(120%_90%_at_85%_10%,rgba(236,114,38,0.22),transparent_60%)]"
          />
          <div
            aria-hidden="true"
            className="pointer-events-none absolute -bottom-24 -left-24 h-72 w-72 rounded-full bg-white/[0.04]"
          />

          <div className="relative w-full max-w-md">
            <Link to="/" aria-label="Optimi Santé — accueil" className="inline-block">
              <LogoOptimi fond="sombre" className="h-16 w-auto max-w-[260px] lg:h-24 lg:max-w-[340px]" />
            </Link>

            {/* La signature de marque, telle qu'elle figure sur le logo. */}
            <p className="mt-5 text-[15px] font-medium text-white/70 lg:text-lg">
              Soutenir le handicap et le soin.
            </p>

            <ul className="mt-10 space-y-4">
              {REPERES.map((repere) => (
                <li key={repere} className="flex items-start gap-3 text-[15px] text-white/85">
                  <ShieldCheck aria-hidden="true" className="mt-0.5 h-[18px] w-[18px] shrink-0 text-brand-accent" />
                  <span>{repere}</span>
                </li>
              ))}
            </ul>

            <p className="mt-10 border-t border-white/15 pt-6 text-[13px] leading-relaxed text-white/55">
              Vos documents sont conservés dans un espace personnel, accessible à vous seul et
              aux équipes chargées de votre dossier.
            </p>
          </div>
        </div>

        {/* ------------------------------------------------------------ formulaire -- */}
        <div className="order-2 flex items-center justify-center px-6 py-10 sm:px-10 lg:px-16 lg:py-16 xl:px-24">
          <div className="w-full" style={{ maxWidth: largeur }}>
            {/* Sur mobile et tablette le panneau de marque disparaît : le logotype vient
                donc ici, sans quoi rien n'identifierait le site sur cet écran sans barre
                du haut. */}
            <Link to="/" aria-label="Optimi Santé — accueil" className="mb-8 inline-block lg:hidden">
              <LogoOptimi fond="clair" className="h-14 w-auto max-w-[220px]" />
            </Link>

            <h1 className="text-[2rem] font-bold leading-tight tracking-tight text-brand-dark">
              {titre}
            </h1>
            <p className="mt-2 text-[15px] text-slate-600">{sousTitre}</p>

            {children}

            <p className="mt-6 text-center">
              <Link
                to="/"
                className="rounded text-[13px] text-slate-500 underline-offset-2 transition-colors hover:text-brand hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/30"
              >
                Retour au site
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
