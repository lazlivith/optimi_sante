/**
 * Garde-fou de la charte graphique.
 *
 * Quatre contrôles, exécutés au build (voir `prebuild` et `postbuild` dans package.json) :
 *
 *   1. CONTRASTE — chaque paire texte/fond du thème doit rester au-dessus de 4,5:1. Les valeurs
 *      sont lues dans `src/index.css`, jamais recopiées ici : un contrôle portant sur une copie
 *      ne verrait pas une couleur modifiée dans le fichier.
 *
 *   2. CLIQUET — le nombre de couleurs d'identité écrites en classes Tailwind ne doit pas
 *      augmenter. Exiger zéro ferait échouer tous les builds dès aujourd'hui, et le garde-fou
 *      serait désactivé dans la semaine. Interdire l'augmentation arrête l'hémorragie sans
 *      bloquer personne.
 *
 *   3. VALEURS BRUTES — une couleur écrite en hexadécimal ou en rgb() échappe à tout : au
 *      typage, au cliquet, et au changement de palette. Le lot B l'a prouvé — 64 boutons
 *      gardaient `hover:bg-[#0f3c35]`, l'ancien vert, et viraient donc au vert au survol
 *      pendant que tout le reste passait au bleu. Rien ne l'avait signalé.
 *
 *   4. POIDS — ce qui est servi au navigateur reste sous un plafond. Les visuels de marque
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

// ── Outils ───────────────────────────────────────────────────────────────────────────

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

function fichiersSource(dossier, acc = []) {
  for (const nom of readdirSync(dossier)) {
    const chemin = join(dossier, nom);
    if (statSync(chemin).isDirectory()) fichiersSource(chemin, acc);
    else if (/\.(tsx|ts)$/.test(nom)) acc.push(chemin);
  }
  return acc;
}

function jetons() {
  const css = readFileSync(join(RACINE, 'src/index.css'), 'utf8');
  const trouves = {};
  for (const m of css.matchAll(/--color-([a-z-]+):\s*#([0-9A-Fa-f]{6})\s*;/g)) {
    trouves[m[1]] = m[2].toUpperCase();
  }
  return trouves;
}

// ── 1. Contraste ─────────────────────────────────────────────────────────────────────

function verifierContrastes() {
  console.log('\nCONTRASTE DES PAIRES DU THÈME');
  const J = jetons();
  const BLANC = 'FFFFFF';

  const paires = [
    ['texte de marque sur blanc',        J.brand,           BLANC,             SEUIL_TEXTE],
    ['texte de marque sur le papier',    J.brand,           J['brand-cream'],  SEUIL_TEXTE],
    ['blanc sur la couleur de marque',   BLANC,             J.brand,           SEUIL_TEXTE],
    ['blanc sur la marque au survol',    BLANC,             J['brand-fonce'],  SEUIL_TEXTE],
    ['encre sur blanc',                  J['brand-dark'],   BLANC,             SEUIL_TEXTE],
    ['encre sur le papier',              J['brand-dark'],   J['brand-cream'],  SEUIL_TEXTE],
    ['accent sur blanc',                 J['brand-accent'], BLANC,             SEUIL_TEXTE],
    ['accent sur le papier',             J['brand-accent'], J['brand-cream'],  SEUIL_TEXTE],
    ['blanc sur l’accent',               BLANC,             J['brand-accent'], SEUIL_TEXTE],
    ['blanc sur l’accent au survol',     BLANC,             J['brand-accent-fonce'], SEUIL_TEXTE],
    ['texte de marque sur brand-light',  J.brand,           J['brand-light'],  SEUIL_TEXTE],
    ['encre sur l’orange de la charte',  J['brand-dark'],   J['brand-orange'], SEUIL_TEXTE],
    ['jaune sur l’encre',                J['brand-jaune'],  J['brand-dark'],   SEUIL_TEXTE],
    ['succès sur blanc',                 J.success,         BLANC,             SEUIL_TEXTE],
    ['alerte sur blanc',                 J.warning,         BLANC,             SEUIL_TEXTE],
    ['danger sur blanc',                 J.danger,          BLANC,             SEUIL_TEXTE],
    ['bordure de marque sur blanc',      J.brand,           BLANC,             SEUIL_OBJET],
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
  const fondsClairs = [['blanc', BLANC], ['papier', J['brand-cream']], ['brand-light', J['brand-light']]];
  for (const [nom, fond] of fondsClairs) {
    const r = contraste(J['brand-jaune'], fond);
    if (r < SEUIL_TEXTE) ok(`le jaune reste réservé aux fonds sombres (${r.toFixed(2)}:1 sur ${nom})`);
    else echec(`le jaune atteint ${r.toFixed(2)}:1 sur ${nom} : la règle « jamais sur fond clair » n’a plus de sens, relisez-la`);
  }
}

// ── 2. Cliquet sur les classes d'identité ────────────────────────────────────────────

function verifierCliquet() {
  console.log('\nCOULEURS D’IDENTITÉ EN CLASSES TAILWIND');
  const familles = REFERENCE.famillesIdentite.join('|');
  const motif = new RegExp(
    '\\b(?:bg|text|border|from|to|via|ring|fill|divide)-(?:' + familles + ')-\\d{2,3}\\b', 'g');

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

// ── 3. Couleurs écrites en valeur brute ──────────────────────────────────────────────

function verifierValeursBrutes() {
  console.log('\nCOULEURS ÉCRITES EN VALEUR BRUTE');
  const autorisees = new Set(REFERENCE.valeursBrutesAutorisees.map((v) => v.toLowerCase()));
  const motif = /#[0-9a-fA-F]{6}|rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}/g;

  const nouvelles = [];
  for (const chemin of fichiersSource(join(RACINE, 'src'))) {
    for (const brut of readFileSync(chemin, 'utf8').match(motif) ?? []) {
      const valeur = brut.toLowerCase().replace(/\s/g, '');
      if (!autorisees.has(valeur)) nouvelles.push([chemin.slice(RACINE.length + 5), brut]);
    }
  }

  if (nouvelles.length === 0) {
    ok(`aucune valeur brute hors des ${autorisees.size} déjà recensées.`);
    return;
  }
  echec(`${nouvelles.length} couleur(s) écrite(s) en dur, non recensée(s) :`);
  for (const [f, v] of nouvelles.slice(0, 8)) console.error(`           ${v}  dans ${f}`);
  console.error('         Employez un jeton. Si la valeur est légitime — palette de graphique,');
  console.error('         ombre, transparence — ajoutez-la à « valeursBrutesAutorisees » dans');
  console.error('         charte-reference.json, en disant pourquoi.');
}

// ── 4. Poids servi ───────────────────────────────────────────────────────────────────

function poids(dossier) {
  if (!existsSync(dossier)) return null;
  let total = 0;
  for (const nom of readdirSync(dossier)) {
    const chemin = join(dossier, nom);
    total += statSync(chemin).isDirectory() ? poids(chemin) : statSync(chemin).size;
  }
  return total;
}

function poidsJs() {
  const assets = join(RACINE, 'dist/assets');
  if (!existsSync(assets)) return null;
  return readdirSync(assets)
    .filter((f) => f.endsWith('.js'))
    .reduce((t, f) => t + statSync(join(assets, f)).size, 0);
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

// ── Exécution ────────────────────────────────────────────────────────────────────────

const seulementPoids = process.argv.includes('--poids');
console.log(seulementPoids ? 'Vérification du poids servi' : 'Vérification de la charte graphique');

if (!seulementPoids) { verifierContrastes(); verifierCliquet(); verifierValeursBrutes(); }
verifierPoids();

if (echecs > 0) {
  console.error(`\n${echecs} contrôle(s) en échec. Le build s’arrête ici.`);
  process.exit(1);
}
console.log('\nCharte respectée.');
