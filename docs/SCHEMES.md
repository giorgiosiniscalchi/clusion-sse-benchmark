# SSE Schemes Documentation

Detailed technical documentation for each Searchable Symmetric Encryption scheme implemented in this benchmark suite.

## Overview

Searchable Symmetric Encryption (SSE) allows a client to outsource encrypted data to a server while maintaining the ability to search over it. Each scheme offers different trade-offs between:

- **Search efficiency**: Time complexity of search operations
- **Storage overhead**: Size of encrypted index
- **Security**: Information leaked during operations
- **Functionality**: Types of queries supported

---

## ZMF (Zhao-Matryoshka Filter)

### Description

ZMF is a baseline SSE scheme that uses Matryoshka (nested) Bloom filters for compact storage. It provides a simple but space-efficient approach to encrypted search.

### Key Properties

| Property | Value |
|----------|-------|
| **Search Complexity** | O(n) - linear scan |
| **Index Size** | Compact (Bloom filter based) |
| **Boolean Queries** | Not supported natively |
| **Forward Privacy** | No |
| **Backward Privacy** | No |

### How It Works

1. **Setup**: Generate master secret key K
2. **Index Build**: For each (keyword, docId) pair:
   - Compute token T = PRF(K, keyword)
   - Add encrypted docId to Bloom filter at position f(T)
3. **Search**: 
   - Client sends search token T = PRF(K, keyword)
   - Server scans Bloom filter for all entries at f(T)
   - Returns encrypted document IDs

### Leakage Profile

```
✓ Search Pattern: LEAKED - Repeated queries are linkable
✓ Access Pattern: LEAKED - Matching documents revealed
✓ Size Pattern: LEAKED - Result count visible
✗ Query Content: HIDDEN - Actual keywords encrypted
```

### Use Cases

- Prototyping and testing
- Small databases where linear scan is acceptable
- Scenarios prioritizing index compactness over search speed

### Example Usage

```java
ZMFScheme zmf = new ZMFScheme();
zmf.setup();
zmf.buildIndex(keywordMap);

List<String> results = zmf.search("diabetes");
```

---

## 2Lev (Two-Level Index)

### Description

2Lev provides sub-linear search complexity using a two-level index structure. Available in two variants:

- **2Lev-RR (Response-Revealing)**: Standard efficiency
- **2Lev-RH (Response-Hiding)**: Hides result count

### Key Properties

| Property | 2Lev-RR | 2Lev-RH |
|----------|---------|---------|
| **Search Complexity** | O(r/p + log n) | O(r/p + log n) |
| **Result Size** | Revealed | Hidden (padded) |
| **Storage Overhead** | Moderate | Higher (padding) |

Where:
- r = number of results
- p = packing parameter (documents per bucket)
- n = total documents

### Two-Level Structure

```
Level 1: Keyword → Bucket IDs
         ┌─────────────┐
         │  "diabetes" │ → [B1, B2, B3]
         │  "insulin"  │ → [B4]
         └─────────────┘

Level 2: Bucket ID → Document IDs
         ┌─────────────┐
         │     B1      │ → [doc1, doc2, doc3, ...]
         │     B2      │ → [doc15, doc16, ...]
         └─────────────┘
```

### Response-Hiding Mode

In RH mode, results are padded to the nearest power of 2:
- 5 results → padded to 8
- 100 results → padded to 128

This prevents exact result count leakage.

### Leakage Profile

```
RR Mode:
  ✓ Search Pattern: LEAKED
  ✓ Access Pattern: LEAKED
  ✓ Size Pattern: LEAKED

RH Mode:
  ✓ Search Pattern: LEAKED
  ✓ Access Pattern: LEAKED
  ✗ Size Pattern: HIDDEN (approximate)
```

### Use Cases

- Large databases requiring efficient search
- When sub-linear complexity is essential
- RH variant when result count is sensitive

### Example Usage

```java
// Response-Revealing
TwoLevScheme rr = new TwoLevScheme(false);
rr.setup();
rr.buildIndex(keywordMap);

// Response-Hiding
TwoLevScheme rh = new TwoLevScheme(true);
rh.setup();
rh.buildIndex(keywordMap);
```

---

## IEX-2Lev (Index Expression with 2Lev)

### Description

IEX-2Lev extends 2Lev with native support for boolean queries (AND, OR). Based on the OXT (Oblivious Cross-Tags) protocol.

### Key Properties

| Property | Value |
|----------|-------|
| **Search Complexity** | O(r_s + t) |
| **Boolean Support** | AND, OR queries |
| **Index Size** | Larger (cross-tag index) |

