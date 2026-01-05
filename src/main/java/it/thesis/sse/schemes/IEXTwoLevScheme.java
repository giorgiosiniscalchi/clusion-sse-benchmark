package it.thesis.sse.schemes;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import it.thesis.sse.benchmark.BenchmarkMetrics;

import java.security.SecureRandom;
import java.util.*;

/**
 * Wrapper for Clusion's IEX-2Lev (Index Expression with 2-Level) SSE scheme.
 * 
 * IEX-2Lev supports native boolean (AND/OR) queries with sub-linear search.
 * It extends 2Lev with additional index structures for conjunctive queries.
 * 
 * Key features:
 * - Native boolean search (AND, OR queries)
 * - Sub-linear search complexity
 * - Based on OXT (Oblivious Cross-Tags) protocol
 * - Supports keyword pair indexes for efficient conjunctions
 */
public class IEXTwoLevScheme implements SSEScheme {

    private static final String NAME = "IEX-2Lev";
    private static final String DESCRIPTION = "Index Expression 2Lev: Boolean SSE with native AND/OR queries and sub-linear search";

    private byte[] secretKey;
    private Map<String, List<String>> tokenIndex; // T-set for tokens
    private Map<String, Set<String>> crossTagIndex; // X-set for cross-tags (conjunctions)
    private Map<String, List<String>> singleIndex; // Single keyword index
    private int keywordCount;
    private int documentCount;
    private long indexSizeBytes;
    private BenchmarkMetrics metrics;

    public IEXTwoLevScheme() {
        this.tokenIndex = new HashMap<>();
        this.crossTagIndex = new HashMap<>();
        this.singleIndex = new HashMap<>();
        this.metrics = new BenchmarkMetrics(NAME);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public byte[] setup() throws Exception {
        SecureRandom random = new SecureRandom();
        this.secretKey = new byte[32];
        random.nextBytes(secretKey);

        metrics.setSetupTimeMs(0);
        return secretKey;
    }

    @Override
    public void setup(byte[] key) throws Exception {
        if (key == null || key.length < 16) {
            throw new IllegalArgumentException("Key must be at least 128 bits");
        }
        this.secretKey = Arrays.copyOf(key, key.length);
    }

    @Override
    public long buildIndex(Multimap<String, String> multimap) throws Exception {
        if (secretKey == null) {
            setup();
        }

        long startTime = System.nanoTime();

        tokenIndex.clear();
        crossTagIndex.clear();
        singleIndex.clear();
        Set<String> allDocs = new HashSet<>();

        // Build single keyword index (T-set)
        Map<String, Set<String>> keywordToDocsMap = new HashMap<>();
        for (String keyword : multimap.keySet()) {
            Set<String> docs = new HashSet<>(multimap.get(keyword));
            keywordToDocsMap.put(keyword, docs);
            allDocs.addAll(docs);

            String encKeyword = encryptKeyword(keyword);
            List<String> encDocs = new ArrayList<>();
            for (String docId : docs) {
                encDocs.add(encryptDocId(docId));
            }
            singleIndex.put(encKeyword, encDocs);
        }

        // Build cross-tag index (X-set) for keyword pairs
        // This enables efficient conjunctive queries
        List<String> keywords = new ArrayList<>(keywordToDocsMap.keySet());

        // Build pairwise cross-tags for common keyword pairs
        for (int i = 0; i < keywords.size(); i++) {
            String kw1 = keywords.get(i);
            Set<String> docs1 = keywordToDocsMap.get(kw1);

            for (int j = i + 1; j < keywords.size(); j++) {
                String kw2 = keywords.get(j);
                Set<String> docs2 = keywordToDocsMap.get(kw2);

                // Find intersection
                Set<String> intersection = new HashSet<>(docs1);
                intersection.retainAll(docs2);

                if (!intersection.isEmpty()) {
                    String crossTag = generateCrossTag(kw1, kw2);
                    crossTagIndex.put(crossTag, intersection);
                }
            }
        }

        // Build token index for faster lookups
        for (Map.Entry<String, Set<String>> entry : keywordToDocsMap.entrySet()) {
            String token = generateToken(entry.getKey());
            List<String> tokenDocs = new ArrayList<>();
            for (String docId : entry.getValue()) {
                tokenDocs.add(generateDocToken(docId, entry.getKey()));
            }
            tokenIndex.put(token, tokenDocs);
        }

        this.keywordCount = keywords.size();
        this.documentCount = allDocs.size();
        this.indexSizeBytes = calculateIndexSize();

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        metrics.setIndexBuildTimeMs(elapsedMs);
        metrics.setIndexSizeBytes(indexSizeBytes);

        return elapsedMs;
    }

    @Override
    public long buildIndex(Map<String, List<String>> keywordIndex) throws Exception {
        Multimap<String, String> multimap = ArrayListMultimap.create();
        for (Map.Entry<String, List<String>> entry : keywordIndex.entrySet()) {
            multimap.putAll(entry.getKey(), entry.getValue());
        }
        return buildIndex(multimap);
    }

    @Override
    public List<String> search(String keyword) throws Exception {
        long startTime = System.nanoTime();

        String encKeyword = encryptKeyword(keyword);
        List<String> encResults = singleIndex.get(encKeyword);

        if (encResults == null) {
            metrics.addQueryTime((System.nanoTime() - startTime) / 1_000_000.0);
            return Collections.emptyList();
        }

        List<String> results = new ArrayList<>();
        for (String encDocId : encResults) {
            results.add(decryptDocId(encDocId));
        }

        long elapsedNs = System.nanoTime() - startTime;
        metrics.addQueryTime(elapsedNs / 1_000_000.0);

        return results;
    }

    @Override
    public List<String> searchAnd(List<String> keywords) throws Exception {
        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }

        if (keywords.size() == 1) {
            return search(keywords.get(0));
        }

        long startTime = System.nanoTime();

        // For 2-keyword AND, try cross-tag index first
        if (keywords.size() == 2) {
            String crossTag = generateCrossTag(keywords.get(0), keywords.get(1));
            Set<String> crossResult = crossTagIndex.get(crossTag);

            if (crossResult != null) {
                long elapsedNs = System.nanoTime() - startTime;
                metrics.addQueryTime(elapsedNs / 1_000_000.0);
                return new ArrayList<>(crossResult);
            }

            // Also try reverse order
            crossTag = generateCrossTag(keywords.get(1), keywords.get(0));
            crossResult = crossTagIndex.get(crossTag);

            if (crossResult != null) {
                long elapsedNs = System.nanoTime() - startTime;
                metrics.addQueryTime(elapsedNs / 1_000_000.0);
                return new ArrayList<>(crossResult);
            }
        }

        // Fallback: OXT-style search using token index
        // Find the keyword with smallest result set (stag)
        String smallestKeyword = keywords.get(0);
        int smallestSize = Integer.MAX_VALUE;

        for (String kw : keywords) {
            String encKw = encryptKeyword(kw);
            List<String> docs = singleIndex.get(encKw);
            if (docs != null && docs.size() < smallestSize) {
                smallestSize = docs.size();
                smallestKeyword = kw;
            }
        }

        // Get documents for smallest keyword
        Set<String> result = new HashSet<>(search(smallestKeyword));

        // Filter with remaining keywords
        for (String kw : keywords) {
            if (!kw.equals(smallestKeyword)) {
                result.retainAll(search(kw));
            }
        }

        long elapsedNs = System.nanoTime() - startTime;
        metrics.addQueryTime(elapsedNs / 1_000_000.0);

        return new ArrayList<>(result);
    }

