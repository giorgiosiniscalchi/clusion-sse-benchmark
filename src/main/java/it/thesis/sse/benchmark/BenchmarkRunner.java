package it.thesis.sse.benchmark;

import com.google.common.collect.Multimap;
import it.thesis.sse.dataset.DatasetLoader;
import it.thesis.sse.schemes.SSEScheme;
import it.thesis.sse.schemes.SchemeFactory;
import it.thesis.sse.util.ConsoleFormatter;

import java.util.*;

/**
 * Executes SSE benchmarks with warmup, multiple iterations, and memory
 * measurement.
 * Orchestrates the complete benchmark workflow for all configured schemes.
 */
public class BenchmarkRunner {

    private final DatasetLoader datasetLoader;
    private final List<SSEScheme> schemes;
    private final List<QueryMetric> queries;
    private final Map<String, BenchmarkMetrics> results;

    // Configuration
    private int warmupIterations = 3;
    private int measurementIterations = 10;
    private boolean gcBetweenRuns = true;
    private boolean verbose = true;

    public BenchmarkRunner(DatasetLoader datasetLoader) {
        this.datasetLoader = datasetLoader;
        this.schemes = new ArrayList<>();
        this.queries = new ArrayList<>();
        this.results = new LinkedHashMap<>();
    }

    /**
     * Add a scheme to benchmark.
     */
    public void addScheme(SSEScheme scheme) {
        schemes.add(scheme);
    }

    /**
     * Add schemes by name.
     */
    public void addSchemes(String... schemeNames) {
        for (String name : schemeNames) {
            try {
                schemes.add(SchemeFactory.createScheme(name));
            } catch (Exception e) {
                System.err.println("Warning: Could not create scheme " + name + ": " + e.getMessage());
            }
        }
    }

    /**
     * Set test queries.
     */
    public void setQueries(List<QueryMetric> queries) {
        this.queries.clear();
        this.queries.addAll(queries);
    }

    /**
     * Run complete benchmark suite.
     */
    public Map<String, BenchmarkMetrics> runBenchmarks() throws Exception {
        results.clear();

        if (schemes.isEmpty()) {
            throw new IllegalStateException("No schemes configured for benchmarking");
        }

        if (queries.isEmpty()) {
            throw new IllegalStateException("No queries configured for benchmarking");
        }

        Multimap<String, String> multimap = datasetLoader.buildClusionMultimap();

        if (verbose) {
            System.out.println("\n" + ConsoleFormatter.header("SSE Benchmark Suite"));
            System.out.println("Documents: " + datasetLoader.getDocumentCount());
            System.out.println("Keywords: " + datasetLoader.getKeywordCount());
            System.out.println("Queries: " + queries.size());
            System.out.println("Schemes: " + schemes.size());
            System.out.println();
        }

        for (SSEScheme scheme : schemes) {
            if (verbose) {
                System.out.println(ConsoleFormatter.subHeader("Benchmarking: " + scheme.getName()));
                System.out.println(scheme.getDescription());
            }

            BenchmarkMetrics metrics = runSchemeBenchmark(scheme, multimap);
            results.put(scheme.getName(), metrics);

            if (verbose) {
                System.out.println();
                System.out.println(metrics.toString());
            }

            scheme.reset();

            if (gcBetweenRuns) {
                System.gc();
                Thread.sleep(100);
            }
        }

        if (verbose) {
            printSummary();
        }

        return results;
    }

