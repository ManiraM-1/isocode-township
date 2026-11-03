package com.isocode.township;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class SameOriginCsrf {

    public static final String COOKIE = "XSRF-TOKEN";
    public static final String HEADER = "X-XSRF-TOKEN";

    private SameOriginCsrf() {
    }

    public static RequestPostProcessor sameOriginCsrf() {
        return withTokens(UUID.randomUUID().toString(), null);
    }

    public static RequestPostProcessor mismatchedCsrf() {
        return withTokens(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    private static RequestPostProcessor withTokens(String cookieToken, String headerTokenOrNull) {
        return request -> {
            Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            Cookie[] cookies = Arrays.copyOf(existing, existing.length + 1);
            cookies[existing.length] = new Cookie(COOKIE, cookieToken);
            request.setCookies(cookies);
            request.addHeader(HEADER, headerTokenOrNull == null ? cookieToken : headerTokenOrNull);
            return request;
        };
    }
}
