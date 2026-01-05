# Results Interpretation Guide

How to understand and analyze SSE benchmark results.

## Output Files Overview

After running benchmarks, you'll find these files in the `results/` directory:

| File | Format | Purpose |
|------|--------|---------|
| `benchmark_results.json` | JSON | Complete structured results |
| `benchmark_results.csv` | CSV | Spreadsheet-compatible data |
| `benchmark_summary.md` | Markdown | Human-readable report |

---

## Understanding Metrics

### 1. Timing Metrics

#### Setup Time

```json
"setupTimeMs": 5
```

Time to generate encryption keys. Usually negligible (< 10ms) and performed once.

**Interpretation**: Not a differentiating factor between schemes.

---

#### Index Build Time

```json
"indexBuildTimeMs": 1250
```

Time to construct the encrypted index from the keyword-document mapping.

**Key factors affecting build time**:
- Number of documents
- Number of unique keywords
- Index structure complexity
- Pre-computation (e.g., cross-tag index for IEX)

**Expected ranges** (1000 documents):
| Scheme | Typical Range |
|--------|---------------|
| ZMF | 100-300 ms |
| 2Lev | 200-500 ms |
| IEX-2Lev | 500-2000 ms |
| IEX-ZMF | 300-800 ms |

**Red flags**:
- Build time > 10x expected → Check memory/GC issues
- Large variance between runs → Unstable measurements

---

### 2. Size Metrics

#### Index Size

```json
"indexSize": {
  "indexSizeBytes": 524288,
  "indexSizeKB": 512.0,
  "indexSizeMB": 0.5
}
```

Size of the encrypted index structure.

**Trade-offs**:
- Smaller index → Better for storage-constrained environments
- Larger index → Often enables faster search (pre-computation)

**Expected ratios** (index size / raw data size):
| Scheme | Typical Ratio |
|--------|---------------|
| ZMF | 0.2x - 0.5x (compact) |
| 2Lev-RR | 1.0x - 2.0x |
| 2Lev-RH | 1.5x - 3.0x (padding) |
| IEX-2Lev | 2.0x - 5.0x (cross-tags) |
| IEX-ZMF | 0.5x - 1.5x |

---

### 3. Memory Metrics

```json
"memory": {
  "peakMemoryMB": 256.5,
  "baselineMemoryBytes": 52428800,
  "memoryUsedMB": 206.5
}
```

- **peakMemoryMB**: Maximum heap usage during benchmark
- **baselineMemoryBytes**: Memory before index building
- **memoryUsedMB**: Difference (actual index memory)

**Considerations**:
- JVM overhead is ~50-100MB baseline
- Index memory ≈ index size + overhead structures
- GC can cause spikes in peak memory

---

### 4. Query Performance

```json
"queryPerformance": {
  "totalQueries": 350,
  "successfulQueries": 350,
  "failedQueries": 0,
  "successRate": 100.0,
  "latency": {
    "avgMs": 0.125,
    "minMs": 0.05,
    "maxMs": 2.5,
    "stdDevMs": 0.08,
    "p50Ms": 0.1,
    "p95Ms": 0.3,
    "p99Ms": 0.8
  },
  "queriesPerSecond": 8000.0
}
```

#### Key Latency Metrics

| Metric | Description | When to Use |
|--------|-------------|-------------|
| **avgMs** | Mean query time | General comparison |
| **p50Ms** | Median (50th percentile) | Typical user experience |
| **p95Ms** | 95th percentile | Worst-case for most users |
| **p99Ms** | 99th percentile | True worst-case |
| **stdDevMs** | Standard deviation | Consistency measure |

**Why P95/P99 matter**:
- Average can hide outliers
- P95 represents "almost all" queries
- Low P95 with high P99 → Occasional slow queries

**Expected ranges** (warm cache):
| Scheme | Avg | P95 |
|--------|-----|-----|
| ZMF | 0.1-1 ms | 0.5-5 ms |
| 2Lev | 0.05-0.5 ms | 0.2-2 ms |
| IEX (AND) | 0.1-1 ms | 0.5-5 ms |

#### Throughput

```json
"queriesPerSecond": 8000.0
```

Calculated as `1000 / avgMs`. Higher is better.

**Typical values**:
- < 1,000 QPS: May be bottleneck for high-load apps
- 1,000-10,000 QPS: Good for most applications
- > 10,000 QPS: Excellent performance

---

## Comparing Schemes

### Performance vs. Functionality Matrix

