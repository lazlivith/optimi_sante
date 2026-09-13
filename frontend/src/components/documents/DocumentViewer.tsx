import { useEffect, useId, useRef } from 'react';
import { Download, FileText, X } from 'lucide-react';
import { enregistrerDocument, type DocumentRapatrie } from '../../lib/documentFichier';

/**
 * Aperçu d'un document par-dessus la page, sans la quitter.
 *
 * <p>Avant, un reçu ou un devis s'ouvrait dans un onglet — ou à la place de la page quand le
 * navigateur refusait l'onglet —, et le visiteur devait revenir en arrière pour retrouver la
 * plateforme, parfois en perdant sa position ou le filtre qu'il avait posé. Ici, fermer
 * l'aperçu ramène exactement là où il était.</p>
 *
 * <p><b>Ce qu'une fenêtre modale doit faire, et qu'on oublie :</b></p>
 * <ul>
 *   <li>se fermer à la touche Échap et d'un clic sur le fond ;</li>
 *   <li>garder le focus clavier à l'intérieur tant qu'elle est ouverte — sinon la tabulation
 *       parcourt la page cachée dessous, invisible ;</li>
 *   <li>rendre le focus au bouton qui l'a ouverte — sinon, au clavier, on repart du haut de la
 *       page ;</li>
 *   <li>bloquer le défilement de la page dessous, qui défilerait sous le doigt sur téléphone.</li>
 * </ul>
 */
export function DocumentViewer({ libelle, doc, onFermer, retour }: {
  libelle: string;
  doc: DocumentRapatrie;
  onFermer: () => void;
  /**
   * Élément qui reprend le focus à la fermeture. On ne peut pas le lire au montage : le
   * bouton qui a demandé le document est désactivé pendant la préparation, et un élément
   * désactivé perd le focus — au montage, il est déjà retombé sur la page.
   */
  retour?: HTMLElement | null;
}) {
  const idTitre = useId();
  const fenetre = useRef<HTMLDivElement>(null);
  const boutonFermer = useRef<HTMLButtonElement>(null);
  // La fonction de fermeture passe par une référence, et l'effet ne s'exécute qu'une fois.
  // Placée dans ses dépendances, une fonction recréée à chaque rendu relancerait l'effet : il
  // mémoriserait alors comme « élément d'avant » le bouton Fermer lui-même, et le focus ne
  // reviendrait jamais au bouton qui a ouvert l'aperçu.
  const fermer = useRef(onFermer);
  useEffect(() => { fermer.current = onFermer; }, [onFermer]);
  const retourInitial = useRef(retour);

  useEffect(() => {
    const avant = retourInitial.current ?? (document.activeElement as HTMLElement | null);
    const defilement = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    boutonFermer.current?.focus();

    const auClavier = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.preventDefault(); fermer.current(); return; }
      if (e.key !== 'Tab' || !fenetre.current) return;
      const cibles = fenetre.current.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), iframe, [tabindex]:not([tabindex="-1"])');
      if (cibles.length === 0) return;
      const premier = cibles[0];
      const dernier = cibles[cibles.length - 1];
      if (e.shiftKey && document.activeElement === premier) { e.preventDefault(); dernier.focus(); }
      else if (!e.shiftKey && document.activeElement === dernier) { e.preventDefault(); premier.focus(); }
    };
    document.addEventListener('keydown', auClavier);

    return () => {
      document.removeEventListener('keydown', auClavier);
      document.body.style.overflow = defilement;
      avant?.focus?.();
    };
  }, []);

  const estPdf = doc.type === 'application/pdf';
  // Android n'affiche pas un PDF dans un cadre : il propose de le télécharger, et le cadre reste
  // blanc. `pdfViewerEnabled` le dit sans deviner l'appareil ; absent (vieux navigateur), on tente.
  const pdfLisible = (navigator as Navigator & { pdfViewerEnabled?: boolean }).pdfViewerEnabled !== false;

  return (
    <div
      className="fixed inset-0 z-[70] flex items-stretch justify-center bg-brand-dark/60 backdrop-blur-sm sm:p-6 lg:p-10"
      // Le fond ferme l'aperçu ; un clic DANS la fenêtre ne remonte pas jusqu'ici.
      onMouseDown={(e) => { if (e.target === e.currentTarget) onFermer(); }}
    >
      <div
        ref={fenetre}
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitre}
        className="flex h-full w-full max-w-5xl flex-col overflow-hidden bg-white shadow-2xl sm:rounded-2xl"
      >
        <div className="flex items-center gap-3 border-b border-slate-200 px-4 py-3 sm:px-5">
          <FileText className="h-5 w-5 shrink-0 text-brand" aria-hidden="true" />
          <h2 id={idTitre} className="min-w-0 flex-1 truncate text-base font-bold text-brand-dark">
            {libelle}
          </h2>
          <button
            type="button"
            onClick={() => enregistrerDocument(doc, false)}
            className="inline-flex h-10 items-center gap-2 rounded-lg bg-brand px-3 text-sm font-semibold text-white transition-colors hover:bg-brand-fonce sm:px-4"
          >
            <Download className="h-4 w-4" aria-hidden="true" />
            <span className="hidden sm:inline">Télécharger</span>
            <span className="sr-only sm:hidden">Télécharger</span>
          </button>
          <button
            ref={boutonFermer}
            type="button"
            onClick={onFermer}
            className="inline-flex h-10 w-10 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-100 hover:text-brand-dark"
          >
            <X className="h-5 w-5" aria-hidden="true" />
            <span className="sr-only">Fermer l'aperçu</span>
          </button>
        </div>

        <div className="relative min-h-0 flex-1 bg-slate-100">
          {estPdf && pdfLisible && (
            <iframe src={doc.url} title={libelle} className="h-full w-full border-0" />
          )}

          {!estPdf && (
            <div className="flex h-full items-center justify-center overflow-auto p-4">
              <img src={doc.url} alt={libelle} className="max-h-full max-w-full object-contain" />
            </div>
          )}

          {estPdf && !pdfLisible && (
            <div className="flex h-full flex-col items-center justify-center gap-4 p-8 text-center">
              <p className="max-w-sm text-slate-700">
                Cet appareil n'affiche pas les PDF directement. Téléchargez le document pour le
                consulter — vous restez sur la plateforme.
              </p>
              <button
                type="button"
                onClick={() => enregistrerDocument(doc, false)}
                className="inline-flex items-center gap-2 rounded-xl bg-brand px-5 py-3 font-bold text-white transition-colors hover:bg-brand-fonce"
              >
                <Download className="h-4 w-4" aria-hidden="true" /> Télécharger le PDF
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
