import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, Download, Loader2, CheckCircle, GraduationCap, Clock, MapPin } from 'lucide-react';
import { trainingService, type LeadCaptureRequestDto, type TrainingSummaryDto } from '../../api/trainingService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { useAuth } from '../../context/AuthContext';
import { ProductImage } from '../../components/common/ProductImage';

const formatDuration = (durationDays: number, isLongStay: boolean): string => {
  if (isLongStay) {
    const months = Math.round(durationDays / 30);
    return `${months} mois`;
  }
  return `${durationDays} jours`;
};

export function TrainingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { isAuthenticated, user } = useAuth();

  const [training, setTraining] = useState<TrainingSummaryDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchTraining = async () => {
      try {
        const all = await trainingService.getTrainings();
        setTraining(all.find(t => t.id === id) ?? null);
      } catch (error) {
        console.error('Failed to fetch training', error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchTraining();
  }, [id]);

  // Un médecin déjà titulaire d'un compte (créé lors d'une candidature payée précédente)
  // s'inscrit directement à une session supplémentaire, sans repayer les frais de dossier.
  // Un candidat sans compte encore passe par la candidature payante (création de compte incluse).
  const isExistingDoctor = isAuthenticated && user?.role === 'MEDECIN';
  const applyPath = isExistingDoctor ? `/formations/${id}/enroll` : `/formations/${id}/postuler`;

  const [form, setForm] = useState<LeadCaptureRequestDto>({
    firstName: '',
    lastName: '',
    email: '',
    phoneWhatsapp: '',
    country: '',
    specialty: ''
  });

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return;

    setIsSubmitting(true);
    try {
      const response = await trainingService.captureLead(id, form);
      if (response.brochureDownloadUrl) {
        setToast({ message: 'Brochure téléchargée avec succès !', type: 'success' });
        window.open(response.brochureDownloadUrl, '_blank');
      }
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors du téléchargement de la brochure.', type: 'error' });
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Loader2 className="w-8 h-8 text-brand-green animate-spin" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-brand-cream/30 py-12">
      <div className="container mx-auto px-6 max-w-5xl">
        <Link to="/formations" className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-8 transition-colors">
          <ArrowLeft className="w-4 h-4 mr-2" />
          Retour aux formations
        </Link>

        {training?.imageUrl && (
          <ProductImage
            src={training.imageUrl}
            alt={training.title}
            className="w-full h-64 rounded-2xl mb-8"
            objectFit="cover"
          />
        )}

        <div className="grid md:grid-cols-2 gap-12">
          {/* Informations sur la formation */}
          <div>
            <div className="flex items-center text-[10px] font-bold tracking-widest text-slate-400 uppercase mb-3">
              <span>Mobilité Clinique</span>
              <span className="mx-2">·</span>
              <span>Formation Pratique</span>
            </div>
            <h1 className="text-3xl md:text-4xl font-bold text-brand-dark mb-6 tracking-tight">
              {training?.title ?? 'Détails de la Formation'}
            </h1>

            {training?.description && (
              <p className="text-slate-600 mb-8 leading-relaxed whitespace-pre-line">
                {training.description}
              </p>
            )}

            {training?.videoUrl && (
              <video
                src={training.videoUrl}
                controls
                className="w-full rounded-xl mb-8 bg-black"
              />
            )}

            <div className="space-y-4 mb-8">
              <div className="flex items-center gap-4 bg-white p-4 rounded-xl border border-slate-200">
                <div className="w-10 h-10 bg-brand-light text-brand-green rounded-full flex items-center justify-center">
                  <GraduationCap className="w-5 h-5" />
                </div>
                <div>
                  <p className="text-xs text-slate-400 font-bold uppercase">Spécialité</p>
                  <p className="font-semibold text-brand-dark">{training?.medicalSpecialty ?? 'Toutes spécialités médicales'}</p>
                </div>
              </div>
              <div className="flex items-center gap-4 bg-white p-4 rounded-xl border border-slate-200">
                <div className="w-10 h-10 bg-brand-light text-brand-green rounded-full flex items-center justify-center">
                  <Clock className="w-5 h-5" />
                </div>
                <div>
                  <p className="text-xs text-slate-400 font-bold uppercase">Durée</p>
                  <p className="font-semibold text-brand-dark">
                    {training ? formatDuration(training.durationDays, training.isLongStay) : 'Variable selon la session'}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-4 bg-white p-4 rounded-xl border border-slate-200">
                <div className="w-10 h-10 bg-brand-light text-brand-green rounded-full flex items-center justify-center">
                  <MapPin className="w-5 h-5" />
                </div>
                <div>
                  <p className="text-xs text-slate-400 font-bold uppercase">Lieu</p>
                  <p className="font-semibold text-brand-dark">{training?.location ?? 'CHU Partenaires'}</p>
                </div>
              </div>
            </div>

            <Link to={applyPath} className="inline-flex items-center justify-center px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors">
              <CheckCircle className="w-5 h-5 mr-2" />
              Postuler à cette formation
            </Link>
          </div>

          {/* Formulaire Lead Capture */}
          <div>
            <div className="bg-white p-8 rounded-2xl shadow-sm border border-slate-200 sticky top-28">
              <div className="flex items-center gap-3 mb-6">
                <div className="p-3 bg-brand-orange/10 text-brand-orange rounded-xl">
                  <Download className="w-6 h-6" />
                </div>
                <h2 className="text-xl font-bold text-brand-dark">Télécharger la brochure</h2>
              </div>
              <p className="text-sm text-slate-500 mb-6">
                Renseignez vos coordonnées pour recevoir le lien de téléchargement sécurisé du programme détaillé.
              </p>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">Prénom</label>
                    <input
                      type="text" required
                      value={form.firstName} onChange={(e) => setForm({...form, firstName: e.target.value})}
                      className="w-full rounded-lg border-slate-300 border p-2.5 focus:ring-brand-green focus:border-brand-green"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">Nom</label>
                    <input
                      type="text" required
                      value={form.lastName} onChange={(e) => setForm({...form, lastName: e.target.value})}
                      className="w-full rounded-lg border-slate-300 border p-2.5 focus:ring-brand-green focus:border-brand-green"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Email</label>
                  <input
                    type="email" required
                    value={form.email} onChange={(e) => setForm({...form, email: e.target.value})}
                    className="w-full rounded-lg border-slate-300 border p-2.5 focus:ring-brand-green focus:border-brand-green"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Téléphone (WhatsApp de préférence)</label>
                  <input
                    type="tel" required
                    value={form.phoneWhatsapp} onChange={(e) => setForm({...form, phoneWhatsapp: e.target.value})}
                    className="w-full rounded-lg border-slate-300 border p-2.5 focus:ring-brand-green focus:border-brand-green"
                  />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">Pays d'exercice</label>
                    <input
                      type="text" required
                      value={form.country} onChange={(e) => setForm({...form, country: e.target.value})}
                      className="w-full rounded-lg border-slate-300 border p-2.5 focus:ring-brand-green focus:border-brand-green"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">Spécialité</label>
                    <input
                      type="text" required
                      value={form.specialty} onChange={(e) => setForm({...form, specialty: e.target.value})}
                      className="w-full rounded-lg border-slate-300 border p-2.5 focus:ring-brand-green focus:border-brand-green"
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="w-full flex justify-center items-center py-3.5 mt-2 bg-brand-dark text-white font-bold rounded-xl hover:bg-slate-800 transition-colors disabled:opacity-70"
                >
                  {isSubmitting ? <Loader2 className="w-5 h-5 mr-2 animate-spin" /> : null}
                  Recevoir le lien PDF
                </button>
              </form>
            </div>
          </div>
        </div>
      </div>

      {toast && (
        <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  );
}
