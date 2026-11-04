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
public class ComplaintEscalator {

    private static final Logger log = LoggerFactory.getLogger(ComplaintEscalator.class);

    private final EscalationRepository escalationRepository;
    private final ComplaintEventRepository eventRepository;
    private final AppUserRepository userRepository;
    private final FlatRepository flatRepository;
    private final OutboxService outboxService;

    public ComplaintEscalator(EscalationRepository escalationRepository, ComplaintEventRepository eventRepository,
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
        Optional<String> reason = EscalationPolicy.reasonFor(complaint.getSafetyTerms(), complaint.getClassifiedBy(),
                complaint.getAiUrgency(), complaint.getAiReason());
        if (reason.isEmpty()) {
            return Optional.empty();
        }
        Optional<AppUser> dutyManager = dutyManager();
        if (dutyManager.isEmpty()) {
            log.error("Complaint {} could not be escalated: no active help-desk staff", complaint.getRefNo());
            return Optional.empty();
        }
        Escalation escalation = escalationRepository.save(
                new Escalation(complaint.getId(), dutyManager.get().getId(), reason.get(), now));
        eventRepository.save(new ComplaintEvent(complaint.getId(), ComplaintEventType.ESCALATED, null, null,
                dutyManager.get().getId().toString(), reason.get(), now));
        String flatCode = flatRepository.findById(complaint.getFlatId()).map(Flat::getCode).orElse("a flat");
        outboxService.send(dutyManager.get().getId(), MessageChannel.SMS, alertText(complaint, flatCode));
        return Optional.of(escalation);
    }

    private static String alertText(Complaint complaint, String flatCode) {
        String why = complaint.getSafetyTerms().isEmpty()
                ? "rated critical by the AI"
                : String.join(", ", complaint.getSafetyTerms());
        return "URGENT complaint " + complaint.getRefNo() + " at " + flatCode + " (" + why + "). Open the help desk now.";
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
