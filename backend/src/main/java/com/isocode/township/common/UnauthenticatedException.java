package com.isocode.township.common;

import java.util.Map;

public class UnauthenticatedException extends RuntimeException {

    private final Map<String, Object> properties;

    public UnauthenticatedException(String detail) {
        this(detail, Map.of());
    }

    public UnauthenticatedException(String detail, Map<String, Object> properties) {
        super(detail);
        this.properties = Map.copyOf(properties);
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
