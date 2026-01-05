package it.thesis.sse.benchmark;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Individual query result metrics.
 * Captures execution time, result count, and correctness for a single query.
 */
public class QueryMetric {

    private final String queryId;
    private final String queryType; // "single", "AND", "OR"
    private final List<String> keywords;
    private final int expectedResults;
    private int actualResults;
    private double executionTimeMs;
    private boolean success;
    private String errorMessage;
    private List<String> resultDocIds;

    public QueryMetric(String queryId, String queryType, List<String> keywords, int expectedResults) {
        this.queryId = queryId;
        this.queryType = queryType;
        this.keywords = keywords;
        this.expectedResults = expectedResults;
        this.success = true;
    }

    // Static factory methods

    public static QueryMetric singleKeyword(String queryId, String keyword, int expectedResults) {
        return new QueryMetric(queryId, "single", List.of(keyword), expectedResults);
    }

    public static QueryMetric andQuery(String queryId, List<String> keywords, int expectedResults) {
        return new QueryMetric(queryId, "AND", keywords, expectedResults);
    }

    public static QueryMetric orQuery(String queryId, List<String> keywords, int expectedResults) {
        return new QueryMetric(queryId, "OR", keywords, expectedResults);
    }

    // Result setters

    public void setResult(List<String> docIds, double timeMs) {
        this.resultDocIds = docIds;
        this.actualResults = docIds != null ? docIds.size() : 0;
        this.executionTimeMs = timeMs;
        this.success = true;
    }

    public void setError(String message) {
        this.success = false;
        this.errorMessage = message;
    }

    public void setExecutionTimeMs(double timeMs) {
        this.executionTimeMs = timeMs;
    }

    // Getters

    public String getQueryId() {
        return queryId;
    }

    public String getQueryType() {
        return queryType;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public String getKeywordsString() {
        return String.join(" " + queryType + " ", keywords);
    }

    public int getExpectedResults() {
        return expectedResults;
    }

    public int getActualResults() {
        return actualResults;
    }

    public double getExecutionTimeMs() {
        return executionTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<String> getResultDocIds() {
        return resultDocIds;
    }

    /**
     * Check if actual results match expected results.
     */
    public boolean isResultCorrect() {
        return actualResults == expectedResults;
    }

    /**
     * Get the difference between actual and expected results.
     */
    public int getResultDifference() {
        return actualResults - expectedResults;
    }

    /**
     * Get result accuracy as a percentage.
     */
    public double getAccuracy() {
        if (expectedResults == 0) {
            return actualResults == 0 ? 100.0 : 0.0;
        }

        // Use Jaccard similarity if we have result sets
        if (expectedResults > 0 && actualResults > 0) {
            double recall = Math.min(actualResults, expectedResults) / (double) expectedResults;
            return recall * 100.0;
        }

        return 0.0;
    }

    /**
     * Convert to map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("queryId", queryId);
        map.put("type", queryType);
        map.put("keywords", keywords);
        map.put("expectedResults", expectedResults);
        map.put("actualResults", actualResults);
        map.put("executionTimeMs", Math.round(executionTimeMs * 1000) / 1000.0);
        map.put("success", success);
        map.put("resultCorrect", isResultCorrect());

        if (!success && errorMessage != null) {
            map.put("error", errorMessage);
        }

        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Query[%s]: %s '%s'", queryId, queryType, getKeywordsString()));
        sb.append(String.format(" -> %d results (expected %d)", actualResults, expectedResults));
        sb.append(String.format(" in %.3f ms", executionTimeMs));

        if (!success) {
            sb.append(" [FAILED: ").append(errorMessage).append("]");
        } else if (!isResultCorrect()) {
            sb.append(" [MISMATCH]");
        }

        return sb.toString();
    }
}