Where:
- r_s = size of smallest term's result set
- t = number of query terms

### Cross-Tag Index

For efficient AND queries, IEX maintains pre-computed intersections:

```
Keyword Index:
  "diabetes" → {doc1, doc2, doc3, doc5}
  "insulin"  → {doc1, doc3, doc7}

Cross-Tag Index:
  ("diabetes", "insulin") → {doc1, doc3}  // Pre-computed intersection
```

### Query Processing

**AND Query**: `search("diabetes" AND "insulin")`
1. Look up cross-tag index for pair
2. If found, return pre-computed result
3. Otherwise, use OXT protocol:
   - Get smallest result set (s-term)
   - Filter with remaining terms (x-terms)

**OR Query**: `search("diabetes" OR "insulin")`
1. Search each term independently
2. Union the results

### Leakage Profile

```
✓ Search Pattern: LEAKED
✓ Access Pattern: LEAKED
✓ Intersection Pattern: LEAKED (for AND queries)
⚠ IP Leakage: s-term result set fully revealed
```

### Security Considerations

The Intersection Pattern (IP) leakage is significant:
- Attacker learns which documents contain multiple keywords
- Can enable inference attacks on query content
- Mitigated by careful s-term selection

### Use Cases

- Applications requiring complex queries
- When AND/OR operations are common
- Acceptable IP leakage trade-off

### Example Usage

```java
IEXTwoLevScheme iex = new IEXTwoLevScheme();
iex.setup();
iex.buildIndex(keywordMap);

// Boolean queries
List<String> andResults = iex.searchAnd(List.of("diabetes", "insulin"));
List<String> orResults = iex.searchOr(List.of("cardiologia", "neurologia"));
```

---

## IEX-ZMF (Index Expression with ZMF)

### Description

IEX-ZMF combines boolean query support with ZMF's compact Bloom filter storage. Provides space-efficient boolean search with potential false positives.

### Key Properties

| Property | Value |
|----------|-------|
| **Search Complexity** | O(min r_i) with Bloom filtering |
| **Index Size** | Compact (Bloom filters) |
| **Boolean Support** | AND, OR queries |
| **False Positives** | Possible (configurable rate) |

### Bloom Filter Optimization

Before executing expensive intersection, Bloom filters provide fast filtering:

```
Query: "diabetes" AND "insulin"

1. Bloom Filter Check:
   BF_diabetes AND BF_insulin = BF_result
   
2. If BF_result is empty → No matches (fast rejection)

3. Otherwise → Verify with actual document lookup
```

### False Positive Rate

Configurable via:
```java
iexZmf.setFalsePositiveRate(0.01);  // 1% FP rate
iexZmf.setNumHashFunctions(3);
```

Trade-off:
- Lower FP rate → Larger Bloom filters
- Higher FP rate → More false verifications

### Leakage Profile

```
✓ Search Pattern: LEAKED
✓ Access Pattern: LEAKED (with possible FP)
✓ Bloom Membership: Approximate document presence
⚠ False Positives leak approximate set sizes
```

### Use Cases

- Memory-constrained environments
- When small false positive rate is acceptable
- Boolean queries with compact storage

### Example Usage

```java
IEXZMFScheme iexZmf = new IEXZMFScheme();
iexZmf.setFalsePositiveRate(0.001);  // 0.1% FP rate
iexZmf.setup();
iexZmf.buildIndex(keywordMap);

List<String> results = iexZmf.searchAnd(List.of("cardiologia", "ipertensione"));
```

---

## Comparison Table

| Scheme | Search | Bool | Size | Security | Best For |
|--------|--------|------|------|----------|----------|
| ZMF | O(n) | No | Small | Low | Small DB, testing |
| 2Lev-RR | O(r/p+log n) | No | Medium | Low | Large DB, speed |
| 2Lev-RH | O(r/p+log n) | No | Large | Medium | Size-sensitive |
| IEX-2Lev | O(r_s+t) | Yes | Large | Low | Complex queries |
| IEX-ZMF | O(min r_i) | Yes | Medium | Low | Compact + bool |

---

## References

1. Cash, D., et al. "Dynamic Searchable Encryption in Very-Large Databases: Data Structures and Implementation." NDSS 2014.

2. Kamara, S., et al. "Structured Encryption and Controlled Disclosure." ASIACRYPT 2010.

3. Curtmola, R., et al. "Searchable symmetric encryption: improved definitions and efficient constructions." CCS 2006.

4. [Clusion GitHub Repository](https://github.com/encryptedsystems/Clusion)
