package it.thesis.sse.dataset;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts and normalizes keywords from e-health documents.
 * Provides consistent keyword extraction for indexing and searching.
 */
public class KeywordExtractor {

    // Common Italian stopwords to exclude
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "il", "lo", "la", "i", "gli", "le", "un", "uno", "una",
            "di", "a", "da", "in", "con", "su", "per", "tra", "fra",
            "e", "o", "ma", "che", "chi", "cui", "quale", "quanto",
            "come", "dove", "quando", "perché", "se", "non", "più",
            "anche", "solo", "già", "ancora", "sempre", "mai", "molto",
            "poco", "tutto", "niente", "nulla", "ogni", "altro",
            "questo", "quello", "quale", "stesso", "proprio",
            "essere", "avere", "fare", "dire", "andare", "venire",
            "volere", "potere", "dovere", "sapere", "vedere", "dare",
            "the", "a", "an", "and", "or", "but", "in", "on", "at",
            "to", "for", "of", "with", "by", "from", "as", "is", "was",
            "are", "were", "been", "be", "have", "has", "had", "do",
            "does", "did", "will", "would", "could", "should", "may",
            "might", "must", "shall", "can", "need", "not", "no"));

    // Pattern for valid keyword characters
    private static final Pattern VALID_CHARS = Pattern.compile("[a-zA-Z0-9àèéìòùÀÈÉÌÒÙ]+");
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s,.;:!?()\\[\\]{}\"'\\-/\\\\]+");

    private final int minKeywordLength;
    private final int maxKeywordLength;
    private final boolean lowercase;
    private final boolean removeStopwords;

    public KeywordExtractor() {
        this(2, 50, true, true);
    }

    public KeywordExtractor(int minKeywordLength, int maxKeywordLength,
            boolean lowercase, boolean removeStopwords) {
        this.minKeywordLength = minKeywordLength;
        this.maxKeywordLength = maxKeywordLength;
        this.lowercase = lowercase;
        this.removeStopwords = removeStopwords;
    }

    /**
     * Extract keywords from a document.
     */
    public Set<String> extract(Document document) {
        Set<String> keywords = new HashSet<>();

        // Extract from structured fields
        keywords.addAll(extractFromField(document.getFirstName()));
        keywords.addAll(extractFromField(document.getLastName()));
        keywords.addAll(extractFromField(document.getDepartment()));

        // Extract from diagnoses
        for (String diagnosis : document.getDiagnoses()) {
            keywords.addAll(extractFromField(diagnosis));
        }

        // Extract from treatments
        for (String treatment : document.getTreatments()) {
            keywords.addAll(extractFromField(treatment));
        }

        // Extract from clinical notes
        keywords.addAll(extractFromField(document.getClinicalNotes()));

        // Add age-based keywords
        keywords.addAll(getAgeKeywords(document.getAge()));

        return keywords;
    }

    /**
     * Extract keywords from a text field.
     */
    public Set<String> extractFromField(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }

        return Arrays.stream(SPLIT_PATTERN.split(text))
                .map(this::normalizeKeyword)
                .filter(this::isValidKeyword)
                .collect(Collectors.toSet());
    }

    /**
     * Extract keywords from raw text content.
     */
    public Set<String> extractFromText(String text) {
        return extractFromField(text);
    }

    /**
     * Normalize a keyword (lowercase, trim, etc.).
     */
    public String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }

        String normalized = keyword.trim();
        if (lowercase) {
            normalized = normalized.toLowerCase(Locale.ITALIAN);
        }

        return normalized;
    }

    /**
     * Check if a keyword is valid for indexing.
     */
    public boolean isValidKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }

        if (keyword.length() < minKeywordLength || keyword.length() > maxKeywordLength) {
            return false;
        }

        if (removeStopwords && STOPWORDS.contains(keyword.toLowerCase())) {
            return false;
        }

        if (!VALID_CHARS.matcher(keyword).matches()) {
            return false;
        }

        return true;
    }

    /**
     * Get age-range keywords for a patient's age.
     */
    public Set<String> getAgeKeywords(int age) {
        Set<String> keywords = new HashSet<>();

        if (age < 18) {
            keywords.add("minorenne");
            keywords.add("pediatrico");
        } else if (age < 30) {
            keywords.add("giovane");
        } else if (age < 50) {
            keywords.add("adulto");
        } else if (age < 70) {
            keywords.add("anziano");
        } else {
            keywords.add("molto_anziano");
            keywords.add("geriatrico");
        }

        return keywords;
    }

    /**
     * Extract medical-specific keywords from diagnosis text.
     */
    public Set<String> extractMedicalKeywords(String text) {
        Set<String> keywords = extractFromField(text);

        // Add compound terms (e.g., "insufficienza_cardiaca")
        String normalized = normalizeKeyword(text);
        String compound = normalized.replace(" ", "_");
        if (isValidKeyword(compound) && compound.contains("_")) {
            keywords.add(compound);
        }

        return keywords;
    }

    /**
     * Build keyword frequency map from documents.
     */
    public Map<String, Integer> buildKeywordFrequencies(List<Document> documents) {
        Map<String, Integer> frequencies = new HashMap<>();

        for (Document doc : documents) {
            Set<String> docKeywords = extract(doc);
            for (String kw : docKeywords) {
                frequencies.merge(kw, 1, Integer::sum);
            }
        }

        return frequencies;
    }

    /**
     * Get the most frequent keywords.
     */
    public List<Map.Entry<String, Integer>> getTopKeywords(
            Map<String, Integer> frequencies, int limit) {
        return frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Filter keyword index by minimum frequency.
     */
    public Map<String, List<String>> filterByMinFrequency(
            Map<String, List<String>> index, int minFrequency) {
        return index.entrySet().stream()
                .filter(e -> e.getValue().size() >= minFrequency)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Create a builder for configuring the extractor.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int minKeywordLength = 2;
        private int maxKeywordLength = 50;
        private boolean lowercase = true;
        private boolean removeStopwords = true;

        public Builder minLength(int length) {
            this.minKeywordLength = length;
            return this;
        }

        public Builder maxLength(int length) {
            this.maxKeywordLength = length;
            return this;
        }

        public Builder lowercase(boolean flag) {
            this.lowercase = flag;
            return this;
        }

        public Builder removeStopwords(boolean flag) {
            this.removeStopwords = flag;
            return this;
        }

        public KeywordExtractor build() {
            return new KeywordExtractor(minKeywordLength, maxKeywordLength,
                    lowercase, removeStopwords);
        }
    }
}
