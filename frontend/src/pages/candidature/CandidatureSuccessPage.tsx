import { useEffect, useState, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle, Loader2, Mail } from 'lucide-react';
import { doctorApplicationService } from '../../api/doctorApplicationService';

const MAX_POLL_ATTEMPTS = 10;
const POLL_INTERVAL_MS = 2000;

/**
 * Le paiement Stripe est confirmé côté serveur de façon asynchrone via webhook (checkout.session.
 * completed), qui peut arriver quelques instants après que le navigateur atterrisse ici. On
 * interroge donc le statut à intervalles courts plutôt que de supposer qu'il est déjà PAID.
 */
export function CandidatureSuccessPage() {
  const [searchParams] = useSearchParams();
  const stripeSessionId = searchParams.get('session_id');

  const [status, setStatus] = useState<'checking' | 'paid' | 'pending' | 'error'>('checking');
  const [trainingTitle, setTrainingTitle] = useState<string | null>(null);
  const attemptsRef = useRef(0);

  useEffect(() => {
    if (!stripeSessionId) {
      setStatus('error');
      return;
    }

    let cancelled = false;

    const poll = async () => {
      try {
        const data = await doctorApplicationService.getStatusByStripeSession(stripeSessionId);
        if (cancelled) return;
        setTrainingTitle(data.trainingTitle);
        if (data.status === 'PAID') {
          setStatus('paid');
          return;
        }
      } catch {
        // On retente silencieusement, le webhook peut simplement ne pas être encore passé.
      }

      attemptsRef.current += 1;
      if (attemptsRef.current >= MAX_POLL_ATTEMPTS) {
        if (!cancelled) setStatus('pending');
        return;
      }
      if (!cancelled) setTimeout(poll, POLL_INTERVAL_MS);
    };

    poll();
    return () => { cancelled = true; };
  }, [stripeSessionId]);

  return (
    <div className="max-w-2xl mx-auto py-20 px-6 text-center">
      <div className="bg-white p-10 rounded-3xl shadow-sm border border-slate-200">
        {status === 'checking' && (
          <>
            <div className="w-20 h-20 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto mb-6">
              <Loader2 className="w-10 h-10 animate-spin" />
            </div>
            <h1 className="text-3xl font-bold text-brand-dark mb-4">Confirmation du paiement...</h1>
            <p className="text-slate-600 mb-8">Merci de patienter quelques instants pendant que nous confirmons votre paiement.</p>
          </>
        )}

        {status === 'paid' && (
          <>
            <div className="w-20 h-20 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto mb-6">
              <CheckCircle className="w-10 h-10" />
            </div>
            <h1 className="text-3xl font-bold text-brand-dark mb-4">Candidature confirmée !</h1>
            <p className="text-slate-600 mb-4">
              Votre paiement des frais de dossier a bien été reçu{trainingTitle ? ` pour la formation « ${trainingTitle} »` : ''}.
            </p>
            <div className="bg-blue-50 text-blue-800 p-4 rounded-xl text-sm flex items-start space-x-3 mb-8 text-left">
              <Mail className="w-5 h-5 shrink-0 mt-0.5 text-blue-600" />
              <p>
                Un email contenant vos identifiants de connexion vient de vous être envoyé. Connectez-vous pour suivre
                votre dossier en temps réel et transmettre vos pièces justificatives depuis votre espace personnel.
              </p>
            </div>
            <Link to="/login" className="px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors">
              Me connecter
            </Link>
          </>
        )}

        {(status === 'pending' || status === 'error') && (
          <>
            <div className="w-20 h-20 bg-amber-100 text-amber-600 rounded-full flex items-center justify-center mx-auto mb-6">
              <Mail className="w-10 h-10" />
            </div>
            <h1 className="text-3xl font-bold text-brand-dark mb-4">Paiement en cours de confirmation</h1>
            <p className="text-slate-600 mb-8">
              Votre paiement a été transmis à Stripe. La confirmation finale peut prendre quelques minutes — vous
              recevrez vos identifiants de connexion par email dès qu'elle sera traitée.
            </p>
            <Link to="/" className="px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors">
              Retour à l'accueil
            </Link>
          </>
        )}
      </div>
    </div>
  );
}
