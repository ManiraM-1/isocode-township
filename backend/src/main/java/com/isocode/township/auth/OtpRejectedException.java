package com.isocode.township.auth;

import com.isocode.township.common.UnauthenticatedException;
import java.util.LinkedHashMap;
import java.util.Map;

public class OtpRejectedException extends UnauthenticatedException {

    private final OtpRejection reason;
    private final int attemptsRemaining;

    public OtpRejectedException(OtpRejection reason, int attemptsRemaining) {
        super(reason.message(), properties(reason, attemptsRemaining));
        this.reason = reason;
        this.attemptsRemaining = attemptsRemaining;
    }

    public OtpRejection reason() {
        return reason;
    }

    public int attemptsRemaining() {
        return attemptsRemaining;
    }

    private static Map<String, Object> properties(OtpRejection reason, int attemptsRemaining) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("reason", reason.name());
        if (reason == OtpRejection.INVALID_CODE) {
            properties.put("attemptsRemaining", attemptsRemaining);
        }
        return properties;
    }
}
