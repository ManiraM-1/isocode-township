package com.isocode.township;

import com.isocode.township.ai.AiComplaintModel;
import com.isocode.township.ai.AiUnavailableException;
import com.isocode.township.ai.AiVerdict;
import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.complaint.Urgency;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ScriptedAiModel implements AiComplaintModel {

    public static final AiVerdict ORDINARY = new AiVerdict(ComplaintCategory.OTHER, Urgency.MEDIUM,
            new BigDecimal("0.90"), "Resident complaint", "Scripted verdict for tests");
    private static final long LONGEST_HOLD_SECONDS = 20;

    private AiVerdict nextVerdict;
    private AiUnavailableException.Cause nextFailure;
    private final List<String> seenDescriptions = new ArrayList<>();
    private volatile CountDownLatch gate = new CountDownLatch(0);

    @Override
    public AiVerdict classify(String description) {
        try {
            gate.await(LONGEST_HOLD_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            seenDescriptions.add(description);
            if (nextFailure != null) {
                throw new AiUnavailableException(nextFailure, "scripted failure");
            }
            return nextVerdict != null ? nextVerdict : ORDINARY;
        }
    }

    public synchronized void willAnswer(AiVerdict verdict) {
        nextVerdict = verdict;
        nextFailure = null;
    }

    public synchronized void willFail(AiUnavailableException.Cause cause) {
        nextFailure = cause;
        nextVerdict = null;
    }

    public void holdAnswers() {
        gate = new CountDownLatch(1);
    }

    public void releaseAnswers() {
        gate.countDown();
    }

    public synchronized List<String> seenDescriptions() {
        return List.copyOf(seenDescriptions);
    }

    public synchronized void reset() {
        releaseAnswers();
        nextVerdict = null;
        nextFailure = null;
        seenDescriptions.clear();
    }
}
