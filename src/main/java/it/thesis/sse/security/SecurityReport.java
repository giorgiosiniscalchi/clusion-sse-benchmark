package it.thesis.sse.security;

import java.util.*;

/**
 * Generates security analysis reports for SSE schemes.
 * Contains detailed leakage information and security scoring.
 */
public class SecurityReport {

    private final String schemeName;
    private final Map<LeakageAnalyzer.LeakageType, LeakageInfo> leakageMap;
    private int securityScore;
    private String securityRating;

    public SecurityReport(String schemeName) {
        this.schemeName = schemeName;
        this.leakageMap = new LinkedHashMap<>();
    }

    /**
     * Leakage information for a specific type.
     */
    public static class LeakageInfo {
        private final LeakageAnalyzer.LeakageType type;
        private final String description;
        private final boolean leaked;

        public LeakageInfo(LeakageAnalyzer.LeakageType type, String description, boolean leaked) {
            this.type = type;
            this.description = description;
            this.leaked = leaked;
        }

        public LeakageAnalyzer.LeakageType getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public boolean isLeaked() {
            return leaked;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type.name());
            map.put("description", description);
            map.put("leaked", leaked);
            return map;
        }
    }

    /**
     * Add leakage information.
     */
    public void addLeakage(LeakageAnalyzer.LeakageType type, String description, boolean leaked) {
        leakageMap.put(type, new LeakageInfo(type, description, leaked));
    }

    /**
     * Check if a specific leakage type is present.
     */
    public boolean isLeaked(LeakageAnalyzer.LeakageType type) {
        LeakageInfo info = leakageMap.get(type);
        return info != null && info.isLeaked();
    }

    /**
     * Get all leaked types.
     */
    public Set<LeakageAnalyzer.LeakageType> getLeakedTypes() {
        Set<LeakageAnalyzer.LeakageType> leaked = new HashSet<>();
        for (Map.Entry<LeakageAnalyzer.LeakageType, LeakageInfo> entry : leakageMap.entrySet()) {
            if (entry.getValue().isLeaked()) {
                leaked.add(entry.getKey());
            }
        }
        return leaked;
    }

    /**
     * Calculate overall security score (0-100).
     */
    public void calculateSecurityScore() {
        int totalTypes = LeakageAnalyzer.LeakageType.values().length;
        int leakedCount = getLeakedTypes().size();
        int protectedCount = totalTypes - leakedCount;

        // Base score from protection ratio
        securityScore = (protectedCount * 100) / totalTypes;

        // Adjust for critical leakages
        if (isLeaked(LeakageAnalyzer.LeakageType.ACCESS_PATTERN)) {
            securityScore -= 15; // Access pattern is critical
        }
        if (!isLeaked(LeakageAnalyzer.LeakageType.FORWARD_PRIVACY)) {
            securityScore += 10; // Bonus for forward privacy
        }
        if (!isLeaked(LeakageAnalyzer.LeakageType.BACKWARD_PRIVACY)) {
            securityScore += 10; // Bonus for backward privacy
        }

        // Clamp to valid range
        securityScore = Math.max(0, Math.min(100, securityScore));

        // Determine rating
        if (securityScore >= 80) {
            securityRating = "HIGH";
        } else if (securityScore >= 60) {
            securityRating = "MEDIUM";
        } else if (securityScore >= 40) {
            securityRating = "LOW";
        } else {
            securityRating = "MINIMAL";
        }
    }

    /**
     * Get security score.
     */
    public int getSecurityScore() {
        return securityScore;
    }

    /**
     * Get security rating.
     */
    public String getSecurityRating() {
        return securityRating;
    }

    /**
     * Get scheme name.
     */
    public String getSchemeName() {
        return schemeName;
    }

    /**
     * Convert to map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemeName", schemeName);
        map.put("securityScore", securityScore);
        map.put("securityRating", securityRating);

        List<Map<String, Object>> leakageList = new ArrayList<>();
        for (LeakageInfo info : leakageMap.values()) {
            leakageList.add(info.toMap());
        }
        map.put("leakageProfile", leakageList);

        map.put("leakedCount", getLeakedTypes().size());
        map.put("protectedCount", leakageMap.size() - getLeakedTypes().size());

        return map;
    }

    /**
     * Generate formatted report string.
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║  Security Report: %-42s ║%n", schemeName));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Security Score: %-3d/100  Rating: %-25s ║%n",
                securityScore, securityRating));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append("║  LEAKAGE ANALYSIS                                            ║\n");
        sb.append("╟──────────────────────────────────────────────────────────────╢\n");

        for (LeakageInfo info : leakageMap.values()) {
            String status = info.isLeaked() ? "[LEAKED]  " : "[SAFE]    ";
            sb.append(String.format("║  %s %-50s ║%n", status, info.getType().name()));
        }

        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Protected: %d/%d types                                        ║%n",
                leakageMap.size() - getLeakedTypes().size(), leakageMap.size()));
        sb.append("╚══════════════════════════════════════════════════════════════╝\n");

        return sb.toString();
    }

    /**
     * Generate markdown report.
     */
    public String generateMarkdownReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("## Security Report: ").append(schemeName).append("\n\n");
        sb.append("**Security Score:** ").append(securityScore).append("/100\n");
        sb.append("**Rating:** ").append(securityRating).append("\n\n");

        sb.append("### Leakage Profile\n\n");
        sb.append("| Leakage Type | Status | Description |\n");
        sb.append("|--------------|--------|-------------|\n");

        for (LeakageInfo info : leakageMap.values()) {
            String status = info.isLeaked() ? "⚠️ LEAKED" : "✅ SAFE";
            sb.append("| ").append(info.getType().name())
                    .append(" | ").append(status)
                    .append(" | ").append(info.getDescription())
                    .append(" |\n");
        }

        sb.append("\n### Summary\n\n");
        sb.append("- **Protected:** ").append(leakageMap.size() - getLeakedTypes().size())
                .append("/").append(leakageMap.size()).append(" leakage types\n");

        return sb.toString();
    }

    @Override
    public String toString() {
        return generateReport();
    }
}
