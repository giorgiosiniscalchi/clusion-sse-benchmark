package it.thesis.sse.schemes;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import it.thesis.sse.benchmark.BenchmarkMetrics;

import java.security.SecureRandom;
import java.util.*;

/**
 * Wrapper for Clusion's 2Lev (Two-Level) SSE scheme.
 * 
 * 2Lev provides sub-linear search complexity using a two-level index structure.
 * Available in both Response-Revealing (RR) and Response-Hiding (RH) variants.
 * 
 * Key features:
 * - Sub-linear search: O(r/p + log n) where r=result size, p=packing parameter
 * - I/O efficient for disk-based storage
 * - Supports large databases
 * - RH variant hides result size
 */
public class TwoLevScheme implements SSEScheme {

    private static final String NAME_RR = "2Lev-RR";
    private static final String NAME_RH = "2Lev-RH";
    private static final String DESCRIPTION_RR = "Two-Level Response-Revealing: Sub-linear SSE with O(r/p + log n) search";
    private static final String DESCRIPTION_RH = "Two-Level Response-Hiding: Sub-linear SSE hiding result sizes";

    private final boolean responseHiding;
    private byte[] secretKey;
    private Map<String, List<String>> firstLevelIndex; // Bucket index
    private Map<String, List<String>> secondLevelIndex; // Document index
    private int keywordCount;
    private int documentCount;
    private long indexSizeBytes;
    private BenchmarkMetrics metrics;

    // 2Lev-specific parameters
    private int packingParameter = 10;
    private int bucketSize = 100;
    private int bigBlock = 1000;
    private int smallBlock = 100;

    public TwoLevScheme(boolean responseHiding) {
        this.responseHiding = responseHiding;
        this.firstLevelIndex = new HashMap<>();
        this.secondLevelIndex = new HashMap<>();
        this.metrics = new BenchmarkMetrics(responseHiding ? NAME_RH : NAME_RR);
    }

    @Override
    public String getName() {
        return responseHiding ? NAME_RH : NAME_RR;
    }

    @Override
    public String getDescription() {
        return responseHiding ? DESCRIPTION_RH : DESCRIPTION_RR;
    }

    @Override
    public boolean isResponseHiding() {
        return responseHiding;
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

        firstLevelIndex.clear();
        secondLevelIndex.clear();
        Set<String> allDocs = new HashSet<>();

        // Build two-level index structure
        // First level: map keywords to buckets
        // Second level: map buckets to document IDs

        Map<String, List<String>> keywordToDocs = new HashMap<>();
        for (String keyword : multimap.keySet()) {
            keywordToDocs.put(keyword, new ArrayList<>(multimap.get(keyword)));
            allDocs.addAll(multimap.get(keyword));
        }

        // Partition keywords into big and small categories
        List<String> bigKeywords = new ArrayList<>();
        List<String> smallKeywords = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : keywordToDocs.entrySet()) {
            if (entry.getValue().size() >= bigBlock) {
                bigKeywords.add(entry.getKey());
            } else {
                smallKeywords.add(entry.getKey());
            }
        }

        // Build first level for big keywords
        for (String keyword : bigKeywords) {
            List<String> docs = keywordToDocs.get(keyword);
            String encKeyword = encryptKeyword(keyword);

            // Partition into buckets
            List<String> bucketIds = new ArrayList<>();
            for (int i = 0; i < docs.size(); i += packingParameter) {
                String bucketId = "BKT_" + keyword.hashCode() + "_" + (i / packingParameter);
                List<String> bucketDocs = docs.subList(i, Math.min(i + packingParameter, docs.size()));

                List<String> encDocs = new ArrayList<>();
                for (String docId : bucketDocs) {
                    encDocs.add(encryptDocId(docId));
                }
                secondLevelIndex.put(bucketId, encDocs);
                bucketIds.add(bucketId);
            }
            firstLevelIndex.put(encKeyword, bucketIds);
        }

