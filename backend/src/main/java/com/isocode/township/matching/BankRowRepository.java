package com.isocode.township.matching;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankRowRepository extends JpaRepository<BankRow, Long> {

    List<BankRow> findByImportIdOrderByRowNoAsc(Long importId);

    @Query("""
            select r from BankRow r
            where r.resolution is null and r.decision in ('SUGGESTED', 'CONFLICT', 'UNMATCHED', 'POSSIBLE_DUPLICATE')
            order by r.txnDate asc, r.id asc
            """)
    List<BankRow> findOpenQueue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BankRow r where r.id = :id")
    Optional<BankRow> findLockedById(@Param("id") Long id);
}
