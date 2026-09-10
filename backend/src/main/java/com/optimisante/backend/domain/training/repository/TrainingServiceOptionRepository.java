package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.ServiceOptionType;
import com.optimisante.backend.domain.training.entity.TrainingServiceOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingServiceOptionRepository extends JpaRepository<TrainingServiceOption, UUID> {

    /** Ce que le medecin peut souscrire aujourd'hui sur cette formation. */
    List<TrainingServiceOption> findByTrainingIdAndIsActiveTrueOrderByOptionTypeAsc(UUID trainingId);

    /** Catalogue complet, offres retirees comprises : vue de l'administration. */
    List<TrainingServiceOption> findByTrainingIdOrderByOptionTypeAscCreatedAtDesc(UUID trainingId);

    /**
     * L'offre vivante d'un type donne.
     *
     * <p>L'index unique partiel {@code uq_tso_active_type} (V43) garantit qu'il n'y en a
     * qu'une : cette methode peut donc renvoyer un {@code Optional} sans risque.</p>
     */
    Optional<TrainingServiceOption> findByTrainingIdAndOptionTypeAndIsActiveTrue(
            UUID trainingId, ServiceOptionType optionType);
}
