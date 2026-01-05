package it.thesis.sse.security;

import it.thesis.sse.schemes.SSEScheme;

import java.util.*;

/**
 * Analyzes leakage profiles for SSE schemes.
 * Evaluates security properties and potential information leakage.
 */
public class LeakageAnalyzer {

    /**
     * Leakage types in SSE schemes.
     */
    public enum LeakageType {
        SEARCH_PATTERN("Search Pattern", "Repeated queries for the same keyword are linkable"),
        ACCESS_PATTERN("Access Pattern", "Reveals which documents match a query"),
        SIZE_PATTERN("Size Pattern", "Reveals the number of matching documents"),
        VOLUME_PATTERN("Volume Pattern", "Reveals total size of matching documents"),
        QUERY_EQUALITY("Query Equality", "Reveals when same query is repeated"),
        INTERSECTION_PATTERN("Intersection Pattern", "Reveals document overlap between queries"),
        FORWARD_PRIVACY("Forward Privacy", "Updates don't leak information about past queries"),
        BACKWARD_PRIVACY("Backward Privacy", "Deletions don't leak information about deleted data");

        private final String displayName;
        private final String description;

        LeakageType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Analyze a scheme's leakage profile.
     */
    public SecurityReport analyze(SSEScheme scheme) {
        SecurityReport report = new SecurityReport(scheme.getName());

        // Get scheme's declared leakage
        Map<String, String> leakage = scheme.getLeakageProfile();

        // Analyze search pattern leakage
        report.addLeakage(LeakageType.SEARCH_PATTERN,
                leakage.getOrDefault("search_pattern", "Unknown"),
                determineLeakedValue(leakage, "search_pattern"));

        // Analyze access pattern leakage
        report.addLeakage(LeakageType.ACCESS_PATTERN,
                leakage.getOrDefault("access_pattern", "Unknown"),
                determineLeakedValue(leakage, "access_pattern"));

        // Analyze size pattern leakage
        boolean sizeHidden = scheme.isResponseHiding() ||
                containsHidden(leakage.get("size_pattern"));
        report.addLeakage(LeakageType.SIZE_PATTERN,
                leakage.getOrDefault("size_pattern", sizeHidden ? "Hidden" : "Revealed"),
                !sizeHidden);

        // Forward and backward privacy
        report.addLeakage(LeakageType.FORWARD_PRIVACY,
                leakage.getOrDefault("forward_privacy", "No"),
                !isYes(leakage.get("forward_privacy")));

        report.addLeakage(LeakageType.BACKWARD_PRIVACY,
                leakage.getOrDefault("backward_privacy", "No"),
                !isYes(leakage.get("backward_privacy")));

        // Boolean query specific leakage
        if (scheme.supportsBoolean()) {
            report.addLeakage(LeakageType.INTERSECTION_PATTERN,
                    leakage.getOrDefault("cross_term_pattern", "Revealed for conjunctions"),
                    true);
        }

        // Calculate overall security score
        report.calculateSecurityScore();

        return report;
    }

    /**
     * Compare security of multiple schemes.
     */
    public Map<String, SecurityReport> compareSchemes(List<SSEScheme> schemes) {
        Map<String, SecurityReport> reports = new LinkedHashMap<>();

        for (SSEScheme scheme : schemes) {
            reports.put(scheme.getName(), analyze(scheme));
        }

        return reports;
    }

    /**
     * Get recommended scheme based on security requirements.
     */
    public SSEScheme recommendScheme(List<SSEScheme> schemes, Set<LeakageType> mustAvoid) {
        SSEScheme best = null;
        int bestScore = Integer.MIN_VALUE;

        for (SSEScheme scheme : schemes) {
            SecurityReport report = analyze(scheme);

            // Check if scheme avoids required leakage
            boolean acceptable = true;
            for (LeakageType type : mustAvoid) {
                if (report.isLeaked(type)) {
                    acceptable = false;
                    break;
                }
            }

            if (acceptable && report.getSecurityScore() > bestScore) {
                bestScore = report.getSecurityScore();
                best = scheme;
            }
        }

        return best;
    }

    /**
     * Generate a comparison matrix for schemes.
     */
    public String generateComparisonMatrix(List<SSEScheme> schemes) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(String.format("%-20s", "Leakage Type"));
        for (SSEScheme scheme : schemes) {
            sb.append(String.format(" | %-12s", scheme.getName()));
        }
        sb.append("\n");
        sb.append("-".repeat(20 + schemes.size() * 15)).append("\n");

        // Rows for each leakage type
        for (LeakageType type : LeakageType.values()) {
            sb.append(String.format("%-20s", type.name()));

            for (SSEScheme scheme : schemes) {
                SecurityReport report = analyze(scheme);
                String status = report.isLeaked(type) ? "LEAKED" : "SAFE";
                sb.append(String.format(" | %-12s", status));
            }
            sb.append("\n");
        }

        // Security scores
        sb.append("-".repeat(20 + schemes.size() * 15)).append("\n");
        sb.append(String.format("%-20s", "Security Score"));
        for (SSEScheme scheme : schemes) {
            SecurityReport report = analyze(scheme);
            sb.append(String.format(" | %-12d", report.getSecurityScore()));
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Get theoretical attack vectors for a scheme.
     */
    public List<String> getAttackVectors(SSEScheme scheme) {
        List<String> attacks = new ArrayList<>();
        SecurityReport report = analyze(scheme);

        if (report.isLeaked(LeakageType.SEARCH_PATTERN)) {
            attacks.add(
                    "Frequency Analysis Attack: Attacker can correlate encrypted queries with known keyword frequencies");
        }

        if (report.isLeaked(LeakageType.ACCESS_PATTERN)) {
            attacks.add("Known Document Attack: Attacker knowing some documents can infer query keywords");
            attacks.add("Co-occurrence Attack: Statistical analysis of document access patterns");
        }

        if (report.isLeaked(LeakageType.SIZE_PATTERN)) {
            attacks.add("Count Attack: Result count reveals information about query selectivity");
        }

        if (report.isLeaked(LeakageType.INTERSECTION_PATTERN)) {
            attacks.add("IKK Attack: Intersection patterns leak information in conjunctive queries");
        }

        if (!report.isLeaked(LeakageType.FORWARD_PRIVACY)) {
            attacks.add("Update-Query Correlation: New additions may reveal past query information");
        }

        return attacks;
    }

    // Helper methods

    private boolean determineLeakedValue(Map<String, String> leakage, String key) {
        String value = leakage.get(key);
        if (value == null)
            return true; // Assume leaked if not specified
        return containsRevealed(value);
    }

    private boolean containsRevealed(String value) {
        if (value == null)
            return true;
        String lower = value.toLowerCase();
        return lower.contains("reveal") || lower.contains("leak") ||
                lower.contains("visible") || lower.contains("yes");
    }

    private boolean containsHidden(String value) {
        if (value == null)
            return false;
        String lower = value.toLowerCase();
        return lower.contains("hidden") || lower.contains("hide") ||
                lower.contains("private") || lower.contains("protected");
    }

    private boolean isYes(String value) {
        if (value == null)
            return false;
        String lower = value.toLowerCase();
        return lower.equals("yes") || lower.equals("true");
    }
}
