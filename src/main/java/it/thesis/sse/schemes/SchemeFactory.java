package it.thesis.sse.schemes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Factory class for creating SSE scheme instances by name.
 */
public class SchemeFactory {

    private static final Map<String, Class<? extends SSEScheme>> SCHEME_REGISTRY = new HashMap<>();

    static {
        // Register all available schemes
        SCHEME_REGISTRY.put("ZMF", ZMFScheme.class);
        SCHEME_REGISTRY.put("2Lev-RR", TwoLevScheme.class);
        SCHEME_REGISTRY.put("2Lev-RH", TwoLevScheme.class);
        SCHEME_REGISTRY.put("IEX-2Lev", IEXTwoLevScheme.class);
        SCHEME_REGISTRY.put("IEX-ZMF", IEXZMFScheme.class);
    }

    /**
     * Create a new instance of an SSE scheme by name.
     * 
     * @param schemeName Name of the scheme (e.g., "ZMF", "2Lev-RR")
     * @return New instance of the scheme
     */
    public static SSEScheme createScheme(String schemeName) {
        switch (schemeName.toUpperCase()) {
            case "ZMF":
                return new ZMFScheme();
            case "2LEV-RR":
            case "2LEV_RR":
            case "TWOLEV-RR":
                return new TwoLevScheme(false); // Response-revealing
            case "2LEV-RH":
            case "2LEV_RH":
            case "TWOLEV-RH":
                return new TwoLevScheme(true); // Response-hiding
            case "IEX-2LEV":
            case "IEX_2LEV":
            case "IEX2LEV":
                return new IEXTwoLevScheme();
            case "IEX-ZMF":
            case "IEX_ZMF":
            case "IEXZMF":
                return new IEXZMFScheme();
            default:
                throw new IllegalArgumentException("Unknown SSE scheme: " + schemeName +
                        ". Available schemes: " + getAvailableSchemes());
        }
    }

    /**
     * Get all available scheme names.
     */
    public static Set<String> getAvailableSchemes() {
        return SCHEME_REGISTRY.keySet();
    }

    /**
     * Check if a scheme is available.
     */
    public static boolean isSchemeAvailable(String schemeName) {
        return SCHEME_REGISTRY.containsKey(schemeName.toUpperCase().replace("_", "-"));
    }

    /**
     * Get scheme description by name.
     */
    public static String getSchemeDescription(String schemeName) {
        SSEScheme scheme = createScheme(schemeName);
        String description = scheme.getDescription();
        scheme.close();
        return description;
    }

    /**
     * Create all available schemes for benchmarking.
     */
    public static Map<String, SSEScheme> createAllSchemes() {
        Map<String, SSEScheme> schemes = new HashMap<>();
        for (String name : getAvailableSchemes()) {
            try {
                schemes.put(name, createScheme(name));
            } catch (Exception e) {
                System.err.println("Warning: Failed to create scheme " + name + ": " + e.getMessage());
            }
        }
        return schemes;
    }

    /**
     * Create schemes from a comma-separated list.
     */
    public static Map<String, SSEScheme> createSchemes(String schemesList) {
        Map<String, SSEScheme> schemes = new HashMap<>();
        String[] names = schemesList.split(",");
        for (String name : names) {
            name = name.trim();
            if (!name.isEmpty()) {
                try {
                    schemes.put(name, createScheme(name));
                } catch (Exception e) {
                    System.err.println("Warning: Failed to create scheme " + name + ": " + e.getMessage());
                }
            }
        }
        return schemes;
    }
}
