import { axiosClient } from '../api/axiosClient';

/**
 * Télécharge un endpoint API en respectant l'authentification JWT (un simple <a href> ne
 * transporterait pas l'en-tête Authorization ajouté par l'intercepteur axios). Récupère la
 * réponse en blob puis déclenche l'enregistrement du fichier côté navigateur.
 */
export async function downloadApiFile(url: string, fallbackFilename: string): Promise<void> {
  const response = await axiosClient.get(url, { responseType: 'blob' });

  const disposition = response.headers['content-disposition'] as string | undefined;
  const match = disposition?.match(/filename="?([^"]+)"?/i);
  const filename = match?.[1] ?? fallbackFilename;

  const blobUrl = window.URL.createObjectURL(response.data as Blob);
  const link = document.createElement('a');
  link.href = blobUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(blobUrl);
}

/** Enregistre un objet JSON en fichier .json côté navigateur (export RGPD notamment). */
export function downloadJson(data: unknown, filename: string): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const blobUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = blobUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(blobUrl);
}
