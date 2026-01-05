package it.thesis.sse.benchmark;

import java.util.*;

/**
 * Data class holding all benchmark metrics for an SSE scheme.
 * Collects timing, memory, index size, and query performance data.
 */
public class BenchmarkMetrics {

    private final String schemeName;
    private long setupTimeMs;
    private long indexBuildTimeMs;
    private long indexSizeBytes;
    private long peakMemoryBytes;
    private long baselineMemoryBytes;

    // Query metrics
    private List<Double> queryTimesMs;
    private int totalQueries;
    private int successfulQueries;
    private int failedQueries;

    // Detailed query results
    private Map<String, QueryMetric> queryResults;

    // Statistical aggregates
    private double avgQueryTimeMs;
    private double minQueryTimeMs;
    private double maxQueryTimeMs;
    private double stdDevQueryTimeMs;
    private double p50QueryTimeMs;
    private double p95QueryTimeMs;
    private double p99QueryTimeMs;

    public BenchmarkMetrics(String schemeName) {
        this.schemeName = schemeName;
        this.queryTimesMs = new ArrayList<>();
        this.queryResults = new LinkedHashMap<>();
    }

    // Setters for main metrics

    public void setSetupTimeMs(long ms) {
        this.setupTimeMs = ms;
    }

    public void setIndexBuildTimeMs(long ms) {
        this.indexBuildTimeMs = ms;
    }

    public void setIndexSizeBytes(long bytes) {
        this.indexSizeBytes = bytes;
    }

    public void setPeakMemoryBytes(long bytes) {
        this.peakMemoryBytes = bytes;
    }

    public void setBaselineMemoryBytes(long bytes) {
        this.baselineMemoryBytes = bytes;
    }

    // Query timing methods

    public void addQueryTime(double timeMs) {
        queryTimesMs.add(timeMs);
        totalQueries++;
    }

    public void addQueryResult(QueryMetric metric) {
        queryResults.put(metric.getQueryId(), metric);
        addQueryTime(metric.getExecutionTimeMs());

        if (metric.isSuccess()) {
            successfulQueries++;
        } else {
            failedQueries++;
        }
    }

    // Calculate statistical aggregates

    public void calculateStatistics() {
        if (queryTimesMs.isEmpty()) {
            return;
        }

        // Sort for percentiles
        List<Double> sorted = new ArrayList<>(queryTimesMs);
        Collections.sort(sorted);

        // Min, max
        minQueryTimeMs = sorted.get(0);
        maxQueryTimeMs = sorted.get(sorted.size() - 1);

        // Average
        double sum = 0;
        for (double t : queryTimesMs) {
            sum += t;
        }
        avgQueryTimeMs = sum / queryTimesMs.size();

        // Standard deviation
        double variance = 0;
        for (double t : queryTimesMs) {
            variance += Math.pow(t - avgQueryTimeMs, 2);
        }
        stdDevQueryTimeMs = Math.sqrt(variance / queryTimesMs.size());

        // Percentiles
        p50QueryTimeMs = getPercentile(sorted, 50);
        p95QueryTimeMs = getPercentile(sorted, 95);
        p99QueryTimeMs = getPercentile(sorted, 99);
    }

