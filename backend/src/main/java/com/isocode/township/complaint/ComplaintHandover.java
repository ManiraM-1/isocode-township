package com.isocode.township.complaint;

import com.isocode.township.complaint.AutoAssigner.StaffLoad;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ComplaintHandover {

    private final StaffLoads staffLoads;
    private final ComplaintEventRepository eventRepository;

    public ComplaintHandover(StaffLoads staffLoads, ComplaintEventRepository eventRepository) {
        this.staffLoads = staffLoads;
        this.eventRepository = eventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void handOverIfOwnerLacksTheSkill(Complaint complaint, Instant now) {
        if (!complaint.getStatus().isActive()) {
            return;
        }
        List<StaffLoad> staff = staffLoads.current();
        if (AutoAssigner.suits(complaint.getCategory(), complaint.getAssigneeId(), staff)) {
            return;
        }
        Long previous = complaint.getAssigneeId();
        AutoAssigner.pick(complaint.getCategory(), staff).map(StaffLoad::staffId).ifPresent(staffId -> {
            complaint.assignTo(staffId);
            eventRepository.save(new ComplaintEvent(complaint.getId(), ComplaintEventType.AUTO_ASSIGNED, null,
                    previous == null ? null : previous.toString(), staffId.toString(), null, now));
        });
    }
}
