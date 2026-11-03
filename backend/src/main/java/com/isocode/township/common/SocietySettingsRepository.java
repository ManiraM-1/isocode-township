package com.isocode.township.common;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SocietySettingsRepository extends JpaRepository<SocietySettings, Short> {

    default SocietySettings current() {
        return findById(SocietySettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("society_settings row is missing"));
    }
}
