package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.infrastructure.legal.CompanyIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ordre de virement SEPA d'un reversement, à importer dans la banque d'Optimi Santé.
 *
 * <p><b>Format.</b> {@code pain.001.001.03} (ISO 20022, virement SEPA), celui qu'acceptent les
 * banques françaises à l'import de fichiers. Un fichier = un reversement = un virement, avec pour
 * identifiant de bout en bout la référence du reversement : c'est elle qui arrive sur le relevé du
 * CHU, et qui permet de rapprocher le virement du bordereau.</p>
 *
 * <p><b>La plateforme n'exécute pas le virement.</b> Elle produit l'ordre ; la banque l'exécute
 * après import et validation par une personne habilitée. Aucun argent ne part sans elle — c'est
 * voulu : un clic mal placé dans l'administration ne doit pas pouvoir vider un compte.</p>
 *
 * <p><b>Refus explicites plutôt que fichier bancal.</b> Sans IBAN pour Optimi Santé (donneur
 * d'ordre, {@code LEGAL_IBAN}) ou pour le CHU, le fichier serait rejeté par la banque — souvent
 * sans message clair. On refuse de le produire en disant ce qui manque.</p>
 */
@Service
@RequiredArgsConstructor
public class SepaVirementService {

    private static final String ESPACE_NOMS = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03";

    private final PartnerPayoutRepository payoutRepository;
    private final EnrollmentPaymentRepository paymentRepository;
    private final CompanyIdentity identite;

    public record FichierSepa(String nomFichier, byte[] contenu) {
    }

    @Transactional(readOnly = true)
    public FichierSepa ordreDeVirement(UUID payoutId) {
        PartnerPayout reversement = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new IllegalArgumentException("Reversement introuvable"));
        if (reversement.getStatus() == PayoutStatus.PAID) {
            // Réimporter l'ordre d'un virement déjà exécuté paierait le CHU une seconde fois.
            throw new IllegalStateException("Ce reversement est déjà viré : aucun ordre de virement n'est "
                    + "produit, pour qu'il ne puisse pas être exécuté deux fois.");
        }
        if (reversement.getStatus() == PayoutStatus.CANCELLED) {
            throw new IllegalStateException("Ce reversement est annulé.");
        }

        if (identite.iban() == null || identite.iban().isBlank()) {
            throw new IllegalStateException("L'IBAN d'Optimi Santé n'est pas renseigné (LEGAL_IBAN) : "
                    + "le fichier n'a pas de compte à débiter. Renseignez-le, puis réessayez.");
        }
        String ibanDonneur = CoordonneesBancaires.validerIban(identite.iban());
        String bicDonneur = CoordonneesBancaires.validerBic(identite.bic());

        PartnerProfile chu = reversement.getPartnerProfile();
        if (chu.getIban() == null || chu.getBankAccountHolder() == null) {
            throw new IllegalStateException("Les coordonnées bancaires de " + chu.getInstitutionName()
                    + " ne sont pas renseignées : aucun compte à créditer.");
        }

        List<EnrollmentPayment> lignes = paymentRepository.findByPartnerPayoutId(payoutId);
        BigDecimal montant = reversement.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        String reference = CoordonneesBancaires.texteSepa(reversement.getReference(), 35);

        try {
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            XMLStreamWriter x = XMLOutputFactory.newInstance()
                    .createXMLStreamWriter(sortie, StandardCharsets.UTF_8.name());
            x.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            x.writeStartElement("Document");
            x.writeDefaultNamespace(ESPACE_NOMS);
            x.writeStartElement("CstmrCdtTrfInitn");

            x.writeStartElement("GrpHdr");
            element(x, "MsgId", reference);
            element(x, "CreDtTm", OffsetDateTime.now(ZoneOffset.UTC).withNano(0)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            element(x, "NbOfTxs", "1");
            element(x, "CtrlSum", montant.toPlainString());
            x.writeStartElement("InitgPty");
            element(x, "Nm", nomDonneur());
            x.writeEndElement();
            x.writeEndElement(); // GrpHdr

            x.writeStartElement("PmtInf");
            element(x, "PmtInfId", reference);
            element(x, "PmtMtd", "TRF");
            element(x, "BtchBookg", "false");
            element(x, "NbOfTxs", "1");
            element(x, "CtrlSum", montant.toPlainString());
            x.writeStartElement("PmtTpInf");
            x.writeStartElement("SvcLvl");
            element(x, "Cd", "SEPA");
            x.writeEndElement();
            x.writeEndElement();
            element(x, "ReqdExctnDt", LocalDate.now().toString());
            x.writeStartElement("Dbtr");
            element(x, "Nm", nomDonneur());
            x.writeEndElement();
            compte(x, "DbtrAcct", ibanDonneur);
            agent(x, "DbtrAgt", bicDonneur);
            // Frais partagés : seule option admise pour un virement SEPA.
            element(x, "ChrgBr", "SLEV");

            x.writeStartElement("CdtTrfTxInf");
            x.writeStartElement("PmtId");
            element(x, "EndToEndId", reference);
            x.writeEndElement();
            x.writeStartElement("Amt");
            x.writeStartElement("InstdAmt");
            x.writeAttribute("Ccy", "EUR");
            x.writeCharacters(montant.toPlainString());
            x.writeEndElement();
            x.writeEndElement();
            agent(x, "CdtrAgt", chu.getBic());
            x.writeStartElement("Cdtr");
            element(x, "Nm", CoordonneesBancaires.texteSepa(chu.getBankAccountHolder(), 70));
            x.writeEndElement();
            compte(x, "CdtrAcct", chu.getIban());
            x.writeStartElement("RmtInf");
            // Ce que lira le comptable du CHU : la référence, puis les dossiers réglés.
            element(x, "Ustrd", CoordonneesBancaires.texteSepa(reversement.getReference() + " " + libelleDossiers(lignes), 140));
            x.writeEndElement();
            x.writeEndElement(); // CdtTrfTxInf

            x.writeEndElement(); // PmtInf
            x.writeEndElement(); // CstmrCdtTrfInitn
            x.writeEndElement(); // Document
            x.writeEndDocument();
            x.close();
            return new FichierSepa(reversement.getReference() + ".xml", sortie.toByteArray());
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Production du fichier SEPA impossible", e);
        }
    }

    private String nomDonneur() {
        String nom = identite.denomination();
        return CoordonneesBancaires.texteSepa(nom == null || nom.isBlank() ? "Optimi Sante" : nom, 70);
    }

    private String libelleDossiers(List<EnrollmentPayment> lignes) {
        return lignes.stream()
                .map(l -> ReferencesReversement.codeDossier(l.getEnrollment()) + " "
                        + ReferencesReversement.codeTranche(l.getInstallment()))
                .distinct()
                .collect(Collectors.joining(" "));
    }

    private static void element(XMLStreamWriter x, String nom, String valeur) throws XMLStreamException {
        x.writeStartElement(nom);
        x.writeCharacters(valeur);
        x.writeEndElement();
    }

    private static void compte(XMLStreamWriter x, String nom, String iban) throws XMLStreamException {
        x.writeStartElement(nom);
        x.writeStartElement("Id");
        element(x, "IBAN", iban);
        x.writeEndElement();
        x.writeEndElement();
    }

    /** Sans BIC, la norme admet « NOTPROVIDED » : la banque le retrouve depuis l'IBAN. */
    private static void agent(XMLStreamWriter x, String nom, String bic) throws XMLStreamException {
        x.writeStartElement(nom);
        x.writeStartElement("FinInstnId");
        if (bic != null && !bic.isBlank()) {
            element(x, "BIC", bic);
        } else {
            x.writeStartElement("Othr");
            element(x, "Id", "NOTPROVIDED");
            x.writeEndElement();
        }
        x.writeEndElement();
        x.writeEndElement();
    }
}
