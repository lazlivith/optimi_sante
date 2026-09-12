import { useState } from 'react';
import { Link } from 'react-router-dom';
import { MessageCircle, Mail, Phone, MapPin, Loader2, Check } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { LEGAL } from '../config/legal';
import { newsletterService } from '../api/newsletterService';

const WHATSAPP = 'https://wa.me/33600000000';

/**
 * Libellé du consentement, défini ici et envoyé tel quel au serveur.
 *
 * Le RGPD demande de pouvoir prouver **à quoi** la personne a consenti, pas seulement qu'elle
 * a coché. Ce texte est donc enregistré avec l'inscription ; s'il évolue, les consentements
 * antérieurs restent lisibles tels qu'ils ont été donnés.
 */
const CONSENTEMENT =
  "Je consens à recevoir par email les offres, actualités et informations d'Optimi Santé. "
  + "J'ai lu et compris la politique de confidentialité relative au traitement de mes données "
  + "personnelles.";

/**
 * Pied de page.
 *
 * <p><b>Aucun lien mort.</b> Chaque destination correspond à une route ou à une section qui
 * existe réellement — les entrées d'un pied de page qui ne mènent nulle part coûtent plus de
 * confiance que leur absence. « Livraison » et « Retour » pointent vers les articles
 * correspondants des conditions générales plutôt que vers des pages fictives, et
 * « Cookies » vers la section qui les traite dans la politique de confidentialité.</p>
 */
export const Footer = () => {
  const { user } = useAuth();
  const [logoOk, setLogoOk] = useState(true);

  return (
    <footer className="bg-brand-dark text-slate-300 mt-16">
      <div className="container mx-auto px-6 py-12">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-8 lg:gap-10">

          {/* Marque */}
          <div>
            {/* Declinaison claire, sur fond transparent : le pied de page est sombre.
                `onError` conserve le monogramme si le fichier venait a manquer — une image
                cassee en pied de page passerait longtemps inapercue. */}
            <Link to="/" aria-label="Optimi Santé — accueil" className="inline-block mb-4">
              {logoOk ? (
                <img
                  src="/marque/optimi-logo-clair.webp"
                  alt="Optimi Santé — soutenir le handicap et le soin"
                  onError={() => setLogoOk(false)}
                  className="h-14 w-auto max-w-[220px] object-contain"
                />
              ) : (
                <span className="flex items-center gap-2.5">
                  <span className="bg-brand-green text-white font-bold rounded-lg flex items-center justify-center w-10 h-10 text-sm shrink-0">
                    OS
                  </span>
                  <span>
                    <span className="block text-base font-bold text-white leading-tight">Optimi Santé</span>
                    <span className="block text-[11px] text-slate-400 leading-tight">
                      soutenir le handicap et le soin
                    </span>
                  </span>
                </span>
              )}
            </Link>
            <p className="text-sm text-slate-400 leading-relaxed">
              Équipements médicaux, formations cliniques et mobilité médicale internationale.
            </p>
            <p className="flex items-center gap-1.5 mt-4 text-xs text-slate-500">
              <MapPin className="w-3.5 h-3.5 shrink-0" /> {LEGAL.villeSiege}
            </p>
          </div>

          <Colonne titre="À propos">
            <Lien to="/services">Qui sommes-nous ?</Lien>
            <Lien to="/services#accompagnement">Accompagnement visa</Lien>
            <Lien to="/services#pack">Pack logistique</Lien>
            <Lien to="/services#formations">Le parcours</Lien>
            <Lien to="/services#negoce">Négoce médical</Lien>
            <Lien to="/services#partenariat">Devenir partenaire</Lien>
            <Lien to="/services#faq">Questions fréquentes</Lien>
          </Colonne>

          <Colonne titre="Boutique">
            <Lien to="/catalog">Tout le catalogue</Lien>
            <Lien to="/catalog?promo=true">Promotions</Lien>
            <Lien to="/services#devis">Devis pour professionnels</Lien>
            <Lien to="/formations">Nos formations</Lien>
            <Lien to="/login">Accès espace professionnel</Lien>
          </Colonne>

          <Colonne titre="Informations">
            {/* Ces deux entrées mènent aux articles correspondants des conditions générales,
                et non à des pages qui n'existent pas. */}
            <Lien to="/cgv#livraison">Livraison</Lien>
            <Lien to="/cgv#retractation">Retour / Remboursement</Lien>
            <Lien to="/cgv">Conditions de vente (CGV / CGU)</Lien>
            <Lien to="/mentions-legales">Mentions légales</Lien>
            <Lien to="/politique-confidentialite">Politique de confidentialité</Lien>
            <Lien to="/politique-confidentialite#cookies">Politique de cookies</Lien>
            {user && <Lien to="/mes-donnees">Mes données personnelles</Lien>}
          </Colonne>

          {/* Contact + lettre d'information */}
          <div>
            <h3 className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-3.5">
              Nous contacter
            </h3>
            <ul className="space-y-2.5 mb-6">
              <li>
                <a
                  href={WHATSAPP} target="_blank" rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 text-sm text-slate-300 hover:text-white transition-colors"
                >
                  <MessageCircle className="w-4 h-4 text-emerald-400 shrink-0" />
                  Discuter sur WhatsApp
                </a>
              </li>
              <li>
                <a
                  href={`mailto:${LEGAL.emailContact}`}
                  className="inline-flex items-center gap-2 text-sm text-slate-300 hover:text-white transition-colors break-all"
                >
                  <Mail className="w-4 h-4 text-slate-500 shrink-0" />
                  {LEGAL.emailContact}
                </a>
              </li>
              {/* Le téléphone n'apparaît que s'il est renseigné : un numéro absent vaut mieux
                  qu'un numéro de démonstration que quelqu'un finirait par composer. */}
              {LEGAL.telephone && (
                <li>
                  <a
                    href={`tel:${LEGAL.telephone.replace(/\s/g, '')}`}
                    className="inline-flex items-center gap-2 text-sm text-slate-300 hover:text-white transition-colors"
                  >
                    <Phone className="w-4 h-4 text-slate-500 shrink-0" />
                    {LEGAL.telephone}
                  </a>
                </li>
              )}
            </ul>

            <Newsletter />
          </div>
        </div>
      </div>

      <div className="border-t border-white/10">
        <div className="container mx-auto px-6 py-5 flex flex-col md:flex-row items-center justify-between gap-3 text-xs">
          <p className="text-slate-500">© 2026 Optimi Santé · Tous droits réservés</p>
          <p className="text-slate-500 text-center">
            Négoce B2B/B2C · Formations médicales · Mobilité Afrique → France
          </p>
          <a
            href={`mailto:${LEGAL.emailContact}`}
            className="text-slate-400 hover:text-white transition-colors"
          >
            Écrivez-nous : {LEGAL.emailContact}
          </a>
        </div>
      </div>
    </footer>
  );
};

