package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.ProspectLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface ProspectLeadRepository extends JpaRepository<ProspectLead, UUID> {

    /**
     * Une demande existe-t-elle déjà pour ce couple (adresse, formation) depuis {@code depuis} ?
     *
     * <p><b>Pourquoi cette question est posée avant d'enregistrer.</b> La route est publique :
     * n'importe qui peut la rappeler en boucle, et chaque appel enverrait un courriel à toute
     * l'administration. La demande, elle, continue d'être enregistrée — c'est l'alerte qu'on
     * ne répète pas, pas la trace.</p>
     *
     * <p>L'adresse est comparée sans égard à la casse : {@code Jean@x.fr} et
     * {@code jean@x.fr} sont la même personne, et un contournement de la règle ne doit pas
     * tenir à une majuscule.</p>
     */
    boolean existsByEmailIgnoreCaseAndTrainingIdAndDownloadedAtAfter(
            String email, UUID trainingId, OffsetDateTime depuis);
}
