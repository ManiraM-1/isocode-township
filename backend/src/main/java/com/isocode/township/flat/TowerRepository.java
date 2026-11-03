package com.isocode.township.flat;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TowerRepository extends JpaRepository<Tower, Long> {

    Optional<Tower> findByCode(String code);
}