/**
 * Inscription à la lettre d'information.
 *
 * <p>La case de consentement est <b>obligatoire et non pré-cochée</b> : un consentement
 * pré-coché n'en est pas un. Le bouton reste inactif tant qu'elle ne l'est pas, plutôt que de
 * laisser envoyer puis refuser — l'utilisateur voit ce qui manque avant de cliquer.</p>
 */
function Newsletter() {
  const [email, setEmail] = useState('');
  const [consent, setConsent] = useState(false);
  const [etat, setEtat] = useState<'repos' | 'envoi' | 'ok' | 'erreur'>('repos');
  const [message, setMessage] = useState('');

  const envoyer = async (e: React.FormEvent) => {
    e.preventDefault();
    setEtat('envoi');
    try {
      await newsletterService.subscribe(email.trim(), CONSENTEMENT);
      setEtat('ok');
      setEmail('');
      setConsent(false);
    } catch (err: any) {
      setEtat('erreur');
      setMessage(err?.response?.data?.message ?? "L'inscription n'a pas pu être enregistrée.");
    }
  };

  if (etat === 'ok') {
    return (
      <div className="rounded-xl bg-emerald-500/15 border border-emerald-500/30 p-4">
        <p className="flex items-start gap-2 text-sm text-emerald-200">
          <Check className="w-4 h-4 mt-0.5 shrink-0" />
          Merci, votre inscription est enregistrée.
        </p>
      </div>
    );
  }

  return (
    <form onSubmit={envoyer}>
      <h3 className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-3.5">
        Lettre d'information
      </h3>
      <div className="flex gap-2 mb-3">
        <input
          type="email" required value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="Votre adresse e-mail"
          aria-label="Votre adresse e-mail"
          className="flex-1 min-w-0 px-3 py-2 text-sm rounded-lg bg-white/10 border border-white/15 text-white placeholder:text-slate-500 focus:border-brand-green focus:ring-1 focus:ring-brand-green outline-none"
        />
        <button
          type="submit"
          disabled={!consent || etat === 'envoi'}
          className="shrink-0 px-4 py-2 rounded-lg bg-brand-green text-white text-xs font-bold uppercase tracking-wide hover:bg-[#0f3c35] disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          {etat === 'envoi' ? <Loader2 className="w-4 h-4 animate-spin" /> : "S'abonner"}
        </button>
      </div>

      <label className="flex items-start gap-2 text-xs text-slate-400 leading-relaxed cursor-pointer">
        <input
          type="checkbox" checked={consent}
          onChange={(e) => setConsent(e.target.checked)}
          className="mt-0.5 shrink-0 accent-brand-green"
        />
        <span>
          Je consens à recevoir par email les offres, actualités et informations d'Optimi
          Santé. J'ai lu et compris la{' '}
          <Link to="/politique-confidentialite" className="underline hover:text-white">
            politique de confidentialité
          </Link>{' '}
          relative au traitement de mes données personnelles.
        </span>
      </label>

      {etat === 'erreur' && <p className="text-xs text-rose-300 mt-2">{message}</p>}
    </form>
  );
}

function Colonne({ titre, children }: { titre: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-3.5">
        {titre}
      </h3>
      <ul className="space-y-2.5">{children}</ul>
    </div>
  );
}

function Lien({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <li>
      <Link to={to} className="text-sm text-slate-300 hover:text-white transition-colors">
        {children}
      </Link>
    </li>
  );
}
