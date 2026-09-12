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

  /**
   * Adresses de contact et du délégué à la protection des données.
   *
   * <p>Le repli est volontairement <b>fictif et non routable</b> : le domaine `.invalid` est
   * réservé par la RFC 2606 et ne peut, par construction, être enregistré ni résolu — aucun
   * courrier ne partira jamais vers quiconque. Les replis précédents étaient sur le domaine
   * de l'ancien site : les demandes RGPD de nos clients seraient arrivées chez un tiers.</p>
   *
   * <p>« a-definir » plutôt qu'un nom crédible : une adresse fictive <i>plausible</i> se lit
   * comme une vraie et part en production sans qu'on la voie. Celle-ci se dénonce.</p>
   */
  emailContact: lire(env.VITE_LEGAL_EMAIL_CONTACT) ?? 'contact@a-definir.invalid',
  emailDpo: lire(env.VITE_LEGAL_EMAIL_DPO) ?? 'dpo@a-definir.invalid',
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
  /**
   * Frais de dossier **par défaut**, alignés sur `DOCTOR_APPLICATION_FEE` côté serveur.
   *
   * Depuis la V40, chaque formation peut fixer les siens ; cette valeur ne s'applique qu'à
   * défaut. Les pages qui affichent un montant pour une formation précise doivent lire
   * `applicationFee` renvoyé par l'API, pas cette constante — elle ne sert qu'aux textes
   * généraux, où aucune formation n'est encore choisie.
   */
  fraisDossier: lire(env.VITE_LEGAL_FRAIS_DOSSIER) ?? '100,00 €',

  /**
   * Part des frais de formation exigée à la confirmation d'admission — l'acompte.
   *
   * ⚠️ **Doit rester alignée sur `app.tuition.deposit-rate` (application.yml) et sur
   * `TUITION_DEPOSIT_RATE` (docker-compose.yml), qui prime.** Cette valeur alimente les CGV :
   * annoncer un taux dans le contrat et en prélever un autre est exactement l'incident survenu
   * sur les frais de dossier, où le défaut du fichier de configuration n'était pas la valeur
   * déployée.
   *
   * L'échéancier est effectivement appliqué depuis la V45 : `TuitionPaymentService` ouvre
   * l'acompte au passage en `PENDING_TUITION_FEE`, puis le solde à `VISA_GRANTED`.
   */
  partExigeeALAdmission: Number(lire(env.VITE_LEGAL_PART_ACOMPTE) ?? '60'),

  /** Le complément, appelé à la délivrance du visa. Dérivé : les deux ne peuvent pas diverger. */
  get partExigeeAuVisa() {
    return 100 - this.partExigeeALAdmission;
  },
} as const;

/** Champ obligatoire non renseigné : affiché en évidence plutôt que laissé vide. */
export const AC = '⚠️ à compléter';

export const ou = (valeur: string | null) => valeur ?? AC;
