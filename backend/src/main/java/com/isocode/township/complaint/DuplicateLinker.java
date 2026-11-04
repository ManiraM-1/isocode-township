package com.isocode.township.complaint;

import com.isocode.township.complaint.DuplicateDetector.Candidate;
import com.isocode.township.flat.FlatRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DuplicateLinker {

    private final ComplaintRepository complaintRepository;
    private final ComplaintEventRepository eventRepository;
    private final FlatRepository flatRepository;
    private final DuplicateProperties properties;
    private final DuplicateDetector detector;

    public DuplicateLinker(ComplaintRepository complaintRepository, ComplaintEventRepository eventRepository,
                           FlatRepository flatRepository, DuplicateProperties properties) {
        this.complaintRepository = complaintRepository;
        this.eventRepository = eventRepository;
        this.flatRepository = flatRepository;
        this.properties = properties;
        this.detector = properties.detector();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void linkIfDuplicate(Complaint complaint, Instant now) {
        if (complaint.getParentId() != null || complaintRepository.existsByParentId(complaint.getId())
                || eventRepository.existsByComplaintIdAndType(complaint.getId(), ComplaintEventType.UNLINKED)) {
            return;
        }
        Long towerId = flatRepository.findById(complaint.getFlatId()).map(flat -> flat.getTower().getId()).orElseThrow();
        Instant since = complaint.getCreatedAt().minus(longestWindow());
        List<Complaint> recent = complaintRepository.recentInTower(towerId, since, ComplaintStatus.CLOSED);
        Map<Long, Complaint> byId = recent.stream().collect(Collectors.toMap(Complaint::getId, Function.identity()));
        detector.findParent(candidate(complaint, towerId), recent.stream().map(c -> candidate(c, towerId)).toList())
                .ifPresent(rootId -> {
                    Complaint root = byId.containsKey(rootId) ? byId.get(rootId)
                            : complaintRepository.findById(rootId).orElseThrow();
                    complaint.linkTo(root.getId());
                    record(complaint, ComplaintEventType.LINKED, null, root.getRefNo(), now);
                    record(root, ComplaintEventType.LINKED, null, complaint.getRefNo(), now);
                });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void relinkAfterCategoryChange(Complaint complaint, Instant now) {
        if (complaint.getParentId() != null) {
            Complaint root = complaintRepository.findById(complaint.getParentId()).orElseThrow();
            if (root.getCategory() == complaint.getCategory()) {
                return;
            }
            unlink(complaint, root, null, now);
        }
        linkIfDuplicate(complaint, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void unlink(Complaint child, Complaint root, Long actorId, Instant now) {
        child.unlink();
        record(child, ComplaintEventType.UNLINKED, actorId, root.getRefNo(), now);
        record(root, ComplaintEventType.UNLINKED, actorId, child.getRefNo(), now);
    }

    private Duration longestWindow() {
        return properties.liftWindow().compareTo(properties.textWindow()) > 0
                ? properties.liftWindow()
                : properties.textWindow();
    }

    private static Candidate candidate(Complaint complaint, Long towerId) {
        return new Candidate(complaint.getId(), complaint.getFlatId(), towerId, complaint.getCategory(),
                complaint.getStatus(), complaint.getCreatedAt(), complaint.getDescription(), complaint.getParentId());
    }

    private void record(Complaint complaint, ComplaintEventType type, Long actorId, String otherRefNo, Instant at) {
        eventRepository.save(new ComplaintEvent(complaint.getId(), type, actorId, null, otherRefNo, null, at));
    }
}
