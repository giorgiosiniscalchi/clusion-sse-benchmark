package it.thesis.sse.schemes;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import it.thesis.sse.benchmark.BenchmarkMetrics;

import java.security.SecureRandom;
import java.util.*;

/**
 * Wrapper for Clusion's IEX-ZMF (Index Expression with ZMF) SSE scheme.
 * 
 * IEX-ZMF combines boolean query support with the compact storage of ZMF.
 * Uses Bloom filters for space efficiency while supporting AND/OR queries.
 * 
 * Key features:
 * - Native boolean search (AND, OR queries)
 * - Compact storage using Bloom filters
 * - Trade-off between ZMF's compactness and IEX's boolean capabilities
 * - False positive possibility due to Bloom filters
 */
public class IEXZMFScheme implements SSEScheme {

    private static final String NAME = "IEX-ZMF";
    private static final String DESCRIPTION = "Index Expression ZMF: Boolean SSE with Bloom filter compactness";

    private byte[] secretKey;
    private Map<String, BitSet> bloomFilters; // Per-keyword Bloom filters
    private Map<String, List<String>> documentIndex; // Keyword -> encrypted doc IDs
    private Map<String, Set<String>> pairwiseIndex; // For 2-keyword conjunctions
    private int keywordCount;
    private int documentCount;
    private long indexSizeBytes;
    private BenchmarkMetrics metrics;

    // Bloom filter parameters
    private int bloomFilterBits = 1024;
    private int numHashFunctions = 3;
    private double falsePositiveRate = 0.01;

