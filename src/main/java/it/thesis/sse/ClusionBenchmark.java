package it.thesis.sse;

import it.thesis.sse.benchmark.*;
import it.thesis.sse.dataset.DatasetLoader;
import it.thesis.sse.schemes.*;
import it.thesis.sse.security.*;
import it.thesis.sse.util.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Main entry point for the Clusion SSE Benchmark suite.
 * 
 * Usage:
 * java -jar clusion-benchmark.jar [options]
 * 
 * Options:
 * --dataset <path> Path to dataset directory (default: ./dataset/data)
 * --output <path> Output directory for results (default: ./results)
 * --schemes <list> Comma-separated list of schemes to benchmark
 * --warmup <n> Number of warmup iterations (default: 3)
 * --iterations <n> Number of measurement iterations (default: 10)
 * --security Include security analysis in output
 * --verbose Enable verbose output
 * --help Show this help message
 */
public class ClusionBenchmark {

    private static final String VERSION = "1.0.0";

    // Default configuration
    private String datasetPath = "./dataset/data";
    private String outputPath = "./results";
    private String schemesConfig = "ZMF,2Lev-RR,IEX-2Lev";
    private int warmupIterations = 3;
    private int measurementIterations = 10;
    private boolean includeSecurityAnalysis = true;
    private boolean verbose = true;

