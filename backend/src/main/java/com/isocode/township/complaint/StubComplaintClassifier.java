package com.isocode.township.complaint;

import org.springframework.stereotype.Component;

@Component
public class StubComplaintClassifier implements ComplaintClassifier {

    @Override
    public Complaint.Classification classify(String description) {
        return new Complaint.Classification(ComplaintCategory.OTHER, Urgency.MEDIUM, null, null, null, null,
                ClassifiedBy.RULES_FALLBACK, false);
    }
}
