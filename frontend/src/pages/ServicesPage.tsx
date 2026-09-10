import { Link } from 'react-router-dom';
import {
  ShoppingBag, GraduationCap, Building2, FileText, ArrowRight, CheckCircle2,
  ClipboardList, UserCheck, Plane, Award, ShieldCheck, Home, Car, Stamp, Wallet,
} from 'lucide-react';
import { usePageMeta } from '../hooks/usePageMeta';
import { SectionNav } from '../components/common/SectionNav';
import { CONDITIONS_MOBILITE } from '../config/legal';

/**
 * Sommaire. Les identifiants `negoce`, `devis`, `formations`, `partenariat` et `faq` sont
 * visés directement par le pied de page : les renommer y créerait des liens morts.
 */
const SECTIONS = [
  { id: 'accompagnement', label: 'Accompagnement visa' },
  { id: 'pack', label: 'Pack logistique' },
  { id: 'formations', label: 'Le parcours' },
  { id: 'tarifs', label: 'Frais & conditions' },
  { id: 'faq', label: 'Questions fréquentes' },
  { id: 'negoce', label: 'Négoce médical' },
  { id: 'devis', label: 'Devis professionnel' },
  { id: 'partenariat', label: 'Établissements' },
] as const;

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
    r: `Non. Ils s'élèvent à ${CONDITIONS_MOBILITE.fraisDossier} et rémunèrent l'instruction de `
      + `votre dossier, la vérification de vos pièces et sa transmission à l'établissement — un `
      + `travail effectué quelle que soit l'issue de la candidature.`,
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

/**
 * Page « Nos services & accompagnement ».
 *
 * <p>Destinée au médecin qui découvre Optimi Santé et veut savoir <b>qui s'occupe de lui</b>
 * avant de s'engager. Elle porte tout ce qui alourdirait une fiche de formation — visa,
 * assurance, logement, transport, conditions financières — pour que celle-ci reste concentrée
 * sur le programme médical.</p>
 *
 * <p><b>Aucun montant n'est écrit dans le texte.</b> Les frais et la part exigée à l'admission
 * viennent de {@link CONDITIONS_MOBILITE}, la même source que les conditions générales. Sur une
 * page qui engage commercialement, un chiffre recopié finit par contredire le contrat.</p>
 */
