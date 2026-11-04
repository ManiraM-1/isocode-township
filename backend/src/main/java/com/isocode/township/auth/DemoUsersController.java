package com.isocode.township.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.demo-mode", havingValue = "true")
public class DemoUsersController {

    private static final List<DemoPick> PICKS = List.of(
            new DemoPick("+919000000001", "Resident · all dues paid"),
            new DemoPick("+919000000002", "Resident · 47 days overdue, open leak"),
            new DemoPick("+919000000003", "Resident · part-paid bill"),
            new DemoPick("+919000000101", "Help desk · accounts and complaints"),
            new DemoPick("+919000000106", "Help desk · duty manager"));

    private final AppUserRepository userRepository;
    private final MeService meService;

    public DemoUsersController(AppUserRepository userRepository, MeService meService) {
        this.userRepository = userRepository;
        this.meService = meService;
    }

    @GetMapping("/api/auth/demo-users")
    public List<DemoUser> demoUsers() {
        return PICKS.stream()
                .map(pick -> userRepository.findByPhone(pick.phone()).map(user -> describe(user, pick)))
                .flatMap(Optional::stream)
                .toList();
    }

    private DemoUser describe(AppUser user, DemoPick pick) {
        String flatCode = meService.describe(user.getId()).flats().stream()
                .map(flat -> flat.code()).findFirst().orElse(null);
        return new DemoUser(user.getFullName(), pick.phone(), user.getRole(), flatCode, pick.note());
    }

    private record DemoPick(String phone, String note) {
    }

    public record DemoUser(String name, String phone, Role role, String flatCode, String note) {
    }
}
