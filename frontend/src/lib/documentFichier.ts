/**
 * Rapatrie un document dans la page, pour l'afficher sans quitter la plateforme.
 *
 * <p><b>Pourquoi on télécharge d'abord, au lieu de pointer un cadre sur l'adresse.</b> L'API
 * répond avec {@code X-Frame-Options: DENY} : le navigateur refuse d'afficher ses réponses
 * dans un {@code <iframe>}. C'est une protection de toute l'API contre le détournement de
 * clics, et la desserrer pour les documents l'affaiblirait partout. Un objet {@code blob:}
 * créé dans la page, lui, n'a pas d'en-têtes HTTP : il appartient à la page et s'affiche dans
 * un cadre sans rien négocier.</p>
 *
 * <p>Pas d'en-tête d'authentification : l'adresse porte un jeton signé et périssable, qui EST
 * l'autorisation (voir {@code DocumentFileResource} côté serveur).</p>
 */
export interface DocumentRapatrie {
  /** Adresse `blob:` à révoquer quand l'aperçu se ferme, sous peine de garder le fichier en mémoire. */
  url: string;
  /** Type réel, lu par le serveur dans les premiers octets du fichier. */
  type: string;
  /** Nom proposé à l'enregistrement. */
  nom: string;
}

export async function rapatrierDocument(lien: string, nomParDefaut: string): Promise<DocumentRapatrie> {
  const reponse = await fetch(lien, { credentials: 'same-origin' });
  if (!reponse.ok) {
    // 403 : jeton périmé ou invalide. 404 : fichier absent du stockage. Les deux se disent
    // pareil au visiteur — il ne peut rien y faire, sinon réessayer ou signaler.
    throw new Error(`Document inaccessible (HTTP ${reponse.status})`);
  }
  const blob = await reponse.blob();
  const type = (reponse.headers.get('content-type') ?? blob.type ?? '').split(';')[0].trim();
  return {
    url: URL.createObjectURL(blob),
    type,
    nom: nomDepuisDisposition(reponse.headers.get('content-disposition')) ?? nomParDefaut,
  };
}

/**
 * Lit le nom de fichier dans {@code Content-Disposition}.
 *
 * <p>Le serveur envoie deux formes : {@code filename*=UTF-8''re%C3%A7u.pdf}, qui préserve les
 * accents, et {@code filename="..."} pour les vieux navigateurs, encodé en RFC 2047 dès qu'un
 * accent s'y trouve — illisible tel quel. On prend donc la forme étoilée d'abord.</p>
 */
function nomDepuisDisposition(entete: string | null): string | null {
  if (!entete) return null;
  const etoile = entete.match(/filename\*\s*=\s*UTF-8''([^;]+)/i);
  if (etoile) {
    try { return decodeURIComponent(etoile[1].trim()); } catch { /* forme simple ci-dessous */ }
  }
  const simple = entete.match(/filename\s*=\s*"([^"]+)"/i);
  return simple && !simple[1].startsWith('=?') ? simple[1] : null;
}

/**
 * Titre de la fenêtre d'aperçu.
 *
 * <p>Le libellé du bouton ne convient pas : c'est une action, pas un nom. Trois boutons de la
 * plateforme s'appellent « Ouvrir », deux « Télécharger » — autant de fenêtres qui
 * s'intituleraient ainsi. Le serveur, lui, envoie le nom du document dans
 * {@code Content-Disposition} ; on le prend, débarrassé de son extension et, le cas échéant,
 * de l'identifiant aléatoire que la clé de stockage place devant.</p>
 */
export function titreDocument(libelle: string, nom: string): string {
  if (nom && nom !== libelle) {
    const propre = nom
      .replace(/\.[a-z0-9]{2,5}$/i, '')
      .replace(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_/i, '')
      // Les soulignés seulement : les tirets appartiennent aux références (OPT-20260829-E7D2).
      .replace(/_+/g, ' ')
      .trim();
    if (propre) return propre.charAt(0).toUpperCase() + propre.slice(1);
  }
  const sansVerbe = libelle
    .replace(/^(ouvrir|voir|télécharger)\s*(le |la |les |l['’]|mon |ma |mes )?/i, '')
    .trim();
  return sansVerbe ? sansVerbe.charAt(0).toUpperCase() + sansVerbe.slice(1) : 'Document';
}

/** Ce qu'un navigateur sait montrer lui-même. Le reste s'enregistre. */
export const estAffichable = (type: string) =>
  type === 'application/pdf' || type.startsWith('image/');

/**
 * Enregistre un document rapatrié, sans quitter la page.
 *
 * <p>La révocation attend un tour : révoquée dans la foulée du clic, l'adresse disparaît
 * avant que Firefox n'ait commencé à lire le fichier, et l'enregistrement échoue en silence.</p>
 */
export function enregistrerDocument(doc: DocumentRapatrie, revoquer = true) {
  const lien = document.createElement('a');
  lien.href = doc.url;
  lien.download = doc.nom;
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  if (revoquer) setTimeout(() => URL.revokeObjectURL(doc.url), 1000);
}
