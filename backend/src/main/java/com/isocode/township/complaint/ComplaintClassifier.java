package com.isocode.township.complaint;

public interface ComplaintClassifier {

    Complaint.Classification provisional(String description);

    Complaint.Classification classify(String description);
}
