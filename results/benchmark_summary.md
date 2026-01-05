# SSE Benchmark Results

## Performance Comparison

| Scheme | Index Build | Index Size | Avg Query | P95 Query | QPS |
|--------|-------------|------------|-----------|-----------|-----|
| ZMF | 43 ms | 541.80 KB | 4.532 ms | 26.263 ms | 220.6 |
| 2Lev-RR | 41 ms | 731.62 KB | 0.030 ms | 0.113 ms | 33852.4 |
| IEX-2Lev | 723 ms | 4917.08 KB | 0.015 ms | 0.058 ms | 68254.1 |


## Security Analysis

## Security Report: ZMF

**Security Score:** 22/100
**Rating:** MINIMAL

### Leakage Profile

| Leakage Type | Status | Description |
|--------------|--------|-------------|
| SEARCH_PATTERN | ⚠️ LEAKED | Revealed - repeated queries for same keyword are linkable |
| ACCESS_PATTERN | ⚠️ LEAKED | Revealed - which documents match a query |
| SIZE_PATTERN | ⚠️ LEAKED | Revealed - number of matching documents |
| FORWARD_PRIVACY | ⚠️ LEAKED | No |
| BACKWARD_PRIVACY | ⚠️ LEAKED | No |

### Summary

- **Protected:** 0/5 leakage types

## Security Report: 2Lev-RR

**Security Score:** 22/100
**Rating:** MINIMAL

### Leakage Profile

| Leakage Type | Status | Description |
|--------------|--------|-------------|
| SEARCH_PATTERN | ⚠️ LEAKED | Revealed - repeated queries linkable |
| ACCESS_PATTERN | ⚠️ LEAKED | Revealed - which documents match |
| SIZE_PATTERN | ⚠️ LEAKED | Revealed - result count visible |
| FORWARD_PRIVACY | ⚠️ LEAKED | No |
| BACKWARD_PRIVACY | ⚠️ LEAKED | No |

### Summary

- **Protected:** 0/5 leakage types

## Security Report: IEX-2Lev

**Security Score:** 10/100
**Rating:** MINIMAL

### Leakage Profile

| Leakage Type | Status | Description |
|--------------|--------|-------------|
| SEARCH_PATTERN | ⚠️ LEAKED | Revealed |
| ACCESS_PATTERN | ⚠️ LEAKED | Revealed |
| SIZE_PATTERN | ⚠️ LEAKED | Revealed for smallest term |
| FORWARD_PRIVACY | ⚠️ LEAKED | No |
| BACKWARD_PRIVACY | ⚠️ LEAKED | No |
| INTERSECTION_PATTERN | ⚠️ LEAKED | Revealed - intersection sizes visible |

### Summary

- **Protected:** 0/6 leakage types

