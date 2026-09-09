import { Link } from 'react-router-dom';
import {
  ShoppingBag, GraduationCap, Plane, MessageCircle, ArrowRight, MapPin,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

/** Numéro de contact, aligné sur le bouton flottant de la page d'accueil. */
const WHATSAPP = 'https://wa.me/33600000000';

/**
 * Pied de page de la boutique.
 *
 * <p>Il remplace une simple ligne de copyright. Le pied de page est le second endroit où l'on
 * cherche un lien quand la barre de navigation n'a pas répondu : il porte donc de la
 * navigation réelle, pas seulement une mention légale.</p>
 *
 * <p><b>Aucun lien mort.</b> Chaque destination correspond à une route qui existe.</p>
 */
export const Footer = () => {
  const { user } = useAuth();

  return (
    <footer className="bg-brand-dark text-slate-300 mt-16">
      {/* Nos trois metiers, presentes et cliquables. Ils n'apparaissaient qu'en texte inerte
          dans la barre du bas : un visiteur arrive en pied de page ne pouvait ni comprendre
          l'etendue de l'offre, ni y acceder. Chaque carte vise un public distinct — un
          acheteur, un medecin, un etablissement — et mene a une page differente. */}
      <div className="border-b border-white/10">
        <div className="container mx-auto px-6 py-10">
          <h2 className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-5">
            Nos services
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <ServiceCard
              to="/catalog" icon={ShoppingBag} titre="Négoce médical"
              texte="Équipements, consommables et mobilier de soin, pour les professionnels comme pour les particuliers."
              cta="Voir le catalogue"
            />
            <ServiceCard
              to="/formations" icon={GraduationCap} titre="Formations & mobilité"
              texte="Stages cliniques en CHU français, avec accompagnement administratif et constitution du dossier de visa."
              cta="Voir les formations"
            />
            <ServiceCard
              to="/devenir-partenaire" icon={Plane} titre="Partenariat établissement"
              texte="Accueillez des médecins en formation et publiez vos sessions auprès de candidats pré-qualifiés."
              cta="Devenir partenaire"
            />
          </div>
        </div>
      </div>

      <div className="container mx-auto px-6 py-12">
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-8 lg:gap-10">

          {/* Identité + contact */}
          <div className="col-span-2">
            <div className="flex items-center gap-2.5 mb-4">
              <div className="bg-brand-green text-white font-bold rounded-lg flex items-center justify-center w-9 h-9 text-sm shrink-0">
                OS
              </div>
              <div>
                <div className="text-base font-bold text-white leading-tight">Optimi Santé</div>
                <div className="text-[11px] text-slate-400 leading-tight">Équipement &amp; mobilité médicale</div>
              </div>
            </div>

            <p className="text-sm text-slate-400 leading-relaxed max-w-sm">
              Équipements médicaux pour les professionnels et les particuliers, et
              accompagnement des médecins vers les CHU français.
            </p>

            <a
              href={WHATSAPP}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 mt-5 px-4 py-2.5 rounded-xl bg-white/10 hover:bg-white/15 text-sm font-semibold text-white transition-colors"
            >
              <MessageCircle className="w-4 h-4" />
              Nous écrire sur WhatsApp
            </a>

            <p className="flex items-center gap-1.5 mt-5 text-xs text-slate-500">
              <MapPin className="w-3.5 h-3.5 shrink-0" /> Bordeaux, France
            </p>
          </div>

          {/* Les trois colonnes reprennent les trois métiers, dans l'ordre où un visiteur les
              rencontre : il achète, puis il se forme, puis il gère son compte. */}
          <FooterColumn icon={ShoppingBag} title="Boutique">
            <FooterLink to="/catalog">Tout le catalogue</FooterLink>
            <FooterLink to="/catalog?promo=true">Promotions</FooterLink>
            <FooterLink to="/cart">Mon panier</FooterLink>
          </FooterColumn>

          <FooterColumn icon={GraduationCap} title="Formations">
            <FooterLink to="/formations">Nos formations</FooterLink>
            <FooterLink to="/devenir-partenaire">Devenir partenaire</FooterLink>
          </FooterColumn>

          <FooterColumn icon={Plane} title="Mon compte">
            {user ? (
              <>
                <FooterLink to="/profile">Mon profil</FooterLink>
                <FooterLink to="/my-orders">
                  {user.role === 'CLIENT_B2B' ? 'Mes commandes et devis' : 'Mes commandes'}
                </FooterLink>
                <FooterLink to="/mes-donnees">Mes données personnelles</FooterLink>
              </>
            ) : (
              <>
                {/* Proposer « Mon profil » à un visiteur déconnecté l'enverrait sur un mur de
                    connexion : la colonne s'adapte plutôt que de promettre une page fermée. */}
                <FooterLink to="/login">Se connecter</FooterLink>
                <FooterLink to="/register">Créer un compte</FooterLink>
              </>
            )}
          </FooterColumn>
        </div>
      </div>

      <div className="border-t border-white/10">
        <div className="container mx-auto px-6 py-5 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 text-xs">
            <p className="text-slate-500">© 2026 Optimi Santé · SAS</p>
            <span aria-hidden="true" className="text-slate-600">·</span>
            {/* Obligatoires et donc placees dans la barre du bas, la ou on les cherche. */}
            <Link to="/mentions-legales" className="text-slate-400 hover:text-white transition-colors">Mentions légales</Link>
            <Link to="/cgv" className="text-slate-400 hover:text-white transition-colors">CGV / CGU</Link>
            <Link to="/politique-confidentialite" className="text-slate-400 hover:text-white transition-colors">Confidentialité</Link>
          </div>
          <p className="text-xs text-slate-500">
            Négoce B2B/B2C · Formations médicales · Mobilité Afrique → France
          </p>
        </div>
      </div>
    </footer>
  );
};

function ServiceCard({
  to, icon: Icon, titre, texte, cta,
}: { to: string; icon: typeof ShoppingBag; titre: string; texte: string; cta: string }) {
  return (
    <Link
      to={to}
      className="group block rounded-2xl bg-white/5 hover:bg-white/10 border border-white/10 p-5 transition-colors"
    >
      <div className="flex items-center gap-2.5 mb-2">
        <div className="w-9 h-9 rounded-xl bg-brand-green/25 text-white flex items-center justify-center shrink-0">
          <Icon className="w-4 h-4" />
        </div>
        <h3 className="font-bold text-white">{titre}</h3>
      </div>
      <p className="text-sm text-slate-400 leading-relaxed">{texte}</p>
      <span className="inline-flex items-center gap-1 mt-3 text-sm font-semibold text-white">
        {cta}
        <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-1 transition-transform" />
      </span>
    </Link>
  );
}

function FooterColumn({
  icon: Icon, title, children,
}: { icon: typeof ShoppingBag; title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-3.5">
        <Icon className="w-3.5 h-3.5" /> {title}
      </h3>
      <ul className="space-y-2.5">{children}</ul>
    </div>
  );
}

function FooterLink({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <li>
      <Link
        to={to}
        className="group inline-flex items-center gap-1 text-sm text-slate-300 hover:text-white transition-colors"
      >
        {children}
        {/* La flèche n'apparaît qu'au survol : elle confirme la cible sans alourdir une liste
            de huit entrées au repos. */}
        <ArrowRight className="w-3 h-3 opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all" />
      </Link>
    </li>
  );
}
