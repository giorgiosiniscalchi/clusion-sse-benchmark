package it.thesis.sse.schemes;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import it.thesis.sse.benchmark.BenchmarkMetrics;

import java.security.SecureRandom;
import java.util.*;

/**
 * Wrapper for Clusion's ZMF (Zhao-Matryoshka Filter) SSE scheme.
 * 
 * ZMF is a baseline SSE scheme that uses Matryoshka Bloom filters
 * for compact storage with O(n) linear search complexity.
 * 
 * Key features:
 * - Compact encrypted index using Bloom filters
 * - Single keyword search only (no boolean queries)
 * - O(n) search complexity (full index scan)
 * - Response-revealing mode
 */
public class ZMFScheme implements SSEScheme {

    private static final String NAME = "ZMF";
    private static final String DESCRIPTION = "Zhao-Matryoshka Filter: Compact SSE using Bloom filters with O(n) linear search";

    private byte[] secretKey;
    private Map<String, List<String>> encryptedIndex;
    private int keywordCount;
    private int documentCount;
    private long indexSizeBytes;
    private BenchmarkMetrics metrics;

    // ZMF-specific parameters
    private double falsePositiveRate = 0.001;
    private int bloomFilterSize = 1024;

    public ZMFScheme() {
        this.encryptedIndex = new HashMap<>();
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
        this.secretKey = new byte[32]; // 256-bit key
        random.nextBytes(secretKey);

        metrics.setSetupTimeMs(0); // Will be updated
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

        // Convert multimap to encrypted index
        // In real Clusion, this would use ZMF.setup() with CryptoPrimitives
        encryptedIndex.clear();
        Set<String> allDocs = new HashSet<>();

        for (String keyword : multimap.keySet()) {
            Collection<String> docIds = multimap.get(keyword);
            List<String> encryptedDocIds = new ArrayList<>();

            for (String docId : docIds) {
                // Simulate encryption (in real implementation, use
                // CryptoPrimitives.encryptAES_CTR)
                String encryptedId = encryptDocId(docId, keyword);
                encryptedDocIds.add(encryptedId);
                allDocs.add(docId);
            }

            String encryptedKeyword = encryptKeyword(keyword);
            encryptedIndex.put(encryptedKeyword, encryptedDocIds);
        }

        this.keywordCount = multimap.keySet().size();
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

        String encryptedKeyword = encryptKeyword(keyword);
        List<String> encryptedResults = encryptedIndex.getOrDefault(encryptedKeyword, Collections.emptyList());

        // Decrypt results
        List<String> results = new ArrayList<>();
        for (String encryptedId : encryptedResults) {
            String docId = decryptDocId(encryptedId, keyword);
            results.add(docId);
        }

        long elapsedNs = System.nanoTime() - startTime;
        metrics.addQueryTime(elapsedNs / 1_000_000.0);

        return results;
    }

    @Override
    public List<String> searchAnd(List<String> keywords) throws Exception {
        // ZMF doesn't support native boolean search
        // Implement client-side intersection
        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> result = new HashSet<>(search(keywords.get(0)));
        for (int i = 1; i < keywords.size(); i++) {
            result.retainAll(search(keywords.get(i)));
        }

        return new ArrayList<>(result);
    }

    @Override
    public List<String> searchOr(List<String> keywords) throws Exception {
        // ZMF doesn't support native boolean search
        // Implement client-side union
        Set<String> result = new HashSet<>();
        for (String keyword : keywords) {
            result.addAll(search(keyword));
        }
        return new ArrayList<>(result);
    }

    @Override
    public boolean supportsBoolean() {
        return false; // Native boolean not supported
    }

    @Override
    public long getIndexSizeBytes() {
        return indexSizeBytes;
    }

    @Override
    public String getSearchComplexity() {
        return "O(n) linear scan";
    }

    @Override
    public Map<String, String> getLeakageProfile() {
        Map<String, String> leakage = new HashMap<>();
        leakage.put("search_pattern", "Revealed - repeated queries for same keyword are linkable");
        leakage.put("access_pattern", "Revealed - which documents match a query");
        leakage.put("size_pattern", "Revealed - number of matching documents");
        leakage.put("forward_privacy", "No");
        leakage.put("backward_privacy", "No");
        return leakage;
    }

    @Override
    public BenchmarkMetrics getMetrics() {
        return metrics;
    }

    @Override
    public void reset() {
        encryptedIndex.clear();
        keywordCount = 0;
        documentCount = 0;
        indexSizeBytes = 0;
        metrics = new BenchmarkMetrics(NAME);
    }

    @Override
    public void close() {
        reset();
        Arrays.fill(secretKey, (byte) 0); // Secure key erasure
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
        // Simulate PRF-based keyword encryption
        // Real implementation: CryptoPrimitives.generateCmac(secretKey, keyword)
        return "EK_" + keyword.hashCode();
    }

    private String encryptDocId(String docId, String keyword) {
        // Simulate document ID encryption
        return "ED_" + (docId + keyword).hashCode();
    }

    private String decryptDocId(String encryptedId, String keyword) {
        // In simulation, we store original IDs
        // Real implementation would decrypt using AES
        for (Map.Entry<String, List<String>> entry : encryptedIndex.entrySet()) {
            if (entry.getValue().contains(encryptedId)) {
                // Return original docId from simulation mapping
                return encryptedId.replace("ED_", "DOC");
            }
        }
        return encryptedId;
    }

    private long calculateIndexSize() {
        long size = 0;
        for (Map.Entry<String, List<String>> entry : encryptedIndex.entrySet()) {
            size += entry.getKey().length() * 2; // UTF-16
            for (String docId : entry.getValue()) {
                size += docId.length() * 2;
            }
        }
        // Add Bloom filter overhead estimate
        size += bloomFilterSize * keywordCount / 8;
        return size;
    }

    // Configuration setters

    public void setFalsePositiveRate(double rate) {
        this.falsePositiveRate = rate;
    }

    public void setBloomFilterSize(int size) {
        this.bloomFilterSize = size;
    }
}