export function ServicesPage() {
  usePageMeta('Nos services & accompagnement',
    "Accompagnement visa, assurance rapatriement, hébergement et accueil : comment Optimi Santé "
    + "accompagne les médecins vers les CHU français, et à quelles conditions.");

  return (
    <div className="bg-slate-50">
      <section className="bg-brand-dark text-white">
        <div className="container mx-auto px-4 md:px-8 py-14 max-w-4xl">
          <p className="text-[11px] font-bold uppercase tracking-wider text-brand-green mb-3">
            Nos services &amp; accompagnement
          </p>
          <h1 className="text-3xl md:text-5xl font-bold leading-tight mb-4">
            Vous vous formez.<br />Nous nous occupons du reste.
          </h1>
          <p className="text-slate-300 text-lg leading-relaxed max-w-2xl">
            Un stage clinique en France, ce n'est pas seulement un programme médical : c'est un
            dossier consulaire, une assurance, un logement et une arrivée à organiser. Voici ce
            qu'Optimi Santé prend en charge, et ce que cela coûte.
          </p>
          <div className="flex flex-wrap gap-3 mt-7">
            <Link
              to="/formations"
              className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-brand-green text-white font-bold hover:bg-[#0f3c35] transition-colors"
            >
              Voir les formations <ArrowRight className="w-4 h-4" />
            </Link>
            <a
              href="https://wa.me/33600000000"
              target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-5 py-3 rounded-xl bg-white/10 hover:bg-white/20 text-white font-semibold transition-colors"
            >
              Parler à un conseiller
            </a>
          </div>
        </div>
      </section>

      <div className="container mx-auto px-4 md:px-8 py-12 max-w-6xl grid lg:grid-cols-[230px_minmax(0,1fr)] gap-8 lg:gap-12">
        <aside className="lg:pt-2">
          <SectionNav sections={SECTIONS} />
        </aside>

        <div className="min-w-0 space-y-14">

          {/* ── Accompagnement visa ── */}
          <section id="accompagnement" className="scroll-mt-40">
            <Titre icon={Stamp} titre="Accompagnement visa & administratif" />
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
          </section>

          {/* ── Pack logistique ── */}
          <section id="pack" className="scroll-mt-40">
            <Titre icon={Home} titre="Le pack logistique" />
            <p className="text-slate-600 leading-relaxed mb-6">
              Trois prestations, à choisir séparément selon vos besoins. Elles sont assurées par
              Optimi Santé et se souscrivent au moment de la procédure, une fois votre admission
              prononcée.
            </p>
            <div className="grid sm:grid-cols-3 gap-4 mb-5">
              {OPTIONS.map((o) => (
                <div key={o.titre} className="bg-white rounded-2xl border border-slate-200 p-5">
                  <div className="w-10 h-10 rounded-xl bg-brand-green/10 text-brand-green flex items-center justify-center mb-3">
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
          </section>

          {/* ── Le parcours ── */}
          <section id="formations" className="scroll-mt-40">
            <Titre icon={GraduationCap} titre="Le parcours, étape par étape" />
            <p className="text-slate-600 leading-relaxed mb-5">
              Du dépôt de votre candidature à votre première journée dans le service.
            </p>
            <ol className="space-y-3 mb-6">
              {PHASES.map((p, i) => (
                <li key={p.titre} className="flex gap-4 bg-white rounded-2xl border border-slate-200 p-4">
                  <div className="shrink-0 w-9 h-9 rounded-xl bg-brand-green/10 text-brand-green flex items-center justify-center font-bold text-sm">
                    {i + 1}
                  </div>
                  <div className="min-w-0">
                    <p className="font-semibold text-brand-dark flex items-center gap-2">
                      <p.icon className="w-4 h-4 text-brand-green" /> {p.titre}
                    </p>
                    <p className="text-sm text-slate-600 leading-relaxed mt-1">{p.texte}</p>
                  </div>
                </li>
              ))}
            </ol>
            <Bouton to="/formations">Voir les formations disponibles</Bouton>
          </section>

          {/* ── Frais & conditions ── */}
          <section id="tarifs" className="scroll-mt-40">
            <Titre icon={Wallet} titre="Frais, règlement et conditions" />
            <div className="grid sm:grid-cols-2 gap-4 mb-5">
              <div className="bg-white rounded-2xl border border-slate-200 p-5">
                <h3 className="font-bold text-brand-dark mb-2">Frais de dossier</h3>
                <p className="text-2xl font-bold text-brand-green mb-2">
                  {CONDITIONS_MOBILITE.fraisDossier}
                </p>
                <p className="text-sm text-slate-600 leading-relaxed">
                  Réglés au dépôt de la candidature. Ils rémunèrent l'instruction du dossier et
                  sa transmission à l'établissement, et ne sont pas remboursables.
                </p>
              </div>
              <div className="bg-white rounded-2xl border border-slate-200 p-5">
                <h3 className="font-bold text-brand-dark mb-2">Frais de formation</h3>
                <p className="text-2xl font-bold text-brand-green mb-2">
                  Après admission
                </p>
                <p className="text-sm text-slate-600 leading-relaxed">
                  Indiqués sur chaque session. Ils ne sont exigibles qu'une fois votre
                  candidature acceptée par l'établissement — et à hauteur de{' '}
                  {CONDITIONS_MOBILITE.partExigeeALAdmission} % à ce moment.
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
              <Link to="/cgv" className="text-brand-green font-semibold hover:underline">
                conditions générales
              </Link>{' '}
              — notamment les articles sur la mobilité et les conditions d'annulation.
            </p>
          </section>

          {/* ── FAQ ── */}
          <section id="faq" className="scroll-mt-40">
            <Titre icon={CheckCircle2} titre="Questions fréquentes" />
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
          </section>

          {/* ── Négoce ── */}
          <section id="negoce" className="scroll-mt-40">
            <Titre icon={ShoppingBag} titre="Négoce d'équipements médicaux" />
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
          </section>

          {/* ── Devis professionnel ── */}
          <section id="devis" className="scroll-mt-40">
            <Titre icon={FileText} titre="Devis pour les professionnels" />
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
          </section>

          {/* ── Partenariat ── */}
          <section id="partenariat" className="scroll-mt-40">
            <Titre icon={Building2} titre="Établissements partenaires" />
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
          </section>
        </div>
      </div>
    </div>
  );
}

function Titre({ icon: Icon, titre }: { icon: typeof ShoppingBag; titre: string }) {
  return (
    <h2 className="flex items-center gap-3 text-2xl font-bold text-brand-dark mb-4">
      <span className="w-10 h-10 rounded-xl bg-brand-green text-white flex items-center justify-center shrink-0">
        <Icon className="w-5 h-5" />
      </span>
      {titre}
    </h2>
  );
}

function Point({ children }: { children: React.ReactNode }) {
  return (
    <li className="flex items-start gap-2 text-slate-600">
      <CheckCircle2 className="w-4 h-4 text-brand-green mt-1 shrink-0" />
      <span>{children}</span>
    </li>
  );
}

function Bouton({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <Link
      to={to}
      className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-brand-green text-white font-bold hover:bg-[#0f3c35] transition-colors"
    >
      {children} <ArrowRight className="w-4 h-4" />
    </Link>
  );
}

function BoutonSecondaire({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <Link
      to={to}
      className="inline-flex items-center gap-2 px-6 py-3 rounded-xl border border-slate-300 bg-white text-slate-700 font-semibold hover:bg-slate-50 transition-colors"
    >
      {children}
    </Link>
  );
}
