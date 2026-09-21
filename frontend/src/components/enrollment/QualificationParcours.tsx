import { Globe2, Hospital } from 'lucide-react';
import type { RegistrationType } from '../../api/enrollmentService';

interface QualificationParcoursProps {
  parcours: RegistrationType;
  onParcoursChange: (parcours: RegistrationType) => void;
  rpps: string;
  onRppsChange: (rpps: string) => void;
}

/**
 * Question d'orientation posée avant toute autre : où le médecin exerce-t-il ?
 *
 * <p>Tout le reste du dossier en découle — les étapes, les pièces, l'échéancier et le contrat
 * signé. La poser plus tard obligerait à revenir sur des choix déjà faits ; la poser ici coûte
 * un clic et évite de demander un visa à quelqu'un qui habite Rennes.</p>
 *
 * <p>Le numéro RPPS n'apparaît que pour le parcours France : il n'a pas de sens pour un
 * praticien qui n'est pas encore inscrit à l'Ordre en France.</p>
 */
export function QualificationParcours({
  parcours, onParcoursChange, rpps, onRppsChange,
}: QualificationParcoursProps) {
  const france = parcours === 'LOCAL_FRANCE';

  const choix = [
    {
      valeur: 'INTERNATIONAL_VISA' as const,
      icone: Globe2,
      titre: 'Je réside hors de France',
      detail: 'Accompagnement complet : formation, visa et logistique du séjour.',
    },
    {
      valeur: 'LOCAL_FRANCE' as const,
      icone: Hospital,
      titre: "Je réside et j'exerce déjà en France",
      detail: 'Parcours direct, sans démarche consulaire. Métropole et DROM-COM.',
    },
  ];

  return (
    <fieldset className="space-y-3">
      <legend className="text-sm font-semibold text-slate-900 mb-3">
        Votre situation
        <span className="block mt-1 text-xs font-normal text-slate-500">
          Elle détermine les étapes de votre dossier et les pièces à fournir.
        </span>
      </legend>

      <div className="grid gap-3 sm:grid-cols-2">
        {choix.map(({ valeur, icone: Icone, titre, detail }) => {
          const actif = parcours === valeur;
          return (
            <label
              key={valeur}
              className={`flex cursor-pointer gap-3 rounded-xl border-2 p-4 transition-all ${
                actif ? 'border-brand bg-brand-light' : 'border-slate-200 hover:border-slate-300'}`}
            >
              <input
                type="radio" name="parcours" value={valeur} checked={actif}
                onChange={() => onParcoursChange(valeur)}
                className="mt-1 h-4 w-4 shrink-0 accent-brand"
              />
              <span className="min-w-0">
                <span className="flex items-center gap-2 text-sm font-semibold text-brand-dark">
                  <Icone className="h-4 w-4 shrink-0" aria-hidden="true" />
                  {titre}
                </span>
                <span className="mt-1 block text-xs leading-snug text-slate-600">{detail}</span>
              </span>
            </label>
          );
        })}
      </div>

      {france && (
        <div className="rounded-xl border border-brand/30 bg-brand-light/40 p-4">
          <label htmlFor="rpps" className="block text-sm font-medium text-slate-800">
            Numéro RPPS ou ADELI
          </label>
          <p className="mt-1 text-xs text-slate-600">
            Il atteste de votre inscription à l'Ordre : 11 chiffres pour un RPPS, 9 pour un ADELI.
          </p>
          <input
            id="rpps" type="text" inputMode="numeric" value={rpps} maxLength={11}
            onChange={(e) => onRppsChange(e.target.value.replace(/\D/g, ''))}
            placeholder="10001234567"
            aria-describedby="rpps-aide"
            className="mt-2 w-full max-w-xs rounded-lg border border-slate-300 p-2.5 font-mono text-sm tabular-nums focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
          />
          <p id="rpps-aide" className="mt-1.5 text-xs text-slate-500">
            {rpps.length === 0
              ? 'Obligatoire pour ce parcours.'
              : rpps.length === 9 || rpps.length === 11
                ? `Format reconnu : ${rpps.length === 11 ? 'RPPS' : 'ADELI'}.`
                : `${rpps.length} chiffre${rpps.length > 1 ? 's' : ''} saisi${rpps.length > 1 ? 's' : ''} — il en faut 9 ou 11.`}
          </p>
        </div>
      )}
    </fieldset>
  );
}

/** Vrai quand la qualification suffit pour ouvrir un dossier. */
export function qualificationComplete(parcours: RegistrationType, rpps: string): boolean {
  return parcours !== 'LOCAL_FRANCE' || rpps.length === 9 || rpps.length === 11;
}
