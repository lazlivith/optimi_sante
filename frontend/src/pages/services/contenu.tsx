import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  ShoppingBag, GraduationCap, Building2, FileText, ArrowRight, CircleHelp, CheckCircle2,
  ClipboardList, UserCheck, Plane, Award, ShieldCheck, Home, Car, Stamp, Wallet,
  type LucideIcon,
} from 'lucide-react';
import { CONDITIONS_MOBILITE } from '../../config/legal';

/**
 * Les huit services, chacun avec son contenu.
 *
 * <p><b>Une page par service.</b> Les huit prestations formaient auparavant une seule page de
 * huit sections, parcourue par un sommaire d'ancres : cliquer sur « Devis professionnel »
 * dans le menu déroulait toute l'offre et déposait le visiteur au milieu. Chaque service a
 * désormais son adresse et n'affiche que ce qui le concerne.</p>
 *
 * <p><b>Le contenu n'est décrit qu'ici.</b> La page d'ensemble, les huit pages de détail et le
 * menu de navigation lisent la même liste. Un service ajouté apparaît partout, et un intitulé
 * corrigé l'est partout — là où trois copies auraient divergé.</p>
 *
 * <p><b>Aucun montant n'est écrit dans le texte.</b> Les frais et la part exigée à l'admission
 * viennent de {@link CONDITIONS_MOBILITE}, la même source que les conditions générales. Sur des
 * pages qui engagent commercialement, un chiffre recopié finit par contredire le contrat.</p>
 */
export interface Service {
  slug: string;
  titre: string;
  icon: LucideIcon;
  /** Une ligne, telle qu'elle paraît dans le menu et sur les vignettes d'ensemble. */
  resume: string;
  /** Description pour les moteurs de recherche et le partage. */
  meta: string;
  /** Le métier auquel la prestation se rattache — c'est ainsi que le menu les regroupe. */
  famille: 'Mobilité médicale' | 'Négoce & partenaires';
  Contenu: () => ReactNode;
}

/** Les cinq phases du parcours, telles que la plateforme les applique réellement. */
const PHASES = [
  {
    icon: ClipboardList,
    titre: 'Dépôt du dossier',
    texte: "Vous constituez votre candidature en ligne — diplôme, inscription à l'Ordre, "
      + "passeport — et réglez les frais de dossier. C'est le seul montant demandé à ce stade.",
  },
  {
    icon: UserCheck,
    titre: 'Sélection et entretien',
    texte: "Nous pré-qualifions votre dossier avant de le transmettre à l'établissement, qui "
      + "propose des créneaux d'entretien. Vous choisissez le vôtre ; la convocation et le lien "
      + "de visioconférence vous sont adressés et déposés dans votre coffre-fort.",
  },
  {
    icon: Stamp,
    titre: 'Procédure et pack logistique',
    texte: "Une fois admis, la convention est émise, votre dossier consulaire est constitué "
      + "pièce par pièce, et vous choisissez les options d'accompagnement dont vous avez besoin.",
  },
  {
    icon: Plane,
    titre: 'Arrivée et formation',
    texte: "Accueil à votre arrivée, installation, puis stage clinique au sein du service "
      + "hospitalier pour la durée prévue.",
  },
  {
    icon: Award,
    titre: 'Certification',
    texte: "Une attestation de fin de parcours vous est délivrée et reste disponible dans "
      + "votre coffre-fort documentaire.",
  },
] as const;

