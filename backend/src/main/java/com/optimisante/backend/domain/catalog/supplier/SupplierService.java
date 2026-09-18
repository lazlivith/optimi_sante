package com.optimisante.backend.domain.catalog.supplier;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.catalog.supplier.SupplierDtos.SupplierRequest;
import com.optimisante.backend.domain.catalog.supplier.SupplierDtos.SupplierView;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Fiches fournisseurs : création, modification, activation. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public List<SupplierView> lister() {
        Map<UUID, Long> produits = new HashMap<>();
        supplierRepository.compterProduitsParFournisseur().forEach(ligne ->
                produits.put(UUID.fromString(String.valueOf(ligne.get("id"))),
                        ((Number) ligne.get("total")).longValue()));
        return supplierRepository.findByTenantIdOrderByCompanyNameAsc(requireTenant()).stream()
                .map(f -> vue(f, produits.getOrDefault(f.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierView parId(UUID id) {
        Supplier fournisseur = requireFournisseur(id);
        return vue(fournisseur, supplierRepository.compterProduits(id));
    }

    @Transactional
    public SupplierView creer(SupplierRequest dto, UUID adminId) {
        UUID tenantId = requireTenant();
        String code = code(dto.code(), dto.companyName());
        if (supplierRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) {
            throw new IllegalArgumentException("Le code « " + code + " » est déjà utilisé par un autre fournisseur.");
        }
        Supplier fournisseur = Supplier.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .code(code)
                .companyName(dto.companyName().trim())
                .taxId(vide(dto.taxId()))
                .contactName(vide(dto.contactName()))
                .contactEmail(vide(dto.contactEmail()))
                .contactPhone(vide(dto.contactPhone()))
                .commissionRate(commission(dto.commissionRate()))
                .notes(vide(dto.notes()))
                .isActive(true)
                .createdBy(adminId)
                .build();
        log.info("Fournisseur {} créé ({})", code, dto.companyName());
        return vue(supplierRepository.save(fournisseur), 0);
    }

    @Transactional
    public SupplierView modifier(UUID id, SupplierRequest dto) {
        Supplier fournisseur = requireFournisseur(id);
        String code = code(dto.code(), dto.companyName());
        if (!fournisseur.getCode().equalsIgnoreCase(code)
                && supplierRepository.existsByTenantIdAndCodeIgnoreCase(requireTenant(), code)) {
            throw new IllegalArgumentException("Le code « " + code + " » est déjà utilisé par un autre fournisseur.");
        }
        fournisseur.setCode(code);
        fournisseur.setCompanyName(dto.companyName().trim());
        fournisseur.setTaxId(vide(dto.taxId()));
        fournisseur.setContactName(vide(dto.contactName()));
        fournisseur.setContactEmail(vide(dto.contactEmail()));
        fournisseur.setContactPhone(vide(dto.contactPhone()));
        fournisseur.setCommissionRate(commission(dto.commissionRate()));
        fournisseur.setNotes(vide(dto.notes()));
        return vue(supplierRepository.save(fournisseur), supplierRepository.compterProduits(id));
    }

    /**
     * Activation ou désactivation. Désactiver n'est pas supprimer : les produits livrés restent en
     * vente, et l'historique des imports reste lisible. Seuls les nouveaux dépôts sont fermés.
     */
    @Transactional
    public SupplierView changerActivite(UUID id, boolean actif) {
        Supplier fournisseur = requireFournisseur(id);
        fournisseur.setIsActive(actif);
        return vue(supplierRepository.save(fournisseur), supplierRepository.compterProduits(id));
    }

    // ---------------------------------------------------------------------- OUTILS ----

    private Supplier requireFournisseur(UUID id) {
        Supplier fournisseur = supplierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fournisseur introuvable."));
        if (!fournisseur.getTenant().getId().equals(requireTenant())) {
            throw new IllegalArgumentException("Fournisseur introuvable.");
        }
        return fournisseur;
    }

    /** Code laissé vide : on le dérive de la raison sociale plutôt que d'imposer une saisie de plus. */
    private static String code(String saisi, String raisonSociale) {
        String base = saisi != null && !saisi.isBlank() ? saisi : raisonSociale;
        String code = java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (code.isBlank()) {
            throw new IllegalArgumentException("Le code du fournisseur est vide.");
        }
        return code.length() > 40 ? code.substring(0, 40) : code;
    }

    private static BigDecimal commission(BigDecimal taux) {
        if (taux == null) {
            return BigDecimal.ZERO;
        }
        if (taux.signum() < 0 || taux.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("La commission doit être comprise entre 0 et 100 %.");
        }
        return taux;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static SupplierView vue(Supplier f, long produits) {
        return new SupplierView(f.getId(), f.getCode(), f.getCompanyName(), f.getTaxId(), f.getContactName(),
                f.getContactEmail(), f.getContactPhone(), f.getCommissionRate(), f.getNotes(),
                Boolean.TRUE.equals(f.getIsActive()), produits, f.getCreatedAt());
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant absent du contexte.");
        }
        return tenantId;
    }
}
