import { Check } from 'lucide-react';
import { LIBELLE_ACTEUR, etapeDuParcours, etapesDuParcours } from '../../lib/parcoursDossier';
import type { RegistrationType } from '../../api/enrollmentService';

interface ParcoursDossierProps {
  statut: string;
  /** Le médecin lit « Vous » ; l'administration lit « Médecin ». */
  perspective?: 'medecin' | 'admin';
  /** `complet` sur le détail d'un dossier, `ligne` dans une liste. */
  variante?: 'complet' | 'ligne';
  /** Absent : parcours international, seul existant avant la V60. */
  parcours?: RegistrationType;
}

/**
 * Parcours en 8 étapes d'un dossier de formation.
 *
 * <p>Sur écran large, les étapes s'alignent ; sur téléphone, elles s'empilent, l'étape en cours
 * dépliée avec qui doit agir. Huit pastilles de 40 px côte à côte ne tiennent pas dans 390 px :
 * l'ancien parcours à neuf statuts débordait de l'écran.</p>
 *
 * <p>Un praticien exerçant déjà en France en voit quatre : sans démarche consulaire, les étapes
 * de visa n'ont rien à lui dire. La grille compte ses colonnes d'après la liste reçue.</p>
 */
export function ParcoursDossier({ statut, perspective = 'medecin', variante = 'complet',
                                  parcours }: ParcoursDossierProps) {
  const etapes = etapesDuParcours(parcours);
  const enCours = etapeDuParcours(statut, parcours);
  const clos = enCours < 0;
  const termine = enCours >= etapes.length;
  const pieceDemandee = statut === 'ACTION_REQUIRED';
  const acteurs = LIBELLE_ACTEUR[perspective];

  if (variante === 'ligne') {
    const etape = etapes[Math.min(Math.max(enCours, 0), etapes.length - 1)];
    const progression = clos ? 0 : termine ? 100 : (enCours / etapes.length) * 100;
    return (
      <div className="min-w-[150px] max-w-[200px]">
        <div className="flex items-baseline justify-between gap-2 mb-1.5">
          <span className={`text-xs font-semibold truncate ${
            clos ? 'text-danger' : pieceDemandee ? 'text-warning' : 'text-slate-700'}`}>
            {clos ? 'Hors parcours' : termine ? 'Prêt au départ' : pieceDemandee ? 'Pièce demandée' : etape.titre}
          </span>
          {!clos && (
            <span className="text-[11px] text-slate-400 tabular-nums shrink-0">
              {termine ? `${etapes.length}/${etapes.length}` : `${enCours + 1}/${etapes.length}`}
            </span>
          )}
        </div>
        <div className="h-1.5 rounded-full bg-slate-200 overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ${
              clos ? 'bg-danger' : pieceDemandee ? 'bg-warning' : 'bg-success'}`}
            style={{ width: `${clos ? 100 : Math.max(progression, 6)}%` }}
          />
        </div>
      </div>
    );
  }

  return (
    <ol className={`grid grid-cols-1 gap-2 lg:gap-1.5 ${
        etapes.length === 4 ? 'lg:grid-cols-4' : 'lg:grid-cols-8'}`} aria-label="Parcours du dossier">
      {etapes.map((etape, index) => {
        const faite = !clos && index < enCours;
        const courante = !clos && index === enCours;
        return (
          <li
            key={etape.numero}
            aria-current={courante ? 'step' : undefined}
            className={`flex lg:flex-col items-start gap-3 lg:gap-2 rounded-xl px-3 py-2.5 lg:py-3 border ${
              courante
                ? pieceDemandee ? 'border-warning bg-warning/10' : 'border-brand bg-brand-light'
                : 'border-transparent'
            } ${!courante && !faite ? 'max-lg:hidden' : ''}`}
          >
            <span
              className={`w-8 h-8 shrink-0 rounded-full flex items-center justify-center text-sm font-bold ${
                faite ? 'bg-success text-white'
                  : courante ? pieceDemandee ? 'bg-warning text-white' : 'bg-brand text-white'
                    : 'bg-slate-100 text-slate-400'}`}
            >
              {faite ? <Check className="w-4 h-4" aria-hidden="true" /> : etape.numero}
            </span>
            <span className="min-w-0">
              <span className={`block text-sm lg:text-xs font-semibold leading-tight ${
                faite || courante ? 'text-brand-dark' : 'text-slate-400'}`}>
                {etape.titre}
                {faite && <span className="sr-only"> (terminée)</span>}
              </span>
              <span className={`block text-xs lg:text-[11px] mt-0.5 leading-snug ${
                courante ? 'text-slate-600' : 'text-slate-400'}`}>
                {etape.detail}
              </span>
              {courante && (
                <span className="block text-[11px] font-semibold text-brand mt-1">
                  {pieceDemandee
                    ? `${acteurs.medecin} : pièce à fournir`
                    : etape.acteurs.map((a) => acteurs[a]).join(' · ')}
                </span>
              )}
            </span>
          </li>
        );
      })}
      {/* Sur téléphone, seules les étapes faites et en cours sont listées : on dit ce qui reste. */}
      {!clos && !termine && enCours < etapes.length - 1 && (
        <li className="lg:hidden px-3 text-xs text-slate-500">
          Encore {etapes.length - enCours - 1} étape{etapes.length - enCours - 1 > 1 ? 's' : ''} :{' '}
          {etapes.slice(enCours + 1).map((e) => e.titre).join(', ')}.
        </li>
      )}
    </ol>
  );
}
