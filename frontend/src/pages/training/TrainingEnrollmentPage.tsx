import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Check, ChevronRight, UploadCloud, MapPin, Calendar, Users, Loader2 } from 'lucide-react';
import { enrollmentService } from '../../api/enrollmentService';
import type { TrainingSessionDto, EnrollmentResponseDto } from '../../api/enrollmentService';
import { storageService } from '../../api/storageService';
import { FileUploadDropzone } from '../../components/common/FileUploadDropzone';

export function TrainingEnrollmentPage() {
  const { id: trainingId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  
  const [currentStep, setCurrentStep] = useState(1);
  const [sessions, setSessions] = useState<TrainingSessionDto[]>([]);
  const [isLoadingSessions, setIsLoadingSessions] = useState(true);
  const [selectedSession, setSelectedSession] = useState<TrainingSessionDto | null>(null);
  
  const [enrollment, setEnrollment] = useState<EnrollmentResponseDto | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Upload States
  const [diplomaUrl, setDiplomaUrl] = useState<string | null>(null);
  const [isUploadingDiploma, setIsUploadingDiploma] = useState(false);
  const [orderRegistrationUrl, setOrderRegistrationUrl] = useState<string | null>(null);
  const [isUploadingOrder, setIsUploadingOrder] = useState(false);
  const [passportUrl, setPassportUrl] = useState<string | null>(null);
  const [isUploadingPassport, setIsUploadingPassport] = useState(false);

  useEffect(() => {
    if (trainingId) {
      fetchSessions();
    }
  }, [trainingId]);

  const fetchSessions = async () => {
    try {
      setIsLoadingSessions(true);
      const data = await enrollmentService.getAvailableSessions(trainingId!);
      setSessions(data);
    } catch (error) {
      console.error("Failed to fetch sessions", error);
    } finally {
      setIsLoadingSessions(false);
    }
  };

  const handleCreateEnrollment = async () => {
    if (!selectedSession) return;
    try {
      setIsSubmitting(true);
      const res = await enrollmentService.createEnrollment({ sessionId: selectedSession.id });
      setEnrollment(res);
      setCurrentStep(2);
    } catch (error) {
      console.error("Failed to create enrollment", error);
      alert("Erreur lors de la création du dossier. Vous êtes peut-être déjà inscrit à cette session.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleFileUpload = async (
    file: File, 
    setLoading: (val: boolean) => void, 
    setUrl: (val: string) => void
  ) => {
    try {
      setLoading(true);
      const res = await storageService.uploadFile(file, 'docs/enrollments');
      setUrl(res.publicId);
    } catch (error) {
      console.error("Upload failed", error);
      alert("Le téléversement du fichier a échoué. Veuillez réessayer.");
    } finally {
      setLoading(false);
    }
  };

  const handleSubmitDocuments = async () => {
    if (!enrollment || !diplomaUrl || !orderRegistrationUrl || !passportUrl) return;
    try {
      setIsSubmitting(true);
      await enrollmentService.submitDocuments(enrollment.id, {
        diplomaUrl,
        medicalBoardRegistrationUrl: orderRegistrationUrl,
        passportUrl
      });
      setCurrentStep(3);
    } catch (error) {
      console.error("Failed to submit documents", error);
      alert("Erreur lors de la soumission des documents.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const renderStepper = () => (
    <div className="flex items-center justify-center mb-12">
      <div className="flex items-center">
        <div className={`flex items-center justify-center w-10 h-10 rounded-full font-bold text-sm ${currentStep >= 1 ? 'bg-emerald-600 text-white' : 'bg-slate-200 text-slate-500'}`}>1</div>
        <div className={`w-16 h-1 mx-2 rounded ${currentStep >= 2 ? 'bg-emerald-600' : 'bg-slate-200'}`}></div>
        <div className={`flex items-center justify-center w-10 h-10 rounded-full font-bold text-sm ${currentStep >= 2 ? 'bg-emerald-600 text-white' : 'bg-slate-200 text-slate-500'}`}>2</div>
        <div className={`w-16 h-1 mx-2 rounded ${currentStep >= 3 ? 'bg-emerald-600' : 'bg-slate-200'}`}></div>
        <div className={`flex items-center justify-center w-10 h-10 rounded-full font-bold text-sm ${currentStep >= 3 ? 'bg-emerald-600 text-white' : 'bg-slate-200 text-slate-500'}`}>3</div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-slate-50 py-12 px-4">
      <div className="max-w-3xl mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-slate-900">Candidature CHU</h1>
          <p className="text-slate-500 mt-2">Finalisez votre inscription en quelques étapes</p>
        </div>

        {renderStepper()}

        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-8">
          
          {currentStep === 1 && (
            <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4">
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
                        <div className="text-lg font-bold text-slate-900">{session.price.toFixed(2)} €</div>
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
                  onClick={handleCreateEnrollment}
                  disabled={!selectedSession || isSubmitting}
                  className="px-6 py-3 bg-emerald-600 text-white font-semibold rounded-xl hover:bg-emerald-700 transition disabled:opacity-50 disabled:cursor-not-allowed flex items-center"
                >
                  {isSubmitting ? <Loader2 className="w-5 h-5 animate-spin mr-2" /> : null}
                  Continuer vers les justificatifs
                  {!isSubmitting && <ChevronRight className="w-5 h-5 ml-2" />}
                </button>
              </div>
            </div>
          )}

          {currentStep === 2 && (
            <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4">
              <h2 className="text-xl font-semibold text-slate-900 border-b border-slate-100 pb-4">
                Étape 2 : Pièces justificatives
              </h2>
              <div className="bg-blue-50 text-blue-800 p-4 rounded-xl text-sm mb-6 flex items-start space-x-3">
                <UploadCloud className="w-5 h-5 shrink-0 mt-0.5 text-blue-600" />
                <p>
                  Veuillez fournir vos documents officiels. Ces pièces seront vérifiées par l'équipe OptimiSanté et le CHU d'accueil avant validation définitive.
                </p>
              </div>

              <div className="space-y-6">
                <FileUploadDropzone 
                  label="Glissez-déposez votre Diplôme de Médecine (PDF)"
                  isLoading={isUploadingDiploma}
                  onFileSelect={(file) => handleFileUpload(file, setIsUploadingDiploma, setDiplomaUrl)}
                />

                <FileUploadDropzone 
                  label="Glissez-déposez votre Attestation d'Ordre (PDF)"
                  isLoading={isUploadingOrder}
                  onFileSelect={(file) => handleFileUpload(file, setIsUploadingOrder, setOrderRegistrationUrl)}
                />

                <FileUploadDropzone 
                  label="Glissez-déposez une copie de votre Passeport (PDF)"
                  isLoading={isUploadingPassport}
                  onFileSelect={(file) => handleFileUpload(file, setIsUploadingPassport, setPassportUrl)}
                />
              </div>

              <div className="flex justify-between pt-6 border-t border-slate-100 mt-8">
                <button
                  onClick={() => setCurrentStep(1)}
                  className="px-6 py-3 text-slate-600 font-semibold rounded-xl hover:bg-slate-100 transition"
                >
                  Retour
                </button>
                <button
                  onClick={handleSubmitDocuments}
                  disabled={!diplomaUrl || !orderRegistrationUrl || !passportUrl || isSubmitting}
                  className="px-6 py-3 bg-emerald-600 text-white font-semibold rounded-xl hover:bg-emerald-700 transition disabled:opacity-50 disabled:cursor-not-allowed flex items-center"
                >
                  {isSubmitting ? <Loader2 className="w-5 h-5 animate-spin mr-2" /> : null}
                  Soumettre le dossier
                </button>
              </div>
            </div>
          )}

          {currentStep === 3 && (
            <div className="text-center py-12 space-y-6 animate-in zoom-in-95">
              <div className="w-20 h-20 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto mb-6">
                <Check className="w-10 h-10" />
              </div>
              <h2 className="text-2xl font-bold text-slate-900">Candidature Envoyée !</h2>
              <p className="text-slate-600 max-w-md mx-auto">
                Votre dossier a bien été transmis. Notre équipe va procéder à la vérification de vos pièces justificatives. Vous recevrez une notification d'ici 48h.
              </p>
              
              <div className="pt-8">
                <button
                  onClick={() => navigate('/')}
                  className="px-8 py-3 bg-slate-900 text-white font-semibold rounded-xl hover:bg-slate-800 transition"
                >
                  Retour à l'accueil
                </button>
              </div>
            </div>
          )}
          
        </div>
      </div>
    </div>
  );
}
