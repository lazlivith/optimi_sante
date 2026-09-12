/**
 * Garde-fou de la charte graphique.
 *
 * Trois contrôles, exécutés au build (voir `prebuild` et `postbuild` dans package.json) :
 *
 *   1. CONTRASTE — chaque paire texte/fond du thème doit rester au-dessus de 4,5:1. Les valeurs
 *      sont lues dans `src/index.css`, jamais recopiées ici : un contrôle portant sur une copie
 *      ne verrait pas une couleur modifiée dans le fichier.
 *
 *   2. CLIQUET — le nombre de couleurs d'identité écrites en dur ne doit pas augmenter. Exiger
 *      zéro ferait échouer tous les builds dès aujourd'hui, et le garde-fou serait désactivé
 *      dans la semaine. Interdire l'augmentation arrête l'hémorragie sans bloquer personne.
 *
 *   3. POIDS — ce qui est servi au navigateur reste sous un plafond. Les visuels de marque
 *      étaient passés à 30,7 Mo sans que personne ne s'en aperçoive.
 *
 * Sans dépendance : ce garde-fou doit survivre à une réinstallation des paquets.
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const RACINE = join(dirname(fileURLToPath(import.meta.url)), '..');
const REFERENCE = JSON.parse(readFileSync(join(RACINE, 'scripts/charte-reference.json'), 'utf8'));

const SEUIL_TEXTE = 4.5;   // WCAG 2.1, critère 1.4.3
const SEUIL_OBJET = 3.0;   // WCAG 2.1, critère 1.4.11

let echecs = 0;
const echec = (message) => { echecs++; console.error(`  ÉCHEC  ${message}`); };
const ok = (message) => console.log(`  ok     ${message}`);

// ── Contraste ────────────────────────────────────────────────────────────────────────

function luminance(hex) {
  const canal = (i) => {
    const v = parseInt(hex.slice(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * canal(0) + 0.7152 * canal(2) + 0.0722 * canal(4);
}

function contraste(a, b) {
  const [la, lb] = [luminance(a), luminance(b)];
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

function jetons() {
  const css = readFileSync(join(RACINE, 'src/index.css'), 'utf8');
  const trouves = {};
  for (const m of css.matchAll(/--color-([a-z-]+):\s*#([0-9A-Fa-f]{6})\s*;/g)) {
    trouves[m[1]] = m[2].toUpperCase();
  }
  return trouves;
}

function verifierContrastes() {
  console.log('\nCONTRASTE DES PAIRES DU THÈME');
  const J = jetons();
  const BLANC = 'FFFFFF';

  /** @type {[string, string, string, number][]} intitulé, texte, fond, seuil */
  const paires = [
    ['texte de marque sur blanc',        J.brand,          BLANC,            SEUIL_TEXTE],
    ['texte de marque sur le papier',    J.brand,          J['brand-cream'], SEUIL_TEXTE],
    ['blanc sur la couleur de marque',   BLANC,            J.brand,          SEUIL_TEXTE],
    ['blanc sur la marque au survol',    BLANC,            J['brand-fonce'], SEUIL_TEXTE],
    ['encre sur blanc',                  J['brand-dark'],  BLANC,            SEUIL_TEXTE],
    ['accent sur blanc',                 J['brand-accent'], BLANC,           SEUIL_TEXTE],
    ['blanc sur l’accent',               BLANC,            J['brand-accent'], SEUIL_TEXTE],
    ['blanc sur l’accent au survol',     BLANC,            J['brand-accent-fonce'], SEUIL_TEXTE],
    ['texte de marque sur brand-light',  J.brand,          J['brand-light'], SEUIL_TEXTE],
    ['jaune sur l’encre',                J['brand-jaune'], J['brand-dark'],  SEUIL_TEXTE],
    ['succès sur blanc',                 J.success,        BLANC,            SEUIL_TEXTE],
    ['alerte sur blanc',                 J.warning,        BLANC,            SEUIL_TEXTE],
    ['danger sur blanc',                 J.danger,         BLANC,            SEUIL_TEXTE],
    ['bordure de marque sur blanc',      J.brand,          BLANC,            SEUIL_OBJET],
  ];

  for (const [intitule, texte, fond, seuil] of paires) {
    if (!texte || !fond) {
      echec(`${intitule} : un jeton est introuvable dans index.css — a-t-il été renommé ?`);
      continue;
    }
    const r = contraste(texte, fond);
    if (r >= seuil) ok(`${intitule} — ${r.toFixed(2)}:1`);
    else echec(`${intitule} — ${r.toFixed(2)}:1, sous le seuil de ${seuil}:1`);
  }

  // La règle du jaune se vérifie DANS LES DEUX SENS : on affirme qu'il est illisible sur fond
  // clair, donc on échoue aussi le jour où il le deviendrait — ce serait qu'il a changé.
  for (const [nom, fond] of [['blanc', BLANC], ['papier', J['brand-cream']], ['brand-light', J['brand-light']]]) {
    const r = contraste(J['brand-jaune'], fond);
    if (r < SEUIL_TEXTE) ok(`le jaune reste réservé aux fonds sombres (${r.toFixed(2)}:1 sur ${nom})`);
    else echec(`le jaune atteint ${r.toFixed(2)}:1 sur ${nom} : la règle « jamais sur fond clair » n’a plus de sens, relisez-la`);
  }
}

