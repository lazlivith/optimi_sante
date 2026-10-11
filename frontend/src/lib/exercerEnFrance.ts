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
  if (brut === '' || brut === 'A_COMPLETER') return null;
  // eslint-disable-next-line no-control-regex
  const compacte = brut.replace(/[\s\u0000-\u001F\u007F  -‏﻿]/g, '')
    .toLowerCase();
  return compacte.startsWith('http://') || compacte.startsWith('https://')
    ? null : MESSAGE_ADRESSE;
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
