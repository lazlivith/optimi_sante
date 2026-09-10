import { Link } from 'react-router-dom';
import { LegalLayout, Manquant } from './LegalLayout';
import { LEGAL, CONDITIONS_MOBILITE, ou } from '../../config/legal';
import { usePageMeta } from '../../hooks/usePageMeta';

/** Sommaire. Les identifiants servent aussi de cibles aux liens du pied de page. */
const SECTIONS = [
  { id: 'objet', label: '1. Objet' },
  { id: 'negoce', label: '2. Négoce' },
  { id: 'livraison', label: '— Livraison' },
  { id: 'retractation', label: '— Rétractation' },
  { id: 'mobilite', label: '3. Mobilité' },
  { id: 'compte', label: '4. Compte' },
  { id: 'donnees', label: '5. Données' },
  { id: 'responsabilite', label: '6. Responsabilité' },
  { id: 'mediation', label: '7. Médiation' },
  { id: 'droit', label: '8. Droit applicable' },
] as const;

/**
 * Conditions générales de vente et d'utilisation.
 *
 * <p>Deux activités aux régimes très différents cohabitent : la vente d'équipements, et
 * l'accompagnement d'un parcours de mobilité qui s'étale sur des mois et dépend d'une décision
 * consulaire. Les traiter dans un seul jeu de clauses aurait produit un texte flou sur les
 * deux ; le document les sépare explicitement.</p>
 *
 * <p>Les montants et le calendrier de paiement viennent de `config/legal.ts`, alimenté par ce
 * que le serveur applique réellement. Sur un document opposable, un barème écrit à la main
 * finit par diverger du code — et facturer autrement que ce qui a été annoncé est un
 * manquement, pas un détail.</p>
 */
