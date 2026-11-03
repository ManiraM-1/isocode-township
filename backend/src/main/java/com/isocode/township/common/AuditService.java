package com.isocode.township.common;

import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorUserId, String action, String entityType, Long entityId, Map<String, Object> details) {
        repository.save(new AuditLog(actorUserId, action, entityType, entityId, details, clock.instant()));
    }
}
