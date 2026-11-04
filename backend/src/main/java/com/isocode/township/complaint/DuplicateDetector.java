package com.isocode.township.complaint;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class DuplicateDetector {

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "am", "it", "its", "this", "that", "there",
            "in", "on", "at", "of", "to", "from", "for", "with", "and", "or", "but", "not", "no", "since", "near",
            "my", "our", "we", "i", "me", "us", "you", "your", "please", "pls", "kindly", "very", "again", "also",
            "has", "have", "had", "do", "does", "did", "so", "too", "all", "some", "any", "today",
            "hai", "hain", "ho", "ka", "ki", "ke", "ko", "se", "mein", "par", "pe", "aur", "bhi", "nahi",
            "है", "हैं", "का", "की", "के", "को", "से", "में", "पर", "और", "भी", "आहे", "आहेत", "आणि", "मध्ये");

    private final Duration liftWindow;
    private final Duration textWindow;
    private final double minimumSimilarity;

    public DuplicateDetector(Duration liftWindow, Duration textWindow, double minimumSimilarity) {
        this.liftWindow = liftWindow;
        this.textWindow = textWindow;
        this.minimumSimilarity = minimumSimilarity;
    }

    public Optional<Long> findParent(Candidate complaint, List<Candidate> recent) {
        return recent.stream()
                .filter(earlier -> isDuplicateOf(complaint, earlier))
                .min(Comparator.comparing(Candidate::createdAt).thenComparing(Candidate::id))
                .map(Candidate::rootId);
    }

    private boolean isDuplicateOf(Candidate complaint, Candidate earlier) {
        if (earlier.id().equals(complaint.id())
                || complaint.id().equals(earlier.parentId())
                || earlier.flatId().equals(complaint.flatId())
                || !earlier.towerId().equals(complaint.towerId())
                || earlier.category() != complaint.category()
                || earlier.status() == ComplaintStatus.CLOSED
                || earlier.createdAt().isAfter(complaint.createdAt())) {
            return false;
        }
        Duration gap = Duration.between(earlier.createdAt(), complaint.createdAt());
        if (complaint.category() == ComplaintCategory.LIFT) {
            return gap.compareTo(liftWindow) <= 0;
        }
        return gap.compareTo(textWindow) <= 0
                && similarity(earlier.description(), complaint.description()) >= minimumSimilarity;
    }

    public static double similarity(String first, String second) {
        Set<String> a = tokens(first);
        Set<String> b = tokens(second);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return (double) shared.size() / all.size();
    }

    static Set<String> tokens(String text) {
        String normalised = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return Arrays.stream(normalised.split("[^\\p{L}\\p{M}\\p{N}]+"))
                .filter(token -> !token.isBlank())
                .filter(token -> !STOPWORDS.contains(token))
                .collect(Collectors.toSet());
    }

    public record Candidate(Long id, Long flatId, Long towerId, ComplaintCategory category, ComplaintStatus status,
                            Instant createdAt, String description, Long parentId) {

        Long rootId() {
            return parentId != null ? parentId : id;
        }
    }
}
