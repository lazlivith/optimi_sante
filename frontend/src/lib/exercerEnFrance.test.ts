import { describe, expect, it } from 'vitest';
import {
  BADGES_PERIODE,
  MESSAGE_ADRESSE,
  PARITE_EURO_FCFA,
  badgeDePeriode,
  equivalentFcfa,
  fcfaLisible,
  verifierAdresseDeLien,
  verifierDemande,
} from './exercerEnFrance';

describe('adresse d’un lien officiel', () => {
  it.each([
    'https://www.cng.sante.fr/session-evc-2026',
    'http://exemple.fr/page',
    'HTTPS://MAJUSCULES.FR',
  ])('accepte %s', (url) => {
    expect(verifierAdresseDeLien(url)).toBeNull();
  });

  it.each([
    'javascript:alert(1)',
    'JavaScript:alert(1)',
    // Le navigateur ignore espaces et caracteres de controle a l'analyse d'une adresse :
    // « java\nscript: » s'execute. Le controle porte donc sur la chaine compactee.
    'java\nscript:alert(1)',
    'java\tscript:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'vbscript:msgbox(1)',
    '//exemple.fr/sans-protocole',
    'exemple.fr',
  ])('refuse %s et explique pourquoi', (url) => {
    expect(verifierAdresseDeLien(url)).toBe(MESSAGE_ADRESSE);
  });

  it('laisse passer un champ vide : c’est « à compléter », pas une faute', () => {
    expect(verifierAdresseDeLien('')).toBeNull();
    expect(verifierAdresseDeLien('   ')).toBeNull();
    expect(verifierAdresseDeLien(null)).toBeNull();
    expect(verifierAdresseDeLien('A_COMPLETER')).toBeNull();
  });
});

describe('formulaire de demande', () => {
  const valide = {
    // Le pays est obligatoire depuis que le régime de TVA en dépend (CGV, article 3.9).
    nom: 'Dr Nkounkou', email: 'nkounkou@exemple.cg', pays: 'Congo', consentementRgpd: true,
  };

  it('accepte une saisie complète', () => {
    expect(verifierDemande(valide)).toEqual({});
  });

  it('exige le nom', () => {
    expect(verifierDemande({ ...valide, nom: '   ' })).toHaveProperty('nom');
  });

  it('exige une adresse e-mail plausible', () => {
    expect(verifierDemande({ ...valide, email: '' })).toHaveProperty('email');
    expect(verifierDemande({ ...valide, email: 'pas-une-adresse' })).toHaveProperty('email');
  });

  it('accepte les adresses inhabituelles mais valides', () => {
    // Une expression trop stricte econduit un praticien par son adresse reelle, et il ne
    // revient pas. Le serveur et l'envoi du courriel trancheront.
    for (const email of ['o.n+evc@sous-domaine.exemple.cg', 'a_b@exemple.africa']) {
      expect(verifierDemande({ ...valide, email })).toEqual({});
    }
  });

  it('exige le consentement, qui n’est pas une case parmi d’autres', () => {
    const erreurs = verifierDemande({ ...valide, consentementRgpd: false });
    expect(erreurs.consentementRgpd).toContain('consentement');
  });

  it('exige le pays, parce que le régime de TVA en dépend', () => {
    // Hors Union européenne, la prestation est facturée hors taxe au même montant : sans
    // le pays, la facture ne peut pas être établie correctement.
    expect(verifierDemande({ ...valide, pays: '  ' })).toHaveProperty('pays');
  });

  it('signale TOUS les champs fautifs d’un coup', () => {
    // Les rendre un par un fait recommencer le praticien autant de fois qu'il a d'erreurs.
    // Quatre désormais : nom, e-mail, pays, consentement.
    expect(Object.keys(verifierDemande({}))).toHaveLength(4);
  });
});

describe('badge d’une période', () => {
  it('fait correspondre chaque statut à son badge', () => {
    expect(badgeDePeriode('ouvert')).toBe(BADGES_PERIODE.ouvert);
    expect(badgeDePeriode('ferme')).toBe(BADGES_PERIODE.ferme);
    expect(badgeDePeriode('a_venir')).toBe(BADGES_PERIODE.a_venir);
  });

  it('nomme les trois états en français, pour le visiteur', () => {
    expect(badgeDePeriode('ouvert').libelle).toBe('Ouvert');
    expect(badgeDePeriode('ferme').libelle).toBe('Fermé');
    expect(badgeDePeriode('a_venir').libelle).toBe('À venir');
  });

  it('retombe sur « À venir » pour un statut inconnu', () => {
    // Si le serveur ajoute un etat, une carte sans badge serait plus trompeuse qu'un badge
    // prudent : « a venir » n'annonce aucune echeance et n'invite a rien.
    expect(badgeDePeriode('inconnu')).toBe(BADGES_PERIODE.a_venir);
    expect(badgeDePeriode('')).toBe(BADGES_PERIODE.a_venir);
  });

  it('distingue les trois états par la couleur autant que par le mot', () => {
    const classes = Object.values(BADGES_PERIODE).map((b) => b.classe);
    expect(new Set(classes).size).toBe(3);
  });
});

describe('équivalent en francs CFA', () => {
  it('convertit à la parité fixe et arrondit à 500 FCFA', () => {
    // La parité est FIXE (accord de coopération monétaire) : ces valeurs ne bougeront pas
    // avec les marchés, et un chiffre qui change ici est donc une régression, pas une
    // variation de change.
    expect(equivalentFcfa(89)).toBe(58500);    // 58 380,17 arrondi au demi-millier
    expect(equivalentFcfa(450)).toBe(295000);  // 295 180,65
    expect(equivalentFcfa(650)).toBe(426500);  // 426 372,05
    expect(equivalentFcfa(990)).toBe(649500);  // 649 397,43
  });

  it('arrondit au plus proche, dans les deux sens', () => {
    expect(equivalentFcfa(450)).toBeLessThan(450 * PARITE_EURO_FCFA);     // vers le bas
    expect(equivalentFcfa(89)).toBeGreaterThan(89 * PARITE_EURO_FCFA);    // vers le haut
  });

  it('reste un multiple de 500, pour ne pas feindre une précision inexistante', () => {
    [1, 7, 89, 123, 450, 650, 990, 1100, 5000].forEach(euros => {
      expect(equivalentFcfa(euros) % 500).toBe(0);
    });
  });

  it("s'écrit avec le séparateur de milliers français et une approximation assumée", () => {
    // Le « ≈ » n'est pas décoratif : la facture est en euros, ce montant ne sera jamais
    // encaissé tel quel.
    expect(fcfaLisible(89)).toMatch(/^≈ 58\s500 FCFA$/);
  });

  it('ne rend pas zéro pour un prix nul : zéro euro vaut zéro franc', () => {
    expect(equivalentFcfa(0)).toBe(0);
  });
});
