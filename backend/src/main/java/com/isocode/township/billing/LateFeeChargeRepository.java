package com.isocode.township.billing;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LateFeeChargeRepository extends Repository<LateFeeCharge, Long> {

    <S extends LateFeeCharge> S save(S charge);

    List<LateFeeCharge> findByFlatIdOrderByToDateAscIdAsc(Long flatId);

    List<LateFeeCharge> findByBillIdOrderByToDateAscIdAsc(Long billId);

    long count();

    @Query("select c.billId as billId, max(c.toDate) as lastChargedDay from LateFeeCharge c "
            + "where c.billId in :billIds group by c.billId")
    List<LastChargedDay> findLastChargedDays(@Param("billIds") Collection<Long> billIds);

    default Map<Long, LocalDate> lastChargedDayByBill(Collection<Long> billIds) {
        if (billIds.isEmpty()) {
            return Map.of();
        }
        return findLastChargedDays(billIds).stream()
                .collect(Collectors.toMap(LastChargedDay::getBillId, LastChargedDay::getLastChargedDay));
    }

    interface LastChargedDay {
        Long getBillId();

        LocalDate getLastChargedDay();
    }
}
