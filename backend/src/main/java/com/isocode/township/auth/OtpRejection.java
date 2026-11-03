package com.isocode.township.auth;

public enum OtpRejection {
    INVALID_CODE("That code is not correct."),
    EXPIRED("This code has expired or was already used. Request a new code."),
    LOCKED("Too many wrong attempts. Request a new code.");

    private final String message;

    OtpRejection(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
