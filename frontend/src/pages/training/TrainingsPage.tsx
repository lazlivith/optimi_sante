import { useState } from 'react';
import { Download, Lock, CheckCircle, Settings } from 'lucide-react';
import { LeadCaptureModal } from './LeadCaptureModal';
import { TrainingBrochureModal } from './TrainingBrochureModal';
import { useAuth } from '../../context/AuthContext';

const MOCK_TRAININGS = [
  {
    id: 'a1b2c3d4-e5f6-7890-1234-56789abcdef1',
    specialty: 'RADIOLOGIE',
    title: 'Échographie clinique appliquée',
    duration: '10 jours',
    location: 'CHU Bordeaux',
    isLongStay: false,
  },
  {
    id: 'b2c3d4e5-f6a7-8901-2345-6789abcdef12',
    specialty: 'CHIRURGIE',
    title: 'Chirurgie mini-invasive — stage pratique',
    duration: '3 mois',
    location: 'CHU Toulouse',
    isLongStay: true,
  },
  {
    id: 'c3d4e5f6-a7b8-9012-3456-789abcdef123',
    specialty: 'PÉDIATRIE',
    title: 'Réanimation néonatale',
    duration: '15 jours',
    location: 'CHU Bordeaux',
    isLongStay: false,
  }
];

export function TrainingsPage() {
  const [selectedTraining, setSelectedTraining] = useState<{id: string, title: string} | null>(null);
  const [manageTraining, setManageTraining] = useState<{id: string, title: string} | null>(null);
  const { user, isAuthenticated } = useAuth();

  const canManage = user?.role === 'SUPER_ADMIN' || user?.role === 'ADMIN' || user?.role === 'CENTRE_FORMATION';

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
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {MOCK_TRAININGS.map((training) => (
            <div key={training.id} className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm flex flex-col h-full relative group">
              
              {canManage && (
                <button 
                  onClick={() => setManageTraining({ id: training.id, title: training.title })}
                  className="absolute top-4 right-4 p-2 bg-slate-100 text-slate-500 rounded-full hover:bg-emerald-100 hover:text-emerald-700 transition-colors opacity-0 group-hover:opacity-100"
                  title="Gérer la brochure"
                >
                  <Settings className="w-4 h-4" />
                </button>
              )}

              {/* Badges */}
              <div className="flex justify-between items-start mb-4">
                <span className="bg-brand-cream text-brand-dark text-[10px] font-bold px-3 py-1.5 rounded uppercase tracking-wider">
                  {training.specialty}
                </span>
                {training.isLongStay && (
                  <span className="bg-brand-orange/10 text-brand-orange border border-brand-orange/20 text-[10px] font-bold px-3 py-1.5 rounded uppercase tracking-wider">
                    VLS-TS &gt; 3 MOIS
                  </span>
                )}
              </div>

              {/* Title & Details */}
              <div className="mb-6 flex-grow">
                <h3 className="text-lg font-bold text-brand-dark mb-2 leading-tight pr-8">
                  {training.title}
                </h3>
                <p className="text-xs text-slate-500 font-medium">
                  {training.location} - {training.duration}
                </p>
              </div>

              {/* Actions */}
              <div className="flex items-center gap-3 mt-auto">
                <button
                  onClick={() => setSelectedTraining({ id: training.id, title: training.title })}
                  className="flex-1 flex items-center justify-center px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-semibold text-brand-dark hover:bg-slate-50 transition-colors"
                >
                  <Download className="w-4 h-4 mr-2" />
                  Télécharger la brochure
                </button>
                <button
                  disabled={!isAuthenticated}
                  title={!isAuthenticated ? "Connectez-vous pour postuler" : "Postuler"}
                  className={`flex items-center justify-center px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors ${isAuthenticated ? 'bg-brand-green text-white hover:bg-[#0f3c35]' : 'bg-slate-400 text-white opacity-80 cursor-not-allowed'}`}
                >
                  {!isAuthenticated ? <Lock className="w-4 h-4 mr-2" /> : <CheckCircle className="w-4 h-4 mr-2" />}
                  Postuler
                </button>
              </div>

            </div>
          ))}
        </div>

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
          onSuccess={(msg) => console.log(msg)}
        />
      )}
    </div>
  );
}
