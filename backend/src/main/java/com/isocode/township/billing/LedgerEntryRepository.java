package com.isocode.township.billing;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {

    <S extends LedgerEntry> S save(S entry);

    List<LedgerEntry> findByFlatIdOrderByEntryDateAscIdAsc(Long flatId);

    long count();

    @Query("select coalesce(sum(e.debit), 0) - coalesce(sum(e.credit), 0) from LedgerEntry e where e.flatId = :flatId")
    BigDecimal balanceOf(@Param("flatId") Long flatId);
}
