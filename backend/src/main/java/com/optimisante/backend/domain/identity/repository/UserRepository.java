package com.optimisante.backend.domain.identity.repository;

import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<User> findByRole(Role role, Pageable pageable);

    /**
     * Adresses des comptes ACTIFS portant l'un de ces rôles.
     *
     * <p>Sert à prévenir les personnes concernées par un évènement métier. Les comptes
     * désactivés sont écartés : prévenir quelqu'un qui n'a plus accès à l'administration
     * revient à ne prévenir personne, tout en donnant l'impression du contraire.</p>
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT u.email FROM User u WHERE u.isActive = true AND u.role IN :roles")
    java.util.List<String> emailsActifsParRoles(
            @org.springframework.data.repository.query.Param("roles")
            java.util.Collection<Role> roles);

    /** Identifiant de tenant sans charger l'entité User (association LAZY) — utilisé par les notifications. */
    @Query("SELECT u.tenant.id FROM User u WHERE u.id = :id")
    Optional<UUID> findTenantIdById(@Param("id") UUID id);
}
