import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { authService } from '../../api/authService';
import { useAuth } from '../../context/AuthContext';
import { Mail, Lock, Eye, EyeOff, Loader2, ArrowRight } from 'lucide-react';
import { Toast } from '../../components/common/Toast';
import { usePageMeta } from '../../hooks/usePageMeta';
import { adminHomeFor } from '../../lib/adminUniverses';
import { CadreAuthentification } from './CadreAuthentification';


export const LoginPage = () => {
  usePageMeta('Connexion');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [motDePasseVisible, setMotDePasseVisible] = useState(false);
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
    + 'focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20';

  return (
    <CadreAuthentification
      titre="Bienvenue"
      sousTitre="Connectez-vous à votre espace Optimi Santé."
    >
      <>
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
                    className="rounded text-[13px] font-medium text-brand underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/30"
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
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-2 text-slate-400 transition-colors hover:bg-brand-cream hover:text-brand-dark focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/30"
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
                className="group flex w-full items-center justify-center gap-2 rounded-xl bg-brand py-3.5 text-[15px] font-semibold text-white shadow-sm transition-colors hover:bg-brand-fonce focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-70"
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
            className="rounded font-semibold text-brand underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/30"
          >
            S'inscrire
          </Link>
        </p>
      </>

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
    </CadreAuthentification>
  );
};
