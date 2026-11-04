package com.isocode.township;

import com.isocode.township.ai.AiComplaintModel;
import com.isocode.township.ai.AiUnavailableException;
import com.isocode.township.ai.AiVerdict;
import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.complaint.Urgency;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ScriptedAiModel implements AiComplaintModel {

    public static final AiVerdict ORDINARY = new AiVerdict(ComplaintCategory.OTHER, Urgency.MEDIUM,
            new BigDecimal("0.90"), "Resident complaint", "Scripted verdict for tests");

    private AiVerdict nextVerdict;
    private AiUnavailableException.Cause nextFailure;
    private final List<String> seenDescriptions = new ArrayList<>();

    @Override
    public synchronized AiVerdict classify(String description) {
        seenDescriptions.add(description);
        if (nextFailure != null) {
            throw new AiUnavailableException(nextFailure, "scripted failure");
        }
        return nextVerdict != null ? nextVerdict : ORDINARY;
    }

    public synchronized void willAnswer(AiVerdict verdict) {
        nextVerdict = verdict;
        nextFailure = null;
    }

    public synchronized void willFail(AiUnavailableException.Cause cause) {
        nextFailure = cause;
        nextVerdict = null;
    }

    public synchronized List<String> seenDescriptions() {
        return List.copyOf(seenDescriptions);
    }

    public synchronized void reset() {
        nextVerdict = null;
        nextFailure = null;
        seenDescriptions.clear();
    }
}
