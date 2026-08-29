import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { authService, type User } from '../../api/authService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { Save, User as UserIcon, Mail, Phone, MapPin, Stethoscope, Briefcase, FileText, KeyRound } from 'lucide-react';

export const ProfilePage = () => {
  const { user, updateUser } = useAuth();

  const [formData, setFormData] = useState<Partial<User>>({});
  const [isLoading, setIsLoading] = useState(false);
  const [toast, setToast] = useState<{ message: string, type: ToastType } | null>(null);

  // Changement de mot de passe (utile notamment pour les comptes créés avec un mot de passe
  // temporaire généré : partenaires CHU et médecins issus d'une candidature validée/payée).
  const [passwordData, setPasswordData] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [isChangingPassword, setIsChangingPassword] = useState(false);

  useEffect(() => {
    if (user) {
      setFormData(user);
    }
  }, [user]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setToast(null);

    try {
      // The API endpoint /api/v1/users/me will handle updating the profile
      const updatedUser = await authService.updateProfile(formData);
      
      // Update the global context
      updateUser(updatedUser);
      setToast({ message: 'Profil mis à jour avec succès.', type: 'success' });
    } catch (error) {
      console.error(error);
      setToast({ message: 'Erreur lors de la mise à jour du profil.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const handlePasswordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setPasswordData(prev => ({ ...prev, [name]: value }));
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (passwordData.newPassword !== passwordData.confirmPassword) {
      setToast({ message: 'Les deux mots de passe ne correspondent pas.', type: 'error' });
      return;
    }
    if (passwordData.newPassword.length < 8) {
      setToast({ message: 'Le nouveau mot de passe doit contenir au moins 8 caractères.', type: 'error' });
      return;
    }
    setIsChangingPassword(true);
    try {
      await authService.changePassword(passwordData.currentPassword, passwordData.newPassword);
      setToast({ message: 'Mot de passe modifié avec succès.', type: 'success' });
      setPasswordData({ currentPassword: '', newPassword: '', confirmPassword: '' });
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Mot de passe actuel incorrect.', type: 'error' });
    } finally {
      setIsChangingPassword(false);
    }
  };

  if (!user) {
    // Skeleton loader
    return (
      <div className="max-w-4xl mx-auto py-10 px-4 sm:px-6 lg:px-8">
        <div className="animate-pulse flex space-x-4">
          <div className="flex-1 space-y-6 py-1">
            <div className="h-2 bg-slate-200 rounded w-1/4"></div>
            <div className="space-y-3">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="h-10 bg-slate-200 rounded"></div>
                <div className="h-10 bg-slate-200 rounded"></div>
              </div>
              <div className="h-10 bg-slate-200 rounded"></div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const isDoctor = user.role === 'MEDECIN';
  const isB2B = user.role === 'CLIENT_B2B';

  return (
    <div className="max-w-4xl mx-auto py-10 px-4 sm:px-6 lg:px-8">
      <div className="bg-white shadow rounded-lg overflow-hidden border border-slate-200">
        <div className="px-4 py-5 sm:px-6 bg-brand-dark">
          <h3 className="text-lg leading-6 font-medium text-white flex items-center gap-2">
            <UserIcon className="w-5 h-5" />
            Mon Profil
          </h3>
          <p className="mt-1 max-w-2xl text-sm text-brand-green/80">
            Mettez à jour vos informations personnelles et professionnelles.
          </p>
        </div>
        
        <div className="px-4 py-5 sm:p-6">
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              
              {/* Common Fields */}
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-slate-700 flex items-center gap-2">
                  <Mail className="w-4 h-4 text-slate-400" /> Adresse Email (Lecture seule)
                </label>
                <input
                  type="email"
                  disabled
                  value={user.email || ''}
                  className="mt-1 block w-full bg-slate-100 rounded-md border-slate-300 shadow-sm text-slate-500 sm:text-sm py-2 px-3 border"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700">Rôle</label>
                <input
                  type="text"
                  disabled
                  value={user.role || ''}
                  className="mt-1 block w-full bg-slate-100 rounded-md border-slate-300 shadow-sm text-slate-500 sm:text-sm py-2 px-3 border"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-slate-700 flex items-center gap-2">
                  <Phone className="w-4 h-4 text-slate-400" /> Téléphone
                </label>
                <input
                  type="text"
                  name="phoneWhatsapp"
                  value={formData.phoneWhatsapp || ''}
                  onChange={handleChange}
                  className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                />
              </div>

              {/* Doctor Specific Fields */}
              {isDoctor && (
                <>
                  <div>
                    <label className="block text-sm font-medium text-slate-700">Prénom</label>
                    <input
                      type="text"
                      name="firstName"
                      value={formData.firstName || ''}
                      onChange={handleChange}
                      className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700">Nom</label>
                    <input
                      type="text"
                      name="lastName"
                      value={formData.lastName || ''}
                      onChange={handleChange}
                      className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 flex items-center gap-2">
                      <Stethoscope className="w-4 h-4 text-slate-400" /> Spécialité Médicale
                    </label>
                    <input
                      type="text"
                      name="medicalSpecialty"
                      value={formData.medicalSpecialty || ''}
                      onChange={handleChange}
                      className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 flex items-center gap-2">
                      <FileText className="w-4 h-4 text-slate-400" /> Numéro d'Ordre
                    </label>
                    <input
                      type="text"
                      name="medicalCouncilNumber"
                      value={formData.medicalCouncilNumber || ''}
                      onChange={handleChange}
                      className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 flex items-center gap-2">
                      <MapPin className="w-4 h-4 text-slate-400" /> Pays de Résidence
                    </label>
                    <input
                      type="text"
                      name="countryOfResidence"
                      value={formData.countryOfResidence || ''}
                      onChange={handleChange}
                      className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700">Hôpital d'exercice</label>
                    <input
                      type="text"
                      name="currentHospital"
                      value={formData.currentHospital || ''}
                      onChange={handleChange}
                      className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                    />
                  </div>
                </>
              )}

              {/* B2B Specific Fields */}
              {isB2B && (
                <>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 flex items-center gap-2">
                      <Briefcase className="w-4 h-4 text-slate-400" /> Nom de l'entreprise
                    </label>
                    <input
                      type="text"
                      name="companyName"
                      value={formData.companyName || ''}
                      onChange={handleChange}
                      className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700">SIRET / FINESS</label>
                    <input
                      type="text"
                      name="siretFiness"
                      value={formData.siretFiness || ''}
                      onChange={handleChange}
                      className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                    />
                  </div>
                </>
              )}
            </div>

            <div className="pt-5 border-t border-slate-200 mt-6 flex justify-end">
              <button
                type="submit"
                disabled={isLoading}
                className="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-brand-green hover:bg-[#0f3c35] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-brand-green transition-colors disabled:opacity-70 gap-2 items-center"
              >
                <Save className="w-4 h-4" />
                {isLoading ? 'Enregistrement...' : 'Enregistrer les modifications'}
              </button>
            </div>
          </form>
        </div>
      </div>

      <div className="bg-white shadow rounded-lg overflow-hidden border border-slate-200 mt-8">
        <div className="px-4 py-5 sm:px-6 bg-slate-800">
          <h3 className="text-lg leading-6 font-medium text-white flex items-center gap-2">
            <KeyRound className="w-5 h-5" />
            Changer mon mot de passe
          </h3>
          <p className="mt-1 max-w-2xl text-sm text-slate-300">
            Si vous avez reçu un mot de passe temporaire par email, changez-le dès votre première connexion.
          </p>
        </div>

        <div className="px-4 py-5 sm:p-6">
          <form onSubmit={handlePasswordSubmit} className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div>
                <label className="block text-sm font-medium text-slate-700">Mot de passe actuel</label>
                <input
                  type="password" required
                  name="currentPassword"
                  value={passwordData.currentPassword}
                  onChange={handlePasswordChange}
                  className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700">Nouveau mot de passe</label>
                <input
                  type="password" required minLength={8}
                  name="newPassword"
                  value={passwordData.newPassword}
                  onChange={handlePasswordChange}
                  className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700">Confirmer le nouveau mot de passe</label>
                <input
                  type="password" required minLength={8}
                  name="confirmPassword"
                  value={passwordData.confirmPassword}
                  onChange={handlePasswordChange}
                  className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green sm:text-sm py-2 px-3 border"
                />
              </div>
            </div>

            <div className="pt-5 border-t border-slate-200 mt-6 flex justify-end">
              <button
                type="submit"
                disabled={isChangingPassword}
                className="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-slate-800 hover:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-slate-800 transition-colors disabled:opacity-70 gap-2 items-center"
              >
                <KeyRound className="w-4 h-4" />
                {isChangingPassword ? 'Modification...' : 'Changer le mot de passe'}
              </button>
            </div>
          </form>
        </div>
      </div>

      {toast && (
        <Toast 
          type={toast.type}
          message={toast.message}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  );
};