/** Le pack logistique. Ces prestations sont assurées par Optimi Santé, jamais par le CHU. */
const OPTIONS = [
  {
    icon: ShieldCheck,
    titre: 'Assurance rapatriement & santé',
    texte: "Couverture médicale pendant toute la durée de votre séjour en France, et "
      + "rapatriement sanitaire en cas de nécessité. L'attestation vous est remise et déposée "
      + "dans votre coffre-fort — elle fait partie des pièces attendues par le consulat.",
  },
  {
    icon: Home,
    titre: 'Hébergement',
    texte: "Solution de logement recherchée pour vous, à proximité de l'établissement "
      + "d'accueil, pour la durée exacte de votre stage. Vous n'avez ni bail à négocier à "
      + "distance, ni garant français à trouver.",
  },
  {
    icon: Car,
    titre: 'Accueil & transport',
    texte: "Navette à l'aéroport ou à la gare, accompagnement le jour de l'arrivée et "
      + "orientation vers votre logement puis vers le service hospitalier.",
  },
] as const;

const FAQ = [
  {
    q: "Garantissez-vous l'obtention du visa ?",
    r: "Non, et personne ne le peut : la décision appartient aux autorités consulaires. Nous "
      + "constituons le dossier le plus solide possible et vous accompagnons pièce par pièce. "
      + "En cas de refus dûment justifié, les sommes versées vous sont restituées dans les "
      + "conditions prévues aux conditions générales.",
  },
  {
    q: "Quand suis-je engagé financièrement ?",
    r: "Au dépôt, vous ne réglez que les frais de dossier. Les frais de formation ne sont "
      + "exigibles qu'après acceptation de votre candidature par l'établissement d'accueil — "
      + "jamais avant. Ce n'est pas une intention : la plateforme refuse techniquement d'ouvrir "
      + "un paiement de formation tant que l'admission n'est pas prononcée.",
  },
  {
    q: "Les frais de dossier sont-ils remboursables ?",
    r: `Non. Leur montant est indiqué sur la fiche de chaque formation — ${CONDITIONS_MOBILITE.fraisDossier} `
      + `à défaut de tarif propre — et ils rémunèrent l'instruction de votre dossier, la `
      + `vérification de vos pièces et sa transmission à l'établissement : un travail effectué `
      + `quelle que soit l'issue de la candidature.`,
  },
  {
    q: "Les options logistiques sont-elles obligatoires ?",
    r: "Non. Assurance, hébergement et transport se choisissent séparément, selon ce dont vous "
      + "avez besoin. Un médecin déjà logé à Bordeaux ne prend que l'assurance.",
  },
  {
    q: "Qui voit les pièces que je dépose ?",
    r: "Vous, l'équipe qui instruit votre dossier, et l'établissement d'accueil une fois votre "
      + "dossier transmis — jamais avant. Chaque document n'est consultable que par un lien "
      + "signé à durée limitée.",
  },
  {
    q: "Puis-je acheter du matériel sans être professionnel de santé ?",
    r: "Oui. Le catalogue est ouvert aux particuliers comme aux professionnels. Les comptes "
      + "professionnels bénéficient en plus de conditions tarifaires et de la procédure de devis.",
  },
] as const;

