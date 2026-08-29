import { useNavigate } from 'react-router-dom';
import { XCircle } from 'lucide-react';

export function CandidatureCancelPage() {
  const navigate = useNavigate();

  return (
    <div className="max-w-2xl mx-auto py-20 px-6 text-center">
      <div className="bg-white p-10 rounded-3xl shadow-sm border border-slate-200">
        <div className="w-20 h-20 bg-amber-100 text-amber-600 rounded-full flex items-center justify-center mx-auto mb-6">
          <XCircle className="w-10 h-10" />
        </div>
        <h1 className="text-3xl font-bold text-brand-dark mb-4">Paiement annulé</h1>
        <p className="text-slate-600 mb-8">
          Le paiement des frais de dossier a été annulé. Votre candidature n'a pas été validée et aucun compte n'a
          été créé — vous pouvez soumettre une nouvelle candidature quand vous le souhaitez.
        </p>
        <button onClick={() => navigate('/formations')} className="px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors">
          Voir les formations
        </button>
      </div>
    </div>
  );
}
