package com.isocode.township.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String detail) {
        super(detail);
    }
}
