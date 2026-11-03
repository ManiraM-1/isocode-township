package com.isocode.township.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends Repository<Payment, Long> {

    <S extends Payment> S save(S payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByReceiptNo(String receiptNo);

    Optional<Payment> findByGatewayRef(String gatewayRef);

    List<Payment> findByFlatIdOrderByPaidOnAscIdAsc(Long flatId);

    long count();

    @Query(value = "SELECT nextval('receipt_number_seq')", nativeQuery = true)
    long nextReceiptSequence();

    @Query(value = """
            SELECT p.* FROM payment p
            WHERE p.flat_id = :flatId
              AND p.amount > (SELECT coalesce(sum(a.amount), 0) FROM payment_allocation a WHERE a.payment_id = p.id)
            ORDER BY p.paid_on, p.id
            """, nativeQuery = true)
    List<Payment> findWithUnallocatedCredit(@Param("flatId") Long flatId);
}
