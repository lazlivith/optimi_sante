import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { authService } from '../../api/authService';
import { useAuth } from '../../context/AuthContext';
import { User as UserIcon, Lock } from 'lucide-react';
import { Toast } from '../../components/common/Toast';

export const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
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
          if (role === 'SUPER_ADMIN' || role === 'ADMIN') {
            navigate('/admin/quotes', { replace: true });
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

  return (
    <div className="min-h-[calc(100vh-200px)] flex items-center justify-center bg-slate-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8 bg-white p-10 rounded-xl shadow-lg border border-slate-100">
        <div>
          <h2 className="mt-2 text-center text-3xl font-bold tracking-tight text-brand-dark">
            Bienvenue
          </h2>
          <p className="mt-2 text-center text-sm text-slate-600">
            Connectez-vous à votre espace Optimi Santé
          </p>
        </div>
        
        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          {/* Toast is rendered below */}
          
          <div className="space-y-4 rounded-md shadow-sm">
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-slate-700">
                Adresse email
              </label>
              <div className="relative mt-1 rounded-md shadow-sm">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <UserIcon className="h-5 w-5 text-slate-400" />
                </div>
                <input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="block w-full rounded-md border-slate-300 pl-10 focus:border-brand-green focus:ring-brand-green sm:text-sm py-2.5 border"
                  placeholder="vous@exemple.com"
                />
              </div>
            </div>
            
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-slate-700">
                Mot de passe
              </label>
              <div className="relative mt-1 rounded-md shadow-sm">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                  <Lock className="h-5 w-5 text-slate-400" />
                </div>
                <input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="block w-full rounded-md border-slate-300 pl-10 focus:border-brand-green focus:ring-brand-green sm:text-sm py-2.5 border"
                  placeholder="••••••••"
                />
              </div>
            </div>
          </div>

          <div className="flex items-center justify-between">
            <div className="text-sm">
              <a href="#" className="font-medium text-brand-green hover:text-brand-dark transition-colors">
                Mot de passe oublié ?
              </a>
            </div>
          </div>

          <div>
            <button
              type="submit"
              disabled={isLoading}
              className="flex w-full justify-center rounded-md border border-transparent bg-brand-green py-2.5 px-4 text-sm font-medium text-white shadow-sm hover:bg-[#0f3c35] focus:outline-none focus:ring-2 focus:ring-brand-green focus:ring-offset-2 transition-colors disabled:opacity-70"
            >
              {isLoading ? 'Connexion...' : 'Se connecter'}
            </button>
          </div>
          
          <div className="text-center text-sm text-slate-600 mt-4">
            Pas encore de compte ?{' '}
            <Link to="/register" className="font-medium text-brand-green hover:text-brand-dark transition-colors">
              S'inscrire
            </Link>
          </div>
        </form>
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
