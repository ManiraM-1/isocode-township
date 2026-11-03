package com.isocode.township.common;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SocietySettingsRepository extends JpaRepository<SocietySettings, Short> {

    default SocietySettings current() {
        return findById(SocietySettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("society_settings row is missing"));
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SocietySettings s where s.id = " + SocietySettings.SINGLETON_ID)
    Optional<SocietySettings> findLockedForBillingRun();

    default SocietySettings lockForBillingRun() {
        return findLockedForBillingRun()
                .orElseThrow(() -> new IllegalStateException("society_settings row is missing"));
    }
}
