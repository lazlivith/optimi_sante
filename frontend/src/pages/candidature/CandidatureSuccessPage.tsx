import { useEffect, useState, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle, Loader2, Mail } from 'lucide-react';
import { doctorApplicationService } from '../../api/doctorApplicationService';
import { useAuth } from '../../context/AuthContext';

// Une minute d'attente au total. Chaque interrogation demande désormais au serveur de vérifier
// le paiement chez Stripe : la confirmation arrive en général dès la première. La marge couvre
// le délai de Stripe lui-même à marquer le paiement réglé.
const MAX_POLL_ATTEMPTS = 20;
const POLL_INTERVAL_MS = 3000;

/**
 * Le paiement Stripe est confirmé côté serveur — par le webhook, ou par la vérification que
 * déclenche chaque interrogation de cette page. On interroge donc le statut à intervalles
 * courts plutôt que de supposer qu'il est déjà PAID.
 *
 * <p>La version précédente abandonnait au bout de 20 secondes, alors qu'une confirmation a déjà
 * pris 106 secondes sur la base de travail : le candidat lisait « en cours de confirmation »
 * pour un paiement qui aboutissait une minute plus tard.</p>
 */
export function CandidatureSuccessPage() {
  const [searchParams] = useSearchParams();
  const stripeSessionId = searchParams.get('session_id');

  const [status, setStatus] = useState<'checking' | 'paid' | 'pending' | 'error'>('checking');
  const [trainingTitle, setTrainingTitle] = useState<string | null>(null);
  const [comptePromu, setComptePromu] = useState(false);
  const { logout } = useAuth();
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
          setComptePromu(Boolean(data.existingAccountPromoted));
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

        {status === 'paid' && comptePromu && (
          <>
            <div className="w-20 h-20 bg-success/10 text-success rounded-full flex items-center justify-center mx-auto mb-6">
              <CheckCircle className="w-10 h-10" />
            </div>
            <h1 className="text-3xl font-bold text-brand-dark mb-4">Votre espace médecin est ouvert</h1>
            <p className="text-slate-600 mb-4">
              Votre paiement des frais de dossier a bien été reçu{trainingTitle ? ` pour la formation « ${trainingTitle} »` : ''}.
              Votre compte client est désormais votre compte médecin.
            </p>
            {/* Le rôle voyage dans le jeton de session : celui en cours dit encore « client ». Sans
                nouvelle connexion, l'espace médecin refuserait l'accès. Aucun identifiant n'est
                envoyé — ce sont ceux que la personne utilise déjà. */}
            <div className="bg-brand-light text-brand-dark p-4 rounded-xl text-sm flex items-start space-x-3 mb-8 text-left">
              <Mail className="w-5 h-5 shrink-0 mt-0.5 text-brand" />
              <p>
                Reconnectez-vous avec vos identifiants habituels pour accéder à votre espace médecin, suivre
                votre dossier et transmettre vos pièces justificatives.
              </p>
            </div>
            <button type="button" onClick={logout} className="px-6 py-3 bg-brand text-white font-bold rounded-xl hover:bg-brand-fonce transition-colors">
              Me reconnecter
            </button>
          </>
        )}

        {status === 'paid' && !comptePromu && (
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
            <Link to="/login" className="px-6 py-3 bg-brand text-white font-bold rounded-xl hover:bg-brand-fonce transition-colors">
              Me connecter
            </Link>
          </>
        )}

        {status === 'pending' && (
          <>
            <div className="w-20 h-20 bg-amber-100 text-amber-600 rounded-full flex items-center justify-center mx-auto mb-6">
              <Mail className="w-10 h-10" />
            </div>
            <h1 className="text-3xl font-bold text-brand-dark mb-4">Paiement en cours de confirmation</h1>
            <p className="text-slate-600 mb-4">
              Votre paiement a été transmis à Stripe, mais sa confirmation n'est pas encore revenue. Vous
              recevrez vos identifiants de connexion par email dès qu'elle sera traitée.
            </p>
            {/* Un délai dit sans issue laisse le candidat attendre indéfiniment un email qui
                n'arrivera peut-être pas. La référence lui permet de se faire retrouver. */}
            <p className="text-sm text-slate-500 mb-8">
              Sans email d'ici une heure, contactez-nous en indiquant cette référence de paiement :{' '}
              <span className="font-mono break-all text-slate-700">{stripeSessionId}</span>
            </p>
            <Link to="/" className="px-6 py-3 bg-brand text-white font-bold rounded-xl hover:bg-brand-fonce transition-colors">
              Retour à l'accueil
            </Link>
          </>
        )}

        {status === 'error' && (
          <>
            <div className="w-20 h-20 bg-amber-100 text-amber-600 rounded-full flex items-center justify-center mx-auto mb-6">
              <Mail className="w-10 h-10" />
            </div>
            {/* Distinct de l'attente : sans référence de paiement dans l'adresse, il n'y a rien à
                vérifier, et promettre un email serait faux. */}
            <h1 className="text-3xl font-bold text-brand-dark mb-4">Paiement introuvable</h1>
            <p className="text-slate-600 mb-8">
              Cette page ne contient pas de référence de paiement. Si vous avez payé, vérifiez vos emails ;
              sinon, reprenez votre candidature depuis la fiche de la formation.
            </p>
            <Link to="/formations" className="px-6 py-3 bg-brand text-white font-bold rounded-xl hover:bg-brand-fonce transition-colors">
              Voir les formations
            </Link>
          </>
        )}
      </div>
    </div>
  );
}
