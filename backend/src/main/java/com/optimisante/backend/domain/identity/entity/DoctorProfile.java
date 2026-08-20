package com.optimisante.backend.domain.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "doctor_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone_whatsapp", nullable = false, length = 30)
    private String phoneWhatsapp;

    @Column(name = "country_of_residence", nullable = false, length = 100)
    private String countryOfResidence;

    @Column(name = "medical_specialty", nullable = false, length = 150)
    private String medicalSpecialty;

    @Column(name = "medical_council_number", length = 100)
    private String medicalCouncilNumber;

    @Column(name = "current_hospital", length = 255)
    private String currentHospital;

    @Column(name = "passport_number", length = 100)
    private String passportNumber;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
