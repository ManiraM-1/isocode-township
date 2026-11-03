package com.isocode.township.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {

    <S extends LedgerEntry> S save(S entry);

    List<LedgerEntry> findByFlatIdOrderByEntryDateAscIdAsc(Long flatId);

    long count();

    Optional<LedgerEntry> findFirstByRefTypeAndRefId(String refType, Long refId);

    @Query(value = "SELECT p.id AS paymentId, p.receipt_no AS receiptNo FROM payment p WHERE p.flat_id = :flatId",
            nativeQuery = true)
    List<PaymentReceipt> findReceiptsForFlat(@Param("flatId") Long flatId);

    interface PaymentReceipt {
        Long getPaymentId();

        String getReceiptNo();
    }

    @Query("select coalesce(sum(e.debit), 0) - coalesce(sum(e.credit), 0) from LedgerEntry e where e.flatId = :flatId")
    BigDecimal balanceOf(@Param("flatId") Long flatId);
}
