package com.optimisante.backend.domain.ai.repository;

import com.optimisante.backend.domain.ai.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
