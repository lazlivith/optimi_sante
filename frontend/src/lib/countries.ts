/**
 * Liste des pays proposés à l'inscription B2B (portée internationale).
 * Codes ISO 3166-1 alpha-2, libellés français. Couverture prioritaire :
 * Afrique (cœur de mission de la plateforme), Europe, Maghreb, Amérique du Nord.
 */
export interface Country {
  code: string;
  name: string;
}

export const COUNTRIES: Country[] = [
  // — Marchés principaux —
  { code: 'FR', name: 'France' },
  { code: 'MA', name: 'Maroc' },
  { code: 'DZ', name: 'Algérie' },
  { code: 'TN', name: 'Tunisie' },
  // — Afrique de l'Ouest —
  { code: 'SN', name: 'Sénégal' },
  { code: 'CI', name: "Côte d'Ivoire" },
  { code: 'ML', name: 'Mali' },
  { code: 'BF', name: 'Burkina Faso' },
  { code: 'NE', name: 'Niger' },
  { code: 'GN', name: 'Guinée' },
  { code: 'BJ', name: 'Bénin' },
  { code: 'TG', name: 'Togo' },
  { code: 'MR', name: 'Mauritanie' },
  { code: 'GM', name: 'Gambie' },
  { code: 'GH', name: 'Ghana' },
  { code: 'NG', name: 'Nigéria' },
  { code: 'SL', name: 'Sierra Leone' },
  { code: 'LR', name: 'Liberia' },
  { code: 'GW', name: 'Guinée-Bissau' },
  { code: 'CV', name: 'Cap-Vert' },
  // — Afrique centrale —
  { code: 'CM', name: 'Cameroun' },
  { code: 'GA', name: 'Gabon' },
  { code: 'CG', name: 'Congo' },
  { code: 'CD', name: 'République démocratique du Congo' },
  { code: 'TD', name: 'Tchad' },
  { code: 'CF', name: 'République centrafricaine' },
  { code: 'GQ', name: 'Guinée équatoriale' },
  { code: 'ST', name: 'Sao Tomé-et-Principe' },
  // — Afrique de l'Est & australe —
  { code: 'MG', name: 'Madagascar' },
  { code: 'MU', name: 'Maurice' },
  { code: 'KM', name: 'Comores' },
  { code: 'DJ', name: 'Djibouti' },
  { code: 'ET', name: 'Éthiopie' },
  { code: 'KE', name: 'Kenya' },
  { code: 'TZ', name: 'Tanzanie' },
  { code: 'UG', name: 'Ouganda' },
  { code: 'RW', name: 'Rwanda' },
  { code: 'BI', name: 'Burundi' },
  { code: 'SO', name: 'Somalie' },
  { code: 'SD', name: 'Soudan' },
  { code: 'ZA', name: 'Afrique du Sud' },
  { code: 'AO', name: 'Angola' },
  { code: 'MZ', name: 'Mozambique' },
  { code: 'ZM', name: 'Zambie' },
  { code: 'ZW', name: 'Zimbabwe' },
  { code: 'BW', name: 'Botswana' },
  { code: 'NA', name: 'Namibie' },
  { code: 'MW', name: 'Malawi' },
  { code: 'SC', name: 'Seychelles' },
  // — Afrique du Nord (suite) —
  { code: 'EG', name: 'Égypte' },
  { code: 'LY', name: 'Libye' },
  // — Europe —
  { code: 'BE', name: 'Belgique' },
  { code: 'CH', name: 'Suisse' },
  { code: 'LU', name: 'Luxembourg' },
  { code: 'ES', name: 'Espagne' },
  { code: 'PT', name: 'Portugal' },
  { code: 'IT', name: 'Italie' },
  { code: 'DE', name: 'Allemagne' },
  { code: 'NL', name: 'Pays-Bas' },
  { code: 'GB', name: 'Royaume-Uni' },
  { code: 'IE', name: 'Irlande' },
  { code: 'AT', name: 'Autriche' },
  { code: 'PL', name: 'Pologne' },
  { code: 'RO', name: 'Roumanie' },
  { code: 'GR', name: 'Grèce' },
  { code: 'SE', name: 'Suède' },
  { code: 'NO', name: 'Norvège' },
  { code: 'DK', name: 'Danemark' },
  { code: 'FI', name: 'Finlande' },
  // — Amérique du Nord —
  { code: 'CA', name: 'Canada' },
  { code: 'US', name: 'États-Unis' },
  // — Moyen-Orient —
  { code: 'AE', name: 'Émirats arabes unis' },
  { code: 'SA', name: 'Arabie saoudite' },
  { code: 'QA', name: 'Qatar' },
  { code: 'KW', name: 'Koweït' },
  { code: 'LB', name: 'Liban' },
  { code: 'JO', name: 'Jordanie' },
  { code: 'TR', name: 'Turquie' },
  // — Autre —
  { code: 'OTHER', name: 'Autre pays' },
];