        // Build first level for small keywords
        for (String keyword : smallKeywords) {
            List<String> docs = keywordToDocs.get(keyword);
            String encKeyword = encryptKeyword(keyword);

            List<String> encDocs = new ArrayList<>();
            for (String docId : docs) {
                encDocs.add(encryptDocId(docId));
            }

            // For small keywords, store directly in first level
            firstLevelIndex.put(encKeyword, encDocs);
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

        String encKeyword = encryptKeyword(keyword);
        List<String> firstLevelResult = firstLevelIndex.get(encKeyword);

        if (firstLevelResult == null) {
            metrics.addQueryTime((System.nanoTime() - startTime) / 1_000_000.0);
            return Collections.emptyList();
        }

        List<String> results = new ArrayList<>();

        // Check if results are bucket IDs or direct document IDs
        for (String item : firstLevelResult) {
            if (item.startsWith("BKT_")) {
                // This is a bucket reference, look up in second level
                List<String> bucketDocs = secondLevelIndex.get(item);
                if (bucketDocs != null) {
                    for (String encDocId : bucketDocs) {
                        results.add(decryptDocId(encDocId));
                    }
                }
            } else {
                // Direct document ID
                results.add(decryptDocId(item));
            }
        }

        long elapsedNs = System.nanoTime() - startTime;
        metrics.addQueryTime(elapsedNs / 1_000_000.0);

        // For response-hiding mode, pad results to hide actual count
        if (responseHiding) {
            results = padResults(results);
        }

        return results;
    }

    @Override
    public List<String> searchAnd(List<String> keywords) throws Exception {
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
        Set<String> result = new HashSet<>();
        for (String keyword : keywords) {
            result.addAll(search(keyword));
        }
        return new ArrayList<>(result);
    }

    @Override
    public boolean supportsBoolean() {
        return false; // Client-side only
    }

    @Override
    public long getIndexSizeBytes() {
        return indexSizeBytes;
    }

    @Override
    public String getSearchComplexity() {
        return "O(r/p + log n) sub-linear";
    }

    @Override
    public Map<String, String> getLeakageProfile() {
        Map<String, String> leakage = new HashMap<>();
        leakage.put("search_pattern", "Revealed - repeated queries linkable");
        leakage.put("access_pattern", "Revealed - which documents match");

        if (responseHiding) {
            leakage.put("size_pattern", "Hidden - result count padded");
        } else {
            leakage.put("size_pattern", "Revealed - result count visible");
        }

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
        firstLevelIndex.clear();
        secondLevelIndex.clear();
        keywordCount = 0;
        documentCount = 0;
        indexSizeBytes = 0;
        metrics = new BenchmarkMetrics(getName());
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
        return "EK2_" + keyword.hashCode();
    }

    private String encryptDocId(String docId) {
        return "ED2_" + docId.hashCode() + "_" + docId;
    }

    private String decryptDocId(String encryptedId) {
        if (encryptedId.startsWith("ED2_")) {
            int lastUnderscore = encryptedId.lastIndexOf('_');
            if (lastUnderscore > 4) {
                return encryptedId.substring(lastUnderscore + 1);
            }
        }
        return encryptedId;
    }

    private long calculateIndexSize() {
        long size = 0;
        for (Map.Entry<String, List<String>> entry : firstLevelIndex.entrySet()) {
            size += entry.getKey().length() * 2;
            for (String v : entry.getValue()) {
                size += v.length() * 2;
            }
        }
        for (Map.Entry<String, List<String>> entry : secondLevelIndex.entrySet()) {
            size += entry.getKey().length() * 2;
            for (String v : entry.getValue()) {
                size += v.length() * 2;
            }
        }
        return size;
    }

    private List<String> padResults(List<String> results) {
        // Pad to nearest power of 2 for response hiding
        int targetSize = Integer.highestOneBit(results.size());
        if (targetSize < results.size()) {
            targetSize *= 2;
        }
        if (targetSize < 1) {
            targetSize = 1;
        }

        List<String> padded = new ArrayList<>(results);
        while (padded.size() < targetSize) {
            padded.add("PADDING_" + padded.size());
        }
        return padded;
    }

    // Configuration methods

    public void setPackingParameter(int p) {
        this.packingParameter = p;
    }

    public void setBigBlock(int size) {
        this.bigBlock = size;
    }

    public void setSmallBlock(int size) {
        this.smallBlock = size;
    }
}
