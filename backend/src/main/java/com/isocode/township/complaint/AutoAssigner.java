package com.isocode.township.complaint;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AutoAssigner {

    private static final Comparator<StaffLoad> FEWEST_OPEN_THEN_LONGEST_SERVING =
            Comparator.comparingInt(StaffLoad::openComplaints).thenComparing(StaffLoad::staffId);

    private AutoAssigner() {
    }

    public static Optional<StaffLoad> pick(ComplaintCategory category, List<StaffLoad> staff) {
        Optional<StaffLoad> specialist = staff.stream()
                .filter(member -> !member.isGeneralDesk())
                .filter(member -> member.skills().contains(category))
                .min(FEWEST_OPEN_THEN_LONGEST_SERVING);
        if (specialist.isPresent()) {
            return specialist;
        }
        return staff.stream().filter(StaffLoad::isGeneralDesk).min(FEWEST_OPEN_THEN_LONGEST_SERVING);
    }

    public static boolean suits(ComplaintCategory category, Long staffId, List<StaffLoad> staff) {
        List<StaffLoad> specialists = staff.stream()
                .filter(member -> !member.isGeneralDesk())
                .filter(member -> member.skills().contains(category))
                .toList();
        List<StaffLoad> eligible = specialists.isEmpty()
                ? staff.stream().filter(StaffLoad::isGeneralDesk).toList()
                : specialists;
        return eligible.stream().anyMatch(member -> member.staffId().equals(staffId));
    }

    public record StaffLoad(Long staffId, Set<ComplaintCategory> skills, int openComplaints) {

        public boolean isGeneralDesk() {
            return skills.containsAll(EnumSet.allOf(ComplaintCategory.class));
        }
    }
}
