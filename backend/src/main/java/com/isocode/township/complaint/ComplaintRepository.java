package com.isocode.township.complaint;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByFlatIdOrderByCreatedAtDescIdDesc(Long flatId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Complaint c where c.id = :id")
    Optional<Complaint> findLockedById(@Param("id") Long id);

    @Query("""
            select c from Complaint c
            where c.status in :statuses
              and (:safetyOnly = false or c.safetyEscalated = true
                   or exists (select e.id from Escalation e where e.complaintId = c.id))
              and (:reviewOnly = false or c.needsReview = true)
              and (:category is null or c.category = :category)
              and (:assigneeId is null or c.assigneeId = :assigneeId)
              and (:towerCode is null or c.flatId in (select f.id from Flat f where f.tower.code = :towerCode))
            """)
    List<Complaint> search(@Param("statuses") Collection<ComplaintStatus> statuses,
                           @Param("safetyOnly") boolean safetyOnly,
                           @Param("reviewOnly") boolean reviewOnly,
                           @Param("category") ComplaintCategory category,
                           @Param("assigneeId") Long assigneeId,
                           @Param("towerCode") String towerCode);

    @Query(value = "SELECT nextval('complaint_ref_seq')", nativeQuery = true)
    long nextRefSequence();

    @Query("""
            select c.assigneeId as staffId, count(c) as openComplaints from Complaint c
            where c.assigneeId is not null and c.status in ('OPEN', 'ASSIGNED', 'IN_PROGRESS')
            group by c.assigneeId
            """)
    List<OpenLoad> countOpenByAssignee();

    interface OpenLoad {
        Long getStaffId();

        long getOpenComplaints();
    }
}
