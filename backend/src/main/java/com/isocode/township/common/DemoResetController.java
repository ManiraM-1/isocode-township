package com.isocode.township.common;

import com.isocode.township.auth.SessionUser;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.demo-mode", havingValue = "true")
public class DemoResetController {

    private final DemoResetService resetService;

    public DemoResetController(DemoResetService resetService) {
        this.resetService = resetService;
    }

    @PostMapping("/api/desk/demo/reset")
    public DemoResetResponse reset(@AuthenticationPrincipal SessionUser staff) {
        return new DemoResetResponse(resetService.reset(staff.userId()));
    }

    public record DemoResetResponse(Instant resetAt) {
    }
}
