import { Link } from 'react-router-dom';
import {
  ShoppingBag, GraduationCap, Building2, FileText, ArrowRight, CheckCircle2,
  ClipboardList, UserCheck, Plane, Award, ShieldCheck,
} from 'lucide-react';
import { usePageMeta } from '../hooks/usePageMeta';
import { SectionNav } from '../components/common/SectionNav';
import { CONDITIONS_MOBILITE } from '../config/legal';

/** Sommaire de la page. Les identifiants correspondent aux ancres visées par le pied de page. */
const SECTIONS = [
  { id: 'negoce', label: 'Négoce médical' },
  { id: 'devis', label: 'Devis professionnel' },
  { id: 'formations', label: 'Formations & mobilité' },
  { id: 'partenariat', label: 'Partenariat' },
  { id: 'faq', label: 'Questions fréquentes' },
] as const;

/** Les cinq phases du parcours, telles que l'automate les applique réellement. */
const PHASES = [
  {
    icon: ClipboardList,
    titre: 'Dépôt du dossier',
    texte: "Vous constituez votre candidature en ligne — diplôme, inscription à l'Ordre, "
      + "passeport — et réglez les frais de dossier.",
  },
  {
    icon: UserCheck,
    titre: 'Sélection',
    texte: "Nous pré-qualifions votre dossier, puis le transmettons à l'établissement "
      + "d'accueil, qui décide de l'admission et peut demander un entretien.",
  },
  {
    icon: FileText,
    titre: 'Procédure & pack logistique',
    texte: "Convention de stage, constitution du dossier consulaire, pièces réclamées une à "
      + "une et suivies depuis votre espace jusqu'à ce que le dossier soit complet.",
  },
  {
    icon: Plane,
    titre: 'Formation',
    texte: "Vous rejoignez le service hospitalier pour la durée prévue de votre stage "
      + "clinique.",
  },
  {
    icon: Award,
    titre: 'Certification',
    texte: "Une attestation de fin de parcours vous est délivrée et reste disponible dans "
      + "votre coffre-fort documentaire.",
  },
] as const;

const FAQ = [
  {
    q: "Garantissez-vous l'obtention du visa ?",
    r: "Non, et personne ne le peut : la décision appartient aux autorités consulaires. Nous "
      + "constituons le dossier le plus solide possible et vous accompagnons à chaque pièce. "
      + "En cas de refus dûment justifié, les frais de formation déjà réglés vous sont "
      + "remboursés, déduction faite des prestations exécutées.",
  },
  {
    q: "Quand dois-je payer les frais de formation ?",
    r: `Jamais avant que l'établissement d'accueil ait accepté votre candidature. Ils sont `
      + `exigibles à la confirmation d'admission, à hauteur de `
      + `${CONDITIONS_MOBILITE.partExigeeALAdmission} % du montant total.`,
  },
  {
    q: "Les frais de dossier sont-ils remboursables ?",
    r: `Non. Ils s'élèvent à ${CONDITIONS_MOBILITE.fraisDossier} et rémunèrent l'instruction `
      + `de votre dossier et sa transmission à l'établissement — un travail effectué quelle `
      + `que soit l'issue de la candidature.`,
  },
  {
    q: "Qui voit les pièces que je dépose ?",
    r: "Vous, l'équipe qui instruit votre dossier, et l'établissement d'accueil une fois "
      + "votre dossier transmis — jamais avant. Chaque document n'est consultable que par un "
      + "lien signé à durée limitée.",
  },
  {
    q: "Puis-je acheter du matériel sans être professionnel de santé ?",
    r: "Oui. Le catalogue est ouvert aux particuliers comme aux professionnels. Les comptes "
      + "professionnels bénéficient en plus de conditions tarifaires et de la procédure de "
      + "devis.",
  },
  {
    q: "Certains équipements n'affichent pas de prix, pourquoi ?",
    r: "Leur tarif dépend de la configuration retenue — options, accessoires, mise en "
      + "service. Ils sont proposés sur devis : vous recevez une proposition chiffrée plutôt "
      + "qu'un prix qui ne correspondrait pas à votre besoin.",
  },
] as const;

/**
 * Page « Nos services ».
 *
 * <p>Destinée à quelqu'un qui hésite <b>avant</b> de s'engager : elle explique ce que la
 * plateforme fait, ce qu'elle ne fait pas, et ce que cela coûte. Les sections portent des
 * ancres (`#formations`, `#devis`, `#faq`) parce que le pied de page y renvoie directement —
 * un lien « FAQ » qui déposerait en haut d'une longue page obligerait à la parcourir.</p>
 */
