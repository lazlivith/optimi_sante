import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { authService } from '../../api/authService';
import { useAuth } from '../../context/AuthContext';
import { Mail, Lock, Eye, EyeOff, Loader2, ArrowRight, ShieldCheck } from 'lucide-react';
import { Toast } from '../../components/common/Toast';
import { usePageMeta } from '../../hooks/usePageMeta';
import { adminHomeFor } from '../../lib/adminUniverses';

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

export const LoginPage = () => {
  usePageMeta('Connexion');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [motDePasseVisible, setMotDePasseVisible] = useState(false);
  const [logoOk, setLogoOk] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      const response = await authService.login({ email, password });

      // Assume login gives token, but we still need user data.
      // We will set the token first so getProfile works.
      localStorage.setItem('token', response.accessToken);

      const profile = await authService.getProfile();
      login(response.accessToken, profile);

      setSuccess('Bienvenue, ' + (profile.email || 'Utilisateur'));

      setTimeout(() => {
        if (location.state?.from?.pathname && location.state.from.pathname !== '/') {
          navigate(location.state.from.pathname, { replace: true });
        } else {
          const role = profile.role;
          // Chaque administrateur atterrit sur le tableau de bord de SON métier, déduit de
          // la même source que la navigation — et non sur une page choisie au hasard.
          if (role === 'SUPER_ADMIN' || role === 'ADMIN'
              || role === 'ADMIN_ECOMMERCE' || role === 'ADMIN_MOBILITE') {
            navigate(adminHomeFor(role), { replace: true });
          } else if (role === 'CENTRE_FORMATION') {
            navigate('/partner/sessions', { replace: true });
          } else if (role === 'MEDECIN') {
            navigate('/doctor/vault', { replace: true });
          } else {
            navigate('/', { replace: true });
          }
        }
      }, 1000);
    } catch (err: any) {
      if (err.response?.status === 401 || err.response?.status === 403) {
        setError('Identifiants incorrects.');
      } else {
        setError('Une erreur est survenue lors de la connexion.');
      }
      localStorage.removeItem('token');
    } finally {
      setIsLoading(false);
    }
  };

  const champ =
    'block w-full rounded-xl border border-brand-light bg-white py-3 pl-11 pr-4 text-[15px] '
    + 'text-brand-dark placeholder:text-slate-400 transition-colors '
    + 'focus:border-brand-green focus:outline-none focus:ring-2 focus:ring-brand-green/20';

  return (
    <div className="bg-brand-cream">
      {/* Deux panneaux a parts egales. L'ordre du DOM place le formulaire en premier : sur
          mobile, ou les colonnes s'empilent, c'est lui qu'on doit atteindre sans defiler —
          le panneau de marque passe alors en bandeau, plus haut, via `order`. */}
      <div className="grid min-h-[calc(100vh-8.25rem)] lg:grid-cols-2">

        {/* ------------------------------------------------------------ formulaire -- */}
        <div className="order-2 flex items-center justify-center px-6 py-12 sm:px-10 lg:order-1 lg:justify-end lg:py-16 lg:pr-16 xl:pr-24">
          <div className="w-full max-w-[26rem]">
            <h1 className="text-[2rem] font-bold leading-tight tracking-tight text-brand-dark">
              Bienvenue
            </h1>
            <p className="mt-2 text-[15px] text-slate-600">
              Connectez-vous à votre espace Optimi Santé.
            </p>

            <form className="mt-9 space-y-5" onSubmit={handleSubmit}>
              <div>
                <label htmlFor="email" className="mb-1.5 block text-sm font-semibold text-brand-dark">
                  Adresse email
                </label>
                <div className="relative">
                  <Mail
                    aria-hidden="true"
                    className="pointer-events-none absolute left-3.5 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-slate-400"
                  />
                  <input
                    id="email"
                    name="email"
                    type="email"
                    autoComplete="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className={champ}
                    placeholder="vous@exemple.com"
                  />
                </div>
              </div>

              <div>
                <div className="mb-1.5 flex items-baseline justify-between gap-3">
                  <label htmlFor="password" className="block text-sm font-semibold text-brand-dark">
                    Mot de passe
                  </label>
                  {/* Placé à hauteur du libellé plutôt qu'en dessous du champ : c'est là qu'on
                      le cherche, au moment où l'on constate qu'on ne s'en souvient pas. */}
                  <a
                    href="#"
                    className="rounded text-[13px] font-medium text-brand-green underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-green/30"
                  >
                    Mot de passe oublié ?
                  </a>
                </div>
                <div className="relative">
                  <Lock
                    aria-hidden="true"
                    className="pointer-events-none absolute left-3.5 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-slate-400"
                  />
                  <input
                    id="password"
                    name="password"
                    type={motDePasseVisible ? 'text' : 'password'}
                    autoComplete="current-password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className={champ + ' pr-12'}
                    placeholder="••••••••"
                  />
                  {/* Une faute de frappe dans un mot de passe masqué se corrige a l'aveugle :
                      pouvoir le relire evite un second echec de connexion. */}
                  <button
                    type="button"
                    onClick={() => setMotDePasseVisible((v) => !v)}
                    aria-label={motDePasseVisible ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-2 text-slate-400 transition-colors hover:bg-brand-cream hover:text-brand-dark focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-green/30"
                  >
                    {motDePasseVisible
                      ? <EyeOff className="h-[18px] w-[18px]" />
                      : <Eye className="h-[18px] w-[18px]" />}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className="group flex w-full items-center justify-center gap-2 rounded-xl bg-brand-green py-3.5 text-[15px] font-semibold text-white shadow-sm transition-colors hover:bg-[#0f3c35] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-green focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="h-[18px] w-[18px] animate-spin" />
                    Connexion…
                  </>
                ) : (
                  <>
                    Se connecter
                    <ArrowRight className="h-[18px] w-[18px] transition-transform group-hover:translate-x-0.5" />
                  </>
                )}
              </button>
            </form>

            <p className="mt-8 border-t border-brand-light pt-6 text-center text-sm text-slate-600">
              Pas encore de compte ?{' '}
              <Link
                to="/register"
                className="rounded font-semibold text-brand-green underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-green/30"
              >
                S'inscrire
              </Link>
            </p>
          </div>
        </div>

        {/* -------------------------------------------------------- panneau de marque -- */}
        <div className="relative order-1 flex items-center overflow-hidden bg-brand-green px-6 py-10 sm:px-12 lg:order-2 lg:py-16 lg:pl-16 xl:pl-24">
          {/* Halo diagonal : il reprend la montée de la flèche du logo. Discret, et non un
              dégradé décoratif posé au hasard. */}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 bg-[radial-gradient(120%_90%_at_85%_10%,rgba(217,138,60,0.22),transparent_60%)]"
          />
          <div
            aria-hidden="true"
            className="pointer-events-none absolute -bottom-24 -left-24 h-72 w-72 rounded-full bg-white/[0.04]"
          />

          <div className="relative w-full max-w-md">
            <Link to="/" aria-label="Optimi Santé — accueil" className="inline-block">
              {logoOk ? (
                <img
                  src="/logo-optimi-clair.png"
                  alt="Optimi Santé — soutenir le handicap et le soin"
                  onError={() => setLogoOk(false)}
                  className="h-16 w-auto max-w-[260px] object-contain lg:h-24 lg:max-w-[340px]"
                />
              ) : (
                <span className="flex items-center gap-3">
                  <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-white/15 text-base font-bold text-white">
                    OS
                  </span>
                  <span className="text-xl font-bold text-white">Optimi Santé</span>
                </span>
              )}
            </Link>

            {/* La signature de marque, telle qu'elle figure sur le logo. */}
            <p className="mt-5 text-[15px] font-medium text-white/70 lg:text-lg">
              Soutenir le handicap et le soin.
            </p>

            {/* Masqués sur mobile : le bandeau y sert à identifier la marque, pas à raconter
                l'offre — et tout ce qui s'y ajoute repousse le formulaire hors de l'écran. */}
            <ul className="mt-10 hidden space-y-4 lg:block">
              {REPERES.map((repere) => (
                <li key={repere} className="flex items-start gap-3 text-[15px] text-white/85">
                  <ShieldCheck aria-hidden="true" className="mt-0.5 h-[18px] w-[18px] shrink-0 text-brand-orange" />
                  <span>{repere}</span>
                </li>
              ))}
            </ul>

            <p className="mt-10 hidden border-t border-white/15 pt-6 text-[13px] leading-relaxed text-white/55 lg:block">
              Vos documents sont conservés dans un espace personnel, accessible à vous seul et
              aux équipes chargées de votre dossier.
            </p>
          </div>
        </div>
      </div>

      {error && (
        <Toast
          type="error"
          message={error}
          onClose={() => setError('')}
        />
      )}
      {success && (
        <Toast
          type="success"
          message={success}
          onClose={() => setSuccess('')}
        />
      )}
    </div>
  );
};
