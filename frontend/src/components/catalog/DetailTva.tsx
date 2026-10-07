import { useEffect, useState } from 'react';
import { tvaService, type VentilationPanier } from '../../api/tvaService';
import { useDevise } from '../../context/DeviseContext';

interface Props {
  /** Les lignes du panier, telles que le contexte les tient. */
  items: { id: string; cartQuantity: number; isQuoteOnly?: boolean }[];
  /** Remise déjà accordée, répartie au prorata des lignes par le serveur. */
  remise?: number;
}

/**
 * Le détail de la taxe contenue dans un panier.
 *
 * <p><b>Il n'ajoute rien au total.</b> Les prix sont annoncés TTC : la taxe est déjà dedans,
 * et ce bloc montre ce qu'elle représente. C'est aussi pourquoi il n'est pas présenté comme
 * une ligne de total — l'aligner avec « Livraison » et « Total à payer » laisserait croire
 * qu'il s'y ajoute.</p>
 *
 * <p><b>Le calcul vient du serveur.</b> Le navigateur ne connaît ni la grille des taux ni la
 * règle de prix : les lui livrer reviendrait à afficher le montant qu'il veut bien.</p>
 *
 * <p>N'affiche rien tant que la ventilation n'est pas ouverte dans l'administration : la règle
 * tient à un seul endroit, et l'écran ne décide pas de ce que les documents annoncent.</p>
 */
export function DetailTva({ items, remise }: Props) {
  const { prix } = useDevise();
  const [ventilation, setVentilation] = useState<VentilationPanier | null>(null);

  const lignes = items
    .filter(i => !i.isQuoteOnly)
    .map(i => ({ productId: i.id, quantity: i.cartQuantity }));
  const empreinte = JSON.stringify([lignes, remise ?? 0]);

  useEffect(() => {
    if (lignes.length === 0) {
      setVentilation(null);
      return;
    }
    let abandonne = false;
    tvaService.ventilerPanier(lignes, remise)
      .then(v => { if (!abandonne) setVentilation(v); })
      // Un detail de taxe indisponible ne doit pas empecher de commander : on n'affiche rien.
      .catch(() => { if (!abandonne) setVentilation(null); });
    return () => { abandonne = true; };
    // L'empreinte evite de relancer le calcul a chaque rendu : le tableau de lignes est
    // reconstruit a l'identique, mais ce n'est pas le meme objet.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [empreinte]);

  if (!ventilation?.affichee || ventilation.ventilation.length === 0) return null;

  return (
    <div className="border-t border-slate-100 pt-3 text-xs text-slate-500">
      <div className="flex items-center justify-between">
        <span>Total hors taxes</span>
        <span className="tabular-nums">{prix(ventilation.totalHt)}</span>
      </div>
      {ventilation.ventilation.map(t => (
        <div key={t.taux} className="flex items-center justify-between">
          <span>dont TVA {String(t.taux).replace('.', ',')} %</span>
          <span className="tabular-nums">{prix(t.taxe)}</span>
        </div>
      ))}
      <p className="mt-1.5 text-[11px] leading-relaxed text-slate-400">
        Les prix sont affichés toutes taxes comprises : la taxe est déjà incluse dans le total.
        {remise != null && remise > 0 && ' La remise est répartie entre les taux.'}
      </p>
    </div>
  );
}
