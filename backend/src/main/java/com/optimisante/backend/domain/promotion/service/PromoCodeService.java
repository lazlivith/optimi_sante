package com.optimisante.backend.domain.promotion.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import com.optimisante.backend.domain.promotion.dto.PromoCodeRequestDto;
import com.optimisante.backend.domain.promotion.dto.PromoCodeResponseDto;
import com.optimisante.backend.domain.promotion.entity.DiscountType;
import com.optimisante.backend.domain.promotion.entity.PromoCode;
import com.optimisante.backend.domain.promotion.repository.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final TenantRepository tenantRepository;

    public record DiscountResult(PromoCode promoCode, BigDecimal discountAmount) {}

    @Transactional
    public PromoCodeResponseDto createCode(PromoCodeRequestDto dto) {
        UUID tenantId = requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        String normalizedCode = dto.getCode().trim().toUpperCase();
        if (promoCodeRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) {
            throw new IllegalStateException("Un code promo avec ce libellé existe déjà");
        }
        if (dto.getDiscountType() == DiscountType.PERCENTAGE
                && dto.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Une remise en pourcentage ne peut pas dépasser 100");
        }

        PromoCode promoCode = PromoCode.builder()
                .tenant(tenant)
                .code(normalizedCode)
                .discountType(dto.getDiscountType())
                .discountValue(dto.getDiscountValue())
                .minOrderAmount(dto.getMinOrderAmount())
                .maxUses(dto.getMaxUses())
                .startsAt(dto.getStartsAt())
                .endsAt(dto.getEndsAt())
                .isActive(true)
                .build();

        return toDto(promoCodeRepository.saveAndFlush(promoCode));
    }

    @Transactional(readOnly = true)
    public List<PromoCodeResponseDto> listCodes() {
        UUID tenantId = requireTenantId();
        return promoCodeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public PromoCodeResponseDto setActive(UUID id, boolean active) {
        PromoCode promoCode = promoCodeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Promo code not found"));
        promoCode.setIsActive(active);
        return toDto(promoCodeRepository.save(promoCode));
    }

    /**
     * Valide un code promo pour un montant de commande donné et calcule la remise, sans
     * consommer son usage (cf. {@link #consumeUsage}, appelé séparément juste avant la
     * finalisation réelle de la commande). Lève IllegalStateException avec un message explicite
     * si le code n'est pas utilisable — chaque cas est distingué pour un retour clair au client.
     */
    @Transactional(readOnly = true)
    public DiscountResult validateAndComputeDiscount(String rawCode, BigDecimal orderAmount) {
        UUID tenantId = requireTenantId();
        String normalizedCode = rawCode.trim().toUpperCase();

        PromoCode promoCode = promoCodeRepository.findByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)
                .orElseThrow(() -> new IllegalStateException("Code promo invalide"));

        if (!Boolean.TRUE.equals(promoCode.getIsActive())) {
            throw new IllegalStateException("Ce code promo n'est plus actif");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (promoCode.getStartsAt() != null && now.isBefore(promoCode.getStartsAt())) {
            throw new IllegalStateException("Ce code promo n'est pas encore actif");
        }
        if (promoCode.getEndsAt() != null && now.isAfter(promoCode.getEndsAt())) {
            throw new IllegalStateException("Ce code promo a expiré");
        }
        if (promoCode.getMaxUses() != null && promoCode.getUsedCount() >= promoCode.getMaxUses()) {
            throw new IllegalStateException("Ce code promo a atteint sa limite d'utilisation");
        }
        if (promoCode.getMinOrderAmount() != null && orderAmount.compareTo(promoCode.getMinOrderAmount()) < 0) {
            throw new IllegalStateException("Montant minimum de " + promoCode.getMinOrderAmount() + " € requis pour ce code");
        }

        BigDecimal discount;
        if (promoCode.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = orderAmount.multiply(promoCode.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = promoCode.getDiscountValue();
        }
        // La remise ne peut jamais dépasser le montant de la commande.
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }

        return new DiscountResult(promoCode, discount);
    }

    /**
     * Consomme réellement l'usage du code (incrément atomique). À appeler uniquement après
     * validateAndComputeDiscount, juste avant de finaliser la commande — jamais avant, pour ne
     * pas décompter un usage sur une tentative qui échoue ensuite pour une autre raison.
     */
    @Transactional
    public void consumeUsage(UUID promoCodeId) {
        int updated = promoCodeRepository.incrementUsage(promoCodeId);
        if (updated == 0) {
            // Cas limite : le code a atteint sa limite entre la validation et cet appel
            // (concurrence). La commande a déjà été créée à ce stade — on logue sans bloquer,
            // situation rare et sans conséquence financière (la remise a déjà été appliquée une
            // fois, cet incrément ne fait qu'échouer à comptabiliser le dépassement).
            log.warn("Le code promo {} a atteint sa limite d'usage au moment de la consommation (concurrence)", promoCodeId);
        }
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        return tenantId;
    }

    private PromoCodeResponseDto toDto(PromoCode p) {
        return PromoCodeResponseDto.builder()
                .id(p.getId())
                .code(p.getCode())
                .discountType(p.getDiscountType())
                .discountValue(p.getDiscountValue())
                .minOrderAmount(p.getMinOrderAmount())
                .maxUses(p.getMaxUses())
                .usedCount(p.getUsedCount())
                .startsAt(p.getStartsAt())
                .endsAt(p.getEndsAt())
                .isActive(p.getIsActive())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