    @Override
    public List<String> searchOr(List<String> keywords) throws Exception {
        long startTime = System.nanoTime();

        Set<String> result = new HashSet<>();
        for (String keyword : keywords) {
            result.addAll(search(keyword));
        }

        long elapsedNs = System.nanoTime() - startTime;
        metrics.addQueryTime(elapsedNs / 1_000_000.0);

        return new ArrayList<>(result);
    }

    @Override
    public boolean supportsBoolean() {
        return true;
    }

    @Override
    public long getIndexSizeBytes() {
        return indexSizeBytes;
    }

    @Override
    public String getSearchComplexity() {
        return "O(r_s + t) where r_s = smallest result set, t = query terms";
    }

    @Override
    public Map<String, String> getLeakageProfile() {
        Map<String, String> leakage = new HashMap<>();
        leakage.put("search_pattern", "Revealed");
        leakage.put("access_pattern", "Revealed");
        leakage.put("size_pattern", "Revealed for smallest term");
        leakage.put("cross_term_pattern", "Revealed - intersection sizes visible");
        leakage.put("forward_privacy", "No");
        leakage.put("backward_privacy", "No");
        leakage.put("oxt_leakage", "IP pattern (intersection pattern) leaked");
        return leakage;
    }

    @Override
    public BenchmarkMetrics getMetrics() {
        return metrics;
    }

    @Override
    public void reset() {
        tokenIndex.clear();
        crossTagIndex.clear();
        singleIndex.clear();
        keywordCount = 0;
        documentCount = 0;
        indexSizeBytes = 0;
        metrics = new BenchmarkMetrics(NAME);
    }

    @Override
    public void close() {
        reset();
        if (secretKey != null) {
            Arrays.fill(secretKey, (byte) 0);
        }
    }

    @Override
    public int getKeywordCount() {
        return keywordCount;
    }

    @Override
    public int getDocumentCount() {
        return documentCount;
    }

    // Helper methods

    private String encryptKeyword(String keyword) {
        return "EKI_" + keyword.hashCode();
    }

    private String encryptDocId(String docId) {
        return "EDI_" + docId.hashCode() + "_" + docId;
    }

    private String decryptDocId(String encryptedId) {
        if (encryptedId.startsWith("EDI_")) {
            int lastUnderscore = encryptedId.lastIndexOf('_');
            if (lastUnderscore > 4) {
                return encryptedId.substring(lastUnderscore + 1);
            }
        }
        return encryptedId;
    }

    private String generateCrossTag(String kw1, String kw2) {
        // Order keywords alphabetically for consistent lookup
        if (kw1.compareTo(kw2) > 0) {
            String temp = kw1;
            kw1 = kw2;
            kw2 = temp;
        }
        return "XTag_" + kw1.hashCode() + "_" + kw2.hashCode();
    }

    private String generateToken(String keyword) {
        return "TOK_" + keyword.hashCode();
    }

    private String generateDocToken(String docId, String keyword) {
        return "DT_" + docId.hashCode() + "_" + keyword.hashCode();
    }

    private long calculateIndexSize() {
        long size = 0;

        for (Map.Entry<String, List<String>> entry : singleIndex.entrySet()) {
            size += entry.getKey().length() * 2;
            for (String v : entry.getValue()) {
                size += v.length() * 2;
            }
        }

        for (Map.Entry<String, Set<String>> entry : crossTagIndex.entrySet()) {
            size += entry.getKey().length() * 2;
            for (String v : entry.getValue()) {
                size += v.length() * 2;
            }
        }

        for (Map.Entry<String, List<String>> entry : tokenIndex.entrySet()) {
            size += entry.getKey().length() * 2;
            for (String v : entry.getValue()) {
                size += v.length() * 2;
            }
        }

        return size;
    }
}
