# Clusion SSE Benchmark

A comprehensive benchmarking suite for Searchable Symmetric Encryption (SSE) schemes using the [Clusion library](https://github.com/encryptedsystems/Clusion) from Brown University's Encrypted Systems Lab.

## Features

- **Multiple SSE Schemes**: Benchmark ZMF, 2Lev (RR/RH), IEX-2Lev, and IEX-ZMF
- **Synthetic Dataset Generator**: Python script for e-health test data
- **Comprehensive Metrics**: Timing, memory, index size, query latency
- **Security Analysis**: Leakage profile evaluation for each scheme
- **Standardized Output**: JSON/CSV results for cross-library comparison

## Quick Start in 3 Steps

### 1. Install Dependencies
You need Java 11+, Maven, and Python 3.8+.

**Install the Clusion Library** (Required locally):
```powershell
# Clone the library
git clone https://github.com/encryptedsystems/Clusion.git
cd Clusion

# Build and install (skip tests to avoid environment issues)
mvn clean install -DskipTests
cd ..
```

### 2. Generate Dataset
```powershell
cd dataset
pip install -r requirements.txt
python generate_dataset.py --num-docs 1000 --output-dir ./data
cd ..
```

### 3. Build and Run
Use this **single command** to build the project and launch the benchmark:

```powershell
mvn package -DskipTests; java -jar target/clusion-sse-benchmark-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Or step-by-step:
1. Build the executable JAR:
   ```powershell
   mvn package -DskipTests
   ```
2. Run the JAR:
   ```powershell
   java -jar target/clusion-sse-benchmark-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

## Usage

```bash
java -jar target/clusion-sse-benchmark-1.0-SNAPSHOT-jar-with-dependencies.jar [options]

Options:
  -d, --dataset <path>    Path to dataset directory (default: ./dataset/data)
  -o, --output <path>     Output directory for results (default: ./results)
  -s, --schemes <list>    Comma-separated list of schemes (ZMF,2Lev-RR,IEX-2Lev)
      --warmup <n>        Number of warmup iterations (default: 3)
  -i, --iterations <n>    Number of measurement iterations (default: 10)
      --security          Include security analysis
  -v, --verbose           Verbose output
```

## Project Structure

```
clusion-sse-benchmark/
├── pom.xml                     # Maven configuration
├── dataset/
│   ├── generate_dataset.py     # Dataset generator
│   └── data/                   # Generated dataset
├── src/main/java/it/thesis/sse/
│   ├── ClusionBenchmark.java   # Main entry point
│   ├── dataset/                # Dataset loading logic
│   ├── schemes/                # Wrappers for Clusion schemes
│   ├── benchmark/              # Benchmark execution engine
│   └── security/               # Leakage analysis
└── results/                    # Output folder
```

## License
MIT License
