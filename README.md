<p align="center">
  <img src="https://img.shields.io/badge/Java-11+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 11+"/>
  <img src="https://img.shields.io/badge/Maven-3.6+-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white" alt="Maven"/>
  <img src="https://img.shields.io/badge/Python-3.8+-3776AB?style=for-the-badge&logo=python&logoColor=white" alt="Python"/>
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License"/>
</p>

<h1 align="center">🔐 Clusion SSE Benchmark</h1>

<p align="center">
  <strong>A comprehensive benchmarking suite for Searchable Symmetric Encryption (SSE) schemes</strong>
</p>

<p align="center">
  Built on top of the <a href="https://github.com/encryptedsystems/Clusion">Clusion library</a> from Brown University's Encrypted Systems Lab
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-sse-schemes">SSE Schemes</a> •
  <a href="#-usage">Usage</a> •
  <a href="#-results">Results</a> •
  <a href="#-documentation">Documentation</a>
</p>

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 🧪 Multiple SSE Schemes
Benchmark and compare state-of-the-art SSE constructions:
- **ZMF** — Compact Bloom filter-based (baseline)
- **2Lev** — Two-level sub-linear search
- **IEX-2Lev** — Boolean queries (AND/OR)
- **IEX-ZMF** — Boolean with compact index

</td>
<td width="50%">

### 📊 Comprehensive Metrics
- ⏱️ Indexing & search latency
- 💾 Memory usage & index size
- 🔄 Throughput (queries/sec)
- 📈 P50, P95, P99 percentiles

</td>
</tr>
<tr>
<td width="50%">

### 🛡️ Security Analysis
Evaluate leakage profiles:
- Search pattern leakage
- Access pattern leakage
- Forward/backward privacy
- Volume hiding properties

</td>
<td width="50%">

### 🏥 Synthetic E-Health Dataset
Python generator for realistic medical records:
- Configurable size (1K-100K docs)
- 25 diagnoses, 20 treatments
- Reproducible with seed parameter

</td>
</tr>
</table>

---

## 🚀 Quick Start

### Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 11+ | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| Python | 3.8+ | `python --version` |

### 3-Step Setup

#### Step 1️⃣ — Install Clusion Library

```bash
# Clone and install locally (required dependency)
git clone https://github.com/encryptedsystems/Clusion.git
cd Clusion
mvn clean install -DskipTests
cd ..
```

#### Step 2️⃣ — Generate Dataset

```bash
cd dataset
pip install -r requirements.txt
python generate_dataset.py --num-docs 1000 --output-dir ./data
cd ..
```

<details>
<summary>📋 Dataset generator options</summary>

```bash
python generate_dataset.py \
    --num-docs 5000 \      # Number of documents
    --output-dir ./data \  # Output directory
    --seed 42 \            # Random seed for reproducibility
    --verbose              # Show progress
```

</details>

#### Step 3️⃣ — Build & Run

```bash
# Build and run in one command
mvn package -DskipTests && java -jar target/clusion-sse-benchmark-1.0-SNAPSHOT-jar-with-dependencies.jar
```

<details>
<summary>📋 Or step-by-step</summary>

```bash
# Compile
mvn clean compile

# Package JAR with dependencies
mvn package -DskipTests

# Run benchmark
java -jar target/clusion-sse-benchmark-1.0-SNAPSHOT-jar-with-dependencies.jar
```

</details>

---

## 🔬 SSE Schemes

| Scheme | Search Complexity | Index Size | Boolean Queries | Best For |
|--------|:-----------------:|:----------:|:---------------:|----------|
| **ZMF** | O(n) | ⭐ Compact | ❌ | Small datasets, memory-constrained |
| **2Lev-RR** | O(r/p + log n) | Medium | ❌ | Large datasets, skewed distributions |
| **2Lev-RH** | O(r/p + log n) | Medium | ❌ | Uniform distributions |
| **IEX-2Lev** | O(r/p + log n) | Large | ✅ AND/OR | Complex queries |
| **IEX-ZMF** | O(n) | Medium | ✅ AND/OR | Boolean queries, space-efficient |

> **Legend**: `n` = total documents, `r` = result size, `p` = packing factor

### Security Properties

```
┌─────────────────┬────────────┬────────────┬────────────┬────────────┐
│    Property     │    ZMF     │   2Lev     │  IEX-2Lev  │  IEX-ZMF   │
├─────────────────┼────────────┼────────────┼────────────┼────────────┤
│ Search Pattern  │  ⚠️ Leaked │ ⚠️ Leaked  │ ⚠️ Leaked  │ ⚠️ Leaked  │
│ Access Pattern  │  ⚠️ Leaked │ ⚠️ Leaked  │ ⚠️ Leaked  │ ⚠️ Leaked  │
│ Forward Privacy │     ❌     │     ❌     │     ❌      │     ❌     │
│ Backward Privacy│     ❌     │     ❌     │     ❌      │     ❌     │
└─────────────────┴────────────┴────────────┴────────────┴────────────┘
```

---

## 💻 Usage

```bash
java -jar target/clusion-sse-benchmark-1.0-SNAPSHOT-jar-with-dependencies.jar [OPTIONS]
```

### Options

| Option | Description | Default |
|--------|-------------|---------|
| `-d, --dataset <path>` | Path to dataset directory | `./dataset/data` |
| `-o, --output <path>` | Output directory for results | `./results` |
| `-s, --schemes <list>` | Comma-separated schemes | All schemes |
| `--warmup <n>` | JVM warmup iterations | `3` |
| `-i, --iterations <n>` | Measurement iterations | `10` |
| `--security` | Include security analysis | Disabled |
| `-v, --verbose` | Verbose output | Disabled |

