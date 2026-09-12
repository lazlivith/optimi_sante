package com.optimisante.backend.domain.document.recu;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.document.DocumentKind;
import com.optimisante.backend.domain.document.service.DocumentNumberService;
import com.optimisante.backend.domain.document.service.DocumentRenderer;
import com.optimisante.backend.domain.identity.entity.CompanyProfile;
import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.CompanyProfileRepository;
import com.optimisante.backend.domain.identity.repository.DoctorProfileRepository;
import com.optimisante.backend.domain.identity.repository.PartnerProfileRepository;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.training.entity.DocumentType;
import com.optimisante.backend.domain.training.entity.EnrollmentDocument;
import com.optimisante.backend.domain.training.repository.EnrollmentDocumentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Émet le reçu d'un encaissement : un seul endroit, appelé par tous les parcours de paiement.
 *
 * <p>Avant, un seul encaissement sur cinq produisait un reçu. Les quatre autres — frais de
 * dossier, acompte, solde, options de service — n'en produisaient aucun, chacun ayant son propre
 * chemin de règlement et personne n'ayant recopié le code du premier. Un point d'entrée unique
 * rend l'oubli impossible : un nouveau moyen de paiement hérite du reçu sans une ligne de plus.</p>
 *
 * <p><b>Volontairement non bloquant.</b> Un reçu qui échoue ne doit jamais annuler un paiement
 * déjà encaissé. Mais l'échec est tracé <em>et</em> rejouable : la ligne du registre subsiste sans
 * clé de document, ce qui permet de relancer la génération plus tard — là où l'ancien
 * {@code catch (Exception)} silencieux laissait l'utilisateur devant un « Non disponible » que
 * personne ne voyait passer.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReceiptIssuer {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PaymentReceiptRepository receipts;
    private final DocumentNumberService numeros;
    private final DocumentRenderer renderer;
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentDocumentRepository enrollmentDocumentRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PartnerProfileRepository partnerProfileRepository;

    /**
     * Émet le reçu, ou rend celui qui existe déjà.
     *
     * @return le reçu, ou vide si l'émission a échoué — l'appelant continue dans tous les cas
     */
    @Transactional
    public Optional<PaymentReceipt> emettre(Encaissement encaissement) {
        // Premier filet : le cas courant du webhook rejoué, traité sans lever.
        Optional<PaymentReceipt> deja = receipts.findByReference(encaissement.reference());
        if (deja.isPresent()) {
            log.info("Reçu déjà émis pour l'encaissement {} ({}), aucun doublon créé.",
                    encaissement.reference(), deja.get().getNumero());
            return deja;
        }

        PaymentReceipt recu;
        try {
            recu = enregistrer(encaissement);
        } catch (DataIntegrityViolationException course) {
            // Second filet : deux livraisons simultanées ont passé le premier test ensemble.
            // C'est l'index unique qui tranche, et c'est pour cela qu'il existe.
            log.info("Émission concurrente détectée pour {} : le reçu existant fait foi.",
                    encaissement.reference());
            return receipts.findByReference(encaissement.reference());
        }

        try {
            byte[] pdf = renderer.rendre(DocumentKind.RECU_PAIEMENT, variables(encaissement, recu));
            String cle = storageService.uploadGeneratedPdf(
                    pdf, "docs/receipts", DocumentKind.RECU_PAIEMENT.gabarit() + "-" + recu.getNumero());
            recu.setDocumentKey(cle);
            receipts.save(recu);

            classerAuCoffreFort(encaissement, recu, cle);
            log.info("Reçu {} émis pour {} ({} EUR).",
                    recu.getNumero(), encaissement.motif(), encaissement.montantPaye());
        } catch (Exception e) {
            // Le numéro reste attribué et la ligne subsiste : le reçu est rejouable, et son
            // absence est visible dans le registre plutôt que perdue dans un journal.
            log.error("Reçu {} enregistré mais non produit — à régénérer. Encaissement {}.",
                    recu.getNumero(), encaissement.reference(), e);
        }
        return Optional.of(recu);
    }

    private PaymentReceipt enregistrer(Encaissement e) {
        PaymentReceipt recu = PaymentReceipt.builder()
                .numero(numeros.prochainNumero("RECU_PAIEMENT", "REC"))
                .reference(e.reference())
                .motif(e.motif())
                .montant(e.montantPaye())
                .devise("EUR")
                .beneficiaireUserId(e.payeurUserId())
                .enrollmentId(e.enrollmentId())
                .orderId(e.orderId())
                .emisLe(e.payeLe())
                .build();
        return receipts.saveAndFlush(recu);   // flush : la collision doit se révéler ici
    }

    /**
     * Classe le reçu dans le coffre-fort du dossier, quand le règlement en concerne un.
     *
     * <p>Le coffre-fort est l'endroit où le médecin cherche ses pièces ; un reçu qui n'y figure
     * pas n'existe pas pour lui. Le type {@code PAYMENT_RECEIPT} est invisible au centre
     * partenaire : ce qui touche à l'argent du médecin ne le regarde pas.</p>
     */
    private void classerAuCoffreFort(Encaissement e, PaymentReceipt recu, String cle) {
        if (e.enrollmentId() == null) return;
        enrollmentRepository.findById(e.enrollmentId()).ifPresent(enrollment ->
                enrollmentDocumentRepository.save(EnrollmentDocument.builder()
                        .enrollment(enrollment)
                        .documentType(DocumentType.PAYMENT_RECEIPT)
                        .cloudinaryPublicId(cle)
                        .isVerified(true)      // émis par la plateforme : rien à vérifier
                        .build()));
    }

    /** Assemble ce que le gabarit attend — les noms viennent du gabarit, pas de l'intuition. */
    private Map<String, Object> variables(Encaissement e, PaymentReceipt recu) {
        Map<String, Object> v = new HashMap<>();
        v.put("numero", recu.getNumero());
        v.put("motif", e.motif().libelle());
        v.put("date", e.payeLe().format(JOUR));
        v.put("moyenPaiement", e.moyenPaiement() == null ? "Carte bancaire" : e.moyenPaiement());
        v.put("reference", e.reference());

        NomPayeur payeur = nomDuPayeur(e.payeurUserId());
        v.put("clientNom", payeur.nom());
        v.put("clientEmail", payeur.email());

        v.put("lignes", e.lignes().stream().map(l -> Map.of(
                "designation", l.designation(),
                "quantite", l.quantite(),
                "prixUnitaire", montant(l.prixUnitaire()),
                "total", montant(l.total()))).toList());

        v.put("remise", montant(e.remise()));
        v.put("aRemise", e.remise().signum() > 0);
        v.put("montantPaye", montant(e.montantPaye()));
        return v;
    }

    private static String montant(BigDecimal valeur) {
        return (valeur == null ? BigDecimal.ZERO : valeur).setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',') + " €";
    }

    /**
     * Qui a payé, tel que la personne se reconnaîtra.
     *
     * <p>L'ancien reçu imprimait l'<b>adresse e-mail</b> à la place du nom. Une société doit voir
     * sa raison sociale, un médecin son nom : c'est ce que son service comptable classera.</p>
     */
    private NomPayeur nomDuPayeur(java.util.UUID userId) {
        if (userId == null) return new NomPayeur("—", "");
        return userRepository.findById(userId)
                .map(u -> new NomPayeur(nomLisible(u), u.getEmail()))
                .orElse(new NomPayeur("—", ""));
    }

    /**
     * Le nom sous lequel la personne — ou la société — sera reconnue.
     *
     * <p>L'ordre compte : <b>le profil d'abord</b>. Une société doit voir sa raison sociale sur
     * son reçu, pas le nom de la personne qui a passé la commande, car c'est sous ce nom que son
     * service comptable le classera. Le premier essai de ce reçu affichait l'adresse e-mail deux
     * fois, faute de consulter le profil.</p>
     */
    private String nomLisible(User u) {
        String duProfil = switch (u.getRole()) {
            case CLIENT_B2B -> companyProfileRepository.findByUserId(u.getId())
                    .map(CompanyProfile::getCompanyName).orElse(null);
            case MEDECIN -> doctorProfileRepository.findByUserId(u.getId())
                    .map(p -> (p.getFirstName() + " " + p.getLastName()).trim()).orElse(null);
            case CENTRE_FORMATION -> partnerProfileRepository.findByUserId(u.getId())
                    .map(PartnerProfile::getInstitutionName).orElse(null);
            default -> null;
        };
        if (duProfil != null && !duProfil.isBlank()) return duProfil;

        String prenomNom = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        // Repli sur l'e-mail seulement s'il n'y a vraiment rien d'autre : c'est mieux que rien,
        // mais ce n'est pas un nom, et cela ne doit pas être le cas courant.
        return prenomNom.isBlank() ? u.getEmail() : prenomNom;
    }

    private record NomPayeur(String nom, String email) {}

    /** Les reçus d'une personne, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<PaymentReceipt> recusDe(java.util.UUID userId) {
        return receipts.findByBeneficiaireUserIdOrderByEmisLeDesc(userId);
    }
}
