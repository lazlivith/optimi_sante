package com.optimisante.backend.domain.exercerenfrance.repository;

import com.optimisante.backend.domain.exercerenfrance.entity.EvcReglage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepotReglages extends JpaRepository<EvcReglage, String> {

    List<EvcReglage> findAllByOrderByCleAsc();
}
