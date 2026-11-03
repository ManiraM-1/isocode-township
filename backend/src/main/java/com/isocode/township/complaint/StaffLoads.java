package com.isocode.township.complaint;

import com.isocode.township.auth.AppUser;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.auth.Role;
import com.isocode.township.complaint.AutoAssigner.StaffLoad;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class StaffLoads {

    private final AppUserRepository userRepository;
    private final ComplaintRepository complaintRepository;

    public StaffLoads(AppUserRepository userRepository, ComplaintRepository complaintRepository) {
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
    }

    public List<StaffLoad> current() {
        Map<Long, Long> openByStaff = complaintRepository.countOpenByAssignee().stream()
                .collect(Collectors.toMap(ComplaintRepository.OpenLoad::getStaffId,
                        ComplaintRepository.OpenLoad::getOpenComplaints));
        return userRepository.findByRoleAndActiveTrue(Role.HELPDESK).stream()
                .map(staff -> new StaffLoad(staff.getId(), staff.getSkills(),
                        openByStaff.getOrDefault(staff.getId(), 0L).intValue()))
                .toList();
    }

    public boolean isActiveStaff(Long userId) {
        return userRepository.findById(userId)
                .filter(AppUser::isActive)
                .filter(user -> user.getRole() == Role.HELPDESK)
                .isPresent();
    }
}