```
                    Search Speed
                    Fast ◄────────────► Slow
                         │
          2Lev-RR ●      │
                         │
    Single   ZMF ●       │
    Keyword              │
    Only                 │
                         │
                    ─────┼─────────────────────
                         │
                         │      ● IEX-2Lev
    Boolean              │
    Queries              │
                         │ ● IEX-ZMF
                         │
                    Compact         Large
                    Index Size
```

### Decision Guide

**Choose ZMF when**:
- Database is small (< 10,000 docs)
- Storage is very limited
- Only single-keyword queries needed
- Simplicity is priority

**Choose 2Lev-RR when**:
- Large database
- Fast single-keyword search critical
- Result count can be visible

**Choose 2Lev-RH when**:
- Result count is sensitive
- Can afford storage overhead

**Choose IEX-2Lev when**:
- AND/OR queries required
- Search speed for conjunctions critical
- Can afford larger index

**Choose IEX-ZMF when**:
- Need boolean queries
- Storage constrained
- Can tolerate small false positive rate

---

## Interpreting Security Analysis

### Leakage Types

| Leakage | Impact | Mitigation |
|---------|--------|------------|
| Search Pattern | Query linkability | Use ORAM |
| Access Pattern | Document correlation | Padding, shuffling |
| Size Pattern | Result inference | Response-hiding mode |
| Intersection | Cross-query info | Avoid repeated queries |

### Security Score

```json
"securityScore": 35,
"securityRating": "LOW"
```

| Score | Rating | Interpretation |
|-------|--------|----------------|
| 80-100 | HIGH | Strong security, few leakages |
| 60-79 | MEDIUM | Balanced security/performance |
| 40-59 | LOW | Significant leakages |
| 0-39 | MINIMAL | Many leakages, use carefully |

**Note**: All practical SSE schemes have some leakage. A "LOW" rating doesn't mean unusable—it means you should understand the implications.

---

## Common Issues & Troubleshooting

### Issue: High Variance in Query Times

**Symptoms**: Large stdDevMs, big gap between avg and P99

**Causes**:
- GC pauses during measurement
- JIT compilation (warmup insufficient)
- Background processes

**Solutions**:
- Increase warmup iterations
- Use `-XX:+UseG1GC` for predictable GC
- Run on dedicated machine

---

### Issue: First Run Much Slower

**Symptoms**: First benchmark significantly slower than subsequent

**Causes**:
- JIT not warmed up
- Disk cache cold
- Class loading overhead

**Solutions**:
- Add warmup phase (done by default)
- Discard first iteration results
- Pre-warm file system cache

---

### Issue: Memory Keeps Growing

**Symptoms**: Peak memory increases over runs

**Causes**:
- Memory leak in scheme
- Insufficient GC between runs
- Large result sets retained

**Solutions**:
- Call `scheme.reset()` between runs
- Force GC: `--gc-between-runs`
- Reduce measurement iterations

---

## Exporting for Analysis

### In Excel/Google Sheets

1. Open `benchmark_results.csv`
2. Create charts:
   - Bar chart for IndexBuildTimeMs by scheme
   - Line chart for P95 latency comparison
   - Scatter plot for Index Size vs Query Speed

### In Python

```python
import pandas as pd
import json

# Load JSON
with open('results/benchmark_results.json') as f:
    data = json.load(f)

# Convert to DataFrame
schemes = pd.DataFrame(data['schemes'])
print(schemes[['schemeName', 'timing', 'queryPerformance']])

# Plot comparison
import matplotlib.pyplot as plt
schemes.plot(x='schemeName', y=['avgMs', 'p95Ms'], kind='bar')
plt.ylabel('Query Time (ms)')
plt.show()
```

### In LaTeX

```latex
\begin{table}[h]
\centering
\caption{SSE Scheme Performance Comparison}
\begin{tabular}{lrrr}
\hline
Scheme & Build (ms) & Avg Query (ms) & Index (KB) \\
\hline
ZMF & 150 & 0.45 & 256 \\
2Lev-RR & 320 & 0.12 & 512 \\
IEX-2Lev & 890 & 0.25 & 1024 \\
\hline
\end{tabular}
\end{table}
```

---

## Reproducing Results

For reproducible benchmarks:

1. **Fix random seed**:
   ```bash
   python generate_dataset.py --seed 42
   ```

2. **Use same queries**:
   ```bash
   java -jar benchmark.jar --queries test_queries.json
   ```

3. **Control environment**:
   - Same JVM version
   - Same hardware
   - Disable CPU scaling
   - Isolate process

4. **Report conditions**:
   ```json
   "metadata": {
     "javaVersion": "17.0.1",
     "osName": "Windows 11",
     "maxMemoryMB": 4096,
     "dataset": "ehealth-1000-seed42"
   }
   ```
