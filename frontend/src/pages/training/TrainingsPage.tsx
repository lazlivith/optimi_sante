import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Download, CheckCircle, Settings, Loader2 } from 'lucide-react';
import { LeadCaptureModal } from './LeadCaptureModal';
import { TrainingBrochureModal } from '../../components/training/TrainingBrochureModal';
import { useAuth } from '../../context/AuthContext';
import { trainingService, type TrainingSummaryDto } from '../../api/trainingService';
import { ProductImage } from '../../components/common/ProductImage';

const formatDuration = (durationDays: number, isLongStay: boolean): string => {
  if (isLongStay) {
    const months = Math.round(durationDays / 30);
    return `${months} mois`;
  }
  return `${durationDays} jours`;
};

export function TrainingsPage() {
  const [trainings, setTrainings] = useState<TrainingSummaryDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedTraining, setSelectedTraining] = useState<{id: string, title: string} | null>(null);
  const [manageTraining, setManageTraining] = useState<{id: string, title: string} | null>(null);
  const { user, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const canManage = user?.role === 'SUPER_ADMIN' || user?.role === 'ADMIN' || user?.role === 'CENTRE_FORMATION';

  useEffect(() => {
    const fetchTrainings = async () => {
      try {
        const data = await trainingService.getTrainings();
        setTrainings(data);
      } catch (error) {
        console.error('Failed to fetch trainings', error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchTrainings();
  }, []);

  return (
    <div className="min-h-screen bg-brand-cream/30 py-12">
      <div className="container mx-auto px-6 max-w-5xl">
        
        {/* Header Section */}
        <div className="mb-12">
          <div className="flex items-center text-[10px] font-bold tracking-widest text-slate-400 uppercase mb-3">
            <span>Mobilité Clinique</span>
            <span className="mx-2">·</span>
            <span>Zone CEMAC &rarr; France</span>
          </div>
          <h1 className="text-3xl md:text-4xl font-bold text-brand-dark mb-4 tracking-tight">
            Formations & stages en CHU
          </h1>
          <p className="text-slate-600 max-w-2xl text-sm leading-relaxed">
            Renseignez vos coordonnées pour recevoir le lien de téléchargement sécurisé de la brochure (valide 60 min). Un compte Médecin est requis pour postuler.
          </p>
        </div>

        {/* Trainings Grid */}
        {isLoading ? (
          <div className="flex justify-center py-16">
            <Loader2 className="w-8 h-8 text-brand-green animate-spin" />
          </div>
        ) : trainings.length === 0 ? (
          <div className="text-center py-16 text-slate-500">
            Aucune formation disponible pour le moment.
          </div>
        ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {trainings.map((training) => (
            <div key={training.id} className="bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col h-full relative group overflow-hidden">

              {canManage && (
                <button
                  onClick={() => setManageTraining({ id: training.id, title: training.title })}
                  className="absolute top-4 right-4 z-10 p-2 bg-white/90 text-slate-500 rounded-full hover:bg-emerald-100 hover:text-emerald-700 transition-colors opacity-0 group-hover:opacity-100"
                  title="Gérer la brochure"
                >
                  <Settings className="w-4 h-4" />
                </button>
              )}

              <button
                onClick={() => navigate(`/formations/${training.id}`)}
                className="block text-left"
              >
                <ProductImage
                  src={training.imageUrl}
                  alt={training.title}
                  className="w-full h-40"
                  objectFit="cover"
                  iconClassName="w-12 h-12"
                />
              </button>

              <div className="p-6 flex flex-col flex-grow">
                {/* Badges */}
                <div className="flex justify-between items-start mb-4">
                  <span className="bg-brand-cream text-brand-dark text-[10px] font-bold px-3 py-1.5 rounded uppercase tracking-wider">
                    {training.medicalSpecialty}
                  </span>
                  {training.isLongStay && (
                    <span className="bg-brand-orange/10 text-brand-orange border border-brand-orange/20 text-[10px] font-bold px-3 py-1.5 rounded uppercase tracking-wider">
                      VLS-TS &gt; 3 MOIS
                    </span>
                  )}
                </div>

                {/* Title & Details */}
                <button onClick={() => navigate(`/formations/${training.id}`)} className="mb-6 flex-grow text-left">
                  <h3 className="text-lg font-bold text-brand-dark mb-2 leading-tight pr-8 hover:text-brand-green transition-colors">
                    {training.title}
                  </h3>
                  <p className="text-xs text-slate-500 font-medium">
                    {training.location ? `${training.location} - ` : ''}{formatDuration(training.durationDays, training.isLongStay)}
                  </p>
                </button>

                {/* Actions */}
                <div className="flex items-center gap-3 mt-auto">
                <button
                  onClick={() => {
                    if (training.brochureUrl) {
                      window.open(training.brochureUrl, '_blank');
                    }
                  }}
                  disabled={!training.brochureUrl}
                  className="flex-1 flex items-center justify-center px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-semibold text-brand-dark hover:bg-slate-50 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <Download className="w-4 h-4 mr-2" />
                  Brochure
                </button>
                <button
                  onClick={() => {
                    if (isAuthenticated) {
                      navigate(`/formations/${training.id}/enroll`);
                    } else {
                      setSelectedTraining({ id: training.id, title: training.title });
                    }
                  }}
                  className={`flex items-center justify-center px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors bg-brand-green text-white hover:bg-[#0f3c35]`}
                >
                  <CheckCircle className="w-4 h-4 mr-2" />
                  Postuler
                </button>
                </div>
              </div>
            </div>
          ))}
        </div>
        )}

      </div>

      {/* Modals */}
      {selectedTraining && (
        <LeadCaptureModal
          isOpen={true}
          onClose={() => setSelectedTraining(null)}
          trainingId={selectedTraining.id}
          trainingTitle={selectedTraining.title}
        />
      )}

      {manageTraining && (
        <TrainingBrochureModal
          isOpen={true}
          onClose={() => setManageTraining(null)}
          trainingId={manageTraining.id}
          trainingTitle={manageTraining.title}
          onSuccess={(msg: string) => console.log(msg)}
        />
      )}
    </div>
  );
}
