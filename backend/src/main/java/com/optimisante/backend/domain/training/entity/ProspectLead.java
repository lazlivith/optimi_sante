package com.optimisante.backend.domain.training.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "prospect_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProspectLead {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id")
    private Training training;

    @Column(nullable = false)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone_whatsapp", nullable = false)
    private String phoneWhatsapp;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String specialty;

    @CreationTimestamp
    @Column(name = "downloaded_at", updatable = false)
    private OffsetDateTime downloadedAt;

    @Column(length = 100)
    private String source;

    @Transient
    public String getFullName() {
        return (this.firstName != null ? this.firstName : "") + " " + (this.lastName != null ? this.lastName : "");
    }
}
