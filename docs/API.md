# API Documentation

Programmatic API reference for using the Clusion SSE Benchmark library in your Java applications.

## Core Components

### 1. Dataset Loading

#### DatasetLoader

Loads e-health documents from JSON or text files.

```java
import it.thesis.sse.dataset.DatasetLoader;

// Create loader
DatasetLoader loader = new DatasetLoader();

// Load from JSON
loader.loadFromJson("path/to/dataset.json");

// Or load from text files
loader.loadFromTextFiles("path/to/documents/");

// Load keyword index (optional, for expected results)
loader.loadKeywordIndex("path/to/keyword_index.json");

// Get Clusion-compatible multimap
Multimap<String, String> multimap = loader.buildClusionMultimap();

// Get statistics
System.out.println("Documents: " + loader.getDocumentCount());
System.out.println("Keywords: " + loader.getKeywordCount());
```

#### KeywordExtractor

Extracts and normalizes keywords from documents.

```java
import it.thesis.sse.dataset.KeywordExtractor;

// Builder pattern
KeywordExtractor extractor = KeywordExtractor.builder()
    .minLength(2)
    .maxLength(50)
    .lowercase(true)
    .removeStopwords(true)
    .build();

// Extract from document
Set<String> keywords = extractor.extract(document);

// Extract from raw text
Set<String> keywords = extractor.extractFromText("Clinical notes here...");

// Get keyword frequencies
Map<String, Integer> frequencies = extractor.buildKeywordFrequencies(documents);
```

---

### 2. SSE Schemes

#### SSEScheme Interface

All schemes implement this common interface:

```java
public interface SSEScheme {
    String getName();
    String getDescription();
    
    byte[] setup() throws Exception;
    void setup(byte[] key) throws Exception;
    
    long buildIndex(Multimap<String, String> multimap) throws Exception;
    long buildIndex(Map<String, List<String>> keywordIndex) throws Exception;
    
    List<String> search(String keyword) throws Exception;
    List<String> searchAnd(List<String> keywords) throws Exception;
    List<String> searchOr(List<String> keywords) throws Exception;
    
    boolean supportsBoolean();
    long getIndexSizeBytes();
    Map<String, String> getLeakageProfile();
    BenchmarkMetrics getMetrics();
    
    void reset();
    void close();
}
```

#### SchemeFactory

Create schemes by name:

```java
import it.thesis.sse.schemes.SchemeFactory;

// Create single scheme
SSEScheme zmf = SchemeFactory.createScheme("ZMF");
SSEScheme twoLev = SchemeFactory.createScheme("2Lev-RR");
SSEScheme iex = SchemeFactory.createScheme("IEX-2Lev");

// Create multiple schemes
Map<String, SSEScheme> schemes = SchemeFactory.createSchemes("ZMF,2Lev-RR,IEX-2Lev");

// Check availability
if (SchemeFactory.isSchemeAvailable("IEX-ZMF")) {
    SSEScheme scheme = SchemeFactory.createScheme("IEX-ZMF");
}

// List all available
Set<String> available = SchemeFactory.getAvailableSchemes();
```

#### Using a Scheme Directly

```java
import it.thesis.sse.schemes.ZMFScheme;

// Create and configure
ZMFScheme zmf = new ZMFScheme();
zmf.setFalsePositiveRate(0.001);
zmf.setBloomFilterSize(2048);

// Setup (generates key)
byte[] key = zmf.setup();

// Or use existing key
zmf.setup(existingKey);

// Build index
long buildTimeMs = zmf.buildIndex(keywordMultimap);

// Search
List<String> results = zmf.search("diabetes");

// Get metrics
BenchmarkMetrics metrics = zmf.getMetrics();
System.out.println("Index size: " + zmf.getIndexSizeBytes() + " bytes");

// Cleanup
zmf.close();
```

---

### 3. Benchmark Infrastructure

#### BenchmarkRunner

Orchestrates complete benchmark workflow:

