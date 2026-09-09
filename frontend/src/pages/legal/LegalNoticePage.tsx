import { LegalLayout, Ligne, Manquant } from './LegalLayout';
import { LEGAL, ou } from '../../config/legal';
import { usePageMeta } from '../../hooks/usePageMeta';

/**
 * Mentions légales — obligations de l'article 6-III de la LCEN.
 *
 * <p>Les valeurs viennent de `config/legal.ts` et non du texte : un identifiant réglementaire
 * écrit en dur dans une page se corrige par un redéploiement applicatif, ce qui est le plus
 * sûr moyen de le laisser faux.</p>
 */
export function LegalNoticePage() {
  usePageMeta('Mentions légales',
    "Éditeur, directeur de la publication, hébergeur et propriété intellectuelle du site Optimi Santé.");

  return (
    <LegalLayout
      titre="Mentions légales"
      chapo="Informations relatives à l'éditeur et à l'hébergeur de la plateforme."
    >
      <h2>Éditeur du site</h2>
      <dl>
        <Ligne label="Dénomination sociale">{LEGAL.raisonSociale}</Ligne>
        <Ligne label="Forme juridique">{LEGAL.formeJuridique}</Ligne>
        <Ligne label="Capital social"><Manquant valeur={ou(LEGAL.capitalSocial)} /></Ligne>
        <Ligne label="Siège social">
          <Manquant valeur={ou(LEGAL.adresseSiege)} />
          {LEGAL.adresseSiege ? null : <span className="text-slate-400"> — {LEGAL.villeSiege}</span>}
        </Ligne>
        <Ligne label="SIREN"><Manquant valeur={ou(LEGAL.siren)} /></Ligne>
        <Ligne label="SIRET"><Manquant valeur={ou(LEGAL.siret)} /></Ligne>
        <Ligne label="RCS"><Manquant valeur={ou(LEGAL.rcs)} /></Ligne>
        <Ligne label="N° de TVA intracommunautaire"><Manquant valeur={ou(LEGAL.tvaIntracom)} /></Ligne>
        <Ligne label="Courriel">
          <a href={`mailto:${LEGAL.emailContact}`}>{LEGAL.emailContact}</a>
        </Ligne>
        <Ligne label="Téléphone"><Manquant valeur={ou(LEGAL.telephone)} /></Ligne>
      </dl>

      <h2>Directeur de la publication</h2>
      <p>
        <strong>{LEGAL.directeurPublication}</strong>, fondateur et dirigeant de{' '}
        {LEGAL.raisonSociale}.
      </p>

      <h2>Activité</h2>
      <p>{LEGAL.objetSocial}</p>
      <p>
        La plateforme réunit trois activités distinctes : la vente d'équipements et de
        consommables médicaux aux professionnels et aux particuliers, l'ingénierie de formation
        médicale continue en partenariat avec des établissements de santé, et l'accompagnement
        administratif de la mobilité médicale internationale.
      </p>

      <h2>Hébergement</h2>
      <dl>
        <Ligne label="Hébergeur"><Manquant valeur={ou(LEGAL.hebergeurNom)} /></Ligne>
        <Ligne label="Adresse"><Manquant valeur={ou(LEGAL.hebergeurAdresse)} /></Ligne>
        <Ligne label="Téléphone"><Manquant valeur={ou(LEGAL.hebergeurTelephone)} /></Ligne>
      </dl>
      <p>
        L'infrastructure est déployée sur des serveurs situés dans l'Union européenne, sous
        système Linux durci et conteneurisation Docker. Les données sont hébergées dans l'Union
        européenne conformément au RGPD.
      </p>

      <h2>Propriété intellectuelle</h2>
      <p>
        L'ensemble des contenus de la plateforme — textes, structure, interfaces, bases de
        données, identité visuelle — est protégé par le droit de la propriété intellectuelle et
        demeure la propriété de {LEGAL.raisonSociale}, sauf mention contraire. Toute
        reproduction ou représentation, totale ou partielle, sans autorisation écrite préalable
        est interdite.
      </p>
      <p>
        Les <strong>marques, logos et dénominations des établissements et fournisseurs
        partenaires</strong> — notamment CHU de Bordeaux, Clinique Saint-Louis, SFRI —
        appartiennent à leurs titulaires respectifs. Ils ne sont cités qu'à titre d'information
        sur les partenariats et les gammes distribuées, et leur mention n'emporte aucune
        cession de droits.
      </p>
      <p>
        Les visuels et descriptifs des équipements sont fournis par les fabricants ou leurs
        distributeurs et restent leur propriété.
      </p>

      <h2>Signalement de contenu</h2>
      <p>
        Conformément à la loi pour la confiance dans l'économie numérique, tout contenu jugé
        illicite peut être signalé à l'adresse{' '}
        <a href={`mailto:${LEGAL.emailContact}`}>{LEGAL.emailContact}</a>, en précisant l'URL
        concernée et le motif du signalement.
      </p>

      <h2>Liens</h2>
      <p>
        La plateforme peut renvoyer vers des sites tiers — établissements partenaires,
        fabricants, prestataire de paiement. {LEGAL.raisonSociale} n'exerce aucun contrôle sur
        ces sites et décline toute responsabilité quant à leur contenu.
      </p>
    </LegalLayout>
  );
}
