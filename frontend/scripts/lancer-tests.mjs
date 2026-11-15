/**
 * Lance vitest depuis un répertoire courant écrit comme le disque l'écrit.
 *
 * Sous Windows, `d:\projet` et `D:\projet` désignent le même dossier, mais Node en fait deux
 * chemins distincts. Lancée depuis `d:\…`, la suite échoue à la collecte, sur les 14 fichiers,
 * avec « Vitest failed to find the current suite » — un message qui ne dit rien de la cause.
 * Elle passe depuis `D:\…`. Mesuré le 15 novembre 2026, y compris sur un clone neuf.
 *
 * Personne ne tape `d:\` : certains terminaux, et l'outil de l'assistant entre deux commandes,
 * ouvrent le dossier ainsi. La correction vit donc ici et non dans une consigne.
 *
 * Elle ne peut pas vivre dans `vitest.config.ts` : essayé, un `process.chdir` en tête de ce
 * fichier s'exécute trop tard, vitest a déjà retenu le chemin fautif. Le fichier de
 * configuration porte donc seulement un garde-fou, pour qui lance `npx vitest` directement.
 *
 * Hors Windows, ce script ne change rien : il lance vitest, c'est tout.
 */
import { spawnSync } from 'node:child_process';
import { realpathSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';

const recu = process.cwd();
let repertoire = recu;

if (process.platform === 'win32') {
  repertoire = realpathSync.native(recu);
  if (repertoire !== recu) {
    process.chdir(repertoire);
    console.warn(
      `[lancer-tests] Répertoire courant corrigé : « ${recu} » -> « ${repertoire} ».\n`
        + '[lancer-tests] Le terminal a ouvert le dossier avec une autre casse que celle du '
        + 'disque ; sans cette correction, la suite échouerait à la collecte.',
    );
  }
}

// vitest est résolu depuis le répertoire corrigé, et non depuis l'adresse de ce script :
// celle-ci peut porter la casse fautive, et la transmettre au processus lancé.
const requerir = createRequire(join(repertoire, 'package.json'));
const paquet = requerir.resolve('vitest/package.json');
const entree = join(dirname(paquet), requerir(paquet).bin.vitest);

const resultat = spawnSync(process.execPath, [entree, ...process.argv.slice(2)], {
  cwd: repertoire,
  stdio: 'inherit',
});

if (resultat.error) {
  console.error(`[lancer-tests] vitest n'a pas pu être lancé : ${resultat.error.message}`);
  process.exit(1);
}
process.exit(resultat.status ?? 1);
