package it.thesis.sse.dataset;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads the e-health dataset from JSON files or individual text files.
 * Provides data structures compatible with Clusion's TextExtractPar.
 */
public class DatasetLoader {

    private final Gson gson;
    private List<Document> documents;
    private Map<String, List<String>> keywordIndex;
    private Path datasetPath;

    public DatasetLoader() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        this.documents = new ArrayList<>();
        this.keywordIndex = new HashMap<>();
    }

    /**
     * Load complete dataset from JSON file.
     */
    public void loadFromJson(String jsonPath) throws IOException {
        Path path = Paths.get(jsonPath);
        this.datasetPath = path.getParent();

        try (FileReader reader = new FileReader(path.toFile(), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<Document>>() {
            }.getType();
            this.documents = gson.fromJson(reader, listType);
        }

        System.out.println("Loaded " + documents.size() + " documents from JSON");
    }

    /**
     * Load keyword index from JSON file.
     */
    public void loadKeywordIndex(String indexPath) throws IOException {
        try (FileReader reader = new FileReader(indexPath, StandardCharsets.UTF_8)) {
            Type mapType = new TypeToken<Map<String, List<String>>>() {
            }.getType();
            this.keywordIndex = gson.fromJson(reader, mapType);
        }

        System.out.println("Loaded " + keywordIndex.size() + " keywords from index");
    }

    /**
     * Load documents from individual text files.
     */
    public void loadFromTextFiles(String directoryPath) throws IOException {
        Path dir = Paths.get(directoryPath);
        this.datasetPath = dir.getParent();

        try (Stream<Path> files = Files.list(dir)) {
            List<Path> txtFiles = files
                    .filter(p -> p.toString().endsWith(".txt"))
                    .collect(Collectors.toList());

            for (Path file : txtFiles) {
                String content = FileUtils.readFileToString(file.toFile(), StandardCharsets.UTF_8);
                Document doc = parseTextDocument(file.getFileName().toString(), content);
                documents.add(doc);
            }
        }

        System.out.println("Loaded " + documents.size() + " documents from text files");
    }

    /**
     * Parse a text document into a Document object.
     */
    private Document parseTextDocument(String filename, String content) {
        Document doc = new Document();
        doc.setDocId(filename.replace(".txt", ""));

        // Simple parsing - extract key fields
        String[] lines = content.split("\n");
        List<String> keywords = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("Patient ID:")) {
                doc.setPatientId(line.substring(11).trim());
            } else if (line.startsWith("Name:")) {
                String[] names = line.substring(5).trim().split(" ", 2);
                doc.setFirstName(names[0]);
                doc.setLastName(names.length > 1 ? names[1] : "");
            } else if (line.startsWith("Age:")) {
                doc.setAge(Integer.parseInt(line.substring(4).trim()));
            } else if (line.startsWith("Department:")) {
                doc.setDepartment(line.substring(11).trim());
            } else if (line.startsWith("Admission Date:")) {
                doc.setAdmissionDate(line.substring(15).trim());
            } else if (line.startsWith("Keywords:")) {
                String[] kws = line.substring(9).trim().split(",\\s*");
                keywords.addAll(Arrays.asList(kws));
            }
        }

        doc.setKeywords(keywords);
        doc.setDiagnoses(new ArrayList<>());
        doc.setTreatments(new ArrayList<>());
        doc.setClinicalNotes(content);

        return doc;
    }

    /**
     * Build Clusion-compatible multimap (keyword -> document IDs).
     * This is the format expected by TextExtractPar.
     */
    public Multimap<String, String> buildClusionMultimap() {
        Multimap<String, String> multimap = ArrayListMultimap.create();

        for (Document doc : documents) {
            for (String keyword : doc.getKeywords()) {
                multimap.put(keyword, doc.getDocId());
            }
        }

        return multimap;
    }

    /**
     * Build lookup map from document ID to full document text.
     * Used for decrypting search results.
     */
    public Map<String, String> buildDocumentLookup() {
        Map<String, String> lookup = new HashMap<>();
        for (Document doc : documents) {
            lookup.put(doc.getDocId(), doc.toTextContent());
        }
        return lookup;
    }

    /**
     * Get documents as a map from document ID to bytes (for Clusion setup).
     */
    public Map<String, byte[]> getDocumentsAsBytes() {
        Map<String, byte[]> result = new HashMap<>();
        for (Document doc : documents) {
            result.put(doc.getDocId(), doc.toTextContent().getBytes(StandardCharsets.UTF_8));
        }
        return result;
    }

    /**
     * Get the path where document text files are stored.
     */
    public String getDocumentsDirectory() {
        return datasetPath.resolve("documents").toString();
    }

    /**
     * Get all unique keywords in the dataset.
     */
    public Set<String> getAllKeywords() {
        if (!keywordIndex.isEmpty()) {
            return keywordIndex.keySet();
        }

        Set<String> keywords = new HashSet<>();
        for (Document doc : documents) {
            keywords.addAll(doc.getKeywords());
        }
        return keywords;
    }

    /**
     * Get document IDs that match a keyword.
     */
    public List<String> getDocumentsForKeyword(String keyword) {
        if (!keywordIndex.isEmpty()) {
            return keywordIndex.getOrDefault(keyword, Collections.emptyList());
        }

        return documents.stream()
                .filter(doc -> doc.getKeywords().contains(keyword))
                .map(Document::getDocId)
                .collect(Collectors.toList());
    }

    public List<Document> getDocuments() {
        return documents;
    }

    public Map<String, List<String>> getKeywordIndex() {
        return keywordIndex;
    }

    public int getDocumentCount() {
        return documents.size();
    }

    public int getKeywordCount() {
        return getAllKeywords().size();
    }

    /**
     * Get dataset statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("numDocuments", documents.size());
        stats.put("numUniqueKeywords", getAllKeywords().size());

        int totalKeywords = documents.stream()
                .mapToInt(d -> d.getKeywords().size())
                .sum();
        stats.put("avgKeywordsPerDocument", (double) totalKeywords / documents.size());

        return stats;
    }
}
