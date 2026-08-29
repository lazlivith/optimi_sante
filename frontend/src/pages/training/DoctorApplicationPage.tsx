import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ChevronRight, MapPin, Calendar, Users, Loader2, CreditCard, ArrowLeft } from 'lucide-react';
import { enrollmentService } from '../../api/enrollmentService';
import type { TrainingSessionDto } from '../../api/enrollmentService';
import { doctorApplicationService } from '../../api/doctorApplicationService';
import { StripeEmbeddedCheckout } from '../../components/payment/StripeEmbeddedCheckout';
import { Toast, type ToastType } from '../../components/common/Toast';

/**
 * Candidature médecin (public, sans compte préalable). Remplace l'ancienne auto-inscription :
 * le candidat choisit une session, renseigne son identité, puis paie des frais de dossier fixes
 * via un formulaire Stripe intégré (Payment Element, ui_mode "elements" — pas de redirection).
 * Le compte MEDECIN + l'inscription à la session ne sont créés qu'après confirmation du paiement
 * (webhook Stripe) — voir DoctorApplicationService côté backend. Les pièces justificatives
 * (diplôme, ordre, passeport) se transmettent ensuite depuis l'espace Médecin, une fois connecté.
 */
export function DoctorApplicationPage() {
  const { id: trainingId } = useParams<{ id: string }>();

  const [currentStep, setCurrentStep] = useState(1);
  const [sessions, setSessions] = useState<TrainingSessionDto[]>([]);
  const [isLoadingSessions, setIsLoadingSessions] = useState(true);
  const [selectedSession, setSelectedSession] = useState<TrainingSessionDto | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const [form, setForm] = useState({
    email: '', firstName: '', lastName: '', phoneWhatsapp: '', countryOfResidence: '',
    medicalSpecialty: '', medicalCouncilNumber: '', currentHospital: '', passportNumber: ''
  });

  useEffect(() => {
    if (trainingId) fetchSessions();
  }, [trainingId]);

  const fetchSessions = async () => {
    try {
      setIsLoadingSessions(true);
      const data = await enrollmentService.getAvailableSessions(trainingId!);
      setSessions(data);
    } catch (error) {
      console.error('Failed to fetch sessions', error);
    } finally {
      setIsLoadingSessions(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedSession) return;
    setIsSubmitting(true);
    try {
      const response = await doctorApplicationService.submitApplication({
        tenantCode: 'FR_MAIN',
        sessionId: selectedSession.id,
        ...form
      });
      if (response.clientSecret) {
        setClientSecret(response.clientSecret);
        setCurrentStep(3);
      } else {
        setToast({ message: 'Erreur : impossible d\'initialiser le paiement.', type: 'error' });
      }
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || "Erreur lors de l'envoi de la candidature.", type: 'error' });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 py-12 px-4">
      <div className="max-w-3xl mx-auto">
        <Link to={`/formations/${trainingId}`} className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-6 transition-colors">
          <ArrowLeft className="w-4 h-4 mr-2" /> Retour à la formation
        </Link>

        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-slate-900">Candidature Médecin</h1>
          <p className="text-slate-500 mt-2">Choisissez votre session, renseignez vos informations, puis réglez les frais de dossier pour recevoir vos identifiants</p>
        </div>

        <div className="flex items-center justify-center mb-12">
          <div className="flex items-center">
            <div className={`flex items-center justify-center w-10 h-10 rounded-full font-bold text-sm ${currentStep >= 1 ? 'bg-emerald-600 text-white' : 'bg-slate-200 text-slate-500'}`}>1</div>
            <div className={`w-16 h-1 mx-2 rounded ${currentStep >= 2 ? 'bg-emerald-600' : 'bg-slate-200'}`}></div>
            <div className={`flex items-center justify-center w-10 h-10 rounded-full font-bold text-sm ${currentStep >= 2 ? 'bg-emerald-600 text-white' : 'bg-slate-200 text-slate-500'}`}>2</div>
            <div className={`w-16 h-1 mx-2 rounded ${currentStep >= 3 ? 'bg-emerald-600' : 'bg-slate-200'}`}></div>
            <div className={`flex items-center justify-center w-10 h-10 rounded-full font-bold text-sm ${currentStep >= 3 ? 'bg-emerald-600 text-white' : 'bg-slate-200 text-slate-500'}`}>3</div>
          </div>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-8">
          {currentStep === 1 && (
            <div className="space-y-6">
              <h2 className="text-xl font-semibold text-slate-900 border-b border-slate-100 pb-4">
                Étape 1 : Choix de la session
              </h2>

              {isLoadingSessions ? (
                <div className="py-12 flex justify-center">
                  <Loader2 className="w-8 h-8 animate-spin text-emerald-600" />
                </div>
              ) : sessions.length === 0 ? (
                <div className="py-12 text-center text-slate-500">
                  Aucune session ouverte pour cette formation actuellement.
                </div>
              ) : (
                <div className="grid gap-4">
                  {sessions.map(session => (
                    <div
                      key={session.id}
                      onClick={() => setSelectedSession(session)}
                      className={`p-5 rounded-xl border-2 cursor-pointer transition-all ${
                        selectedSession?.id === session.id
                          ? 'border-emerald-600 bg-emerald-50'
                          : 'border-slate-200 hover:border-slate-300'
                      }`}
                    >
                      <div className="flex justify-between items-start mb-4">
                        <div className="flex items-center space-x-2 text-emerald-700 font-semibold bg-emerald-100 px-3 py-1 rounded-full text-sm">
                          <Calendar className="w-4 h-4" />
                          <span>Du {new Date(session.startDate).toLocaleDateString('fr-FR')} au {new Date(session.endDate).toLocaleDateString('fr-FR')}</span>
                        </div>
                      </div>
                      <div className="flex items-center space-x-6 text-sm text-slate-600">
                        <div className="flex items-center space-x-2">
                          <MapPin className="w-4 h-4 text-slate-400" />
                          <span>{session.location}</span>
                        </div>
                        <div className="flex items-center space-x-2">
                          <Users className="w-4 h-4 text-slate-400" />
                          <span>{session.availableSeats} places disponibles (sur {session.capacity})</span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <div className="flex justify-end pt-6">
                <button
                  onClick={() => setCurrentStep(2)}
                  disabled={!selectedSession}
                  className="px-6 py-3 bg-emerald-600 text-white font-semibold rounded-xl hover:bg-emerald-700 transition disabled:opacity-50 disabled:cursor-not-allowed flex items-center"
                >
                  Continuer vers mes informations
                  <ChevronRight className="w-5 h-5 ml-2" />
                </button>
              </div>
            </div>
          )}

          {currentStep === 2 && (
            <form onSubmit={handleSubmit} className="space-y-6">
              <h2 className="text-xl font-semibold text-slate-900 border-b border-slate-100 pb-4">
                Étape 2 : Vos informations
              </h2>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Prénom</label>
                  <input type="text" required value={form.firstName} onChange={e => setForm({ ...form, firstName: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Nom</label>
                  <input type="text" required value={form.lastName} onChange={e => setForm({ ...form, lastName: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-slate-700 mb-1">Email</label>
                  <input type="email" required value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                  <p className="text-xs text-slate-400 mt-1">Vos identifiants de connexion seront envoyés à cette adresse une fois le paiement confirmé.</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Téléphone (WhatsApp)</label>
                  <input type="tel" required value={form.phoneWhatsapp} onChange={e => setForm({ ...form, phoneWhatsapp: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Pays de résidence</label>
                  <input type="text" required value={form.countryOfResidence} onChange={e => setForm({ ...form, countryOfResidence: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Spécialité médicale</label>
                  <input type="text" required value={form.medicalSpecialty} onChange={e => setForm({ ...form, medicalSpecialty: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">N° d'inscription à l'Ordre</label>
                  <input type="text" value={form.medicalCouncilNumber} onChange={e => setForm({ ...form, medicalCouncilNumber: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Hôpital d'exercice actuel</label>
                  <input type="text" value={form.currentHospital} onChange={e => setForm({ ...form, currentHospital: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-slate-700 mb-1">N° de passeport</label>
                  <input type="text" value={form.passportNumber} onChange={e => setForm({ ...form, passportNumber: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
              </div>

              <div className="bg-blue-50 text-blue-800 p-4 rounded-xl text-sm flex items-start space-x-3">
                <CreditCard className="w-5 h-5 shrink-0 mt-0.5 text-blue-600" />
                <p>
                  À l'étape suivante, réglez les frais de dossier directement sur cette page (aucune redirection).
                  Vos justificatifs (diplôme, attestation d'ordre, passeport) seront à transmettre depuis votre
                  espace personnel une fois le compte créé.
                </p>
              </div>

              <div className="flex justify-between pt-6 border-t border-slate-100 mt-8">
                <button type="button" onClick={() => setCurrentStep(1)} className="px-6 py-3 text-slate-600 font-semibold rounded-xl hover:bg-slate-100 transition">
                  Retour
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-6 py-3 bg-emerald-600 text-white font-semibold rounded-xl hover:bg-emerald-700 transition disabled:opacity-50 disabled:cursor-not-allowed flex items-center"
                >
                  {isSubmitting ? <Loader2 className="w-5 h-5 animate-spin mr-2" /> : <CreditCard className="w-5 h-5 mr-2" />}
                  Continuer vers le paiement
                </button>
              </div>
            </form>
          )}

          {currentStep === 3 && clientSecret && (
            <div className="space-y-6">
              <h2 className="text-xl font-semibold text-slate-900 border-b border-slate-100 pb-4">
                Étape 3 : Paiement des frais de dossier
              </h2>
              <StripeEmbeddedCheckout clientSecret={clientSecret} payLabel="Payer les frais de dossier" />
            </div>
          )}
        </div>
      </div>

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