// ── Cliquet sur les couleurs écrites en dur ──────────────────────────────────────────

function fichiersSource(dossier, acc = []) {
  for (const nom of readdirSync(dossier)) {
    const chemin = join(dossier, nom);
    if (statSync(chemin).isDirectory()) fichiersSource(chemin, acc);
    else if (/\.(tsx|ts)$/.test(nom)) acc.push(chemin);
  }
  return acc;
}

function verifierCliquet() {
  console.log('\nCOULEURS D’IDENTITÉ ÉCRITES EN DUR');
  const familles = REFERENCE.famillesIdentite.join('|');
  const motif = new RegExp(`\\b(?:bg|text|border|from|to|via|ring|fill|divide)-(?:${familles})-\\d{2,3}\\b`, 'g');

  let total = 0;
  const parFichier = [];
  for (const chemin of fichiersSource(join(RACINE, 'src'))) {
    const n = (readFileSync(chemin, 'utf8').match(motif) ?? []).length;
    if (n) { total += n; parFichier.push([chemin.slice(RACINE.length + 5), n]); }
  }

  const plafond = REFERENCE.couleursEnDur;
  if (total > plafond) {
    echec(`${total} occurrences, contre ${plafond} en référence : ${total - plafond} de plus.`);
    console.error('         Employez les jetons — brand, brand-accent — ou, s’il s’agit d’un état,');
    console.error('         success, warning, danger. Les fichiers les plus concernés :');
    for (const [f, n] of parFichier.sort((a, b) => b[1] - a[1]).slice(0, 5)) {
      console.error(`           ${f} (${n})`);
    }
  } else if (total < plafond) {
    ok(`${total} occurrences, contre ${plafond} en référence — ${plafond - total} de moins.`);
    console.log(`         Pensez à abaisser « couleursEnDur » à ${total} dans charte-reference.json,`);
    console.log('         sinon le cliquet cesse de mordre.');
  } else {
    ok(`${total} occurrences, inchangé.`);
  }
}

// ── Poids servi ──────────────────────────────────────────────────────────────────────

function poids(dossier) {
  if (!existsSync(dossier)) return null;
  let total = 0;
  for (const nom of readdirSync(dossier)) {
    const chemin = join(dossier, nom);
    total += statSync(chemin).isDirectory() ? poids(chemin) : statSync(chemin).size;
  }
  return total;
}

function verifierPoids() {
  console.log('\nPOIDS SERVI AU NAVIGATEUR');
  const controles = [
    ['les visuels de public/', poids(join(RACINE, 'public')), REFERENCE.plafondPublicKo],
    ['le JavaScript produit', poidsJs(), REFERENCE.plafondJsKo],
  ];
  for (const [intitule, octets, plafondKo] of controles) {
    if (octets === null) { console.log(`  (ignoré) ${intitule} : pas encore construit`); continue; }
    const ko = Math.round(octets / 1024);
    if (ko <= plafondKo) ok(`${intitule} — ${ko} Ko (plafond ${plafondKo} Ko)`);
    else echec(`${intitule} — ${ko} Ko, au-dessus du plafond de ${plafondKo} Ko`);
  }
}

function poidsJs() {
  const assets = join(RACINE, 'dist/assets');
  if (!existsSync(assets)) return null;
  return readdirSync(assets)
    .filter((f) => f.endsWith('.js'))
    .reduce((t, f) => t + statSync(join(assets, f)).size, 0);
}

// ── Exécution ────────────────────────────────────────────────────────────────────────

const seulementPoids = process.argv.includes('--poids');
console.log(seulementPoids ? 'Vérification du poids servi' : 'Vérification de la charte graphique');

if (!seulementPoids) { verifierContrastes(); verifierCliquet(); }
verifierPoids();

if (echecs > 0) {
  console.error(`\n${echecs} contrôle(s) en échec. Le build s’arrête ici.`);
  process.exit(1);
}
console.log('\nCharte respectée.');
