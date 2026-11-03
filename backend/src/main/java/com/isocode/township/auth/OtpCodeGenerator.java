package com.isocode.township.auth;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class OtpCodeGenerator {

    private static final int CODE_SPACE = 1_000_000;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        return "%06d".formatted(random.nextInt(CODE_SPACE));
    }
}
