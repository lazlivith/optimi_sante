import { Clock, FileText, Lock, ShieldCheck } from 'lucide-react';
import type { VaultDossier, VaultEntry } from '../../api/officialDocumentService';
import { vaultService } from '../../api/vaultService';
import { DocumentButton } from '../documents/DocumentButton';

const ETAT: Record<VaultEntry['state'], { libelle: string; classe: string; Icone: typeof Lock }> = {
  AVAILABLE: { libelle: 'Disponible', classe: 'bg-success/10 text-success', Icone: ShieldCheck },
  LOCKED: { libelle: 'Verrouillé', classe: 'bg-slate-100 text-slate-600', Icone: Lock },
  UPCOMING: { libelle: 'À venir', classe: 'bg-brand-light text-brand', Icone: Clock },
};

/**
 * Documents d'un dossier, tels que le médecin peut les ouvrir.
 *
 * <p>Un document verrouillé reste listé avec ce qui le débloquera (« après le règlement de
 * l'acompte ») : le médecin sait ce qui l'attend avant sa démarche de visa, au lieu de découvrir
 * un coffre vide. Le verrou est appliqué par le serveur ; l'écran ne fait que l'expliquer.</p>
 */
export function CoffreFortDossier({ dossier }: { dossier: VaultDossier }) {
  if (dossier.entries.length === 0) {
    return <p className="px-5 py-6 text-sm text-slate-500">Aucun document pour ce dossier.</p>;
  }

  return (
    <div role="table" aria-label={`Documents du dossier ${dossier.trainingTitle}`} className="text-sm">
      <div role="row" className="hidden md:grid grid-cols-[minmax(0,2fr)_minmax(0,1.3fr)_7rem_8rem_8rem] gap-4 px-5 py-2.5 bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
        <span role="columnheader">Document</span>
        <span role="columnheader">Émetteur</span>
        <span role="columnheader">Date</span>
        <span role="columnheader">Statut</span>
        <span role="columnheader" className="text-right">Action</span>
      </div>
      <div className="divide-y divide-slate-100">
        {dossier.entries.map((entree) => {
          const { libelle, classe, Icone } = ETAT[entree.state];
          return (
            <div
              key={entree.key}
              role="row"
              className="grid grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[minmax(0,2fr)_minmax(0,1.3fr)_7rem_8rem_8rem] gap-x-4 gap-y-1.5 px-5 py-3.5 items-start"
            >
              <div role="cell" className="min-w-0 flex gap-3">
                <FileText className={`w-5 h-5 shrink-0 mt-0.5 ${entree.state === 'AVAILABLE' ? 'text-brand' : 'text-slate-300'}`} aria-hidden="true" />
                <div className="min-w-0">
                  <p className={`font-semibold ${entree.state === 'AVAILABLE' ? 'text-brand-dark' : 'text-slate-600'}`}>{entree.title}</p>
                  <p className="text-xs text-slate-400">{entree.categoryLabel}</p>
                  {entree.reason && <p className="text-xs text-slate-500 mt-1">{entree.reason}</p>}
                </div>
              </div>
              <div role="cell" className="md:hidden row-span-2 flex flex-col items-end gap-2">
                <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold ${classe}`}>
                  <Icone className="w-3 h-3" aria-hidden="true" /> {libelle}
                </span>
                <Action entree={entree} />
              </div>
              <div role="cell" className="text-slate-600 text-xs md:text-sm pl-8 md:pl-0">
                {entree.issuerLabel}
              </div>
              <div role="cell" className="hidden md:block text-slate-500 tabular-nums">
                {entree.date ? new Date(entree.date).toLocaleDateString('fr-FR') : '—'}
              </div>
              <div role="cell" className="hidden md:block">
                <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold ${classe}`}>
                  <Icone className="w-3 h-3" aria-hidden="true" /> {libelle}
                </span>
              </div>
              <div role="cell" className="hidden md:flex justify-end">
                <Action entree={entree} />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Action({ entree }: { entree: VaultEntry }) {
  if (entree.state === 'AVAILABLE' && entree.downloadType && entree.downloadId) {
    return (
      <DocumentButton
        libelle="Ouvrir"
        obtenirLien={() => vaultService.getPresignedUrl(entree.downloadType!, entree.downloadId!)}
      />
    );
  }
  if (entree.state === 'LOCKED') {
    return <Lock className="w-4 h-4 text-slate-400" aria-label="Verrouillé" />;
  }
  return <span className="text-slate-300" aria-hidden="true">—</span>;
}
