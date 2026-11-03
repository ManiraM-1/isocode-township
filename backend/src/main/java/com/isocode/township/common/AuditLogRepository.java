package com.isocode.township.common;

import java.util.List;
import org.springframework.data.repository.Repository;

public interface AuditLogRepository extends Repository<AuditLog, Long> {

    <S extends AuditLog> S save(S entry);

    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
