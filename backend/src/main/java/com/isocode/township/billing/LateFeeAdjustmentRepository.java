package com.isocode.township.billing;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface LateFeeAdjustmentRepository extends Repository<LateFeeAdjustment, Long> {

    <S extends LateFeeAdjustment> S save(S adjustment);

    List<LateFeeAdjustment> findByLateFeeChargeIdIn(Collection<Long> chargeIds);
}
