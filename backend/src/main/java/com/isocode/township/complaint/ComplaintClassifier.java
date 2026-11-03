package com.isocode.township.complaint;

public interface ComplaintClassifier {

    Complaint.Classification classify(String description);
}