```java
import it.thesis.sse.benchmark.BenchmarkRunner;

// Create runner with dataset
BenchmarkRunner runner = new BenchmarkRunner(datasetLoader);

// Add schemes to benchmark
runner.addSchemes("ZMF", "2Lev-RR", "IEX-2Lev");

// Or add scheme instances
runner.addScheme(new ZMFScheme());

// Set test queries
runner.setQueries(queryList);

// Configure
runner.setWarmupIterations(3);
runner.setMeasurementIterations(10);
runner.setGcBetweenRuns(true);
runner.setVerbose(true);

// Run benchmarks
Map<String, BenchmarkMetrics> results = runner.runBenchmarks();

// Access results
for (Map.Entry<String, BenchmarkMetrics> entry : results.entrySet()) {
    System.out.println(entry.getKey() + ": " + 
        entry.getValue().getAvgQueryTimeMs() + " ms avg");
}
```

#### QueryGenerator

Generate or load test queries:

```java
import it.thesis.sse.benchmark.QueryGenerator;

// Create with keyword index
QueryGenerator qg = new QueryGenerator(keywordIndex);

// With reproducible seed
QueryGenerator qg = new QueryGenerator(keywordIndex, 42L);

// Load pre-generated queries
List<QueryMetric> queries = qg.loadQueries("path/to/test_queries.json");

// Generate new queries
List<QueryMetric> queries = qg.generateQueries(
    15,  // single keyword queries
    10,  // AND queries
    10   // OR queries
);

// Generate specific query
QueryMetric single = qg.generateQueryForKeyword("diabetes");
QueryMetric and = qg.generateAndQueryForKeywords("diabetes", "insulin");

// Get keyword distribution
Map<String, List<String>> byFreq = qg.getKeywordsByFrequency();
System.out.println("Rare keywords: " + byFreq.get("rare").size());
```

#### BenchmarkMetrics

Access benchmark results:

```java
BenchmarkMetrics metrics = scheme.getMetrics();

// Timing
long setupMs = metrics.getSetupTimeMs();
long buildMs = metrics.getIndexBuildTimeMs();

// Size
long indexBytes = metrics.getIndexSizeBytes();
double indexKB = metrics.getIndexSizeKB();
double indexMB = metrics.getIndexSizeMB();

// Memory
double peakMB = metrics.getPeakMemoryMB();
double usedMB = metrics.getMemoryUsedMB();

// Query performance
metrics.calculateStatistics();
double avgMs = metrics.getAvgQueryTimeMs();
double p50Ms = metrics.getP50QueryTimeMs();
double p95Ms = metrics.getP95QueryTimeMs();
double p99Ms = metrics.getP99QueryTimeMs();
double qps = metrics.getQueriesPerSecond();

// Success rate
double successRate = metrics.getSuccessRate();

// Export as map
Map<String, Object> map = metrics.toMap();

// Print summary
System.out.println(metrics.toString());
```

---

### 4. Security Analysis

#### LeakageAnalyzer

Analyze scheme security:

```java
import it.thesis.sse.security.LeakageAnalyzer;
import it.thesis.sse.security.SecurityReport;

LeakageAnalyzer analyzer = new LeakageAnalyzer();

// Analyze single scheme
SecurityReport report = analyzer.analyze(scheme);
System.out.println(report.generateReport());

// Compare multiple schemes
Map<String, SecurityReport> reports = analyzer.compareSchemes(schemeList);

// Generate comparison matrix
String matrix = analyzer.generateComparisonMatrix(schemeList);
System.out.println(matrix);

// Get attack vectors
List<String> attacks = analyzer.getAttackVectors(scheme);

// Find scheme avoiding specific leakages
Set<LeakageType> mustAvoid = Set.of(
    LeakageType.SIZE_PATTERN,
    LeakageType.INTERSECTION_PATTERN
);
SSEScheme recommended = analyzer.recommendScheme(schemes, mustAvoid);
```

#### SecurityReport

Access security analysis results:

```java
SecurityReport report = analyzer.analyze(scheme);

// Overall score
int score = report.getSecurityScore();      // 0-100
String rating = report.getSecurityRating(); // HIGH, MEDIUM, LOW, MINIMAL

// Check specific leakage
if (report.isLeaked(LeakageType.ACCESS_PATTERN)) {
    System.out.println("Warning: Access pattern is leaked");
}

// Get all leaked types
Set<LeakageType> leaked = report.getLeakedTypes();

// Export
Map<String, Object> map = report.toMap();
String markdown = report.generateMarkdownReport();
String formatted = report.generateReport();
```

