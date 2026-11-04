package com.isocode.township.ai;

import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.complaint.Urgency;
import java.math.BigDecimal;

public record AiVerdict(ComplaintCategory category, Urgency urgency, BigDecimal confidence, String summary,
                        String reason) {
}
