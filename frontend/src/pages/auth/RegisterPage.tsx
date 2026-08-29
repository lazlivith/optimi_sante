import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authService } from '../../api/authService';
import { CheckCircle2 } from 'lucide-react';
import { Toast } from '../../components/common/Toast';

export const RegisterPage = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<'B2C' | 'B2B'>('B2C');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  // Common fields
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  // B2B fields
  const [companyName, setCompanyName] = useState('');
  const [siretFiness, setSiretFiness] = useState('');
  const [vatNumber, setVatNumber] = useState('');
  const [billingAddress, setBillingAddress] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess(false);
    setIsLoading(true);

    const tenantCode = 'FR_MAIN';

    try {
      if (activeTab === 'B2C') {
        await authService.registerB2C({ tenantCode, email, password });
      } else if (activeTab === 'B2B') {
        await authService.registerB2B({
          tenantCode, email, password, companyName, siretFiness, vatNumber, billingAddress
        });
      }

      setSuccess(true);
      setTimeout(() => navigate('/login'), 3000);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Une erreur est survenue lors de l\'inscription.');
    } finally {
      setIsLoading(false);
    }
  };

  if (success) {
    return (
      <div className="min-h-[calc(100vh-200px)] flex items-center justify-center bg-slate-50 py-12 px-4 sm:px-6 lg:px-8">
        <div className="max-w-md w-full text-center bg-white p-10 rounded-xl shadow-lg border border-slate-100">
          <CheckCircle2 className="w-16 h-16 text-brand-green mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-brand-dark mb-2">Inscription réussie !</h2>
          <p className="text-slate-600 mb-6">Votre compte a été créé avec succès. Vous allez être redirigé vers la page de connexion...</p>
          <Link to="/login" className="text-brand-green font-medium hover:underline">
            Aller à la connexion manuellement
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-[calc(100vh-200px)] flex items-center justify-center bg-slate-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-2xl w-full bg-white p-8 sm:p-10 rounded-xl shadow-lg border border-slate-100">
        <div className="mb-8 text-center">
          <h2 className="text-3xl font-bold tracking-tight text-brand-dark">Créer un compte</h2>
          <p className="mt-2 text-sm text-slate-600">Choisissez votre type de profil pour commencer</p>
        </div>
        
        {/* Tabs */}
        <div className="flex border-b border-slate-200 mb-8">
          <button
            onClick={() => setActiveTab('B2C')}
            className={`flex-1 py-3 text-sm font-medium border-b-2 ${activeTab === 'B2C' ? 'border-brand-green text-brand-green' : 'border-transparent text-slate-500 hover:text-slate-700'}`}
          >
            Particulier
          </button>
          <button
            onClick={() => setActiveTab('B2B')}
            className={`flex-1 py-3 text-sm font-medium border-b-2 ${activeTab === 'B2B' ? 'border-brand-green text-brand-green' : 'border-transparent text-slate-500 hover:text-slate-700'}`}
          >
            Entreprise (B2B)
          </button>
        </div>

        <div className="mb-6 p-4 bg-brand-light rounded-lg text-sm text-brand-dark">
          Vous êtes médecin et souhaitez postuler à une formation ?{' '}
          <Link to="/formations" className="font-semibold text-brand-green hover:underline">
            Consultez nos formations disponibles
          </Link>
          {' '}— l'inscription se fait directement lors de votre candidature.
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Toast is rendered below */}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-slate-700 mb-1">Email</label>
              <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green p-2.5 border" />
            </div>
            
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-slate-700 mb-1">Mot de passe</label>
              <input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} className="w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green p-2.5 border" />
            </div>

            {activeTab === 'B2B' && (
              <>
                <div className="md:col-span-2"><label className="block text-sm font-medium text-slate-700 mb-1">Nom de l'entreprise</label><input type="text" required value={companyName} onChange={(e) => setCompanyName(e.target.value)} className="w-full rounded-md border-slate-300 shadow-sm p-2.5 border" /></div>
                <div><label className="block text-sm font-medium text-slate-700 mb-1">SIRET / FINESS</label><input type="text" required value={siretFiness} onChange={(e) => setSiretFiness(e.target.value)} className="w-full rounded-md border-slate-300 shadow-sm p-2.5 border" /></div>
                <div><label className="block text-sm font-medium text-slate-700 mb-1">Numéro de TVA</label><input type="text" value={vatNumber} onChange={(e) => setVatNumber(e.target.value)} className="w-full rounded-md border-slate-300 shadow-sm p-2.5 border" /></div>
                <div className="md:col-span-2"><label className="block text-sm font-medium text-slate-700 mb-1">Adresse de facturation</label><input type="text" required value={billingAddress} onChange={(e) => setBillingAddress(e.target.value)} className="w-full rounded-md border-slate-300 shadow-sm p-2.5 border" /></div>
              </>
            )}

          </div>

          <div className="pt-4">
            <button type="submit" disabled={isLoading} className="w-full flex justify-center py-3 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-brand-green hover:bg-[#0f3c35] focus:outline-none disabled:opacity-70 transition-colors">
              {isLoading ? 'Inscription...' : 'Créer mon compte'}
            </button>
          </div>
          
          <div className="text-center text-sm text-slate-600 mt-4">
            Déjà un compte ?{' '}
            <Link to="/login" className="font-medium text-brand-green hover:text-brand-dark transition-colors">
              Se connecter
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
    </div>
  );
};
