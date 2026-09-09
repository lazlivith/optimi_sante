import { Link } from 'react-router-dom';
import { LegalLayout, Manquant } from './LegalLayout';
import { LEGAL, ou } from '../../config/legal';
import { usePageMeta } from '../../hooks/usePageMeta';

/**
 * Politique de confidentialité.
 *
 * <p>Elle décrit les traitements <b>réellement effectués</b> par la plateforme, et non un
 * modèle générique : les durées de conservation, les catégories de données et les
 * destinataires correspondent à ce que le code fait. Une politique qui décrit autre chose que
 * le système est une déclaration inexacte, pas une formalité.</p>
 */
export function PrivacyPolicyPage() {
  usePageMeta('Politique de confidentialité',
    "Quelles données Optimi Santé collecte, pourquoi, combien de temps, et comment exercer vos droits.");

  return (
    <LegalLayout
      titre="Politique de confidentialité"
      chapo="Quelles données nous traitons, pourquoi, combien de temps, et comment exercer vos droits."
    >
      <h2>1. Responsable du traitement</h2>
      <p>
        {LEGAL.raisonSociale} ({LEGAL.formeJuridique}), représentée par{' '}
        {LEGAL.directeurPublication}, est responsable des traitements décrits ci-après.
      </p>
      <p>
        Contact dédié à la protection des données :{' '}
        <a href={`mailto:${LEGAL.emailDpo}`}>{LEGAL.emailDpo}</a>.
      </p>

      <h2>2. Données traitées</h2>

      <h3>Toute personne disposant d'un compte</h3>
      <ul>
        <li>Adresse électronique, nom, prénom, téléphone.</li>
        <li>Type de compte et date de création.</li>
        <li>Identifiant client attribué par le prestataire de paiement.</li>
        <li>Journal des courriels qui vous ont été envoyés : objet, date et statut d'envoi.</li>
      </ul>
      <p>
        <strong>Le contenu des courriels n'est pas conservé.</strong> Ce choix n'est pas
        accessoire : le message transmettant des identifiants de connexion contient un mot de
        passe provisoire, et le stocker en aurait multiplié les copies.
      </p>

      <h3>Clients professionnels</h3>
      <ul>
        <li>Raison sociale, identifiant fiscal, numéro de TVA, adresse de facturation, pays,
          type d'établissement et personne de contact.</li>
      </ul>

      <h3>Commandes</h3>
      <ul>
        <li>Historique des commandes et devis, articles, montants, statuts de paiement et de
          livraison, factures.</li>
      </ul>
      <p>
        <strong>Aucune donnée de carte bancaire ne transite ni n'est stockée sur nos serveurs.</strong>{' '}
        Le paiement est traité intégralement par un prestataire agréé.
      </p>

      <h3>Candidats à la mobilité médicale</h3>
      <ul>
        <li>Données professionnelles : spécialité, établissement d'exercice, pays de résidence.</li>
        <li>Pièces justificatives déposées : diplôme, inscription à l'Ordre, passeport,
          justificatifs consulaires et tout document réclamé au cours de la procédure.</li>
        <li>Historique du dossier : décisions, motifs, échanges de pièces.</li>
      </ul>
      <p>
        Ces pièces peuvent contenir des données sensibles. Elles ne sont traitées que dans la
        finalité de la constitution du dossier, sur la base de votre consentement explicite et
        de l'exécution du contrat.
      </p>

      <h2>3. Finalités et bases légales</h2>
      <ul>
        <li><strong>Exécution du contrat</strong> — gestion du compte, des commandes, des
          livraisons et du parcours de mobilité.</li>
        <li><strong>Obligation légale</strong> — conservation des pièces comptables et
          facturation.</li>
        <li><strong>Intérêt légitime</strong> — sécurité de la plateforme, prévention de la
          fraude, amélioration du service.</li>
        <li><strong>Consentement</strong> — traitement des pièces justificatives sensibles du
          dossier de mobilité, et communications non contractuelles.</li>
      </ul>

      <h2>4. Destinataires</h2>
      <p>Vos données ne sont ni vendues, ni louées, ni cédées. Elles sont transmises :</p>
      <ul>
        <li>aux <strong>établissements de santé partenaires</strong>, pour les seuls dossiers
          de candidature qui leur sont transmis — un dossier non transmis leur reste
          invisible ;</li>
        <li>au <strong>prestataire de paiement</strong>, pour le traitement des transactions ;</li>
        <li>au <strong>prestataire d'envoi de courriels</strong> et à l'<strong>hébergeur</strong>,
          agissant comme sous-traitants ;</li>
        <li>aux <strong>autorités consulaires</strong>, lorsque vous nous mandatez pour la
          constitution de votre dossier de visa ;</li>
        <li>aux autorités compétentes, sur réquisition légale.</li>
      </ul>

      <h2>5. Coffre-fort documentaire</h2>
      <p>
        Les pièces justificatives sont conservées dans un espace dédié et ne sont accessibles
        qu'à vous-même, à l'équipe d'Optimi Santé habilitée sur votre dossier, et à
        l'établissement d'accueil une fois votre dossier transmis.
      </p>
      <p>
        Chaque document n'est consultable que par un <strong>lien signé à durée limitée</strong>,
        généré à la demande : aucune pièce n'est accessible par une adresse permanente, et un
        lien obtenu ne reste pas valable indéfiniment.
      </p>

      <h2>6. Durées de conservation</h2>
      <ul>
        <li><strong>Compte utilisateur</strong> — pendant toute la durée de la relation, puis
          trois ans après le dernier contact.</li>
        <li><strong>Commandes, factures et pièces comptables</strong> —{' '}
          <strong>dix ans</strong> à compter de la clôture de l'exercice, en application de
          l'article L123-22 du Code de commerce. Cette durée s'impose à nous : elle survit à une
          demande d'effacement.</li>
        <li><strong>Dossiers de mobilité et pièces justificatives</strong> — cinq ans après la
          fin du parcours, afin de pouvoir justifier de la procédure suivie.</li>
        <li><strong>Journal des courriels</strong> — trois ans, à des fins de preuve d'envoi.</li>
      </ul>

      <h2>7. Vos droits</h2>
      <p>
        Vous disposez des droits d'accès, de rectification, d'effacement, de limitation,
        d'opposition et de portabilité prévus par le RGPD.
      </p>
      <p>
        <strong>Le droit d'accès et de portabilité s'exerce directement en ligne</strong> :
        la page <Link to="/mes-donnees">Mes données personnelles</Link> affiche ce que nous
        détenons sur vous et permet d'en télécharger une copie au format CSV, immédiatement et
        sans démarche.
      </p>
      <p>
        La plupart de vos informations sont modifiables depuis{' '}
        <Link to="/profile">Mon profil</Link>. Pour les autres demandes — effacement,
        limitation, opposition — écrivez à{' '}
        <a href={`mailto:${LEGAL.emailDpo}`}>{LEGAL.emailDpo}</a>. Nous répondons dans un délai
        d'un mois.
      </p>
      <p>
        Vous pouvez également introduire une réclamation auprès de la Commission nationale de
        l'informatique et des libertés :{' '}
        <a href="https://www.cnil.fr" target="_blank" rel="noopener noreferrer">cnil.fr</a>.
      </p>

      <h2>8. Sécurité</h2>
      <p>
        Les échanges sont chiffrés en transit. Les mots de passe ne sont jamais stockés en
        clair. L'accès aux données est cloisonné par rôle : l'administration du négoce n'accède
        pas aux dossiers médicaux, l'administration de la mobilité n'accède pas aux factures
        clients, et un établissement partenaire ne voit que les dossiers qui lui ont été
        transmis. Ce cloisonnement est appliqué par le serveur, et non seulement masqué à
        l'écran.
      </p>

      <h2>9. Transferts hors Union européenne</h2>
      <p>
        Les données sont hébergées dans l'Union européenne. Certains sous-traitants techniques
        peuvent être établis hors UE ; ces transferts sont alors encadrés par les clauses
        contractuelles types de la Commission européenne.
      </p>

      <h2>10. Cookies</h2>
      <p>
        La plateforme utilise les cookies et le stockage local strictement nécessaires à son
        fonctionnement : session de connexion et contenu du panier. Ils ne requièrent pas de
        consentement préalable. Aucun cookie publicitaire ni de mesure d'audience tierce n'est
        déposé.
      </p>

      <h2>11. Hébergement</h2>
      <p>
        Hébergeur : <Manquant valeur={ou(LEGAL.hebergeurNom)} />. Les serveurs et les bases de
        données sont situés dans l'Union européenne.
      </p>
    </LegalLayout>
  );
}
