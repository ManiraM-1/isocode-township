package com.isocode.township.complaint;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {

    Optional<Escalation> findByComplaintId(Long complaintId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Escalation e where e.id = :id")
    Optional<Escalation> findLockedById(@Param("id") Long id);
}
