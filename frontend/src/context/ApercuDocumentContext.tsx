import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { DocumentViewer } from '../components/documents/DocumentViewer';
import {
  enregistrerDocument, estAffichable, rapatrierDocument, titreDocument, type DocumentRapatrie,
} from '../lib/documentFichier';

/** Ce qui est arrivé au document demandé. Le bouton appelant s'en sert pour son message. */
export type IssueDocument = 'affiche' | 'enregistre' | 'indisponible' | 'echec';

interface ContexteApercu {
  /**
   * Obtient un document et le montre dans la page — ou l'enregistre, si le navigateur ne sait
   * pas l'afficher (un classeur Excel, par exemple). Dans aucun cas on ne quitte la plateforme.
   *
   * @param obtenirLien rend `null` quand le document n'existe pas encore.
   */
  ouvrir: (libelle: string, obtenirLien: () => Promise<string | null>) => Promise<IssueDocument>;
}

const Contexte = createContext<ContexteApercu | null>(null);

/**
 * Un seul aperçu pour toute l'application.
 *
 * <p>Monté une fois, près de la racine : chaque écran qui ouvre un document — reçus, devis,
 * conventions, coffre-fort du médecin, brochures — appelle {@code ouvrir} au lieu de gérer sa
 * propre fenêtre. Seize écrans ne portent donc pas seize copies d'une modale, avec seize façons
 * d'oublier la touche Échap.</p>
 */
export function ApercuDocumentProvider({ children }: { children: ReactNode }) {
  const [apercu, setApercu] = useState<
    { libelle: string; doc: DocumentRapatrie; retour: HTMLElement | null } | null>(null);

  const ouvrir = useCallback(async (libelle: string, obtenirLien: () => Promise<string | null>) => {
    // Lu MAINTENANT, pendant le clic : c'est encore le bouton qui a le focus. Après l'attente
    // réseau, il est désactivé et le focus est retombé sur la page.
    const retour = document.activeElement as HTMLElement | null;
    try {
      const lien = await obtenirLien();
      if (!lien) return 'indisponible';
      const doc = await rapatrierDocument(lien, libelle);
      if (estAffichable(doc.type)) {
        setApercu((precedent) => {
          if (precedent) URL.revokeObjectURL(precedent.doc.url);
          return { libelle: titreDocument(libelle, doc.nom), doc, retour };
        });
        return 'affiche';
      }
      enregistrerDocument(doc);
      return 'enregistre';
    } catch (erreur) {
      console.error(`Échec de l'ouverture du document « ${libelle} »`, erreur);
      return 'echec';
    }
  }, []);

  const fermer = useCallback(() => {
    setApercu((courant) => {
      // Sans révocation, chaque document consulté resterait en mémoire jusqu'au rechargement.
      if (courant) URL.revokeObjectURL(courant.doc.url);
      return null;
    });
  }, []);

  return (
    <Contexte.Provider value={{ ouvrir }}>
      {children}
      {apercu && (
        <DocumentViewer
          libelle={apercu.libelle}
          doc={apercu.doc}
          retour={apercu.retour}
          onFermer={fermer}
        />
      )}
    </Contexte.Provider>
  );
}

export function useApercuDocument(): ContexteApercu {
  const c = useContext(Contexte);
  if (!c) throw new Error('useApercuDocument doit être utilisé sous <ApercuDocumentProvider>');
  return c;
}
