package com.optimisante.backend.domain.notification.repository;

import com.optimisante.backend.domain.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, NotificationPreference.Id> {

    List<NotificationPreference> findByUserId(UUID userId);
}
