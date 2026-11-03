package com.isocode.township.matching;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownUpiIdRepository extends JpaRepository<KnownUpiId, Long> {

    Optional<KnownUpiId> findByUpiId(String upiId);
}
