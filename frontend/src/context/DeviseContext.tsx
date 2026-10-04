import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { devisesService } from '../api/devisesService';
import { EURO, formaterPrix, type DeviseAffichage } from '../lib/devises';
import { useAuth } from './AuthContext';

const CLE_CHOIX = 'devise';

interface DeviseContextType {
  /** La devise dans laquelle les prix sont affichés. */
  devise: DeviseAffichage;
  /** Les devises que le visiteur peut choisir. Toujours au moins l'euro. */
  disponibles: DeviseAffichage[];
  choisir: (code: string) => void;
  /** Un prix en euros, écrit dans la devise courante. */
  prix: (montantEuros: number) => string;
}

const DeviseContext = createContext<DeviseContextType | undefined>(undefined);

/**
 * La devise d'affichage de la boutique.
 *
 * <p><b>Le visiteur choisit, le pays propose.</b> Un praticien congolais doit voir un prix en
 * francs CFA sans rien demander, et pouvoir repasser à l'euro s'il le préfère — il peut être en
 * déplacement, ou payer depuis un compte européen. Le pays décide donc du <i>premier</i>
 * affichage ; un choix explicite l'emporte et se retient.</p>
 *
 * <p><b>L'administration n'en dépend pas.</b> Les écrans d'administration restent en euros :
 * c'est la devise de référence, celle dans laquelle la comptabilité est tenue et dans laquelle
 * les agrégats s'additionnent. Mêler les deux ferait des chiffres d'affaires sans signification.</p>
 */
export function DeviseProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [disponibles, setDisponibles] = useState<DeviseAffichage[]>([EURO]);
  const [code, setCode] = useState<string>(() => {
    try {
      return localStorage.getItem(CLE_CHOIX) ?? '';
    } catch {
      // Navigation privee, stockage bloque : le choix ne se retient pas, et c'est tout.
      return '';
    }
  });

  useEffect(() => {
    const pays = user?.countryOfResidence;
    devisesService.actives(pays && pays.length === 2 ? pays.toUpperCase() : undefined)
      .then(liste => {
        const avecEuro = liste.length > 0 ? liste : [EURO];
        setDisponibles(avecEuro);
        // Aucun choix explicite : on suit la suggestion du pays. Un choix deja fait n'est
        // jamais ecrase — y compris par une connexion ulterieure.
        setCode(precedent => {
          if (precedent && avecEuro.some(d => d.code === precedent)) return precedent;
          return avecEuro.find(d => d.suggeree)?.code ?? EURO.code;
        });
      })
      .catch(() => setDisponibles([EURO]));
  }, [user?.countryOfResidence]);

  const devise = useMemo(
    () => disponibles.find(d => d.code === code) ?? EURO,
    [disponibles, code],
  );

  const choisir = useCallback((nouveau: string) => {
    setCode(nouveau);
    try {
      localStorage.setItem(CLE_CHOIX, nouveau);
    } catch {
      // Sans persistance, le choix vaut pour la session : preferable a une erreur.
    }
  }, []);

  const prix = useCallback(
    (montantEuros: number) => formaterPrix(montantEuros, devise),
    [devise],
  );

  const valeur = useMemo(
    () => ({ devise, disponibles, choisir, prix }),
    [devise, disponibles, choisir, prix],
  );

  return <DeviseContext.Provider value={valeur}>{children}</DeviseContext.Provider>;
}

/**
 * La devise courante et le formateur de prix.
 *
 * <p>Hors du fournisseur — dans un écran d'administration, par exemple — rend l'euro. Un prix
 * doit s'afficher quoi qu'il arrive : lever une erreur ici casserait une page entière pour un
 * symbole monétaire.</p>
 */
export function useDevise(): DeviseContextType {
  const contexte = useContext(DeviseContext);
  if (contexte) return contexte;
  return {
    devise: EURO,
    disponibles: [EURO],
    choisir: () => {},
    prix: (montantEuros: number) => formaterPrix(montantEuros, EURO),
  };
}