    /**
     * Run benchmark for a single scheme.
     */
    private BenchmarkMetrics runSchemeBenchmark(SSEScheme scheme, Multimap<String, String> multimap)
            throws Exception {

        BenchmarkMetrics metrics = scheme.getMetrics();

        // Measure baseline memory
        if (gcBetweenRuns) {
            System.gc();
            Thread.sleep(50);
        }
        long baselineMemory = getUsedMemory();
        metrics.setBaselineMemoryBytes(baselineMemory);

        // Setup
        if (verbose) {
            System.out.println("  Setting up encryption...");
        }
        long setupStart = System.nanoTime();
        scheme.setup();
        long setupTime = (System.nanoTime() - setupStart) / 1_000_000;
        metrics.setSetupTimeMs(setupTime);

        // Build index
        if (verbose) {
            System.out.println("  Building encrypted index...");
        }
        long buildTime = scheme.buildIndex(multimap);
        metrics.setIndexBuildTimeMs(buildTime);
        metrics.setIndexSizeBytes(scheme.getIndexSizeBytes());

        // Measure peak memory after indexing
        long peakMemory = getUsedMemory();
        metrics.setPeakMemoryBytes(peakMemory);

        // Warmup queries
        if (verbose) {
            System.out.println("  Running " + warmupIterations + " warmup iterations...");
        }
        for (int i = 0; i < warmupIterations; i++) {
            for (QueryMetric query : queries) {
                executeQuery(scheme, query);
            }
        }

        // Clear warmup metrics
        metrics = new BenchmarkMetrics(scheme.getName());
        metrics.setSetupTimeMs(setupTime);
        metrics.setIndexBuildTimeMs(buildTime);
        metrics.setIndexSizeBytes(scheme.getIndexSizeBytes());
        metrics.setBaselineMemoryBytes(baselineMemory);
        metrics.setPeakMemoryBytes(peakMemory);

        // Measurement iterations
        if (verbose) {
            System.out.println("  Running " + measurementIterations + " measurement iterations...");
        }

        for (int i = 0; i < measurementIterations; i++) {
            for (QueryMetric queryTemplate : queries) {
                QueryMetric result = executeQuery(scheme, queryTemplate);
                metrics.addQueryResult(result);
            }
        }

        metrics.calculateStatistics();
        return metrics;
    }

    /**
     * Execute a single query and return the result.
     */
    private QueryMetric executeQuery(SSEScheme scheme, QueryMetric queryTemplate) {
        QueryMetric result = new QueryMetric(
                queryTemplate.getQueryId(),
                queryTemplate.getQueryType(),
                queryTemplate.getKeywords(),
                queryTemplate.getExpectedResults());

        try {
            long startTime = System.nanoTime();
            List<String> docIds;

            switch (queryTemplate.getQueryType().toUpperCase()) {
                case "AND":
                    docIds = scheme.searchAnd(queryTemplate.getKeywords());
                    break;
                case "OR":
                    docIds = scheme.searchOr(queryTemplate.getKeywords());
                    break;
                default:
                    docIds = scheme.search(queryTemplate.getKeywords().get(0));
            }

            double timeMs = (System.nanoTime() - startTime) / 1_000_000.0;
            result.setResult(docIds, timeMs);

        } catch (Exception e) {
            result.setError(e.getMessage());
        }

        return result;
    }

    /**
     * Get current used memory.
     */
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Print comparison summary.
     */
    private void printSummary() {
        System.out.println("\n" + ConsoleFormatter.header("Benchmark Summary"));

        // Table header
        System.out.println(String.format("%-12s %12s %12s %12s %12s %10s",
                "Scheme", "Index Build", "Index Size", "Avg Query", "P95 Query", "QPS"));
        System.out.println(ConsoleFormatter.separator(72));

        for (Map.Entry<String, BenchmarkMetrics> entry : results.entrySet()) {
            BenchmarkMetrics m = entry.getValue();
            System.out.println(String.format("%-12s %10d ms %10.2f KB %10.3f ms %10.3f ms %10.1f",
                    entry.getKey(),
                    m.getIndexBuildTimeMs(),
                    m.getIndexSizeKB(),
                    m.getAvgQueryTimeMs(),
                    m.getP95QueryTimeMs(),
                    m.getQueriesPerSecond()));
        }

        System.out.println(ConsoleFormatter.separator(72));
    }

    // Configuration methods

    public void setWarmupIterations(int iterations) {
        this.warmupIterations = iterations;
    }

    public void setMeasurementIterations(int iterations) {
        this.measurementIterations = iterations;
    }

    public void setGcBetweenRuns(boolean gc) {
        this.gcBetweenRuns = gc;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public Map<String, BenchmarkMetrics> getResults() {
        return Collections.unmodifiableMap(results);
    }

    public List<SSEScheme> getSchemes() {
        return Collections.unmodifiableList(schemes);
    }
}