export function TermsPage() {
  usePageMeta('Conditions générales de vente et d\'utilisation',
    "Modalités de commande et de livraison d'équipements médicaux, et conditions du parcours de mobilité et de formation.");

  return (
    <LegalLayout
      titre="Conditions générales de vente et d'utilisation"
      chapo="Elles régissent l'usage de la plateforme, la vente d'équipements et le parcours de mobilité médicale."
      sections={SECTIONS}
    >
      <h2 id="objet">1. Objet et acceptation</h2>
      <p>
        Les présentes conditions régissent les relations entre {LEGAL.raisonSociale}, ci-après
        « la Plateforme », et toute personne physique ou morale utilisant ses services. La
        création d'un compte, la passation d'une commande ou le dépôt d'une candidature
        emportent acceptation pleine et entière des présentes.
      </p>
      <p>
        La Plateforme couvre deux activités aux régimes distincts, traitées séparément ci-après :
        le <strong>négoce d'équipements médicaux</strong> (titre 2) et la{' '}
        <strong>mobilité médicale internationale et la formation</strong> (titre 3).
      </p>

      <h2 id="negoce">2. Négoce d'équipements et de consommables</h2>

      <h3>2.1 Comptes et clientèle</h3>
      <p>
        La Plateforme s'adresse aux particuliers comme aux professionnels de santé. Les comptes
        professionnels peuvent bénéficier de conditions tarifaires spécifiques et de la
        procédure de devis. Certains équipements, dont le prix dépend de la configuration
        retenue, sont proposés <strong>sur devis</strong> et ne peuvent pas être commandés
        directement.
      </p>

      <h3>2.2 Prix</h3>
      <p>
        Les prix affichés s'entendent <strong>toutes taxes comprises</strong>, en euros, hors
        frais de livraison indiqués avant la validation de la commande. Ils peuvent être
        modifiés à tout moment ; le prix applicable est celui affiché au moment de la
        validation de la commande.
      </p>
      <p>
        Les promotions sont valables pendant la période indiquée sur la fiche produit et dans
        la limite des stocks disponibles.
      </p>

      <h3>2.3 Commande et paiement</h3>
      <p>
        La commande est ferme à sa validation et au règlement. Les paiements par carte sont
        traités par un prestataire de paiement agréé ; la Plateforme ne conserve aucune donnée
        de carte bancaire. Le paiement par virement est proposé aux comptes professionnels,
        la commande étant préparée à réception des fonds.
      </p>
      <p>
        Une facture est mise à disposition dans l'espace client après validation du paiement.
      </p>

      <h3 id="livraison" className="scroll-mt-28">2.4 Livraison</h3>
      <p>
        Les délais annoncés sont indicatifs et courent à compter de la validation du paiement.
        Le transfert des risques intervient à la remise du colis. Il appartient au destinataire
        de vérifier l'état du matériel à la réception et d'émettre toute réserve auprès du
        transporteur.
      </p>

      <h3 id="retractation" className="scroll-mt-28">2.5 Droit de rétractation</h3>
      <p>
        Le client <strong>consommateur</strong> dispose d'un délai de{' '}
        <strong>quatorze jours</strong> à compter de la réception pour exercer son droit de
        rétractation, sans motif ni pénalité, conformément aux articles L221-18 et suivants du
        Code de la consommation. Les frais de retour restent à sa charge sauf produit non
        conforme.
      </p>
      <p>
        Ce droit ne s'applique pas, conformément à l'article L221-28 du même code, aux produits
        <strong> descellés après livraison et ne pouvant être retournés pour des raisons
        d'hygiène ou de protection de la santé</strong>, ni aux biens confectionnés selon les
        spécifications du client. Ces catégories concernent une part importante des
        consommables médicaux.
      </p>
      <p>
        Le droit de rétractation ne bénéficie pas aux clients professionnels commandant dans le
        cadre de leur activité.
      </p>

      <h3>2.6 Garanties</h3>
      <p>
        Les produits bénéficient de la garantie légale de conformité et de la garantie contre
        les vices cachés, ainsi que le cas échéant de la garantie commerciale du fabricant, dont
        la durée est précisée sur la fiche produit.
      </p>

      <h2 id="mobilite">3. Mobilité médicale et formation</h2>

      <h3>3.1 Nature du service</h3>
      <p>
        La Plateforme intervient comme <strong>intermédiaire</strong> entre le médecin candidat
        et l'établissement de santé d'accueil. Elle assure la pré-qualification du dossier, sa
        transmission à l'établissement, le suivi administratif et la constitution du dossier de
        demande de visa.
      </p>
      <p>
        <strong>La Plateforme ne délivre ni la formation, ni le visa.</strong> La décision
        d'admission appartient à l'établissement d'accueil ; la décision consulaire appartient
        aux autorités compétentes. Aucune des deux ne peut être garantie.
      </p>

      <h3>3.2 Déroulement du parcours</h3>
      <p>Le parcours se déroule en cinq phases :</p>
      <ul>
        <li><strong>Dépôt</strong> — constitution du dossier et règlement des frais de dossier.</li>
        <li><strong>Sélection</strong> — pré-qualification par la Plateforme, puis examen et
          entretien éventuel par l'établissement d'accueil.</li>
        <li><strong>Procédure et pack logistique</strong> — émission de la convention,
          constitution du dossier consulaire, accompagnement administratif.</li>
        <li><strong>Formation</strong> — déroulement du stage au sein de l'établissement.</li>
        <li><strong>Certification</strong> — délivrance de l'attestation de fin de parcours.</li>
      </ul>
      <p>
        À chaque étape, le candidat suit l'avancement de son dossier et les pièces qui lui sont
        demandées depuis son espace personnel.
      </p>

      <h3>3.3 Frais de dossier</h3>
      <p>
        Les frais de dossier sont <strong>indiqués sur la fiche de chaque formation</strong> et
        rappelés avant tout règlement. À défaut de tarif propre, ils s'élèvent à{' '}
        <strong>{CONDITIONS_MOBILITE.fraisDossier}</strong>. Ils rémunèrent l'instruction du
        dossier et sa transmission à l'établissement, travail effectué indépendamment de l'issue
        de la candidature.
      </p>
      <p>
        Ils sont <strong>non remboursables</strong>, y compris en cas de refus de
        l'établissement d'accueil ou de refus de visa — sauf exercice du droit de rétractation
        dans les conditions du 3.6.
      </p>

      <h3>3.4 Frais de formation — échéancier en deux versements</h3>
      <p>
        Le montant des frais de formation est indiqué sur la fiche de la session concernée. Il
        est réglé en <strong>deux versements</strong> :
      </p>
      <ul>
        <li>
          un <strong>acompte de {CONDITIONS_MOBILITE.partExigeeALAdmission} %</strong> du montant
          total, exigible <strong>à la confirmation d'admission par l'établissement
          d'accueil</strong> ; son règlement confirme définitivement l'inscription ;
        </li>
        <li>
          le <strong>solde, soit {CONDITIONS_MOBILITE.partExigeeAuVisa} %</strong>, exigible
          <strong> à la délivrance du visa</strong> par les autorités consulaires.
        </li>
      </ul>
      <p>
        Aucun règlement de frais de formation n'est demandé avant que l'établissement d'accueil
        n'ait accepté la candidature. <strong>Aucun solde n'est appelé tant que le visa n'est pas
        accordé</strong> : le candidat n'avance pas l'intégralité d'un séjour qui dépend encore
        d'une décision consulaire.
      </p>
      <p>
        Le départ en formation est subordonné au règlement intégral des frais de formation.
      </p>

      <h3>3.5 Annulation, report et force majeure</h3>
      <p>
        <strong>Refus de l'établissement d'accueil</strong> — aucun frais de formation n'est dû.
        Les frais de dossier restent acquis.
      </p>
      <p>
        <strong>Refus de visa consulaire</strong> — dûment justifié par la notification des
        autorités, il ouvre droit au remboursement des frais de formation déjà réglés — soit
        l'acompte, le solde n'étant pas appelé en l'absence de visa — déduction faite des
        prestations déjà exécutées et des frais engagés auprès de l'établissement d'accueil. Les
        frais de dossier restent acquis.
      </p>
      <p>
        <strong>Désistement du candidat</strong> après confirmation d'admission — les frais de
        formation restent dus au prorata des prestations engagées, l'établissement d'accueil
        ayant réservé une place.
      </p>
      <p>
        <strong>Report</strong> — un report sur une session ultérieure peut être accordé sous
        réserve de disponibilité et de l'accord de l'établissement.
      </p>
      <p>
        <strong>Force majeure</strong> — les obligations sont suspendues en cas d'événement
        échappant au contrôle des parties : fermeture de l'établissement, crise sanitaire,
        suspension des procédures consulaires, restriction de circulation. Les parties
        recherchent alors un report ; à défaut, les sommes correspondant aux prestations non
        exécutées sont restituées.
      </p>

      <h3>3.6 Droit de rétractation applicable aux prestations</h3>
      <p>
        Le candidat consommateur dispose de <strong>quatorze jours</strong> à compter de la
        souscription pour se rétracter. S'il a expressément demandé que l'exécution commence
        avant ce délai, il reste redevable des prestations déjà réalisées.
      </p>

      <h3>3.7 Obligations du candidat</h3>
      <p>
        Le candidat garantit l'exactitude et l'authenticité des pièces qu'il transmet. La
        production de documents inexacts ou falsifiés entraîne le rejet immédiat du dossier sans
        remboursement, sans préjudice des suites légales.
      </p>

      <h2 id="compte">4. Compte et sécurité</h2>
      <p>
        L'utilisateur est responsable de la confidentialité de ses identifiants et des actions
        effectuées depuis son compte. Toute utilisation frauduleuse doit être signalée sans
        délai à <a href={`mailto:${LEGAL.emailContact}`}>{LEGAL.emailContact}</a>.
      </p>
      <p>
        La Plateforme peut suspendre un compte en cas de manquement aux présentes conditions ou
        d'usage susceptible de porter atteinte à la sécurité du service.
      </p>

      <h2 id="donnees">5. Données personnelles</h2>
      <p>
        Le traitement des données est décrit dans la{' '}
        <Link to="/politique-confidentialite">politique de confidentialité</Link>. Chaque
        utilisateur peut consulter et exporter ses données depuis la page{' '}
        <Link to="/mes-donnees">Mes données personnelles</Link>.
      </p>

      <h2 id="responsabilite">6. Responsabilité</h2>
      <p>
        La Plateforme est tenue d'une obligation de moyens dans l'accompagnement des dossiers de
        mobilité. Elle ne saurait être tenue responsable des décisions relevant des
        établissements d'accueil, des autorités consulaires ou des transporteurs.
      </p>

      <h2 id="mediation">7. Réclamation et médiation</h2>
      <p>
        Toute réclamation doit être adressée à{' '}
        <a href={`mailto:${LEGAL.emailContact}`}>{LEGAL.emailContact}</a>. À défaut de solution
        amiable dans un délai de deux mois, le client consommateur peut saisir gratuitement le
        médiateur de la consommation :{' '}
        <Manquant valeur={ou(LEGAL.mediateurNom)} />
        {LEGAL.mediateurSite ? <> — <a href={LEGAL.mediateurSite}>{LEGAL.mediateurSite}</a></> : null}.
      </p>
      <p>
        La plateforme européenne de règlement en ligne des litiges est également accessible :{' '}
        <a href="https://ec.europa.eu/consumers/odr" target="_blank" rel="noopener noreferrer">
          ec.europa.eu/consumers/odr
        </a>.
      </p>

      <h2 id="droit">8. Droit applicable</h2>
      <p>
        Les présentes conditions sont soumises au droit français. À défaut de résolution
        amiable, les tribunaux compétents sont ceux du ressort du siège social, sauf disposition
        impérative contraire protégeant le consommateur.
      </p>
    </LegalLayout>
  );
}
