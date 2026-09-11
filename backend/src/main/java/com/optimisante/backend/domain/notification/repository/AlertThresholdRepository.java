package com.optimisante.backend.domain.notification.repository;

import com.optimisante.backend.domain.notification.entity.AlertThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertThresholdRepository extends JpaRepository<AlertThreshold, String> {
    List<AlertThreshold> findByEnabledTrue();
}
