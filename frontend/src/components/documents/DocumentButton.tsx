import { useEffect, useRef, useState } from 'react';
import { FileText, Loader2, AlertCircle, ExternalLink } from 'lucide-react';

type Etat = 'repos' | 'preparation' | 'ouvert' | 'indisponible' | 'echec';

export interface DocumentButtonProps {
  /** Ce que le bouton annonce, sans le format : « Reçu REC-2026-0007 ». */
  libelle: string;

  /**
   * Comment obtenir l'adresse du document.
   *
   * <p>Une fonction, et non un couple (type, identifiant) : chaque espace a son propre point
   * d'entrée, qui vérifie ses propres droits — le coffre-fort du médecin, les relevés du
   * partenaire, les conventions de l'administration. Le composant ne doit connaître aucun
   * d'eux.</p>
   *
   * <p>Rendre {@code null} signifie « le document n'existe pas encore » : ce n'est pas une
   * erreur, et le message doit le dire autrement.</p>
   */
  obtenirLien: () => Promise<string | null>;

  /** Format annoncé au visiteur. Il doit savoir ce qui va s'ouvrir avant de cliquer. */
  format?: 'PDF' | 'XLSX' | 'CSV';

  /** Ce qu'on dit quand le document n'est pas encore produit. */
  messageIndisponible?: string;

  /** `lien` pour une ligne de tableau, `bouton` pour une action mise en avant. */
  variante?: 'lien' | 'bouton';

  className?: string;
}

/**
 * Ouvre un document, en le disant.
 *
 * <p>Ce composant remplace huit copies du même enchaînement — appeler un service, puis
 * {@code window.open} — dispersées dans les espaces client, médecin, partenaire et
 * administration. Chacune échouait en silence à sa façon.</p>
 *
 * <p><b>Trois choses qu'aucune de ces copies ne faisait :</b></p>
 *
 * <ul>
 *   <li><b>Annoncer ce qui va se passer.</b> Le format et l'ouverture dans un nouvel onglet
 *       figurent dans le nom accessible du bouton : une personne au lecteur d'écran ne
 *       découvre plus un onglet surgi de nulle part (WCAG 3.2.5).</li>
 *   <li><b>Annoncer que c'est fini.</b> Sans zone {@code aria-live}, la fin de préparation est
 *       invisible à qui ne voit pas le tourniquet — et le document s'ouvre sans prévenir.</li>
 *   <li><b>Dire ce qui a échoué.</b> « Non disponible » ne distingue pas un document pas encore
 *       produit d'un échec de génération. Les deux appellent des gestes différents : attendre,
 *       ou signaler.</li>
 * </ul>
 */
export function DocumentButton({
  libelle,
  obtenirLien,
  format = 'PDF',
  messageIndisponible = "Ce document n'est pas encore disponible.",
  variante = 'lien',
  className = '',
}: DocumentButtonProps) {
  const [etat, setEtat] = useState<Etat>('repos');
  const [annonce, setAnnonce] = useState('');
  const monte = useRef(true);

  useEffect(() => () => { monte.current = false; }, []);

  const ouvrir = async () => {
    if (etat === 'preparation') return;
    setEtat('preparation');
    setAnnonce(`Préparation de ${libelle}…`);

    // L'onglet est ouvert MAINTENANT, tant que le clic est encore la cause directe de
    // l'action. Ouvert après l'attente réseau, il est bloqué comme une fenêtre surgissante —
    // systématiquement sur Safari, au gré d'heuristiques ailleurs. C'est le défaut que les
    // huit copies précédentes portaient toutes.
    const onglet = window.open('', '_blank', 'noopener,noreferrer');

    try {
      const lien = await obtenirLien();

      if (!lien) {
        onglet?.close();
        if (!monte.current) return;
        setEtat('indisponible');
        setAnnonce(messageIndisponible);
        return;
      }

      if (onglet) {
        onglet.location.href = lien;
      } else {
        // Onglet refusé par le navigateur : on navigue dans la page courante plutôt que de
        // laisser le visiteur devant un bouton qui ne fait rien.
        window.location.href = lien;
      }
      if (!monte.current) return;
      setEtat('ouvert');
      setAnnonce(`${libelle} ouvert dans un nouvel onglet.`);
    } catch (erreur) {
      onglet?.close();
      console.error(`Échec de l'ouverture du document « ${libelle} »`, erreur);
      if (!monte.current) return;
      setEtat('echec');
      setAnnonce(`${libelle} n'a pas pu être ouvert. Réessayez, ou signalez-le si cela persiste.`);
    }
  };

  const enCours = etat === 'preparation';
  const enDefaut = etat === 'echec' || etat === 'indisponible';

  const styleBase = variante === 'bouton'
    ? 'inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold transition-colors'
    : 'inline-flex items-center gap-1.5 text-xs font-medium transition-colors';

  const styleEtat = enDefaut
    ? (variante === 'bouton'
        ? 'bg-amber-50 text-amber-800 border border-amber-200'
        : 'text-amber-700')
    : (variante === 'bouton'
        ? 'bg-brand text-white hover:bg-brand/90 disabled:opacity-60'
        : 'text-brand hover:text-brand-fonce hover:underline disabled:opacity-60');

  return (
    <>
      <button
        type="button"
        onClick={ouvrir}
        disabled={enCours}
        aria-busy={enCours}
        className={`${styleBase} ${styleEtat} ${className}`}
      >
        {enCours
          ? <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
          : enDefaut
            ? <AlertCircle className="w-3.5 h-3.5" aria-hidden="true" />
            : variante === 'bouton'
              ? <FileText className="w-4 h-4" aria-hidden="true" />
              : <ExternalLink className="w-3 h-3" aria-hidden="true" />}

        {enCours ? 'Préparation…' : libelle}

        {/* Le format et l'ouverture dans un nouvel onglet font partie du nom accessible :
            visibles pour tous, mais sans alourdir la ligne d'un tableau. */}
        {!enCours && (
          <span className={variante === 'bouton' ? '' : 'sr-only'}>
            {variante === 'bouton' ? ` (${format})` : ` (${format}, nouvel onglet)`}
          </span>
        )}
      </button>

      {/* Sans cette zone, la fin de préparation — et l'échec — passent inaperçus de qui ne
          voit pas l'écran. « polite » : l'annonce attend une pause, elle n'interrompt pas. */}
      <span role="status" aria-live="polite" className="sr-only">{annonce}</span>

      {enDefaut && (
        <span className="block text-xs text-amber-700 mt-1">
          {etat === 'indisponible'
            ? messageIndisponible
            : "L'ouverture a échoué. Réessayez dans un instant."}
        </span>
      )}
    </>
  );
}
