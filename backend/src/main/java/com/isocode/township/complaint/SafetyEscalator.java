package com.isocode.township.complaint;

import com.isocode.township.auth.AppUser;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.auth.Role;
import com.isocode.township.common.MessageChannel;
import com.isocode.township.common.OutboxService;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SafetyEscalator {

    private static final Logger log = LoggerFactory.getLogger(SafetyEscalator.class);

    private final EscalationRepository escalationRepository;
    private final ComplaintEventRepository eventRepository;
    private final AppUserRepository userRepository;
    private final FlatRepository flatRepository;
    private final OutboxService outboxService;

    public SafetyEscalator(EscalationRepository escalationRepository, ComplaintEventRepository eventRepository,
                           AppUserRepository userRepository, FlatRepository flatRepository,
                           OutboxService outboxService) {
        this.escalationRepository = escalationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.flatRepository = flatRepository;
        this.outboxService = outboxService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Escalation> escalate(Complaint complaint, Instant now) {
        List<String> terms = complaint.getSafetyTerms();
        if (terms.isEmpty()) {
            return Optional.empty();
        }
        Optional<AppUser> dutyManager = dutyManager();
        if (dutyManager.isEmpty()) {
            log.error("Safety complaint {} could not be escalated: no active help-desk staff", complaint.getRefNo());
            return Optional.empty();
        }
        String reason = "Safety words: " + String.join(", ", terms);
        Escalation escalation = escalationRepository.save(
                new Escalation(complaint.getId(), dutyManager.get().getId(), reason, now));
        eventRepository.save(new ComplaintEvent(complaint.getId(), ComplaintEventType.ESCALATED, null, null,
                dutyManager.get().getId().toString(), reason, now));
        String flatCode = flatRepository.findById(complaint.getFlatId()).map(Flat::getCode).orElse("a flat");
        outboxService.send(dutyManager.get().getId(), MessageChannel.SMS,
                "URGENT safety complaint " + complaint.getRefNo() + " at " + flatCode + " (" + String.join(", ", terms)
                        + "). Open the help desk now.");
        return Optional.of(escalation);
    }

    private Optional<AppUser> dutyManager() {
        List<AppUser> staff = userRepository.findByRoleAndActiveTrue(Role.HELPDESK).stream()
                .sorted(Comparator.comparing(AppUser::getId))
                .toList();
        return staff.stream().filter(AppUser::isDutyManager).findFirst()
                .or(() -> staff.stream()
                        .filter(member -> member.getSkills().containsAll(EnumSet.allOf(ComplaintCategory.class)))
                        .findFirst());
    }
}
