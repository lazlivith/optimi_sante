import {
  Stamp, Home, GraduationCap, Wallet, ShoppingBag, FileText, Building2, CircleHelp,
  type LucideIcon,
} from 'lucide-react';

export interface EntreeService {
  /** Ancre de la section correspondante dans /services. */
  to: string;
  titre: string;
  texte: string;
  icon: LucideIcon;
}

export interface GroupeServices {
  titre: string;
  entrees: EntreeService[];
}

/**
 * Le menu « Services », tel que l'ordinateur et le téléphone l'affichent tous les deux.
 *
 * <p><b>Une seule source.</b> Le panneau déroulant et l'accordéon du menu mobile montrent la
 * même chose sous deux formes. Les décrire à deux endroits garantirait qu'ils divergent : une
 * entrée ajoutée d'un côté manquerait de l'autre, sans que rien ne le signale.</p>
 *
 * <p><b>Rien d'inventé.</b> Chaque entrée pointe vers une section qui existe réellement dans
 * la page Services, et son intitulé est celui de la section. Un menu qui annonce une
 * prestation absente de la page est pire qu'un menu court.</p>
 *
 * <p>Les deux colonnes reprennent les deux métiers de la maison — mobilité médicale d'un
 * côté, négoce et partenariats de l'autre. Ce n'est pas un découpage décoratif : c'est
 * l'organisation réelle de l'activité, et le visiteur sait dès le titre de colonne laquelle
 * le concerne.</p>
 */
export const GROUPES_SERVICES: GroupeServices[] = [
  {
    titre: 'Mobilité médicale',
    entrees: [
      {
        to: '/services#accompagnement',
        titre: 'Accompagnement visa & administratif',
        texte: 'Dossier consulaire, titre de séjour et démarches jusqu’à l’arrivée.',
        icon: Stamp,
      },
      {
        to: '/services#pack',
        titre: 'Le pack logistique',
        texte: 'Logement, transfert et installation à la prise de poste.',
        icon: Home,
      },
      {
        to: '/services#formations',
        titre: 'Le parcours, étape par étape',
        texte: 'De la candidature à l’entrée en établissement partenaire.',
        icon: GraduationCap,
      },
      {
        to: '/services#tarifs',
        titre: 'Frais, règlement et conditions',
        texte: 'Frais de dossier, échéances et conditions de règlement.',
        icon: Wallet,
      },
    ],
  },
  {
    titre: 'Négoce & partenaires',
    entrees: [
      {
        to: '/services#negoce',
        titre: 'Négoce d’équipements médicaux',
        texte: 'Équipements, consommables et mobilier de soin, au catalogue.',
        icon: ShoppingBag,
      },
      {
        to: '/services#devis',
        titre: 'Devis pour les professionnels',
        texte: 'Composez votre demande, nous chiffrons la configuration.',
        icon: FileText,
      },
      {
        to: '/services#partenariat',
        titre: 'Établissements partenaires',
        texte: 'Les CHU et cliniques qui accueillent les praticiens.',
        icon: Building2,
      },
      {
        to: '/services#faq',
        titre: 'Questions fréquentes',
        texte: 'Les réponses aux questions posées avant de s’engager.',
        icon: CircleHelp,
      },
    ],
  },
];
