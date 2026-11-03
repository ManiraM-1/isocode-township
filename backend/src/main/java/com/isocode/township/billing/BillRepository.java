package com.isocode.township.billing;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillRepository extends JpaRepository<Bill, Long> {

    String INTEREST_CANDIDATE_CONDITION = """
            b.due_date < :upTo
            AND (b.amount_paid < b.amount
                 OR EXISTS (SELECT 1 FROM payment_allocation a
                            WHERE a.bill_id = b.id
                              AND a.allocated_on > GREATEST(b.due_date,
                                    COALESCE((SELECT max(c.to_date) FROM late_fee_charge c WHERE c.bill_id = b.id),
                                             b.due_date))))
            """;

    @Query("select b.flatId from Bill b where b.period = :period")
    Set<Long> findFlatIdsBilledFor(@Param("period") LocalDate period);

    @Query(value = "SELECT b.* FROM bill b WHERE " + INTEREST_CANDIDATE_CONDITION + " ORDER BY b.due_date, b.id",
            nativeQuery = true)
    List<Bill> findInterestCandidates(@Param("upTo") LocalDate upTo);

    @Query(value = "SELECT b.* FROM bill b WHERE b.flat_id = :flatId AND " + INTEREST_CANDIDATE_CONDITION
            + " ORDER BY b.due_date, b.id", nativeQuery = true)
    List<Bill> findInterestCandidatesForFlat(@Param("flatId") Long flatId, @Param("upTo") LocalDate upTo);

    @Query("select b from Bill b where b.flatId = :flatId and b.amountPaid < b.amount order by b.period, b.id")
    List<Bill> findUnpaidByFlat(@Param("flatId") Long flatId);

    List<Bill> findByFlatIdOrderByPeriodDesc(Long flatId);
}
