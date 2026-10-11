/**
 * Ce qu'une adresse venue de la base a le droit d'être, avant d'entrer dans un `href`.
 *
 * <p><b>Pourquoi un contrôle ici alors que le serveur refuse déjà.</b> Parce que le serveur
 * ne refuse que les saisies À VENIR. Les adresses déjà enregistrées, elles, sont servies
 * telles quelles : une seule ligne fautive en base suffirait à exécuter du script chez chaque
 * visiteur d'une fiche produit. Ce second contrôle rend cette ligne inoffensive sans qu'on
 * ait à toucher aux données.</p>
 *
 * <p>La chaîne est compactée avant l'examen : un navigateur ignore espaces et caractères de
 * contrôle à l'analyse d'une adresse, et `java\nscript:` s'exécute. Vérifier ce qui est
 * écrit, et non ce qui sera interprété, ne protège de rien.</p>
 */
export function adresseSure(url: string | null | undefined): boolean {
  if (!url) return false;
  // eslint-disable-next-line no-control-regex
  const compacte = url.replace(/[\s\u0000-\u001F\u007F  -‏﻿]/g, '')
    .toLowerCase();
  return compacte.startsWith('http://') || compacte.startsWith('https://');
}

/**
 * L'adresse si elle est sûre, `undefined` sinon.
 *
 * <p>`undefined` et non une chaîne vide : React omet alors l'attribut, et l'élément cesse
 * d'être un lien. Un `href=""` resterait cliquable et rechargerait la page.</p>
 */
export function hrefSur(url: string | null | undefined): string | undefined {
  return adresseSure(url) ? url! : undefined;
}