    public static void main(String[] args) {
        ClusionBenchmark benchmark = new ClusionBenchmark();

        try {
            benchmark.parseArguments(args);
            benchmark.run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (benchmark.verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    /**
     * Parse command line arguments.
     */
    private void parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dataset":
                case "-d":
                    datasetPath = args[++i];
                    break;
                case "--output":
                case "-o":
                    outputPath = args[++i];
                    break;
                case "--schemes":
                case "-s":
                    schemesConfig = args[++i];
                    break;
                case "--warmup":
                    warmupIterations = Integer.parseInt(args[++i]);
                    break;
                case "--iterations":
                case "-i":
                    measurementIterations = Integer.parseInt(args[++i]);
                    break;
                case "--security":
                    includeSecurityAnalysis = true;
                    break;
                case "--no-security":
                    includeSecurityAnalysis = false;
                    break;
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                case "--quiet":
                case "-q":
                    verbose = false;
                    break;
                case "--help":
                case "-h":
                    printHelp();
                    System.exit(0);
                    break;
                case "--version":
                    System.out.println("Clusion SSE Benchmark v" + VERSION);
                    System.exit(0);
                    break;
                default:
                    if (args[i].startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
            }
        }
    }

    /**
     * Print help message.
     */
    private void printHelp() {
        System.out.println("Clusion SSE Benchmark v" + VERSION);
        System.out.println();
        System.out.println("Usage: java -jar clusion-benchmark.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -d, --dataset <path>    Path to dataset directory");
        System.out.println("  -o, --output <path>     Output directory for results");
        System.out.println("  -s, --schemes <list>    Comma-separated list of schemes");
        System.out.println("      --warmup <n>        Warmup iterations (default: 3)");
        System.out.println("  -i, --iterations <n>    Measurement iterations (default: 10)");
        System.out.println("      --security          Include security analysis");
        System.out.println("      --no-security       Skip security analysis");
        System.out.println("  -v, --verbose           Verbose output");
        System.out.println("  -q, --quiet             Quiet output");
        System.out.println("  -h, --help              Show this help");
        System.out.println("      --version           Show version");
        System.out.println();
        System.out.println("Available schemes: " + SchemeFactory.getAvailableSchemes());
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar clusion-benchmark.jar -d ./data -s ZMF,2Lev-RR -i 20");
    }

    /**
     * Run the benchmark suite.
     */
    private void run() throws Exception {
        printBanner();

        // Validate paths
        validatePaths();

        // Load dataset
        if (verbose) {
            System.out.println(ConsoleFormatter.info("Loading dataset from: " + datasetPath));
        }

        DatasetLoader loader = new DatasetLoader();
        Path dataPath = Paths.get(datasetPath);

        if (Files.exists(dataPath.resolve("dataset.json"))) {
            loader.loadFromJson(dataPath.resolve("dataset.json").toString());
        } else if (Files.exists(dataPath.resolve("documents"))) {
            loader.loadFromTextFiles(dataPath.resolve("documents").toString());
        } else {
            throw new FileNotFoundException("No dataset found at: " + datasetPath);
        }

        // Load queries
        List<QueryMetric> queries;
        Path queriesPath = dataPath.resolve("test_queries.json");

        if (Files.exists(queriesPath)) {
            if (verbose) {
                System.out.println(ConsoleFormatter.info("Loading test queries..."));
            }
            QueryGenerator qg = new QueryGenerator(loader.getKeywordIndex());
            queries = qg.loadQueries(queriesPath.toString());
        } else {
            if (verbose) {
                System.out.println(ConsoleFormatter.info("Generating test queries..."));
            }
            QueryGenerator qg = new QueryGenerator(loader.getKeywordIndex());
            queries = qg.generateQueries(15, 10, 10);
        }

        if (verbose) {
            System.out.println(ConsoleFormatter.success(
                    String.format("Dataset loaded: %d documents, %d keywords, %d queries",
                            loader.getDocumentCount(),
                            loader.getKeywordCount(),
                            queries.size())));
            System.out.println();
        }

        // Configure benchmark runner
        BenchmarkRunner runner = new BenchmarkRunner(loader);
        runner.addSchemes(schemesConfig.split(","));
        runner.setQueries(queries);
        runner.setWarmupIterations(warmupIterations);
        runner.setMeasurementIterations(measurementIterations);
        runner.setVerbose(verbose);

        // Run benchmarks
        Map<String, BenchmarkMetrics> results = runner.runBenchmarks();

        // Security analysis
        Map<String, SecurityReport> securityReports = null;
        if (includeSecurityAnalysis) {
            if (verbose) {
                System.out.println("\n" + ConsoleFormatter.subHeader("Security Analysis"));
            }

            LeakageAnalyzer analyzer = new LeakageAnalyzer();
            securityReports = new LinkedHashMap<>();

            for (SSEScheme scheme : runner.getSchemes()) {
                SecurityReport report = analyzer.analyze(scheme);
                securityReports.put(scheme.getName(), report);

                if (verbose) {
                    System.out.println(report.generateReport());
                }
            }
        }

        // Export results
        exportResults(results, securityReports);

        if (verbose) {
            System.out.println("\n" + ConsoleFormatter.success(
                    "Benchmark complete! Results saved to: " + outputPath));
        }
    }

    /**
     * Validate input and output paths.
     */
    private void validatePaths() throws IOException {
        Path dataPath = Paths.get(datasetPath);
        if (!Files.exists(dataPath)) {
            throw new FileNotFoundException("Dataset path not found: " + datasetPath);
        }

        Path outPath = Paths.get(outputPath);
        if (!Files.exists(outPath)) {
            Files.createDirectories(outPath);
        }
    }

    /**
     * Export benchmark results.
     */
    private void exportResults(Map<String, BenchmarkMetrics> results,
            Map<String, SecurityReport> securityReports) throws IOException {
        JsonExporter exporter = new JsonExporter(true);

        String jsonPath = Paths.get(outputPath, "benchmark_results.json").toString();
        String csvPath = Paths.get(outputPath, "benchmark_results.csv").toString();
        String mdPath = Paths.get(outputPath, "benchmark_summary.md").toString();

        // Export JSON
        if (securityReports != null) {
            exporter.exportResultsWithSecurity(results, securityReports, jsonPath);
        } else {
            exporter.exportResults(results, jsonPath);
        }

        // Export CSV
        exporter.exportToCsv(results, csvPath);

        // Export markdown summary
        try (PrintWriter writer = new PrintWriter(mdPath, "UTF-8")) {
            writer.println("# SSE Benchmark Results");
            writer.println();
            writer.println("## Performance Comparison");
            writer.println();
            writer.println(exporter.generateMarkdownTable(results));

            if (securityReports != null) {
                writer.println();
                writer.println("## Security Analysis");
                writer.println();
                for (SecurityReport report : securityReports.values()) {
                    writer.println(report.generateMarkdownReport());
                }
            }
        }

        if (verbose) {
            System.out.println(ConsoleFormatter.info("Exported: " + jsonPath));
            System.out.println(ConsoleFormatter.info("Exported: " + csvPath));
            System.out.println(ConsoleFormatter.info("Exported: " + mdPath));
        }
    }

    /**
     * Print welcome banner.
     */
    private void printBanner() {
        if (!verbose)
            return;

        System.out.println();
        System.out.println(ConsoleFormatter.header("Clusion SSE Benchmark Suite v" + VERSION));
        System.out.println();
        System.out.println("  Searchable Symmetric Encryption Performance Analysis");
        System.out.println("  Based on Clusion library from Brown University");
        System.out.println();
    }
}
