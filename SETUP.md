# Setup Guide

Complete step-by-step installation guide for the Clusion SSE Benchmark suite.

## System Requirements

### Minimum Requirements

- **Operating System**: Windows 10/11, macOS 10.14+, or Linux (Ubuntu 18.04+)
- **Java**: JDK 11 or higher (OpenJDK or Oracle JDK)
- **Maven**: 3.6.0 or higher
- **Python**: 3.8 or higher (for dataset generation)
- **Memory**: 4 GB RAM minimum (8 GB recommended for large datasets)
- **Disk Space**: 1 GB free space

### Recommended

- **Java**: JDK 17 LTS
- **Memory**: 16 GB RAM for benchmarks with 10,000+ documents
- **SSD**: Faster I/O improves index building performance

## Step 1: Install Java

### Windows

1. Download JDK from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
2. Run the installer
3. Add Java to PATH:
   ```powershell
   setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-17"
   setx PATH "%PATH%;%JAVA_HOME%\bin"
   ```
4. Verify:
   ```cmd
   java -version
   ```

### macOS

```bash
brew install openjdk@17
echo 'export JAVA_HOME=$(/usr/libexec/java_home)' >> ~/.zshrc
source ~/.zshrc
java -version
```

### Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install openjdk-17-jdk
java -version
```

## Step 2: Install Maven

### Windows

1. Download from [Maven Downloads](https://maven.apache.org/download.cgi)
2. Extract to `C:\Program Files\Apache\maven`
3. Add to PATH:
   ```powershell
   setx MAVEN_HOME "C:\Program Files\Apache\maven"
   setx PATH "%PATH%;%MAVEN_HOME%\bin"
   ```
4. Verify:
   ```cmd
   mvn -version
   ```

### macOS

```bash
brew install maven
mvn -version
```

### Linux

```bash
sudo apt install maven
mvn -version
```

## Step 3: Install Python

### Windows

1. Download from [Python.org](https://www.python.org/downloads/)
2. Run installer, check "Add Python to PATH"
3. Verify:
   ```cmd
   python --version
   pip --version
   ```

### macOS/Linux

```bash
# macOS
brew install python@3.11

# Linux
sudo apt install python3 python3-pip

# Verify
python3 --version
pip3 --version
```

## Step 4: Clone and Install Clusion

> ⚠️ **Required**: Clusion must be installed to your local Maven repository.

```bash
# Clone Clusion
git clone https://github.com/encryptedsystems/Clusion.git
cd Clusion

# Install to local Maven repository
mvn clean install -DskipTests

# Verify installation
ls ~/.m2/repository/org/crypto/sse/clusion/
```

If the build fails, try:
```bash
# Skip problematic tests
mvn clean install -DskipTests -Dmaven.test.skip=true

# Or with specific Java version
JAVA_HOME=/path/to/jdk11 mvn clean install -DskipTests
```

## Step 5: Set Up This Project

### Clone or Create Project

```bash
# Navigate to desired directory
cd /path/to/projects

# If cloning from repository
git clone <repository-url> clusion-sse-benchmark
cd clusion-sse-benchmark

# Or copy from existing location
cp -r /path/to/clusion-sse-benchmark .
cd clusion-sse-benchmark
```

### Install Python Dependencies

```bash
cd dataset
pip install -r requirements.txt
cd ..
```

### Build the Project

```bash
# Clean build
mvn clean compile

# If successful, you'll see:
# [INFO] BUILD SUCCESS
```

### Troubleshooting Build Issues

**Problem**: Clusion dependency not found
```
[ERROR] Could not resolve dependencies... org.crypto.sse:clusion:1.0-SNAPSHOT
```

**Solution**: Re-install Clusion:
```bash
cd /path/to/Clusion
mvn clean install -DskipTests
```

**Problem**: Java version mismatch
```
[ERROR] Source option 11 is not supported
```

**Solution**: Use correct Java version:
```bash
export JAVA_HOME=/path/to/jdk11
mvn clean compile
```

## Step 6: Generate Test Dataset

```bash
cd dataset

# Generate 1000 documents (recommended for testing)
python generate_dataset.py --num-docs 1000 --output-dir ./data --seed 42

# For larger benchmarks
python generate_dataset.py --num-docs 10000 --output-dir ./data

cd ..
```

Output structure:
```
dataset/data/
├── dataset.json          # All documents as JSON
├── documents/            # Individual .txt files
├── keyword_index.json    # Inverted index
├── statistics.json       # Dataset statistics
└── test_queries.json     # Pre-generated queries
```

## Step 7: Run Benchmark

### Quick Test

```bash
mvn exec:java
```

### Full Benchmark

```bash
# Build JAR
mvn package -DskipTests

# Run with options
java -jar target/clusion-sse-benchmark-1.0-SNAPSHOT-jar-with-dependencies.jar \
    --dataset ./dataset/data \
    --output ./results \
    --schemes ZMF,2Lev-RR,IEX-2Lev \
    --iterations 10 \
    --verbose
```

### Verify Results

```bash
# Check output files
ls results/
# benchmark_results.json
# benchmark_results.csv
# benchmark_summary.md
```

## Configuration

### config.properties

Edit `src/main/resources/config.properties` for default settings:

```properties
# Dataset paths
dataset.path=./dataset/data

# Output
output.path=./results

# Benchmark settings
benchmark.warmup.iterations=3
benchmark.measurement.iterations=10
benchmark.schemes=ZMF,2Lev-RR,IEX-2Lev

# Security analysis
security.leakage.analysis=true
```

### Memory Settings

For large datasets, increase JVM heap:

```bash
# Set heap to 4GB
java -Xmx4g -jar clusion-benchmark.jar ...

# For very large datasets
java -Xmx8g -XX:+UseG1GC -jar clusion-benchmark.jar ...
```

## IDE Setup

### IntelliJ IDEA

1. File → Open → Select project root
2. Trust the project if prompted
3. Wait for Maven import
4. Right-click `pom.xml` → Maven → Reload Project
5. Run `ClusionBenchmark.main()` with Ctrl+Shift+F10

### VS Code

1. Install "Extension Pack for Java"
2. Open project folder
3. Wait for Java project import
4. Press F5 to run

### Eclipse

1. File → Import → Maven → Existing Maven Projects
2. Select project directory
3. Right-click project → Maven → Update Project
4. Run As → Java Application → ClusionBenchmark

## Verification

Run the complete verification:

```bash
# 1. Python dataset generation
cd dataset
python generate_dataset.py --num-docs 100 --output-dir ./test_data --seed 123
ls test_data/  # Should show 5 files + documents folder

# 2. Maven build
cd ..
mvn clean compile test

# 3. Benchmark execution
mvn exec:java -Dexec.args="--dataset ./dataset/test_data --iterations 3"

# 4. Check results
cat results/benchmark_results.json | head -50
```

## Next Steps

- Read [docs/SCHEMES.md](docs/SCHEMES.md) for SSE scheme details
- Read [docs/API.md](docs/API.md) for programmatic usage
- Read [docs/RESULTS_INTERPRETATION.md](docs/RESULTS_INTERPRETATION.md) for understanding output
