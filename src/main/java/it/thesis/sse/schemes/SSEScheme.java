package it.thesis.sse.schemes;

import com.google.common.collect.Multimap;
import it.thesis.sse.benchmark.BenchmarkMetrics;

import java.util.List;
import java.util.Map;

/**
 * Common interface for all SSE (Searchable Symmetric Encryption) schemes.
 * Provides a unified API for setup, indexing, and searching operations.
 */
public interface SSEScheme {

    /**
     * Get the unique name of this SSE scheme.
     */
    String getName();

    /**
     * Get a brief description of the scheme.
     */
    String getDescription();

    /**
     * Initialize the scheme with a new key.
     * 
     * @return The generated secret key (scheme-specific format)
     */
    byte[] setup() throws Exception;

    /**
     * Initialize the scheme with an existing key.
     * 
     * @param key The secret key to use
     */
    void setup(byte[] key) throws Exception;

    /**
     * Build the encrypted index from keyword-document mappings.
     * 
     * @param multimap Multimap from keywords to document IDs
     * @return The time taken to build the index in milliseconds
     */
    long buildIndex(Multimap<String, String> multimap) throws Exception;

    /**
     * Build the encrypted index from a map of keyword to document list.
     * 
     * @param keywordIndex Map from keyword to list of document IDs
     * @return The time taken to build the index in milliseconds
     */
    long buildIndex(Map<String, List<String>> keywordIndex) throws Exception;

    /**
     * Search for documents matching a single keyword.
     * 
     * @param keyword The keyword to search for
     * @return List of matching document IDs
     */
    List<String> search(String keyword) throws Exception;

    /**
     * Search for documents matching all keywords (AND query).
     * 
     * @param keywords List of keywords (conjunction)
     * @return List of matching document IDs
     */
    List<String> searchAnd(List<String> keywords) throws Exception;

    /**
     * Search for documents matching any keyword (OR query).
     * 
     * @param keywords List of keywords (disjunction)
     * @return List of matching document IDs
     */
    List<String> searchOr(List<String> keywords) throws Exception;

    /**
     * Check if this scheme supports boolean queries.
     */
    boolean supportsBoolean();

    /**
     * Get the size of the encrypted index in bytes.
     */
    long getIndexSizeBytes();

    /**
     * Get the theoretical search complexity of this scheme.
     * 
     * @return Description of search complexity (e.g., "O(n)", "O(log n)")
     */
    String getSearchComplexity();

    /**
     * Get the leakage profile of this scheme.
     * 
     * @return Map describing what information is leaked
     */
    Map<String, String> getLeakageProfile();

    /**
     * Get current metrics for this scheme.
     */
    BenchmarkMetrics getMetrics();

    /**
     * Reset the scheme state (for fresh benchmarks).
     */
    void reset();

    /**
     * Clean up resources.
     */
    void close();

    /**
     * Check if the scheme is in response-hiding mode.
     */
    default boolean isResponseHiding() {
        return false;
    }

    /**
     * Get the number of indexed keywords.
     */
    int getKeywordCount();

    /**
     * Get the number of indexed documents.
     */
    int getDocumentCount();
}
