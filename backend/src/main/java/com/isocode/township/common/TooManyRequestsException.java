package com.isocode.township.common;

public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String detail) {
        super(detail);
    }
}
