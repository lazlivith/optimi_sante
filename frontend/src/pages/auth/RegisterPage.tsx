import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { CheckCircle2, User as UserIcon, Building2, Info } from 'lucide-react';
import { authService, FACILITY_TYPE_LABELS, type FacilityType } from '../../api/authService';
import { Toast } from '../../components/common/Toast';
import { usePageMeta } from '../../hooks/usePageMeta';
import { COUNTRIES } from '../../lib/countries';
import { CadreAuthentification } from './CadreAuthentification';

/** Styles partagés par tous les champs, pour garder une saisie homogène. */
const FIELD =
  'w-full rounded-lg border border-slate-300 bg-white p-2.5 text-sm shadow-sm transition-colors ' +
  'focus:border-brand focus:ring-2 focus:ring-brand/20 focus:outline-none';
const LABEL = 'block text-sm font-medium text-slate-700 mb-1.5';

export const RegisterPage = () => {
  usePageMeta('Créer un compte');
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<'B2C' | 'B2B'>('B2C');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  // Identifiants communs aux deux parcours
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  // Parcours particulier (B2C)
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');

  // Parcours professionnel (B2B)
  const [companyName, setCompanyName] = useState('');
  const [facilityType, setFacilityType] = useState<FacilityType>('CLINIC');
  const [country, setCountry] = useState('FR');
  const [taxId, setTaxId] = useState('');
  const [contactName, setContactName] = useState('');
  const [phone, setPhone] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess(false);

    if (password.length < 8) {
      setError('Le mot de passe doit contenir au moins 8 caractères.');
      return;
    }

    setIsLoading(true);
    try {
      if (activeTab === 'B2C') {
        await authService.registerB2C({ firstName, lastName, email, password });
      } else {
        await authService.registerB2B({
          companyName, facilityType, country, taxId, contactName, phone, email, password,
        });
      }
      setSuccess(true);
      setTimeout(() => navigate('/login'), 3000);
    } catch (err: any) {
      console.error("Échec de l'inscription", err);
      setError(err.response?.data?.message || "Une erreur est survenue lors de l'inscription.");
    } finally {
      setIsLoading(false);
    }
  };

  if (success) {
    return (
      <CadreAuthentification
        titre="Inscription réussie"
        sousTitre="Votre compte est créé. Vous allez être redirigé vers la page de connexion."
      >
        <div className="mt-8 rounded-xl border border-brand-light bg-brand-cream p-6 text-center">
          <CheckCircle2 aria-hidden="true" className="mx-auto mb-3 h-12 w-12 text-brand" />
          <Link
            to="/login"
            className="rounded font-semibold text-brand underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/30"
          >
            Aller à la connexion
          </Link>
        </div>
      </CadreAuthentification>
    );
  }

  const tabClass = (tab: 'B2C' | 'B2B') =>
    `flex-1 flex items-center justify-center gap-2 py-3 px-4 text-sm font-medium rounded-lg transition-all ${
      activeTab === tab
        ? 'bg-white text-brand shadow-sm ring-1 ring-slate-200'
        : 'text-slate-500 hover:text-slate-700'
    }`;

  return (
    <CadreAuthentification
      titre="Créer un compte"
      sousTitre="Choisissez votre type de profil pour commencer."
      largeur="30rem"
    >
      <>
        {/* Sélecteur de parcours */}
        <div className="mt-8 flex gap-1 p-1 bg-slate-100 rounded-xl mb-8">
          <button type="button" onClick={() => setActiveTab('B2C')} className={tabClass('B2C')}>
            <UserIcon className="w-4 h-4" />
            Particulier
          </button>
          <button type="button" onClick={() => setActiveTab('B2B')} className={tabClass('B2B')}>
            <Building2 className="w-4 h-4" />
            Entreprise / Établissement
          </button>
        </div>

        <div className="mb-8 flex gap-3 p-4 bg-brand-light rounded-lg text-sm text-brand-dark">
          <Info className="w-4 h-4 shrink-0 mt-0.5 text-brand" />
          <p>
            Vous êtes médecin et souhaitez postuler à une formation ?{' '}
            <Link to="/formations" className="font-semibold text-brand hover:underline">
              Consultez nos formations disponibles
            </Link>{' '}
            — l'inscription se fait directement lors de votre candidature.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-8">
          {activeTab === 'B2B' && (
            <fieldset className="space-y-5">
              <legend className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-4">
                Votre établissement
              </legend>

              <div>
                <label className={LABEL}>Raison sociale / Nom de la structure</label>
                <input
                  type="text"
                  required
                  value={companyName}
                  onChange={(e) => setCompanyName(e.target.value)}
                  placeholder="Ex : Clinique Pasteur, Cabinet Dr. Martin"
                  className={FIELD}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                <div>
                  <label className={LABEL}>Type d'établissement</label>
                  <select
                    required
                    value={facilityType}
                    onChange={(e) => setFacilityType(e.target.value as FacilityType)}
                    className={FIELD}
                  >
                    {(Object.keys(FACILITY_TYPE_LABELS) as FacilityType[]).map((key) => (
                      <option key={key} value={key}>{FACILITY_TYPE_LABELS[key]}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className={LABEL}>Pays de domiciliation</label>
                  <select
                    required
                    value={country}
                    onChange={(e) => setCountry(e.target.value)}
                    className={FIELD}
                  >
                    {COUNTRIES.map((c) => (
                      <option key={c.code} value={c.code}>{c.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className={LABEL}>Identifiant fiscal / Numéro d'immatriculation</label>
                <input
                  type="text"
                  required
                  value={taxId}
                  onChange={(e) => setTaxId(e.target.value)}
                  placeholder="Tax ID, N° TVA, ICE, SIRET, Registration No."
                  className={FIELD}
                />
                <p className="mt-1.5 text-xs text-slate-500">
                  Numéro d'immatriculation légale de votre structure, selon votre pays.
                </p>
              </div>
            </fieldset>
          )}

          <fieldset className="space-y-5">
            <legend className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-4">
              {activeTab === 'B2B' ? 'Contact référent' : 'Vos informations'}
            </legend>

            {activeTab === 'B2C' ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                <div>
                  <label className={LABEL}>Prénom</label>
                  <input
                    type="text"
                    required
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    className={FIELD}
                  />
                </div>
                <div>
                  <label className={LABEL}>Nom</label>
                  <input
                    type="text"
                    required
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    className={FIELD}
                  />
                </div>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                <div>
                  <label className={LABEL}>Nom & prénom du responsable</label>
                  <input
                    type="text"
                    required
                    value={contactName}
                    onChange={(e) => setContactName(e.target.value)}
                    placeholder="Responsable des achats"
                    className={FIELD}
                  />
                </div>
                <div>
                  <label className={LABEL}>Téléphone professionnel</label>
                  <input
                    type="tel"
                    required
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="+33 6 12 34 56 78"
                    className={FIELD}
                  />
                  <p className="mt-1.5 text-xs text-slate-500">Indicatif international inclus.</p>
                </div>
              </div>
            )}
          </fieldset>

          <fieldset className="space-y-5">
            <legend className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-4">
              Identifiants de connexion
            </legend>

            <div>
              <label className={LABEL}>
                {activeTab === 'B2B' ? 'Email professionnel' : 'Adresse email'}
              </label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder={activeTab === 'B2B' ? 'achats@votre-structure.com' : 'vous@exemple.com'}
                className={FIELD}
              />
            </div>

            <div>
              <label className={LABEL}>Mot de passe</label>
              <input
                type="password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={FIELD}
              />
              <p className="mt-1.5 text-xs text-slate-500">8 caractères minimum.</p>
            </div>
          </fieldset>

          <div className="pt-2">
            <button
              type="submit"
              disabled={isLoading}
              className="w-full flex justify-center py-3 px-4 rounded-lg shadow-sm text-sm font-medium text-white bg-brand hover:bg-brand-fonce focus:outline-none focus:ring-2 focus:ring-brand/40 disabled:opacity-70 transition-colors"
            >
              {isLoading ? 'Inscription...' : 'Créer mon compte'}
            </button>
            {activeTab === 'B2B' && (
              <p className="mt-3 text-center text-xs text-slate-500">
                Votre compte est actif immédiatement : vous pourrez demander un devis dès la connexion.
              </p>
            )}
          </div>

          <div className="text-center text-sm text-slate-600">
            Déjà un compte ?{' '}
            <Link to="/login" className="font-medium text-brand hover:text-brand-dark transition-colors">
              Se connecter
            </Link>
          </div>
        </form>
      </>

      {error && <Toast type="error" message={error} onClose={() => setError('')} />}
    </CadreAuthentification>
  );
};
