package com.optimisante.backend.domain.identity.service;

import com.optimisante.backend.common.email.EmailLog;
import com.optimisante.backend.common.email.EmailLogRepository;
import com.optimisante.backend.domain.identity.dto.PersonalDataDto;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.CompanyProfileRepository;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.orders.entity.Order;
import com.optimisante.backend.domain.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Droit d'accès et de portabilité (RGPD, articles 15 et 20).
 *
 * <p><b>L'utilisateur n'est jamais désigné par un paramètre.</b> Toutes les méthodes lisent
 * l'identité authentifiée, jamais un identifiant reçu de l'appelant. Un endpoint de données
 * personnelles qui accepterait un identifiant en paramètre serait une fuite en attente d'une
 * boucle : c'est le seul point qui compte vraiment dans ce service.</p>
 */
@Service
@RequiredArgsConstructor
public class PersonalDataService {

    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final OrderRepository orderRepository;
    private final EmailLogRepository emailLogRepository;

    @Transactional(readOnly = true)
    public PersonalDataDto collectForCurrentUser() {
        User user = requireCurrentUser();

        PersonalDataDto.Identity identite = PersonalDataDto.Identity.builder()
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .createdAt(user.getCreatedAt() == null ? null : user.getCreatedAt().toOffsetDateTime())
                .paymentProviderId(user.getStripeCustomerId())
                .build();

        PersonalDataDto.Company entreprise = companyProfileRepository.findByUserId(user.getId())
                .map(p -> PersonalDataDto.Company.builder()
                        .companyName(p.getCompanyName())
                        .taxId(p.getTaxId())
                        .vatNumber(p.getVatNumber())
                        .billingAddress(p.getBillingAddress())
                        .country(p.getCountry())
                        .facilityType(p.getFacilityType() == null ? null : p.getFacilityType().name())
                        .contactName(p.getContactName())
                        .b2bDiscountRate(p.getB2bDiscountRate())
                        .build())
                .orElse(null);

        List<PersonalDataDto.OrderLine> commandes = orderRepository.findByUserId(user.getId())
                .stream()
                .sorted(Comparator.comparing(Order::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(o -> PersonalDataDto.OrderLine.builder()
                        .orderNumber(o.getOrderNumber())
                        .createdAt(o.getCreatedAt())
                        .status(o.getStatus() != null ? o.getStatus().name() : null)
                        .paymentStatus(o.getPaymentStatus() != null ? o.getPaymentStatus().name() : null)
                        .paymentMethod(o.getPaymentMethod() != null ? o.getPaymentMethod().name() : null)
                        .isQuote(o.getIsQuote())
                        .totalAmount(o.getTotalAmount())
                        .itemCount(o.getItems() != null ? o.getItems().size() : 0)
                        .build())
                .toList();

        // Les emails recus font partie des donnees detenues sur la personne, meme si elle ne
        // les a pas saisis. Le corps n'est pas conserve (V24) : il n'y a donc rien a restituer
        // de plus que l'entete.
        List<PersonalDataDto.EmailLine> emails = emailLogRepository
                .findByRecipientIgnoreCaseOrderBySentAtDesc(user.getEmail())
                .stream()
                .map(e -> PersonalDataDto.EmailLine.builder()
                        .type(e.getEmailType() != null ? e.getEmailType().name() : null)
                        .subject(e.getSubject())
                        .status(e.getStatus() != null ? e.getStatus().name() : null)
                        .sentAt(e.getSentAt() != null ? e.getSentAt().toOffsetDateTime() : null)
                        .build())
                .toList();

        return PersonalDataDto.builder()
                .identity(identite)
                .company(entreprise)
                .orders(commandes)
                .emails(emails)
                .leadCount(0)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    /**
     * Export CSV du même contenu.
     *
     * <p>Format long — {@code Rubrique;Champ;Valeur} — plutôt qu'un tableau par entité : les
     * données d'un compte sont hétérogènes (identité, entreprise, commandes, emails) et ne
     * partagent aucune colonne. Un seul fichier lisible vaut mieux que quatre à assembler.</p>
     *
     * <p>Le point-virgule et le BOM UTF-8 ne sont pas des détails : sans eux, Excel en
     * configuration française ouvre le fichier sur une seule colonne et affiche « Ã© » à la
     * place des accents. Un export illisible ne satisfait pas le droit d'accès.</p>
     */
    @Transactional(readOnly = true)
    public byte[] exportCsvForCurrentUser() {
        PersonalDataDto d = collectForCurrentUser();
        StringBuilder csv = new StringBuilder();
        csv.append("﻿");                       // BOM : Excel reconnait alors l'UTF-8
        csv.append("Rubrique;Champ;Valeur\n");

        ligne(csv, "Export", "Généré le", d.getGeneratedAt() == null ? "" : d.getGeneratedAt().format(HORODATAGE));

        var i = d.getIdentity();
        ligne(csv, "Identité", "Email", i.getEmail());
        ligne(csv, "Identité", "Prénom", i.getFirstName());
        ligne(csv, "Identité", "Nom", i.getLastName());
        ligne(csv, "Identité", "Téléphone", i.getPhone());
        ligne(csv, "Identité", "Type de compte", i.getRole());
        ligne(csv, "Identité", "Compte créé le", i.getCreatedAt() == null ? "" : i.getCreatedAt().format(HORODATAGE));
        ligne(csv, "Identité", "Identifiant chez le prestataire de paiement", i.getPaymentProviderId());

        if (d.getCompany() != null) {
            var e = d.getCompany();
            ligne(csv, "Entreprise", "Raison sociale", e.getCompanyName());
            ligne(csv, "Entreprise", "Identifiant fiscal", e.getTaxId());
            ligne(csv, "Entreprise", "Numéro de TVA", e.getVatNumber());
            ligne(csv, "Entreprise", "Adresse de facturation", e.getBillingAddress());
            ligne(csv, "Entreprise", "Pays", e.getCountry());
            ligne(csv, "Entreprise", "Type d'établissement", e.getFacilityType());
            ligne(csv, "Entreprise", "Contact", e.getContactName());
            ligne(csv, "Entreprise", "Remise professionnelle (%)",
                    e.getB2bDiscountRate() == null ? "" : e.getB2bDiscountRate().toPlainString());
        }

        for (var o : d.getOrders()) {
            String libelle = (Boolean.TRUE.equals(o.getIsQuote()) ? "Devis " : "Commande ") + o.getOrderNumber();
            ligne(csv, libelle, "Date", o.getCreatedAt() == null ? "" : o.getCreatedAt().format(HORODATAGE));
            ligne(csv, libelle, "Statut", o.getStatus());
            ligne(csv, libelle, "Paiement", o.getPaymentStatus());
            ligne(csv, libelle, "Moyen de paiement", o.getPaymentMethod());
            ligne(csv, libelle, "Montant total (€)", o.getTotalAmount() == null ? "" : o.getTotalAmount().toPlainString());
            ligne(csv, libelle, "Nombre d'articles", String.valueOf(o.getItemCount()));
        }

        for (var e : d.getEmails()) {
            ligne(csv, "Email reçu", e.getSubject(),
                    (e.getSentAt() == null ? "" : e.getSentAt().format(HORODATAGE))
                            + " · " + (e.getStatus() == null ? "" : e.getStatus()));
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void ligne(StringBuilder csv, String rubrique, String champ, String valeur) {
        csv.append(echapper(rubrique)).append(';')
           .append(echapper(champ)).append(';')
           .append(echapper(valeur)).append('\n');
    }

    /**
     * Échappement CSV.
     *
     * <p>Une adresse de facturation contient des retours à la ligne et des points-virgules :
     * sans guillemets, chacun décalerait toutes les colonnes suivantes et l'export deviendrait
     * illisible à partir de la première adresse un peu longue.</p>
     */
    private static String echapper(String valeur) {
        if (valeur == null || valeur.isEmpty()) {
            return "";
        }
        String v = valeur.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private User requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("Authentification requise.");
        }
        UUID userId = UUID.fromString(auth.getPrincipal().toString());
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Compte introuvable."));
    }
}
