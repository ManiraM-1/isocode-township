ALTER TABLE society_settings
    ADD COLUMN auto_post_limit_months INT NOT NULL DEFAULT 1 CHECK (auto_post_limit_months >= 0);
