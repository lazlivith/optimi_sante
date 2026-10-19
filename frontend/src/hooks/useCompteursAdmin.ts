import { useEffect, useState } from 'react';
import type { AdminUniverse } from '../lib/adminUniverses';
import { adminExercerEnFranceService } from '../api/adminExercerEnFranceService';

/** Ce qu'une pastille de la barre latérale peut afficher. */
export interface CompteursAdmin {
  demandesAccompagnement: number;
}

const VIDE: CompteursAdmin = { demandesAccompagnement: 0 };

/**
 * Les nombres affichés en pastille dans la barre latérale.
 *
 * <p><b>Rien n'est demandé si rien ne l'affiche.</b> Le relevé n'est fait que si un des
 * univers visibles porte une entrée à compteur : un administrateur du négoce ne doit pas
 * déclencher une requête pour une pastille qu'il ne verra jamais.</p>
 *
 * <p><b>Un échec ne casse rien.</b> Une pastille absente est un désagrément ; une barre de
 * navigation qui disparaît parce qu'un compteur n'a pas répondu en est un autre, bien pire.
 * L'erreur est donc avalée, et les compteurs restent à zéro — c'est-à-dire invisibles.</p>
 */
export function useCompteursAdmin(universes: AdminUniverse[]): CompteursAdmin {
  const [compteurs, setCompteurs] = useState<CompteursAdmin>(VIDE);

  const concerne = universes.some(u => u.items.some(i => i.compteur));

  useEffect(() => {
    if (!concerne) {
      return;
    }
    let abandonne = false;
    adminExercerEnFranceService.indicateurs()
      .then(i => {
        if (!abandonne) {
          setCompteurs({ demandesAccompagnement: i.demandesATraiter });
        }
      })
      .catch(() => { /* une pastille absente ne doit pas emporter le menu */ });
    return () => { abandonne = true; };
  }, [concerne]);

  return compteurs;
}
