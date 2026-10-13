import { adresseSure } from './adresseWeb';

/**
 * Les règles de « Exercer en France » qui ne dépendent d'aucun composant.
 *
 * <p>Isolées ici pour deux raisons. D'abord, elles se vérifient seules : un badge ou une
 * adresse ne demandent pas de monter une page pour être éprouvés. Ensuite, elles sont
 * employées à deux endroits — la page publique et l'écran d'administration — et les écrire
 * deux fois garantirait qu'elles divergent.</p>
 */

/** Ce que chaque état d'une période dit au visiteur, et la couleur qui le porte. */
export const BADGES_PERIODE = {
  ouvert: { libelle: 'Ouvert', classe: 'bg-success/10 text-success border-success/30' },
  ferme: { libelle: 'Fermé', classe: 'bg-slate-100 text-slate-600 border-slate-200' },
  a_venir: { libelle: 'À venir', classe: 'bg-warning/10 text-warning border-warning/30' },
} as const;

export type StatutPeriode = keyof typeof BADGES_PERIODE;

/**
 * Le badge d'une période.
 *
 * <p>Un statut inconnu rend « À venir » plutôt que rien : si le serveur ajoute un état un
 * jour, une carte sans badge serait plus trompeuse qu'un badge prudent.</p>
 */
export function badgeDePeriode(statut: string) {
  return BADGES_PERIODE[statut as StatutPeriode] ?? BADGES_PERIODE.a_venir;
}

export const MESSAGE_ADRESSE =
  'Seules les adresses commençant par http:// ou https:// sont acceptées.';

/**
 * Vérifie une adresse de lien officiel, avant de l'envoyer au serveur.
 *
 * <p><b>Cette vérification ne protège rien ; elle explique.</b> Le serveur refuse de toute
 * façon ({@code AdresseDeLien}), et c'est lui qui protège — un contrôle dans le navigateur
 * est contourné en ouvrant la console. Il évite à l'administrateur de recevoir une erreur
 * brute après avoir rempli tout un formulaire, sans comprendre laquelle de ses saisies pose
 * problème.</p>
 *
 * <p>La chaîne est compactée avant l'examen, comme au serveur : un navigateur ignore les
 * espaces et caractères de contrôle à l'analyse d'une adresse, et « java\nscript: »
 * s'exécute.</p>
 *
 * @returns le message à afficher, ou `null` si l'adresse convient
 */
export function verifierAdresseDeLien(url: string | null | undefined): string | null {
  const brut = (url ?? '').trim();
  // Vide ou « à compléter » : ce n'est pas une faute ici, c'est un lien que la page masque.
  // Cette règle-là est propre à cette page.
  if (brut === '' || brut === 'A_COMPLETER') return null;
  // Le contrôle de protocole, lui, ne l'est pas : il protège contre la même chose partout,
  // et une seule implémentation évite que les deux divergent.
  return adresseSure(brut) ? null : MESSAGE_ADRESSE;
}

/** Ce qu'un praticien saisit pour demander un accompagnement. */
export interface ChampsDemande {
  nom?: string;
  email?: string;
  telephoneWhatsapp?: string;
  profession?: string;
  specialite?: string;
  consentementRgpd?: boolean;
}

/**
 * Vérifie le formulaire de demande avant l'envoi.
 *
 * <p><b>Le consentement n'est pas une case parmi d'autres.</b> Sans lui, la demande ne peut
 * pas exister — une contrainte en base le garantit. La signaler ici évite au praticien de
 * perdre sa saisie sur un refus du serveur.</p>
 *
 * <p>L'adresse est vérifiée sommairement, et c'est volontaire : une expression régulière
 * stricte rejette des adresses valides — apostrophes, domaines récents, sous-adressage — et
 * un praticien éconduit par son adresse réelle ne revient pas.</p>
 *
 * @returns un message par champ fautif ; objet vide si tout convient
 */
export function verifierDemande(champs: ChampsDemande): Record<string, string> {
  const erreurs: Record<string, string> = {};

  if (!champs.nom?.trim()) {
    erreurs.nom = 'Indiquez votre nom complet.';
  }
  const email = (champs.email ?? '').trim();
  if (!email) {
    erreurs.email = 'Indiquez votre adresse e-mail.';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    erreurs.email = "Cette adresse e-mail ne semble pas valide.";
  }
  if (!champs.consentementRgpd) {
    erreurs.consentementRgpd =
      'Votre consentement est nécessaire pour enregistrer votre demande.';
  }
  return erreurs;
}

/** Clé de l'offre technique rattachée aux demandes d'alerte d'ouverture d'une période AEP. */
export const OFFRE_ALERTE_AEP = 'alerte_aep';

/**
 * Les sept statuts d'une demande, du premier contact à la fin.
 *
 * <p><b>Le ton n'est pas décoratif.</b> Il dit d'un coup d'œil ce qui attend une action
 * (ambre), ce qui avance (bleu), ce qui est acquis (vert) et ce qui est clos (ardoise ou
 * rose). Sur une liste de vingt-cinq lignes, c'est la seule chose qu'on lit vraiment.</p>
 */
export const STATUTS_DEMANDE = {
  nouveau:      { libelle: 'Nouveau',      ton: 'amber'   },
  qualifie:     { libelle: 'Qualifié',     ton: 'blue'    },
  devis_envoye: { libelle: 'Devis envoyé', ton: 'blue'    },
  paye:         { libelle: 'Payé',         ton: 'emerald' },
  en_cours:     { libelle: 'En cours',     ton: 'purple'  },
  termine:      { libelle: 'Terminé',      ton: 'slate'   },
  perdu:        { libelle: 'Perdu',        ton: 'rose'    },
} as const;

export type StatutDemande = keyof typeof STATUTS_DEMANDE;

/** Les quatre étapes du parcours déclarées au dépôt. */
export const ETAPES_DEMANDE: Record<string, string> = {
  avant_evc: 'Avant les EVC',
  laureat: 'Lauréat',
  affecte: 'Affecté',
  autre: 'Autre',
};

/** Un statut inconnu reste lisible plutôt que de laisser une case vide. */
export function libelleStatut(code: string | null | undefined): string {
  if (!code) return '—';
  return STATUTS_DEMANDE[code as StatutDemande]?.libelle ?? code;
}

/**
 * Le libellé d'une offre, à partir de sa clé.
 *
 * <p><b>Pourquoi déduire plutôt que recenser.</b> Les offres se créent et se renomment depuis
 * l'administration : une table de correspondance écrite ici dirait faux dès la première offre
 * ajoutée, et personne ne penserait à la mettre à jour. La clé porte déjà le libellé —
 * {@code accompagnement_complet} se lit « Accompagnement complet » — et une clé inconnue reste
 * donc lisible, au lieu de s'afficher telle quelle à l'écran.</p>
 */
export function libelleOffre(cle: string | null | undefined): string {
  if (!cle) return '—';
  if (cle === OFFRE_ALERTE_AEP) return 'Alerte AEP';
  const mots = cle.replace(/_/g, ' ').trim();
  return mots.charAt(0).toUpperCase() + mots.slice(1);
}

/** Ce qu'un événement du fil raconte, en français plutôt qu'en code. */
export const TYPES_EVENEMENT: Record<string, string> = {
  creation: 'Demande déposée',
  statut: 'Changement de statut',
  assignation: 'Dossier assigné',
  note: 'Note interne',
};
