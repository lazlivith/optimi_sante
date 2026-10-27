/**
 * Les variables sans lesquelles une construction de production ne doit pas exister.
 *
 * Vite inscrit la valeur d'une variable `VITE_*` DANS le code compilé : elle est figée au
 * moment de la construction, pas lue au démarrage. Une variable absente ne produit donc aucune
 * erreur visible — elle produit une chaîne vide dans le paquet servi, et la panne n'apparaît
 * qu'au moment où un visiteur essaie de payer.
 *
 * Le 27 octobre, la clé publique Stripe manquait côté hébergeur du frontend. Le site s'est
 * construit, s'est déployé, s'est affiché normalement, et le paiement par carte était
 * impossible pour tout le monde. Rien dans la chaîne n'a protesté.
 *
 * Ce contrôle ne s'applique qu'à la construction de production : en développement, un poste
 * sans `.env` complet doit continuer à démarrer, et l'écran de paiement sait se présenter
 * comme indisponible.
 */

/** Chaque variable, avec ce qu'elle doit ressembler et pourquoi elle compte. */
const EXIGEES = [
  {
    nom: 'VITE_STRIPE_PUBLISHABLE_KEY',
    // Le préfixe et pas seulement la présence : la clé secrète recopiée par erreur à la place
    // de la publique échouerait exactement de la même façon, en silence.
    valide: (v) => v.startsWith('pk_test_') || v.startsWith('pk_live_'),
    attendu: 'une clé publique Stripe (pk_test_… ou pk_live_…)',
    consequence: 'le paiement par carte serait impossible, sans aucune erreur au déploiement.',
  },
];

/**
 * Production ou non.
 *
 * `NODE_ENV` ne suffit pas : Vite ne le positionne pas pour les scripts npm qui l'entourent.
 * On reconnaît la production à l'hébergeur (`VERCEL=1`, `CI=true`), et `VERIFIER_ENV=1` force
 * le contrôle pour pouvoir l'éprouver en local.
 */
function enProduction() {
  return (
    process.env.VERIFIER_ENV === '1' ||
    process.env.VERCEL === '1' ||
    process.env.CI === 'true' ||
    process.env.NODE_ENV === 'production'
  );
}

if (!enProduction()) {
  console.log(
    'Variables d’environnement : contrôle ignoré hors production ' +
      '(VERIFIER_ENV=1 pour le forcer).',
  );
  process.exit(0);
}

const manquantes = [];
for (const exigee of EXIGEES) {
  const valeur = (process.env[exigee.nom] ?? '').trim();
  if (!exigee.valide(valeur)) {
    manquantes.push({ ...exigee, vide: valeur.length === 0 });
  }
}

if (manquantes.length === 0) {
  console.log(`Variables d’environnement : ${EXIGEES.length} contrôlée(s), tout est en place.`);
  process.exit(0);
}

console.error('\nCONSTRUCTION REFUSÉE — variable d’environnement de production absente ou invalide\n');
for (const m of manquantes) {
  console.error(`  ${m.nom}`);
  console.error(`    état    : ${m.vide ? 'absente ou vide' : 'présente, mais pas au format attendu'}`);
  console.error(`    attendu : ${m.attendu}`);
  console.error(`    sinon   : ${m.consequence}\n`);
}
console.error(
  'Ces variables se posent chez l’hébergeur du FRONTEND (Vercel), pas chez celui du\n' +
    'backend (Render) : elles sont lues à la construction du site. Après les avoir posées,\n' +
    'il faut relancer un déploiement — changer la variable sans reconstruire ne change rien.\n',
);
process.exit(1);
