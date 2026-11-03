package com.isocode.township.auth;

import com.isocode.township.auth.dto.MeResponse;
import com.isocode.township.auth.dto.OtpRequestBody;
import com.isocode.township.auth.dto.OtpRequestResponse;
import com.isocode.township.auth.dto.OtpVerifyBody;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final OtpService otpService;
    private final JwtService jwtService;
    private final SessionCookies sessionCookies;
    private final MeService meService;

    public AuthController(OtpService otpService, JwtService jwtService, SessionCookies sessionCookies,
                          MeService meService) {
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.sessionCookies = sessionCookies;
        this.meService = meService;
    }

    @PostMapping("/otp/request")
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequestBody body) {
        OtpRequestResult result = otpService.request(body.phone());
        return new OtpRequestResponse(result.expiresIn().toSeconds(), result.demoOtp().orElse(null));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<MeResponse> verifyOtp(@Valid @RequestBody OtpVerifyBody body) {
        AppUser user = otpService.verify(body.phone(), body.code());
        String token = jwtService.issue(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.create(token, jwtService.ttl()).toString())
                .body(meService.describe(user.getId()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString())
                .build();
    }
}
