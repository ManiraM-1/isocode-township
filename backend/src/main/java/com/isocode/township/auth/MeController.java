package com.isocode.township.auth;

import com.isocode.township.auth.dto.MeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal SessionUser user) {
        return meService.describe(user.userId());
    }
}