### Examples

```bash
# Run all schemes with default settings
java -jar target/*.jar

# Benchmark specific schemes
java -jar target/*.jar -s ZMF,2Lev-RR

# Custom dataset path with verbose output
java -jar target/*.jar -d /path/to/data -v

# Full benchmark with security analysis
java -jar target/*.jar --security -i 20 -o ./benchmark-results
```

---

## 📈 Results

### Sample Output

```
╔══════════════════════════════════════════════════════════════════════╗
║                    CLUSION SSE BENCHMARK RESULTS                     ║
╠══════════════════════════════════════════════════════════════════════╣
║  Dataset: 1,000 documents | 101 unique keywords | 7.5 avg kw/doc     ║
╚══════════════════════════════════════════════════════════════════════╝

┌──────────────┬─────────────┬────────────┬─────────────┬─────────────┐
│    Scheme    │ Index Time  │ Index Size │ Avg Search  │ Throughput  │
├──────────────┼─────────────┼────────────┼─────────────┼─────────────┤
│ ZMF          │    245 ms   │    48 KB   │   42.3 ms   │   23.6 qps  │
│ 2Lev-RR      │    312 ms   │   156 KB   │    8.7 ms   │  114.9 qps  │
│ 2Lev-RH      │    298 ms   │   148 KB   │    9.2 ms   │  108.7 qps  │
│ IEX-2Lev     │    425 ms   │   312 KB   │   12.4 ms   │   80.6 qps  │
└──────────────┴─────────────┴────────────┴─────────────┴─────────────┘
```

### Output Files

```
results/
├── benchmark_results.json    # Complete metrics in JSON
├── comparison_table.csv      # Summary table for thesis/papers
├── security_analysis.json    # Leakage profile evaluation
└── charts/
    ├── indexing_time.png
    ├── search_latency.png
    └── throughput_comparison.png
```

<details>
<summary>📋 JSON output format</summary>

```json
{
  "benchmark_info": {
    "tool": "Clusion",
    "version": "1.0.0",
    "timestamp": "2026-01-05T15:30:00Z"
  },
  "dataset": {
    "documents": 1000,
    "keywords": 101
  },
  "schemes": [
    {
      "name": "ZMF",
      "indexing_ms": 245,
      "index_size_kb": 48,
      "queries": [
        {
          "keyword": "diabetes",
          "results": 78,
          "latency_ms": 42.3
        }
      ]
    }
  ]
}
```

</details>

---

## 📁 Project Structure

```
clusion-sse-benchmark/
│
├── 📄 pom.xml                          # Maven configuration
├── 📄 README.md                        # This file
│
├── 📂 dataset/
│   ├── 🐍 generate_dataset.py          # Dataset generator script
│   ├── 📄 requirements.txt             # Python dependencies
│   └── 📂 data/                        # Generated data (gitignored)
│       ├── documents/                  # Individual .txt files
│       ├── dataset.json                # Full dataset
│       ├── keyword_index.json          # Keyword → doc_ids mapping
│       └── test_queries.json           # Standardized test queries
│
├── 📂 src/main/java/it/thesis/sse/
│   ├── ☕ ClusionBenchmark.java         # Main entry point
│   ├── 📂 dataset/                     # Dataset loading
│   ├── 📂 schemes/                     # Clusion scheme wrappers
│   ├── 📂 benchmark/                   # Benchmark engine
│   └── 📂 security/                    # Leakage analysis
│
├── 📂 results/                         # Output folder (gitignored)
│
└── 📂 docs/
    ├── 📄 SCHEMES.md                   # Detailed scheme documentation
    ├── 📄 SETUP.md                     # Installation guide
    └── 📄 TROUBLESHOOTING.md           # Common issues & solutions
```

---

## 🔧 Troubleshooting

<details>
<summary><b>❌ Clusion dependency not found</b></summary>

Make sure you've installed Clusion locally:

```bash
git clone https://github.com/encryptedsystems/Clusion.git
cd Clusion
mvn clean install -DskipTests
```

</details>

<details>
<summary><b>❌ Java version error</b></summary>

Clusion requires Java 11+. Check your version:

```bash
java -version
```

If using multiple Java versions, set `JAVA_HOME`:

```bash
export JAVA_HOME=/path/to/java11
```

</details>

<details>
<summary><b>❌ Dataset not found</b></summary>

Generate the dataset first:

```bash
cd dataset
python generate_dataset.py --num-docs 1000 --output-dir ./data
```

Or specify a custom path:

```bash
java -jar target/*.jar -d /custom/path/to/data
```

</details>

<details>
<summary><b>❌ OutOfMemoryError</b></summary>

Increase JVM heap size:

```bash
java -Xmx4g -jar target/*.jar
```

</details>

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [SCHEMES.md](docs/SCHEMES.md) | Detailed explanation of each SSE scheme |
| [SETUP.md](docs/SETUP.md) | Step-by-step installation guide |
| [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Common issues and solutions |

### Academic References

- 📄 [Cash et al., 2014](https://eprint.iacr.org/2013/169) — Highly-Scalable Searchable Symmetric Encryption
- 📄 [Kamara et al., 2012](https://eprint.iacr.org/2012/144) — Dynamic Searchable Symmetric Encryption
- 📄 [Clusion Paper](https://eprint.iacr.org/2016/718) — All Your Queries Are Belong to Us

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <sub>Built with ☕ for the Searchable Encryption research community</sub>
</p>

<p align="center">
  <a href="https://github.com/encryptedsystems/Clusion">
    <img src="https://img.shields.io/badge/Powered%20by-Clusion-blue?style=flat-square" alt="Powered by Clusion"/>
  </a>
</p>
