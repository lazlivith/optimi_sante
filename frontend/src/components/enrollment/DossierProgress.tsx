import { CheckCircle2, Clock, Inbox } from 'lucide-react';
import type { DossierSummary } from '../../api/documentRequestService';

/**
 * Avancement du dossier de pièces, partagé par l'écran d'administration et celui du médecin.
 *
 * Répond à la seule question que se posent les deux : « que reste-t-il à fournir ? ». Sans ce
 * bandeau, il faut compter les lignes du tableau pour le savoir.
 */
export function DossierProgress({ summary }: { summary: DossierSummary }) {
  const { total, accepted, awaitingDoctor, awaitingReview, complete } = summary;
  const progression = total > 0 ? (accepted / total) * 100 : 0;

  if (total === 0) {
    return (
      <p className="text-sm text-slate-500">
        Aucune pièce réclamée pour le moment.
      </p>
    );
  }

  return (
    <div>
      <div className="flex items-baseline justify-between gap-3 mb-2">
        <span className="text-sm font-semibold text-brand-dark">
          {accepted} pièce{accepted > 1 ? 's' : ''} validée{accepted > 1 ? 's' : ''} sur {total}
        </span>
        {complete && (
          <span className="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-700">
            <CheckCircle2 className="w-4 h-4" /> Dossier complet
          </span>
        )}
      </div>

      <div className="h-2 rounded-full bg-slate-200 overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-500 ${complete ? 'bg-emerald-500' : 'bg-brand'}`}
          style={{ width: `${progression}%` }}
        />
      </div>

      {/* Les deux compteurs disent qui doit agir — l'information qui manque le plus souvent
          quand un dossier semble « bloqué ». */}
      <div className="flex flex-wrap gap-x-5 gap-y-1 mt-3 text-xs">
        {awaitingDoctor > 0 && (
          <span className="inline-flex items-center gap-1.5 text-amber-700 font-medium">
            <Inbox className="w-3.5 h-3.5" />
            {awaitingDoctor} en attente du médecin
          </span>
        )}
        {awaitingReview > 0 && (
          <span className="inline-flex items-center gap-1.5 text-blue-700 font-medium">
            <Clock className="w-3.5 h-3.5" />
            {awaitingReview} à vérifier
          </span>
        )}
      </div>
    </div>
  );
}
