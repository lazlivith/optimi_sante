package com.optimisante.backend.domain.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "siret_finess", nullable = false, length = 50)
    private String siretFiness;

    @Column(name = "vat_number", length = 50)
    private String vatNumber;

    @Column(name = "billing_address", nullable = false, columnDefinition = "TEXT")
    private String billingAddress;

    @Column(name = "b2b_discount_rate", precision = 5, scale = 2)
    private BigDecimal b2bDiscountRate = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
