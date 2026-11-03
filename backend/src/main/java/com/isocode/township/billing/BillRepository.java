package com.isocode.township.billing;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillRepository extends JpaRepository<Bill, Long> {

    @Query("select b.flatId from Bill b where b.period = :period")
    Set<Long> findFlatIdsBilledFor(@Param("period") LocalDate period);

    @Query("select b from Bill b where b.amountPaid < b.amount and b.dueDate < :date order by b.dueDate, b.id")
    List<Bill> findUnpaidDueBefore(@Param("date") LocalDate date);

    @Query("select b from Bill b where b.flatId = :flatId and b.amountPaid < b.amount order by b.dueDate, b.id")
    List<Bill> findUnpaidByFlat(@Param("flatId") Long flatId);

    List<Bill> findByFlatIdOrderByPeriodDesc(Long flatId);
}