    private double getPercentile(List<Double> sorted, int percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    // Getters

    public String getSchemeName() {
        return schemeName;
    }

    public long getSetupTimeMs() {
        return setupTimeMs;
    }

    public long getIndexBuildTimeMs() {
        return indexBuildTimeMs;
    }

    public long getIndexSizeBytes() {
        return indexSizeBytes;
    }

    public double getIndexSizeKB() {
        return indexSizeBytes / 1024.0;
    }

    public double getIndexSizeMB() {
        return indexSizeBytes / (1024.0 * 1024.0);
    }

    public long getPeakMemoryBytes() {
        return peakMemoryBytes;
    }

    public double getPeakMemoryMB() {
        return peakMemoryBytes / (1024.0 * 1024.0);
    }

    public long getBaselineMemoryBytes() {
        return baselineMemoryBytes;
    }

    public long getMemoryUsedBytes() {
        return peakMemoryBytes - baselineMemoryBytes;
    }

    public double getMemoryUsedMB() {
        return getMemoryUsedBytes() / (1024.0 * 1024.0);
    }

    public List<Double> getQueryTimesMs() {
        return Collections.unmodifiableList(queryTimesMs);
    }

    public int getTotalQueries() {
        return totalQueries;
    }

    public int getSuccessfulQueries() {
        return successfulQueries;
    }

    public int getFailedQueries() {
        return failedQueries;
    }

    public double getSuccessRate() {
        if (totalQueries == 0)
            return 0;
        return (double) successfulQueries / totalQueries * 100;
    }

    public double getAvgQueryTimeMs() {
        return avgQueryTimeMs;
    }

    public double getMinQueryTimeMs() {
        return minQueryTimeMs;
    }

    public double getMaxQueryTimeMs() {
        return maxQueryTimeMs;
    }

    public double getStdDevQueryTimeMs() {
        return stdDevQueryTimeMs;
    }

    public double getP50QueryTimeMs() {
        return p50QueryTimeMs;
    }

    public double getP95QueryTimeMs() {
        return p95QueryTimeMs;
    }

    public double getP99QueryTimeMs() {
        return p99QueryTimeMs;
    }

    public Map<String, QueryMetric> getQueryResults() {
        return Collections.unmodifiableMap(queryResults);
    }

    /**
     * Get throughput in queries per second.
     */
    public double getQueriesPerSecond() {
        if (queryTimesMs.isEmpty() || avgQueryTimeMs == 0) {
            return 0;
        }
        return 1000.0 / avgQueryTimeMs;
    }

    /**
     * Convert metrics to a map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        calculateStatistics();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemeName", schemeName);

        // Timing metrics
        Map<String, Object> timing = new LinkedHashMap<>();
        timing.put("setupTimeMs", setupTimeMs);
        timing.put("indexBuildTimeMs", indexBuildTimeMs);
        map.put("timing", timing);

        // Size metrics
        Map<String, Object> size = new LinkedHashMap<>();
        size.put("indexSizeBytes", indexSizeBytes);
        size.put("indexSizeKB", Math.round(getIndexSizeKB() * 100) / 100.0);
        size.put("indexSizeMB", Math.round(getIndexSizeMB() * 100) / 100.0);
        map.put("indexSize", size);

        // Memory metrics
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("peakMemoryBytes", peakMemoryBytes);
        memory.put("peakMemoryMB", Math.round(getPeakMemoryMB() * 100) / 100.0);
        memory.put("baselineMemoryBytes", baselineMemoryBytes);
        memory.put("memoryUsedMB", Math.round(getMemoryUsedMB() * 100) / 100.0);
        map.put("memory", memory);

        // Query statistics
        Map<String, Object> queries = new LinkedHashMap<>();
        queries.put("totalQueries", totalQueries);
        queries.put("successfulQueries", successfulQueries);
        queries.put("failedQueries", failedQueries);
        queries.put("successRate", Math.round(getSuccessRate() * 100) / 100.0);

        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("avgMs", Math.round(avgQueryTimeMs * 1000) / 1000.0);
        latency.put("minMs", Math.round(minQueryTimeMs * 1000) / 1000.0);
        latency.put("maxMs", Math.round(maxQueryTimeMs * 1000) / 1000.0);
        latency.put("stdDevMs", Math.round(stdDevQueryTimeMs * 1000) / 1000.0);
        latency.put("p50Ms", Math.round(p50QueryTimeMs * 1000) / 1000.0);
        latency.put("p95Ms", Math.round(p95QueryTimeMs * 1000) / 1000.0);
        latency.put("p99Ms", Math.round(p99QueryTimeMs * 1000) / 1000.0);
        queries.put("latency", latency);

        queries.put("queriesPerSecond", Math.round(getQueriesPerSecond() * 100) / 100.0);
        map.put("queryPerformance", queries);

        return map;
    }

    @Override
    public String toString() {
        calculateStatistics();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== %s Metrics ===%n", schemeName));
        sb.append(String.format("Setup Time: %d ms%n", setupTimeMs));
        sb.append(String.format("Index Build Time: %d ms%n", indexBuildTimeMs));
        sb.append(String.format("Index Size: %.2f KB%n", getIndexSizeKB()));
        sb.append(String.format("Peak Memory: %.2f MB%n", getPeakMemoryMB()));
        sb.append(String.format("Queries: %d total, %d success, %d failed%n",
                totalQueries, successfulQueries, failedQueries));
        sb.append(String.format("Avg Query Time: %.3f ms%n", avgQueryTimeMs));
        sb.append(String.format("P95 Query Time: %.3f ms%n", p95QueryTimeMs));
        sb.append(String.format("Throughput: %.2f queries/sec%n", getQueriesPerSecond()));
        return sb.toString();
    }
}
