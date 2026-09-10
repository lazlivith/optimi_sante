import { Link } from 'react-router-dom';
import { ShieldOff, ArrowLeft } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { adminHomeFor } from '../lib/adminUniverses';

/**
 * Accès refusé.
 *
 * Remplace la redirection silencieuse vers l'accueil : renvoyer quelqu'un ailleurs sans
 * explication lui laisse croire à un bug, et il réessaie. Mieux vaut nommer la raison et
 * proposer la sortie — vers son propre espace, pas vers une page générique.
 */
export function AccessDeniedPage() {
  const { user } = useAuth();
  const home = adminHomeFor(user?.role) || '/';

  return (
    <div className="min-h-[70vh] flex items-center justify-center px-6 py-16">
      <div className="max-w-md w-full text-center">
        <div className="w-14 h-14 rounded-2xl bg-amber-50 border border-amber-200 flex items-center justify-center mx-auto mb-6">
          <ShieldOff className="w-7 h-7 text-amber-600" />
        </div>

        <h1 className="text-2xl font-bold text-brand-dark mb-3">
          Cette section ne relève pas de votre périmètre
        </h1>

        <p className="text-slate-600 mb-2">
          L'administration est répartie entre deux métiers : le négoce et la mobilité. Votre
          compte est rattaché à l'un des deux.
        </p>
        <p className="text-sm text-slate-500 mb-8">
          Si vous devez y accéder, demandez à un administrateur de la plateforme de faire
          évoluer votre rattachement.
        </p>

        <Link
          to={home}
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-brand-green text-white text-sm font-medium hover:bg-[#0f3c35] transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Retour à mon espace
        </Link>
      </div>
    </div>
  );
}
