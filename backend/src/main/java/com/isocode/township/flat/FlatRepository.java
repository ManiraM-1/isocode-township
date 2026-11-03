package com.isocode.township.flat;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlatRepository extends JpaRepository<Flat, Long> {

    Optional<Flat> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Flat f where f.id = :id")
    Optional<Flat> findLockedById(@Param("id") Long id);
}