export const SERVICES: Service[] = [
  {
    slug: 'accompagnement-visa',
    titre: 'Accompagnement visa & administratif',
    icon: Stamp,
    resume: 'Dossier consulaire, convention et suivi jusqu’au dépôt.',
    meta: "Constitution du dossier consulaire, convention tripartite et lettre d'invitation : "
      + "comment Optimi Santé accompagne un médecin jusqu'au dépôt de sa demande de visa.",
    famille: 'Mobilité médicale',
    Contenu: () => (
      <>
        <p className="text-slate-600 leading-relaxed mb-5">
          C'est le cœur de notre métier, et la partie où un dossier échoue le plus souvent :
          une pièce manquante, une traduction non assermentée, une convention mal rédigée.
          Nous constituons le dossier avec vous, pièce par pièce.
        </p>
        <ul className="space-y-2 mb-5">
          <Point>
            <strong>Constitution du dossier consulaire</strong> — nous vous indiquons chaque
            pièce attendue, vous la déposez en ligne, nous la vérifions avant transmission.
          </Point>
          <Point>
            <strong>Convention tripartite</strong> entre vous, l'établissement d'accueil et
            Optimi Santé, émise dès votre admission.
          </Point>
          <Point>
            <strong>Lettre d'invitation officielle</strong> de l'établissement, pièce
            déterminante du dossier consulaire.
          </Point>
          <Point>
            <strong>Suivi jusqu'au dépôt</strong> — vous voyez à tout moment ce qui a été
            accepté, ce qui manque encore, et qui doit agir.
          </Point>
        </ul>
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5">
          <p className="flex items-start gap-2 text-sm text-amber-900 leading-relaxed">
            <ShieldCheck className="w-4 h-4 mt-0.5 shrink-0" />
            <span>
              <strong>Ce que nous ne pouvons pas garantir.</strong> Nous sommes
              intermédiaire : la décision d'admission appartient à l'établissement d'accueil,
              et la décision de visa aux autorités consulaires. Nous ne délivrons ni l'une ni
              l'autre. Ce que nous garantissons, c'est un dossier complet, suivi et défendu.
            </span>
          </p>
        </div>
      </>
    ),
  },
  {
    slug: 'pack-logistique',
    titre: 'Le pack logistique',
    icon: Home,
    resume: 'Assurance, hébergement et accueil, à choisir séparément.',
    meta: "Assurance rapatriement, hébergement à proximité de l'établissement et accueil à "
      + "l'arrivée : trois prestations à choisir séparément, une fois l'admission prononcée.",
    famille: 'Mobilité médicale',
    Contenu: () => (
      <>
        <p className="text-slate-600 leading-relaxed mb-6">
          Trois prestations, à choisir séparément selon vos besoins. Elles sont assurées par
          Optimi Santé et se souscrivent au moment de la procédure, une fois votre admission
          prononcée.
        </p>
        <div className="grid sm:grid-cols-3 gap-4 mb-5">
          {OPTIONS.map((o) => (
            <div key={o.titre} className="bg-white rounded-2xl border border-slate-200 p-5">
              <div className="w-10 h-10 rounded-xl bg-brand/10 text-brand flex items-center justify-center mb-3">
                <o.icon className="w-5 h-5" />
              </div>
              <h3 className="font-bold text-brand-dark mb-2">{o.titre}</h3>
              <p className="text-sm text-slate-600 leading-relaxed">{o.texte}</p>
            </div>
          ))}
        </div>
        <p className="text-sm text-slate-500">
          Les tarifs dépendent de la durée du stage et de la ville d'accueil : ils sont
          indiqués sur la fiche de chaque formation.
        </p>
      </>
    ),
  },
  {
    slug: 'parcours',
    titre: 'Le parcours, étape par étape',
    icon: GraduationCap,
    resume: 'Du dépôt de la candidature à la première journée en service.',
    meta: "Les cinq étapes du parcours de mobilité : dépôt du dossier, entretien, procédure "
      + "consulaire, arrivée en France et certification de fin de stage.",
    famille: 'Mobilité médicale',
    Contenu: () => (
      <>
        <p className="text-slate-600 leading-relaxed mb-5">
          Du dépôt de votre candidature à votre première journée dans le service.
        </p>
        <ol className="space-y-3 mb-6">
          {PHASES.map((p, i) => (
            <li key={p.titre} className="flex gap-4 bg-white rounded-2xl border border-slate-200 p-4">
              <div className="shrink-0 w-9 h-9 rounded-xl bg-brand/10 text-brand flex items-center justify-center font-bold text-sm">
                {i + 1}
              </div>
              <div className="min-w-0">
                <p className="font-semibold text-brand-dark flex items-center gap-2">
                  <p.icon className="w-4 h-4 text-brand" /> {p.titre}
                </p>
                <p className="text-sm text-slate-600 leading-relaxed mt-1">{p.texte}</p>
              </div>
            </li>
          ))}
        </ol>
        <Bouton to="/formations">Voir les formations disponibles</Bouton>
      </>
    ),
  },
  {
    slug: 'frais-et-conditions',
    titre: 'Frais, règlement et conditions',
    icon: Wallet,
    resume: 'Ce qui est dû, quand, et ce qu’il advient en cas de refus.',
    meta: "Frais de dossier, échéances de règlement des frais de formation et conditions "
      + "applicables en cas de refus d'admission, de refus de visa ou de report.",
    famille: 'Mobilité médicale',
    Contenu: () => (
      <>
        <div className="grid sm:grid-cols-2 gap-4 mb-5">
          <div className="bg-white rounded-2xl border border-slate-200 p-5">
            <h3 className="font-bold text-brand-dark mb-2">Frais de dossier</h3>
            <p className="text-2xl font-bold text-brand mb-2">
              à partir de {CONDITIONS_MOBILITE.fraisDossier}
            </p>
            <p className="text-sm text-slate-600 leading-relaxed">
              Réglés au dépôt de la candidature et <strong>indiqués sur la fiche de chaque
              formation</strong>, le montant dépendant de la durée du stage. Ils rémunèrent
              l'instruction du dossier et sa transmission à l'établissement, et ne sont pas
              remboursables.
            </p>
          </div>
          <div className="bg-white rounded-2xl border border-slate-200 p-5">
            <h3 className="font-bold text-brand-dark mb-2">Frais de formation</h3>
            <p className="text-2xl font-bold text-brand mb-2">
              Après admission
            </p>
            <p className="text-sm text-slate-600 leading-relaxed">
              Indiqués sur chaque session, réglés en deux fois :{' '}
              <strong>{CONDITIONS_MOBILITE.partExigeeALAdmission} %</strong> une fois votre
              candidature acceptée par l'établissement, puis{' '}
              <strong>{CONDITIONS_MOBILITE.partExigeeAuVisa} %</strong> à la délivrance de
              votre visa. Vous n'avancez pas la totalité d'un séjour qui dépend encore
              d'une décision consulaire.
            </p>
          </div>
        </div>
        <ul className="space-y-2 mb-5">
          <Point>
            <strong>Refus de l'établissement</strong> — aucun frais de formation n'est dû.
          </Point>
          <Point>
            <strong>Refus de visa</strong> dûment justifié — les sommes versées au titre de
            la formation et des options vous sont restituées, déduction faite des prestations
            déjà exécutées et des frais engagés auprès de tiers.
          </Point>
          <Point>
            <strong>Report</strong> — possible sur une session ultérieure, sous réserve de
            l'accord de l'établissement.
          </Point>
        </ul>
        <p className="text-sm text-slate-500">
          Le détail figure dans nos{' '}
          <Link to="/cgv" className="text-brand font-semibold hover:underline">
            conditions générales
          </Link>{' '}
          — notamment les articles sur la mobilité et les conditions d'annulation.
        </p>
      </>
    ),
  },
  {
    slug: 'questions-frequentes',
    titre: 'Questions fréquentes',
    icon: CircleHelp,
    resume: 'Les réponses aux questions posées avant de s’engager.',
    meta: "Visa, engagement financier, remboursement des frais de dossier, confidentialité des "
      + "pièces déposées : les réponses aux questions les plus fréquentes.",
    famille: 'Négoce & partenaires',
    Contenu: () => (
      <div className="space-y-3">
        {FAQ.map((item) => (
          <details key={item.q} className="group bg-white rounded-2xl border border-slate-200 p-5">
            <summary className="font-semibold text-brand-dark cursor-pointer list-none flex items-start justify-between gap-4">
              {item.q}
              <ArrowRight className="w-4 h-4 text-slate-400 shrink-0 mt-1 group-open:rotate-90 transition-transform" />
            </summary>
            <p className="text-sm text-slate-600 leading-relaxed mt-3">{item.r}</p>
          </details>
        ))}
      </div>
    ),
  },
  {
    slug: 'negoce-medical',
    titre: "Négoce d'équipements médicaux",
    icon: ShoppingBag,
    resume: 'Équipements, consommables et mobilier de soin, au catalogue.',
    meta: "Équipements de diagnostic et de rééducation, consommables à usage unique et mobilier "
      + "médical : un catalogue ouvert aux particuliers comme aux professionnels.",
    famille: 'Négoce & partenaires',
    Contenu: () => (
      <>
        <p className="text-slate-600 leading-relaxed mb-4">
          Notre second métier : un catalogue d'équipements, de consommables et de mobilier de
          soin, ouvert aux particuliers comme aux professionnels.
        </p>
        <ul className="space-y-2 mb-5">
          <Point>Équipements de diagnostic, de laboratoire et de rééducation</Point>
          <Point>Consommables et matériel à usage unique</Point>
          <Point>Mobilier médical et aides à la mobilité</Point>
        </ul>
        <Bouton to="/catalog">Parcourir le catalogue</Bouton>
      </>
    ),
  },
  {
    slug: 'devis-professionnel',
    titre: 'Devis pour les professionnels',
    icon: FileText,
    resume: 'Composez votre demande, nous chiffrons la configuration.',
    meta: "Pour les équipements dont le tarif dépend de la configuration et de la mise en "
      + "service : constituez votre panier et demandez un devis plutôt que de régler.",
    famille: 'Négoce & partenaires',
    Contenu: () => (
      <>
        <p className="text-slate-600 leading-relaxed mb-5">
          Certains équipements ne s'achètent pas sur étagère : leur tarif dépend de la
          configuration, des options et de la mise en service. Constituez votre panier, puis
          demandez un devis plutôt que de régler — nous revenons vers vous avec une
          proposition chiffrée.
        </p>
        <div className="flex flex-wrap gap-3">
          <Bouton to="/catalog">Composer une demande</Bouton>
          <BoutonSecondaire to="/register">Créer un compte professionnel</BoutonSecondaire>
        </div>
      </>
    ),
  },
  {
    slug: 'etablissements-partenaires',
    titre: 'Établissements partenaires',
    icon: Building2,
    resume: 'Publiez vos sessions, recevez des dossiers pré-qualifiés.',
    meta: "CHU, cliniques et centres de formation : publiez vos sessions et recevez des "
      + "candidatures déjà pré-qualifiées, pièces vérifiées.",
    famille: 'Négoce & partenaires',
    Contenu: () => (
      <>
        <p className="text-slate-600 leading-relaxed mb-4">
          Vous êtes un CHU, une clinique ou un centre de formation ? Publiez vos sessions et
          recevez des candidatures déjà pré-qualifiées, pièces vérifiées.
        </p>
        <ul className="space-y-2 mb-5">
          <Point>Vous ne voyez que les dossiers qui vous sont transmis</Point>
          <Point>Vous décidez de l'admission ou demandez une correction</Point>
          <Point>Suivi des reversements et relevés téléchargeables</Point>
        </ul>
        <Bouton to="/devenir-partenaire">Devenir partenaire</Bouton>
      </>
    ),
  },
];

export function serviceParSlug(slug: string | undefined): Service | undefined {
  return SERVICES.find((s) => s.slug === slug);
}

function Point({ children }: { children: ReactNode }) {
  return (
    <li className="flex items-start gap-2 text-slate-600">
      <CheckCircle2 className="w-4 h-4 text-brand mt-1 shrink-0" />
      <span>{children}</span>
    </li>
  );
}

function Bouton({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link
      to={to}
      className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-brand text-white font-bold hover:bg-brand-fonce transition-colors"
    >
      {children} <ArrowRight className="w-4 h-4" />
    </Link>
  );
}

function BoutonSecondaire({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link
      to={to}
      className="inline-flex items-center gap-2 px-6 py-3 rounded-xl border border-slate-300 bg-white text-slate-700 font-semibold hover:bg-slate-50 transition-colors"
    >
      {children}
    </Link>
  );
}
