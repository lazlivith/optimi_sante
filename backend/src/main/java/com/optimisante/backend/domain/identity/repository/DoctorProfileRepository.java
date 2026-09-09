package com.optimisante.backend.domain.identity.repository;

import com.optimisante.backend.domain.identity.entity.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {
    Optional<DoctorProfile> findByUserId(UUID userId);

    /**
     * Chargement groupé, pour les listes qui affichent le nom de plusieurs médecins.
     *
     * <p>Appeler {@link #findByUserId} dans une boucle produit une requête par ligne. Mesuré
     * sur la liste d'administration des dossiers : douze dossiers, douze requêtes sur cette
     * seule table, contre une avec cette méthode.</p>
     */
    List<DoctorProfile> findByUserIdIn(Collection<UUID> userIds);
}