---

### 5. Utilities

#### JsonExporter

Export results to various formats:

```java
import it.thesis.sse.util.JsonExporter;

JsonExporter exporter = new JsonExporter(true); // pretty print

// Export to JSON
exporter.exportResults(results, "results.json");

// Export with security
exporter.exportResultsWithSecurity(results, securityReports, "results.json");

// Export to CSV
exporter.exportToCsv(results, "results.csv");

// Generate markdown table
String table = exporter.generateMarkdownTable(results);

// Get as JSON string
String json = exporter.toJson(results);
```

#### CryptoUtils

Cryptographic primitives:

```java
import it.thesis.sse.util.CryptoUtils;

// Key generation
byte[] key = CryptoUtils.generateKey();
byte[] random = CryptoUtils.generateRandom(32);

// Encryption
byte[] ciphertext = CryptoUtils.encryptAesGcm(plaintext, key);
byte[] decrypted = CryptoUtils.decryptAesGcm(ciphertext, key);

// String encryption
String encrypted = CryptoUtils.encryptString("secret", key);
String decrypted = CryptoUtils.decryptString(encrypted, key);

// HMAC / PRF
byte[] mac = CryptoUtils.hmac(key, data);
byte[] prf = CryptoUtils.prf(key, "input");

// Hashing
byte[] hash = CryptoUtils.hash("data");

// Encoding
String hex = CryptoUtils.toHex(bytes);
String base64 = CryptoUtils.toBase64(bytes);

// Secure operations
CryptoUtils.secureErase(sensitiveData);
boolean equal = CryptoUtils.constantTimeEquals(a, b);
```

#### ConsoleFormatter

Formatted console output:

```java
import it.thesis.sse.util.ConsoleFormatter;

// Headers
System.out.println(ConsoleFormatter.header("Benchmark Results"));
System.out.println(ConsoleFormatter.subHeader("ZMF Scheme"));

// Status messages
System.out.println(ConsoleFormatter.success("Build complete"));
System.out.println(ConsoleFormatter.failure("Query failed"));
System.out.println(ConsoleFormatter.warning("High memory usage"));
System.out.println(ConsoleFormatter.info("Processing..."));

// Formatting
String formatted = ConsoleFormatter.formatDuration(1500);  // "1.50 s"
String size = ConsoleFormatter.formatBytes(1048576);       // "1.00 MB"

// Progress
System.out.print(ConsoleFormatter.progressBar(50, 100, 40));
```

---

## Complete Example

```java
import it.thesis.sse.*;
import it.thesis.sse.dataset.*;
import it.thesis.sse.schemes.*;
import it.thesis.sse.benchmark.*;
import it.thesis.sse.security.*;
import it.thesis.sse.util.*;

public class CustomBenchmark {
    public static void main(String[] args) throws Exception {
        // Load data
        DatasetLoader loader = new DatasetLoader();
        loader.loadFromJson("dataset/data/dataset.json");
        
        // Generate queries
        QueryGenerator qg = new QueryGenerator(loader.getKeywordIndex(), 42);
        List<QueryMetric> queries = qg.generateQueries(20, 15, 10);
        
        // Setup benchmark
        BenchmarkRunner runner = new BenchmarkRunner(loader);
        runner.addSchemes("ZMF", "2Lev-RR", "IEX-2Lev");
        runner.setQueries(queries);
        runner.setMeasurementIterations(20);
        
        // Run
        Map<String, BenchmarkMetrics> results = runner.runBenchmarks();
        
        // Security analysis
        LeakageAnalyzer analyzer = new LeakageAnalyzer();
        Map<String, SecurityReport> security = new HashMap<>();
        for (SSEScheme scheme : runner.getSchemes()) {
            security.put(scheme.getName(), analyzer.analyze(scheme));
        }
        
        // Export
        JsonExporter exporter = new JsonExporter(true);
        exporter.exportResultsWithSecurity(results, security, "custom_results.json");
        
        // Print summary
        System.out.println("\n" + exporter.generateMarkdownTable(results));
    }
}
```
