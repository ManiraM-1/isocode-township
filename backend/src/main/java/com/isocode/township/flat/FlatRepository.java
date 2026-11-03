package com.isocode.township.flat;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlatRepository extends JpaRepository<Flat, Long> {

    Optional<Flat> findByCode(String code);
}
