package com.optimisante.backend.domain.audit.service;

import com.optimisante.backend.domain.audit.entity.AuditLog;
import com.optimisante.backend.domain.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistance bas niveau du journal d'audit, isolée dans son propre bean pour que la
 * transaction {@code REQUIRES_NEW} soit réellement appliquée par le proxy Spring (une
 * auto-invocation depuis {@link AuditService} ne serait pas interceptée). Une entrée d'audit
 * ne doit jamais être annulée par le rollback d'une transaction métier voisine, ni faire
 * échouer celle-ci : l'exception éventuelle est avalée ici.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog entry) {
        try {
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log write failed (ignored): {}", e.getMessage());
        }
    }
}