export function ServicesPage() {
  usePageMeta('Nos services',
    "Négoce d'équipements médicaux, formations cliniques en CHU et accompagnement de la "
    + "mobilité médicale internationale : ce que fait Optimi Santé, et comment.");

  return (
    <div className="bg-slate-50">
      {/* En-tête */}
      <section className="bg-brand-dark text-white">
        <div className="container mx-auto px-4 md:px-8 py-14 max-w-4xl">
          <p className="text-[11px] font-bold uppercase tracking-wider text-brand-green mb-3">
            Nos services
          </p>
          <h1 className="text-3xl md:text-5xl font-bold leading-tight mb-4">
            Équiper les soignants,<br />accompagner les médecins.
          </h1>
          <p className="text-slate-300 text-lg leading-relaxed max-w-2xl">
            Optimi Santé réunit trois métiers : la vente d'équipements médicaux, l'ingénierie
            de formation avec des établissements de santé, et l'organisation de la mobilité
            médicale internationale.
          </p>
        </div>
      </section>

      {/* Deux colonnes : sommaire collant a gauche, contenu a droite. On arrive rarement en
          haut de cette page — le plus souvent par un lien de pied de page visant une section
          precise — et le sommaire dit alors ce que la page contient d'autre. */}
      <div className="container mx-auto px-4 md:px-8 py-12 max-w-6xl grid lg:grid-cols-[220px_minmax(0,1fr)] gap-8 lg:gap-12">
        <aside className="lg:pt-2">
          <SectionNav sections={SECTIONS} />
        </aside>

        <div className="min-w-0 space-y-14">

        {/* Négoce */}
        <section id="negoce" className="scroll-mt-32">
          <Titre icon={ShoppingBag} titre="Négoce d'équipements médicaux" />
          <p className="text-slate-600 leading-relaxed mb-4">
            Un catalogue d'équipements, de consommables et de mobilier de soin, ouvert aux
            particuliers comme aux professionnels. Les comptes professionnels accèdent à des
            conditions tarifaires spécifiques et à la procédure de devis.
          </p>
          <ul className="space-y-2 mb-5">
            <Point>Équipements de diagnostic, de laboratoire et de rééducation</Point>
            <Point>Consommables et matériel à usage unique</Point>
            <Point>Mobilier médical et aides à la mobilité</Point>
            <Point>Facture disponible dans votre espace après chaque commande</Point>
          </ul>
          <Bouton to="/catalog">Parcourir le catalogue</Bouton>
        </section>

        {/* Devis professionnel */}
        <section id="devis" className="scroll-mt-32">
          <Titre icon={FileText} titre="Devis pour les professionnels" />
          <p className="text-slate-600 leading-relaxed mb-4">
            Certains équipements ne s'achètent pas sur étagère : leur tarif dépend de la
            configuration, des options et de la mise en service. Ils sont proposés{' '}
            <strong>sur devis</strong>.
          </p>
          <p className="text-slate-600 leading-relaxed mb-5">
            Constituez votre panier, puis demandez un devis plutôt que de régler. Nous
            revenons vers vous avec une proposition chiffrée, que vous pouvez accepter et
            régler en ligne.
          </p>
          <div className="flex flex-wrap gap-3">
            <Bouton to="/catalog">Composer une demande</Bouton>
            <BoutonSecondaire to="/register">Créer un compte professionnel</BoutonSecondaire>
          </div>
        </section>

        {/* Formations & mobilité */}
        <section id="formations" className="scroll-mt-32">
          <Titre icon={GraduationCap} titre="Formations cliniques & mobilité médicale" />
          <p className="text-slate-600 leading-relaxed mb-4">
            Nous accompagnons des médecins vers des stages cliniques au sein d'établissements
            de santé français : sélection du programme, dossier administratif, convention de
            stage et constitution du dossier de visa.
          </p>

          {/* Ce que nous ne faisons pas — dit avant l'engagement, pas apres. */}
          <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5 mb-6">
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

          <h3 className="font-bold text-brand-dark mb-4">Le parcours, en cinq phases</h3>
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

          <div className="rounded-2xl bg-white border border-slate-200 p-5 mb-5">
            <h3 className="font-bold text-brand-dark mb-2">Ce que cela coûte</h3>
            <p className="text-sm text-slate-600 leading-relaxed">
              <strong>Frais de dossier : {CONDITIONS_MOBILITE.fraisDossier}</strong>, réglés au
              dépôt. Ils rémunèrent l'instruction et la transmission de votre candidature, et
              ne sont pas remboursables.
            </p>
            <p className="text-sm text-slate-600 leading-relaxed mt-2">
              <strong>Frais de formation</strong> : indiqués sur chaque session. Ils ne sont
              exigibles qu'<strong>après acceptation par l'établissement</strong> — jamais
              avant.
            </p>
          </div>

          <Bouton to="/formations">Voir les formations</Bouton>
        </section>

        {/* Partenariat */}
        <section id="partenariat" className="scroll-mt-32">
          <Titre icon={Building2} titre="Partenariat pour les établissements" />
          <p className="text-slate-600 leading-relaxed mb-4">
            Vous êtes un CHU, une clinique ou un centre de formation ? Publiez vos sessions et
            recevez des candidatures déjà pré-qualifiées, avec les pièces vérifiées.
          </p>
          <ul className="space-y-2 mb-5">
            <Point>Vous ne voyez que les dossiers qui vous sont transmis</Point>
            <Point>Vous décidez de l'admission ou demandez une correction</Point>
            <Point>Suivi des reversements et relevés téléchargeables</Point>
          </ul>
          <Bouton to="/devenir-partenaire">Devenir partenaire</Bouton>
        </section>

        {/* FAQ */}
        <section id="faq" className="scroll-mt-32">
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
          <p className="text-sm text-slate-500 mt-5">
            Les conditions complètes figurent dans nos{' '}
            <Link to="/cgv" className="text-brand-green font-semibold hover:underline">
              conditions générales
            </Link>.
          </p>
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
