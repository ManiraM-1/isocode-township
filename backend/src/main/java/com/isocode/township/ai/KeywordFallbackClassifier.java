package com.isocode.township.ai;

import com.isocode.township.complaint.ComplaintCategory;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KeywordFallbackClassifier {

    public static final BigDecimal CONFIDENCE = new BigDecimal("0.50");

    private static final Map<ComplaintCategory, TermMatcher> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(ComplaintCategory.LIFT, TermMatcher.of(List.of("lift*", "elevator*", "लिफ्ट*")));
        KEYWORDS.put(ComplaintCategory.ELECTRICAL, TermMatcher.of(List.of(
                "light*", "power", "electric*", "socket*", "switch*", "fuse", "mcb", "wiring", "fan*", "spark*",
                "short circuit", "current", "bijli", "batti", "बिजली", "वीज", "लाइट", "करंट")));
        KEYWORDS.put(ComplaintCategory.PLUMBING, TermMatcher.of(List.of(
                "leak*", "water", "tap*", "pipe*", "drain*", "flush*", "toilet*", "tank*", "sink*", "pani",
                "paani", "nal", "पानी", "नल", "पाणी", "नळ", "टाकी")));
        KEYWORDS.put(ComplaintCategory.SECURITY, TermMatcher.of(List.of(
                "guard*", "security", "theft", "stolen", "stranger*", "cctv", "gate", "chor*", "चोर*", "सुरक्षा")));
        KEYWORDS.put(ComplaintCategory.HOUSEKEEPING, TermMatcher.of(List.of(
                "garbage", "dirty", "clean*", "dustbin*", "sweep*", "mop*", "kachra", "safai", "कचरा", "सफाई",
                "स्वच्छता")));
        KEYWORDS.put(ComplaintCategory.CIVIL, TermMatcher.of(List.of(
                "crack*", "plaster", "seepage", "wall*", "ceiling", "door*", "window*", "tile*", "paint*",
                "deewar", "दीवार", "भिंत")));
        KEYWORDS.put(ComplaintCategory.PEST_CONTROL, TermMatcher.of(List.of(
                "cockroach*", "rat", "rats", "mice", "mosquito*", "termite*", "pest*", "bedbug*", "keede",
                "machhar", "मच्छर", "चूहे", "उंदीर", "डास")));
        KEYWORDS.put(ComplaintCategory.PARKING, TermMatcher.of(List.of(
                "parking", "parked", "car", "cars", "slot", "bike*", "two-wheeler*", "vehicle*", "गाड़ी", "पार्किंग")));
        KEYWORDS.put(ComplaintCategory.NOISE, TermMatcher.of(List.of(
                "noise", "noisy", "loud", "music", "party", "shor", "शोर", "आवाज", "आवाज़")));
    }

    private KeywordFallbackClassifier() {
    }

    public static Verdict classify(String description) {
        ComplaintCategory best = ComplaintCategory.OTHER;
        List<String> bestWords = List.of();
        for (Map.Entry<ComplaintCategory, TermMatcher> entry : KEYWORDS.entrySet()) {
            List<String> words = entry.getValue().matchesIn(description);
            if (words.size() > bestWords.size()) {
                best = entry.getKey();
                bestWords = words;
            }
        }
        return new Verdict(best, bestWords);
    }

    public record Verdict(ComplaintCategory category, List<String> matchedWords) {

        public String reason() {
            return matchedWords.isEmpty()
                    ? "Keyword rules found no category words"
                    : "Keyword rules matched " + String.join(", ", matchedWords);
        }
    }
}
