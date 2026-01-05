package it.thesis.sse.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generates test queries for SSE benchmarking.
 * Creates single-keyword, AND, and OR queries with expected result counts.
 */
public class QueryGenerator {

    private final Map<String, List<String>> keywordIndex;
    private final Random random;
    private final Gson gson;

    public QueryGenerator(Map<String, List<String>> keywordIndex) {
        this.keywordIndex = keywordIndex;
        this.random = new Random();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public QueryGenerator(Map<String, List<String>> keywordIndex, long seed) {
        this.keywordIndex = keywordIndex;
        this.random = new Random(seed);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Load pre-generated queries from JSON file.
     */
    public List<QueryMetric> loadQueries(String queriesPath) throws IOException {
        try (FileReader reader = new FileReader(queriesPath, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<Map<String, Object>>>() {
            }.getType();
            List<Map<String, Object>> rawQueries = gson.fromJson(reader, listType);

            List<QueryMetric> queries = new ArrayList<>();
            int idx = 0;

            for (Map<String, Object> q : rawQueries) {
                String type = (String) q.get("type");
                @SuppressWarnings("unchecked")
                List<String> keywords = (List<String>) q.get("keywords");
                int expected = ((Number) q.get("expectedResults")).intValue();

                String queryId = String.format("Q%03d", idx++);
                QueryMetric metric;

                switch (type.toLowerCase()) {
                    case "single":
                        metric = QueryMetric.singleKeyword(queryId, keywords.get(0), expected);
                        break;
                    case "and":
                        metric = QueryMetric.andQuery(queryId, keywords, expected);
                        break;
                    case "or":
                        metric = QueryMetric.orQuery(queryId, keywords, expected);
                        break;
                    default:
                        metric = new QueryMetric(queryId, type, keywords, expected);
                }

                queries.add(metric);
            }

            return queries;
        }
    }

    /**
     * Generate a mix of test queries.
     */
    public List<QueryMetric> generateQueries(int numSingle, int numAnd, int numOr) {
        List<QueryMetric> queries = new ArrayList<>();
        int idx = 0;

        // Generate single keyword queries
        for (QueryMetric q : generateSingleKeywordQueries(numSingle)) {
            queries.add(new QueryMetric(String.format("Q%03d", idx++), q.getQueryType(),
                    q.getKeywords(), q.getExpectedResults()));
        }

        // Generate AND queries
        for (QueryMetric q : generateAndQueries(numAnd)) {
            queries.add(new QueryMetric(String.format("Q%03d", idx++), q.getQueryType(),
                    q.getKeywords(), q.getExpectedResults()));
        }

        // Generate OR queries
        for (QueryMetric q : generateOrQueries(numOr)) {
            queries.add(new QueryMetric(String.format("Q%03d", idx++), q.getQueryType(),
                    q.getKeywords(), q.getExpectedResults()));
        }

        return queries;
    }

    /**
     * Generate single keyword queries with varying selectivity.
     */
    public List<QueryMetric> generateSingleKeywordQueries(int count) {
        List<QueryMetric> queries = new ArrayList<>();
        List<String> keywords = new ArrayList<>(keywordIndex.keySet());

        if (keywords.isEmpty()) {
            return queries;
        }

        // Sort by frequency
        keywords.sort(Comparator.comparingInt(k -> keywordIndex.get(k).size()));

        int rareLimit = keywords.size() / 3;
        int commonStart = keywords.size() * 2 / 3;

        // Mix of rare, medium, and common keywords
        int perCategory = count / 3;

        // Rare keywords (low frequency)
        for (int i = 0; i < perCategory && i < rareLimit; i++) {
            String kw = keywords.get(random.nextInt(Math.max(1, rareLimit)));
            int expected = keywordIndex.get(kw).size();
            queries.add(QueryMetric.singleKeyword("", kw, expected));
        }

        // Medium keywords
        for (int i = 0; i < perCategory; i++) {
            int idx = rareLimit + random.nextInt(Math.max(1, commonStart - rareLimit));
            if (idx >= keywords.size())
                idx = keywords.size() - 1;
            String kw = keywords.get(idx);
            int expected = keywordIndex.get(kw).size();
            queries.add(QueryMetric.singleKeyword("", kw, expected));
        }

        // Common keywords (high frequency)
        for (int i = 0; i < perCategory; i++) {
            int idx = commonStart + random.nextInt(Math.max(1, keywords.size() - commonStart));
            if (idx >= keywords.size())
                idx = keywords.size() - 1;
            String kw = keywords.get(idx);
            int expected = keywordIndex.get(kw).size();
            queries.add(QueryMetric.singleKeyword("", kw, expected));
        }

        return queries;
    }

    /**
     * Generate AND (conjunction) queries.
     */
    public List<QueryMetric> generateAndQueries(int count) {
        List<QueryMetric> queries = new ArrayList<>();
        List<String> keywords = new ArrayList<>(keywordIndex.keySet());

        if (keywords.size() < 2) {
            return queries;
        }

        for (int i = 0; i < count; i++) {
            // Pick 2-3 random keywords
            int numKeywords = 2 + random.nextInt(2);
            Collections.shuffle(keywords, random);
            List<String> queryKeywords = new ArrayList<>(keywords.subList(0,
                    Math.min(numKeywords, keywords.size())));

            // Calculate expected intersection
            Set<String> result = new HashSet<>(keywordIndex.get(queryKeywords.get(0)));
            for (int j = 1; j < queryKeywords.size(); j++) {
                result.retainAll(keywordIndex.get(queryKeywords.get(j)));
            }

            queries.add(QueryMetric.andQuery("", queryKeywords, result.size()));
        }

        return queries;
    }

    /**
     * Generate OR (disjunction) queries.
     */
    public List<QueryMetric> generateOrQueries(int count) {
        List<QueryMetric> queries = new ArrayList<>();
        List<String> keywords = new ArrayList<>(keywordIndex.keySet());

        if (keywords.size() < 2) {
            return queries;
        }

        for (int i = 0; i < count; i++) {
            // Pick 2-3 random keywords
            int numKeywords = 2 + random.nextInt(2);
            Collections.shuffle(keywords, random);
            List<String> queryKeywords = new ArrayList<>(keywords.subList(0,
                    Math.min(numKeywords, keywords.size())));

            // Calculate expected union
            Set<String> result = new HashSet<>();
            for (String kw : queryKeywords) {
                result.addAll(keywordIndex.get(kw));
            }

            queries.add(QueryMetric.orQuery("", queryKeywords, result.size()));
        }

        return queries;
    }

    /**
     * Generate queries for specific keywords (for reproducibility testing).
     */
    public QueryMetric generateQueryForKeyword(String keyword) {
        List<String> docs = keywordIndex.get(keyword);
        if (docs == null) {
            return QueryMetric.singleKeyword("manual", keyword, 0);
        }
        return QueryMetric.singleKeyword("manual", keyword, docs.size());
    }

    /**
     * Generate queries for keyword pair (AND).
     */
    public QueryMetric generateAndQueryForKeywords(String kw1, String kw2) {
        Set<String> result = new HashSet<>(keywordIndex.getOrDefault(kw1, Collections.emptyList()));
        result.retainAll(keywordIndex.getOrDefault(kw2, Collections.emptyList()));
        return QueryMetric.andQuery("manual", List.of(kw1, kw2), result.size());
    }

    /**
     * Get keywords by frequency category.
     */
    public Map<String, List<String>> getKeywordsByFrequency() {
        List<String> keywords = new ArrayList<>(keywordIndex.keySet());
        keywords.sort(Comparator.comparingInt(k -> keywordIndex.get(k).size()));

        int third = keywords.size() / 3;

        Map<String, List<String>> categories = new LinkedHashMap<>();
        categories.put("rare", keywords.subList(0, third));
        categories.put("medium", keywords.subList(third, 2 * third));
        categories.put("common", keywords.subList(2 * third, keywords.size()));

        return categories;
    }

    /**
     * Get statistics about keyword distribution.
     */
    public Map<String, Object> getKeywordStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        List<Integer> frequencies = new ArrayList<>();
        for (List<String> docs : keywordIndex.values()) {
            frequencies.add(docs.size());
        }
        Collections.sort(frequencies);

        stats.put("totalKeywords", keywordIndex.size());
        stats.put("minFrequency", frequencies.isEmpty() ? 0 : frequencies.get(0));
        stats.put("maxFrequency", frequencies.isEmpty() ? 0 : frequencies.get(frequencies.size() - 1));

        double avg = frequencies.stream().mapToInt(Integer::intValue).average().orElse(0);
        stats.put("avgFrequency", Math.round(avg * 100) / 100.0);

        return stats;
    }
}
