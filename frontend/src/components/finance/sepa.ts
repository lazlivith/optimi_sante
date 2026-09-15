import { financeService } from '../../api/financeService';
import { downloadApiFile } from '../../lib/download';

/**
 * Télécharge l'ordre de virement SEPA d'un reversement.
 *
 * <p>La réponse est demandée en blob : en cas de refus (IBAN d'Optimi Santé non renseigné,
 * virement déjà exécuté…), le message du serveur arrive lui aussi en blob, et axios ne le lit
 * pas. On le décode pour dire ce qui manque plutôt qu'un « échec » muet.</p>
 *
 * @return {@code null} si le fichier est enregistré, sinon le message à afficher
 */
export async function telechargerOrdreSepa(payoutId: string, reference: string | null): Promise<string | null> {
  try {
    await downloadApiFile(financeService.sepaUrl(payoutId), `${reference ?? 'virement'}.xml`);
    return null;
  } catch (err: any) {
    const data = err.response?.data;
    if (data instanceof Blob) {
      try {
        const message = JSON.parse(await data.text())?.message;
        if (message) return message;
      } catch {
        // Réponse non JSON : message générique ci-dessous.
      }
    }
    return "L'ordre SEPA n'a pas pu être produit. Réessayez.";
  }
}
