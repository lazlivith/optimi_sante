import { axiosClient } from './axiosClient';

/**
 * Signale au serveur une panne survenue dans le navigateur.
 *
 * <p>Sans cela, un incident côté client ne laisse de trace nulle part : le 27 octobre, une clé
 * publique Stripe vide rendait le paiement impossible, et la seule trace au monde était un
 * message dans la console de qui ouvrait les outils de développement.</p>
 *
 * <p><b>N'échoue jamais.</b> On appelle cette fonction depuis un écran qui gère déjà une panne ;
 * si le signalement échoue à son tour — hors ligne, serveur injoignable, visiteur non connecté —
 * ce n'est pas au visiteur d'en subir les conséquences. L'erreur est avalée, et la console
 * garde la trace locale.</p>
 */
export function signalerIncident(contexte: string, message: string): void {
  // Les bornes sont celles de la validation côté serveur : tronquer ici évite un 400 qui
  // n'apprendrait rien à personne.
  const charge = { contexte: contexte.slice(0, 60), message: message.slice(0, 500) };

  console.error(`[${charge.contexte}] ${charge.message}`);

  axiosClient.post('/journal-client', charge).catch(() => {
    /* Un signalement perdu ne doit pas aggraver la panne qu'il décrit. */
  });
}
