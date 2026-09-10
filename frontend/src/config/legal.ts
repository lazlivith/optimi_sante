/**
 * Identité légale de l'éditeur, source unique des pages juridiques.
 *
 * <p>Toutes les valeurs sont surchargeables par variable d'environnement (`VITE_LEGAL_*`),
 * comme demandé : les identifiants réglementaires ne sont pas encore délivrés, et une
 * information légale ne doit pas être figée dans le code pour être corrigée par un
 * redéploiement applicatif.</p>
 *
 * <p><b>Les valeurs manquantes ne sont pas masquées.</b> Une mention légale incomplète est une
 * infraction (LCEN, art. 6-III) ; une mention légale incomplète <i>et silencieuse</i> est une
 * infraction qu'on ne verra jamais. Les champs non renseignés s'affichent donc comme
 * « à compléter » en évidence, pour qu'on ne puisse pas mettre le site en ligne sans les voir.</p>
 */

const env = import.meta.env;

/** Valeur d'environnement, ou `null` si elle n'est pas renseignée. */
const lire = (valeur: unknown): string | null => {
  const v = typeof valeur === 'string' ? valeur.trim() : '';
  return v.length > 0 ? v : null;
};

export const LEGAL = {
  /** Dénomination sociale. */
  raisonSociale: 'OPTIMI SANTÉ',

  formeJuridique: 'Société par Actions Simplifiée (SAS)',

  /** Directeur de la publication au sens de la LCEN. */
  directeurPublication: 'Livith Désiré Boungou-Laz',

  objetSocial:
    "Intermédiaire à valeur ajoutée : ingénierie de formation médicale continue, négoce "
    + "d'équipements de santé et organisation de la mobilité médicale internationale.",

  /** Adresse complète du siège. Une ville seule ne satisfait pas l'obligation légale. */
  adresseSiege: lire(env.VITE_LEGAL_ADRESSE_SIEGE),
  villeSiege: lire(env.VITE_LEGAL_VILLE_SIEGE) ?? 'Bordeaux, France',

  siren: lire(env.VITE_LEGAL_SIREN),
  siret: lire(env.VITE_LEGAL_SIRET),
  rcs: lire(env.VITE_LEGAL_RCS),
  tvaIntracom: lire(env.VITE_LEGAL_TVA),
  capitalSocial: lire(env.VITE_LEGAL_CAPITAL),

  emailContact: lire(env.VITE_LEGAL_EMAIL_CONTACT) ?? 'contact@optimisante.com',
  emailDpo: lire(env.VITE_LEGAL_EMAIL_DPO) ?? 'dpo@optimisante.com',
  telephone: lire(env.VITE_LEGAL_TELEPHONE),

  /**
   * Hébergeur : la loi impose de nommer **celui qui héberge réellement**, avec sa raison
   * sociale, son adresse et son téléphone. « AWS ou OVHcloud ou Scaleway » n'est pas une
   * mention valable — il faut choisir.
   */
  hebergeurNom: lire(env.VITE_LEGAL_HEBERGEUR_NOM),
  hebergeurAdresse: lire(env.VITE_LEGAL_HEBERGEUR_ADRESSE),
  hebergeurTelephone: lire(env.VITE_LEGAL_HEBERGEUR_TEL),

  /** Organisme de médiation de la consommation — obligatoire pour la vente aux particuliers. */
  mediateurNom: lire(env.VITE_LEGAL_MEDIATEUR_NOM),
  mediateurSite: lire(env.VITE_LEGAL_MEDIATEUR_SITE),

  dateMaj: '9 septembre 2026',
} as const;

/**
 * Conditions financières du parcours de mobilité, telles que **la plateforme les applique
 * réellement**.
 *
 * <p>Ces valeurs alimentent les CGV. Les y écrire à la main aurait laissé le contrat décrire
 * un barème que le code n'applique pas — c'est précisément le risque sur un document
 * opposable : facturer autrement que ce qu'on a annoncé.</p>
 */
export const CONDITIONS_MOBILITE = {
  /** Aligné sur `DOCTOR_APPLICATION_FEE` côté serveur (50,00 € par défaut). */
  fraisDossier: lire(env.VITE_LEGAL_FRAIS_DOSSIER) ?? '50,00 €',

  /**
   * Part des frais de formation exigée à la confirmation d'admission.
   *
   * ⚠️ **100 % aujourd'hui.** Le paiement échelonné (acompte de 60 % puis solde) n'est pas
   * implémenté : `EnrollmentPaymentService` ouvre un encaissement unique du montant total au
   * passage en `PENDING_TUITION_FEE`. Tant que ce n'est pas développé, annoncer 60 % dans les
   * CGV promettrait un échéancier que la plateforme ne respecte pas.
   */
  partExigeeALAdmission: 100,
} as const;

/** Champ obligatoire non renseigné : affiché en évidence plutôt que laissé vide. */
export const AC = '⚠️ à compléter';

export const ou = (valeur: string | null) => valeur ?? AC;
