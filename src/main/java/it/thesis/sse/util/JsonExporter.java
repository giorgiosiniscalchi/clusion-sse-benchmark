package it.thesis.sse.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.thesis.sse.benchmark.BenchmarkMetrics;
import it.thesis.sse.security.SecurityReport;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Exports benchmark results to JSON format.
 * Produces output compatible with the thesis standardized schema.
 */
public class JsonExporter {

    private final Gson gson;
    private final boolean prettyPrint;

    public JsonExporter() {
        this(true);
    }

    public JsonExporter(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
        GsonBuilder builder = new GsonBuilder();
        if (prettyPrint) {
            builder.setPrettyPrinting();
        }
        builder.disableHtmlEscaping();
        this.gson = builder.create();
    }

    /**
     * Export benchmark results to JSON file.
     */
    public void exportResults(Map<String, BenchmarkMetrics> results, String outputPath)
            throws IOException {

        Map<String, Object> output = buildResultsMap(results);

        try (FileWriter writer = new FileWriter(outputPath, StandardCharsets.UTF_8)) {
            gson.toJson(output, writer);
        }
    }

    /**
     * Export benchmark results with security reports.
     */
    public void exportResultsWithSecurity(
            Map<String, BenchmarkMetrics> results,
            Map<String, SecurityReport> securityReports,
            String outputPath) throws IOException {

        Map<String, Object> output = buildResultsMap(results);

        // Add security reports
        List<Map<String, Object>> securityList = new ArrayList<>();
        for (SecurityReport report : securityReports.values()) {
            securityList.add(report.toMap());
        }
        output.put("securityAnalysis", securityList);

        try (FileWriter writer = new FileWriter(outputPath, StandardCharsets.UTF_8)) {
            gson.toJson(output, writer);
        }
    }

    /**
     * Build the results map structure.
     */
    private Map<String, Object> buildResultsMap(Map<String, BenchmarkMetrics> results) {
        Map<String, Object> output = new LinkedHashMap<>();

        // Metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("benchmarkLibrary", "Clusion");
        metadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("javaVersion", System.getProperty("java.version"));
        metadata.put("osName", System.getProperty("os.name"));
        metadata.put("osArch", System.getProperty("os.arch"));

        Runtime runtime = Runtime.getRuntime();
        metadata.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        metadata.put("availableProcessors", runtime.availableProcessors());

        output.put("metadata", metadata);

        // Scheme results
        List<Map<String, Object>> schemeResults = new ArrayList<>();
        for (Map.Entry<String, BenchmarkMetrics> entry : results.entrySet()) {
            schemeResults.add(entry.getValue().toMap());
        }
        output.put("schemes", schemeResults);

        // Summary statistics
        output.put("summary", buildSummary(results));

        return output;
    }

    /**
     * Build summary statistics across all schemes.
     */
    private Map<String, Object> buildSummary(Map<String, BenchmarkMetrics> results) {
        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("numSchemes", results.size());

        // Find best/worst for each metric
        String fastestBuild = null;
        long fastestBuildTime = Long.MAX_VALUE;
        String smallestIndex = null;
        long smallestIndexSize = Long.MAX_VALUE;
        String fastestQuery = null;
        double fastestQueryTime = Double.MAX_VALUE;

        for (Map.Entry<String, BenchmarkMetrics> entry : results.entrySet()) {
            BenchmarkMetrics m = entry.getValue();

            if (m.getIndexBuildTimeMs() < fastestBuildTime) {
                fastestBuildTime = m.getIndexBuildTimeMs();
                fastestBuild = entry.getKey();
            }

            if (m.getIndexSizeBytes() < smallestIndexSize) {
                smallestIndexSize = m.getIndexSizeBytes();
                smallestIndex = entry.getKey();
            }

            if (m.getAvgQueryTimeMs() < fastestQueryTime) {
                fastestQueryTime = m.getAvgQueryTimeMs();
                fastestQuery = entry.getKey();
            }
        }

        Map<String, Object> best = new LinkedHashMap<>();
        best.put("fastestIndexBuild", Map.of("scheme", fastestBuild, "timeMs", fastestBuildTime));
        best.put("smallestIndex", Map.of("scheme", smallestIndex, "sizeBytes", smallestIndexSize));
        best.put("fastestQuery", Map.of("scheme", fastestQuery, "avgTimeMs",
                Math.round(fastestQueryTime * 1000) / 1000.0));
        summary.put("bestPerformers", best);

        return summary;
    }

    /**
     * Export to CSV format for spreadsheet analysis.
     */
    public void exportToCsv(Map<String, BenchmarkMetrics> results, String outputPath)
            throws IOException {

        try (PrintWriter writer = new PrintWriter(outputPath, StandardCharsets.UTF_8)) {
            // Header
            writer.println("Scheme,SetupTimeMs,IndexBuildTimeMs,IndexSizeKB,PeakMemoryMB," +
                    "AvgQueryTimeMs,P50QueryTimeMs,P95QueryTimeMs,P99QueryTimeMs," +
                    "QueriesPerSecond,TotalQueries,SuccessRate");

            // Data rows
            for (Map.Entry<String, BenchmarkMetrics> entry : results.entrySet()) {
                BenchmarkMetrics m = entry.getValue();
                m.calculateStatistics();

                writer.printf(Locale.US, "%s,%d,%d,%.2f,%.2f,%.3f,%.3f,%.3f,%.3f,%.2f,%d,%.2f%n",
                        entry.getKey(),
                        m.getSetupTimeMs(),
                        m.getIndexBuildTimeMs(),
                        m.getIndexSizeKB(),
                        m.getPeakMemoryMB(),
                        m.getAvgQueryTimeMs(),
                        m.getP50QueryTimeMs(),
                        m.getP95QueryTimeMs(),
                        m.getP99QueryTimeMs(),
                        m.getQueriesPerSecond(),
                        m.getTotalQueries(),
                        m.getSuccessRate());
            }
        }
    }

    /**
     * Generate comparison table as markdown.
     */
    public String generateMarkdownTable(Map<String, BenchmarkMetrics> results) {
        StringBuilder sb = new StringBuilder();

        sb.append("| Scheme | Index Build | Index Size | Avg Query | P95 Query | QPS |\n");
        sb.append("|--------|-------------|------------|-----------|-----------|-----|\n");

        for (Map.Entry<String, BenchmarkMetrics> entry : results.entrySet()) {
            BenchmarkMetrics m = entry.getValue();
            m.calculateStatistics();

            sb.append(String.format(Locale.US, "| %s | %d ms | %.2f KB | %.3f ms | %.3f ms | %.1f |\n",
                    entry.getKey(),
                    m.getIndexBuildTimeMs(),
                    m.getIndexSizeKB(),
                    m.getAvgQueryTimeMs(),
                    m.getP95QueryTimeMs(),
                    m.getQueriesPerSecond()));
        }

        return sb.toString();
    }

    /**
     * Convert results to JSON string.
     */
    public String toJson(Map<String, BenchmarkMetrics> results) {
        return gson.toJson(buildResultsMap(results));
    }
}