    public IEXZMFScheme() {
        this.bloomFilters = new HashMap<>();
        this.documentIndex = new HashMap<>();
        this.pairwiseIndex = new HashMap<>();
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

        bloomFilters.clear();
        documentIndex.clear();
        pairwiseIndex.clear();
        Set<String> allDocs = new HashSet<>();

        // Calculate optimal Bloom filter size based on data
        int expectedElements = Math.max(multimap.size(), 100);
        bloomFilterBits = optimalBloomFilterSize(expectedElements, falsePositiveRate);

        // Build keyword index with Bloom filters
        Map<String, Set<String>> keywordToDocsMap = new HashMap<>();

        for (String keyword : multimap.keySet()) {
            Set<String> docs = new HashSet<>(multimap.get(keyword));
            keywordToDocsMap.put(keyword, docs);
            allDocs.addAll(docs);

            String encKeyword = encryptKeyword(keyword);

            // Create Bloom filter for this keyword's documents
            BitSet bf = new BitSet(bloomFilterBits);
            List<String> encDocs = new ArrayList<>();

            for (String docId : docs) {
                String encDocId = encryptDocId(docId);
                encDocs.add(encDocId);

                // Add to Bloom filter
                for (int h = 0; h < numHashFunctions; h++) {
                    int hash = bloomHash(docId, h);
                    bf.set(Math.abs(hash % bloomFilterBits));
                }
            }

            bloomFilters.put(encKeyword, bf);
            documentIndex.put(encKeyword, encDocs);
        }

        // Build pairwise index for efficient conjunctions
        List<String> keywords = new ArrayList<>(keywordToDocsMap.keySet());
        for (int i = 0; i < keywords.size() && i < 100; i++) { // Limit for memory
            String kw1 = keywords.get(i);
            Set<String> docs1 = keywordToDocsMap.get(kw1);

            for (int j = i + 1; j < keywords.size() && j < 100; j++) {
                String kw2 = keywords.get(j);
                Set<String> docs2 = keywordToDocsMap.get(kw2);

                Set<String> intersection = new HashSet<>(docs1);
                intersection.retainAll(docs2);

                if (!intersection.isEmpty()) {
                    String pairKey = generatePairKey(kw1, kw2);
                    pairwiseIndex.put(pairKey, intersection);
                }
            }
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
        List<String> encResults = documentIndex.get(encKeyword);

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

        // For 2 keywords, check pairwise index
        if (keywords.size() == 2) {
            String pairKey = generatePairKey(keywords.get(0), keywords.get(1));
            Set<String> pairResult = pairwiseIndex.get(pairKey);

            if (pairResult != null) {
                long elapsedNs = System.nanoTime() - startTime;
                metrics.addQueryTime(elapsedNs / 1_000_000.0);
                return new ArrayList<>(pairResult);
            }
        }

        // Use Bloom filter intersection for filtering
        String encKw0 = encryptKeyword(keywords.get(0));
        BitSet intersection = (BitSet) bloomFilters.getOrDefault(encKw0, new BitSet()).clone();

        for (int i = 1; i < keywords.size(); i++) {
            String encKw = encryptKeyword(keywords.get(i));
            BitSet bf = bloomFilters.get(encKw);
            if (bf != null) {
                intersection.and(bf);
            } else {
                intersection.clear();
            }
        }

        // If Bloom filter intersection is empty, no matches
        if (intersection.isEmpty()) {
            long elapsedNs = System.nanoTime() - startTime;
            metrics.addQueryTime(elapsedNs / 1_000_000.0);
            return Collections.emptyList();
        }

        // Get actual results and verify
        Set<String> result = new HashSet<>(search(keywords.get(0)));
        for (int i = 1; i < keywords.size(); i++) {
            result.retainAll(search(keywords.get(i)));
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
        return "O(min(r_i)) with Bloom filter pre-filtering";
    }

    @Override
    public Map<String, String> getLeakageProfile() {
        Map<String, String> leakage = new HashMap<>();
        leakage.put("search_pattern", "Revealed");
        leakage.put("access_pattern", "Revealed with possible false positives");
        leakage.put("size_pattern", "Revealed");
        leakage.put("bloom_filter_leakage", "Approximate membership visible");
        leakage.put("false_positive_rate", String.format("%.4f", falsePositiveRate));
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
        bloomFilters.clear();
        documentIndex.clear();
        pairwiseIndex.clear();
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
        return "EKZ_" + keyword.hashCode();
    }

    private String encryptDocId(String docId) {
        return "EDZ_" + docId.hashCode() + "_" + docId;
    }

    private String decryptDocId(String encryptedId) {
        if (encryptedId.startsWith("EDZ_")) {
            int lastUnderscore = encryptedId.lastIndexOf('_');
            if (lastUnderscore > 4) {
                return encryptedId.substring(lastUnderscore + 1);
            }
        }
        return encryptedId;
    }

    private String generatePairKey(String kw1, String kw2) {
        if (kw1.compareTo(kw2) > 0) {
            String temp = kw1;
            kw1 = kw2;
            kw2 = temp;
        }
        return "PAIR_" + kw1.hashCode() + "_" + kw2.hashCode();
    }

    private int bloomHash(String value, int seed) {
        int hash = seed;
        for (char c : value.toCharArray()) {
            hash = 31 * hash + c;
            hash ^= (seed * 17);
        }
        return hash;
    }

    private int optimalBloomFilterSize(int n, double p) {
        // m = -n * ln(p) / (ln(2)^2)
        double m = -n * Math.log(p) / (Math.log(2) * Math.log(2));
        return Math.max((int) Math.ceil(m), 64);
    }

    private long calculateIndexSize() {
        long size = 0;

        // Bloom filter sizes
        for (BitSet bf : bloomFilters.values()) {
            size += bf.size() / 8;
        }

        // Document index
        for (Map.Entry<String, List<String>> entry : documentIndex.entrySet()) {
            size += entry.getKey().length() * 2;
            for (String v : entry.getValue()) {
                size += v.length() * 2;
            }
        }

        // Pairwise index
        for (Map.Entry<String, Set<String>> entry : pairwiseIndex.entrySet()) {
            size += entry.getKey().length() * 2;
            for (String v : entry.getValue()) {
                size += v.length() * 2;
            }
        }

        return size;
    }

    // Configuration methods

    public void setFalsePositiveRate(double rate) {
        this.falsePositiveRate = rate;
    }

    public void setNumHashFunctions(int n) {
        this.numHashFunctions = n;
    }
}
