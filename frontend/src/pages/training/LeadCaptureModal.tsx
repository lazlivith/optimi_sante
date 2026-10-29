import { useState } from 'react';
import { CheckCircle, Loader2, X } from 'lucide-react';
import { trainingService, type LeadCaptureRequestDto } from '../../api/trainingService';

interface LeadCaptureModalProps {
  isOpen: boolean;
  onClose: () => void;
  trainingId: string;
  trainingTitle: string;
}

export function LeadCaptureModal({ isOpen, onClose, trainingId, trainingTitle }: LeadCaptureModalProps) {
  const [formData, setFormData] = useState<LeadCaptureRequestDto>({
    email: '',
    firstName: '',
    lastName: '',
    phoneWhatsapp: '',
    country: '',
    specialty: ''
  });
  
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  if (!isOpen) return null;

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      // La réponse ne porte plus de lien : aucun aperçu à ouvrir, aucune promesse à tenir
      // sur un fichier. La demande est enregistrée, un conseiller prend le relais.
      await trainingService.captureLead(trainingId, formData);
      setSuccess(true);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Erreur lors de la candidature.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-brand-dark/50 backdrop-blur-sm p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg relative max-h-[90vh] overflow-y-auto">
        <button 
          onClick={onClose}
          className="absolute top-4 right-4 p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-full transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        {success ? (
          <div className="p-10 text-center space-y-6">
            <div className="w-16 h-16 bg-brand/10 text-brand rounded-full flex items-center justify-center mx-auto mb-4">
              <CheckCircle className="w-8 h-8" />
            </div>
            <h3 className="text-2xl font-bold text-brand-dark">Candidature envoyée</h3>
            {/* Le sort de la brochure est dit au futur : elle n'est pas jointe à cette
                réponse. Aucun onglet ne s'ouvre, aucun fichier n'est promis comme déjà
                remis. */}
            <p className="text-slate-600">
              Votre demande est enregistrée. Un conseiller Optimi Santé vous envoie la
              brochure et les modalités d'inscription.
            </p>
            <button 
              onClick={onClose}
              className="mt-4 inline-flex items-center justify-center px-6 py-3 bg-brand text-white font-medium rounded-xl hover:bg-brand-fonce transition-colors"
            >
              Fermer
            </button>
          </div>
        ) : (
          <>
            <div className="p-8 border-b border-slate-100">
              <h2 className="text-xl font-bold text-brand-dark mb-2">Candidature à la formation</h2>
              <p className="text-sm text-slate-500">
                Formation : <span className="font-semibold text-brand">{trainingTitle}</span>
              </p>
            </div>

            <form onSubmit={handleSubmit} className="p-8 space-y-6">
              {error && (
                <div className="p-3 bg-red-50 text-red-600 text-sm rounded-lg border border-red-200">
                  {error}
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-brand-dark">Prénom</label>
                  <input
                    required
                    type="text"
                    name="firstName"
                    value={formData.firstName}
                    onChange={handleChange}
                    className="w-full p-3 bg-brand-light border border-slate-200 rounded-xl focus:border-brand focus:ring-1 focus:ring-brand outline-none"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-brand-dark">Nom</label>
                  <input
                    required
                    type="text"
                    name="lastName"
                    value={formData.lastName}
                    onChange={handleChange}
                    className="w-full p-3 bg-brand-light border border-slate-200 rounded-xl focus:border-brand focus:ring-1 focus:ring-brand outline-none"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-semibold text-brand-dark">Email</label>
                <input
                  required
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  className="w-full p-3 bg-brand-light border border-slate-200 rounded-xl focus:border-brand focus:ring-1 focus:ring-brand outline-none"
                />
              </div>

              <div className="space-y-2">
                <label className="text-sm font-semibold text-brand-dark">Téléphone (WhatsApp)</label>
                <input
                  required
                  type="text"
                  name="phoneWhatsapp"
                  value={formData.phoneWhatsapp}
                  onChange={handleChange}
                  placeholder="+33 6..."
                  className="w-full p-3 bg-brand-light border border-slate-200 rounded-xl focus:border-brand focus:ring-1 focus:ring-brand outline-none"
                />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-brand-dark">Pays de résidence</label>
                  <input
                    required
                    type="text"
                    name="country"
                    value={formData.country}
                    onChange={handleChange}
                    className="w-full p-3 bg-brand-light border border-slate-200 rounded-xl focus:border-brand focus:ring-1 focus:ring-brand outline-none"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-brand-dark">Spécialité médicale</label>
                  <input
                    required
                    type="text"
                    name="specialty"
                    value={formData.specialty}
                    onChange={handleChange}
                    className="w-full p-3 bg-brand-light border border-slate-200 rounded-xl focus:border-brand focus:ring-1 focus:ring-brand outline-none"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full flex items-center justify-center px-6 py-4 bg-brand text-white font-bold rounded-xl hover:bg-brand-fonce disabled:opacity-50 transition-colors"
              >
                {isSubmitting ? <Loader2 className="animate-spin w-5 h-5 mr-2" /> : <CheckCircle className="w-5 h-5 mr-2" />}
                Postuler
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
