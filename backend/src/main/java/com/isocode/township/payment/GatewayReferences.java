package com.isocode.township.payment;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class GatewayReferences {

    private static final String PREFIX = "PGTEST-";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder reference = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            reference.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return reference.toString();
    }
}
