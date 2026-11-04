package com.isocode.township.complaint;

import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.auth.Role;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffController {

    private final AppUserRepository userRepository;

    public StaffController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/api/desk/staff")
    public List<StaffMember> staff() {
        return userRepository.findByRoleAndActiveTrue(Role.HELPDESK).stream()
                .map(user -> new StaffMember(user.getId(), user.getFullName(), user.getSkills(), user.isDutyManager()))
                .sorted(Comparator.comparing(StaffMember::name))
                .toList();
    }

    public record StaffMember(Long id, String name, Set<ComplaintCategory> skills, boolean dutyManager) {
    }
}
