import { useRef, useState } from 'react';
import { AlertTriangle, CheckCircle2, FileSpreadsheet, Loader2, UploadCloud } from 'lucide-react';
import { partnershipService, type RapportDossier } from '../../api/partnershipService';

/**
 * Dossier Excel du partenariat : télécharger le modèle, le remplir, le faire contrôler.
 *
 * <p>Le contrôle est <b>séparé du dépôt</b> : l'établissement fait relire son classeur autant de
 * fois qu'il le souhaite, corrige, recommence. Rien n'est enregistré à cette étape. Découvrir
 * ses fautes après avoir déposé un dossier, c'est un aller-retour par courriel et plusieurs
 * jours perdus.</p>
 *
 * <p>Le rapport est affiché <b>anomalie par anomalie, avec sa case</b> : feuille, ligne, colonne.
 * « Fichier invalide » obligerait à relire trente lignes.</p>
 */
export function DossierExcelPanel() {
  const [rapport, setRapport] = useState<RapportDossier | null>(null);
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [nomFichier, setNomFichier] = useState<string | null>(null);
  const champ = useRef<HTMLInputElement>(null);

  const verifier = async (fichier: File) => {
    setEnCours(true);
    setErreur(null);
    setRapport(null);
    setNomFichier(fichier.name);
    try {
      setRapport(await partnershipService.verifierDossier(fichier));
    } catch (e) {
      console.error('Vérification du dossier impossible', e);
      setErreur("Le contrôle n'a pas pu être effectué. Réessayez dans un instant.");
    } finally {
      setEnCours(false);
    }
  };

  return (
    <section className="bg-white rounded-2xl border border-slate-200 shadow-sm p-8">
      <h2 className="font-bold text-brand-dark mb-2 flex items-center gap-2">
        <FileSpreadsheet className="w-5 h-5 text-brand-green" aria-hidden="true" />
        Dossier de capacités d'accueil
      </h2>
      <p className="text-sm text-slate-600 mb-6 max-w-2xl">
        Ce classeur recense les spécialités et les places que votre établissement peut ouvrir.
        Remplissez les cases en jaune, puis faites-le contrôler ici avant de déposer votre
        demande : vous saurez tout de suite ce qui manque.
      </p>

      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        {/* Un lien et non un bouton : c'est une navigation vers un fichier, et le navigateur
            sait la gérer seul — y compris l'ouvrir dans le tableur installé. */}
        <a
          href={partnershipService.modeleDossierUrl()}
          className="inline-flex items-center justify-center px-5 py-3 border border-brand-green text-brand-green font-bold rounded-xl hover:bg-brand-light transition-colors"
        >
          <FileSpreadsheet className="w-4 h-4 mr-2" aria-hidden="true" />
          Télécharger le modèle (XLSX)
        </a>

        <button
          type="button"
          onClick={() => champ.current?.click()}
          disabled={enCours}
          aria-busy={enCours}
          className="inline-flex items-center justify-center px-5 py-3 bg-brand-dark text-white font-bold rounded-xl hover:bg-slate-800 transition-colors disabled:opacity-60"
        >
          {enCours
            ? <Loader2 className="w-4 h-4 mr-2 animate-spin" aria-hidden="true" />
            : <UploadCloud className="w-4 h-4 mr-2" aria-hidden="true" />}
          {enCours ? 'Contrôle en cours…' : 'Contrôler mon dossier rempli'}
        </button>

        <input
          ref={champ}
          type="file"
          accept=".xlsx"
          className="sr-only"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) verifier(f);
            // Remis à zéro : sans cela, redéposer le MÊME fichier corrigé ne déclenche rien,
            // le champ ne considérant pas que sa valeur a changé.
            e.target.value = '';
          }}
        />
      </div>

      {/* Le résultat du contrôle est annoncé : sans cela, une personne au lecteur d'écran ne
          sait pas que l'analyse est terminée, ni ce qu'elle a donné. */}
      <div role="status" aria-live="polite">
        {erreur && (
          <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-xl p-4">
            {erreur}
          </p>
        )}

        {rapport?.conforme && (
          <div className="text-sm bg-emerald-50 border border-emerald-200 rounded-xl p-4">
            <p className="font-semibold text-emerald-800 flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4" aria-hidden="true" />
              Dossier conforme — {nomFichier}
            </p>
            <p className="text-emerald-700 mt-1">
              {rapport.capacites.length} capacité{rapport.capacites.length > 1 ? 's' : ''} d'accueil
              {' '}déclarée{rapport.capacites.length > 1 ? 's' : ''} pour
              {' '}<strong>{rapport.identite.institutionName}</strong>. Joignez ce fichier à votre
              demande ci-dessous.
            </p>
          </div>
        )}

        {rapport && !rapport.conforme && (
          <div className="text-sm bg-amber-50 border border-amber-200 rounded-xl p-4">
            <p className="font-semibold text-amber-900 flex items-center gap-2 mb-3">
              <AlertTriangle className="w-4 h-4" aria-hidden="true" />
              {rapport.anomalies.length} point{rapport.anomalies.length > 1 ? 's' : ''} à corriger
              {nomFichier ? ` dans ${nomFichier}` : ''}
            </p>
            <ul className="space-y-2">
              {rapport.anomalies.map((a, i) => (
                <li key={i} className="text-amber-900">
                  <span className="font-mono text-xs bg-amber-100 rounded px-1.5 py-0.5 mr-2">
                    {a.feuille} · ligne {a.ligne}
                  </span>
                  <strong>{a.colonne}</strong>
                  {a.valeur ? <span className="text-amber-700"> (« {a.valeur} »)</span> : null}
                  {' — '}{a.probleme}
                </li>
              ))}
            </ul>
            <p className="text-amber-700 mt-3">
              Corrigez ces points dans le classeur, puis relancez le contrôle. Rien n'a été
              enregistré.
            </p>
          </div>
        )}
      </div>
    </section>
  );
}
