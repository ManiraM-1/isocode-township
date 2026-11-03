package com.isocode.township;

import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.auth.JwtService;
import com.isocode.township.auth.SessionCookies;
import jakarta.servlet.http.Cookie;

public class TestSessions {

    public static final String NEERAJ = "+919000000001";
    public static final String SWATI = "+919000000002";
    public static final String AAMIR = "+919000000003";
    public static final String ANJALI = "+919000000101";

    private final JwtService jwtService;
    private final AppUserRepository userRepository;

    public TestSessions(JwtService jwtService, AppUserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public Cookie sessionFor(String phone) {
        return new Cookie(SessionCookies.NAME, jwtService.issue(userRepository.findByPhone(phone).orElseThrow()));
    }
}
