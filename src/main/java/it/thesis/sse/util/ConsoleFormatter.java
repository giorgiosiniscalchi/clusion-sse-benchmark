package it.thesis.sse.util;

/**
 * Console output formatting with colors and tables.
 * Provides consistent formatting for benchmark output.
 */
public class ConsoleFormatter {

    // ANSI color codes
    public static final String RESET = "\u001B[0m";
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    // Bold and styles
    public static final String BOLD = "\u001B[1m";
    public static final String UNDERLINE = "\u001B[4m";

    // Check if colors are supported
    private static final boolean COLORS_ENABLED = checkColorSupport();

    private static boolean checkColorSupport() {
        String term = System.getenv("TERM");
        String colorterm = System.getenv("COLORTERM");

        // Disable on Windows unless in modern terminal
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return colorterm != null ||
                    (term != null && (term.contains("xterm") || term.contains("256")));
        }

        return term != null || colorterm != null;
    }

    /**
     * Create a header with decorations.
     */
    public static String header(String text) {
        int width = 60;
        StringBuilder sb = new StringBuilder();

        sb.append(color("╔", CYAN))
                .append(color("═".repeat(width - 2), CYAN))
                .append(color("╗", CYAN))
                .append("\n");

        int padding = (width - 2 - text.length()) / 2;
        sb.append(color("║", CYAN))
                .append(" ".repeat(padding))
                .append(bold(text))
                .append(" ".repeat(width - 2 - padding - text.length()))
                .append(color("║", CYAN))
                .append("\n");

        sb.append(color("╚", CYAN))
                .append(color("═".repeat(width - 2), CYAN))
                .append(color("╝", CYAN));

        return sb.toString();
    }

    /**
     * Create a sub-header.
     */
    public static String subHeader(String text) {
        StringBuilder sb = new StringBuilder();
        sb.append(color("──── ", BLUE))
                .append(bold(text))
                .append(color(" ", BLUE))
                .append(color("─".repeat(Math.max(0, 50 - text.length())), BLUE));
        return sb.toString();
    }

    /**
     * Create a separator line.
     */
    public static String separator(int width) {
        return color("─".repeat(width), BLUE);
    }

    /**
     * Format a success message.
     */
    public static String success(String text) {
        return color("✓ ", GREEN) + text;
    }

    /**
     * Format a failure message.
     */
    public static String failure(String text) {
        return color("✗ ", RED) + text;
    }

    /**
     * Format a warning message.
     */
    public static String warning(String text) {
        return color("⚠ ", YELLOW) + text;
    }

    /**
     * Format an info message.
     */
    public static String info(String text) {
        return color("ℹ ", CYAN) + text;
    }

    /**
     * Apply color to text.
     */
    public static String color(String text, String colorCode) {
        if (!COLORS_ENABLED) {
            return text;
        }
        return colorCode + text + RESET;
    }

    /**
     * Make text bold.
     */
    public static String bold(String text) {
        if (!COLORS_ENABLED) {
            return text;
        }
        return BOLD + text + RESET;
    }

    /**
     * Format a key-value pair.
     */
    public static String keyValue(String key, Object value) {
        return String.format("  %s: %s", color(key, CYAN), value);
    }

    /**
     * Format a progress bar.
     */
    public static String progressBar(int current, int total, int width) {
        int completed = (int) ((double) current / total * width);
        StringBuilder sb = new StringBuilder();

        sb.append("[");
        sb.append(color("█".repeat(completed), GREEN));
        sb.append(color("░".repeat(width - completed), WHITE));
        sb.append("] ");
        sb.append(String.format("%d/%d", current, total));

        return sb.toString();
    }

    /**
     * Format a table row.
     */
    public static String tableRow(String... cells) {
        StringBuilder sb = new StringBuilder();
        sb.append("│");
        for (String cell : cells) {
            sb.append(" ").append(cell).append(" │");
        }
        return sb.toString();
    }

    /**
     * Create a table separator.
     */
    public static String tableSeparator(int... widths) {
        StringBuilder sb = new StringBuilder();
        sb.append("├");
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) {
                sb.append("┼");
            }
        }
        sb.append("┤");
        return sb.toString();
    }

    /**
     * Format duration in human-readable form.
     */
    public static String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        } else if (ms < 60000) {
            return String.format("%.2f s", ms / 1000.0);
        } else {
            long minutes = ms / 60000;
            long seconds = (ms % 60000) / 1000;
            return String.format("%d min %d s", minutes, seconds);
        }
    }

    /**
     * Format bytes in human-readable form.
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Format a percentage.
     */
    public static String formatPercent(double value) {
        return String.format("%.1f%%", value);
    }

    /**
     * Print a metric comparison.
     */
    public static String metricComparison(String metric, double value1, double value2) {
        double diff = ((value2 - value1) / value1) * 100;
        String diffStr;

        if (diff > 0) {
            diffStr = color(String.format("+%.1f%%", diff), RED);
        } else if (diff < 0) {
            diffStr = color(String.format("%.1f%%", diff), GREEN);
        } else {
            diffStr = "0%";
        }

        return String.format("%s: %.2f → %.2f (%s)", metric, value1, value2, diffStr);
    }

    /**
     * Create a spinner frame for loading animation.
     */
    public static char spinner(int frame) {
        char[] frames = { '⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏' };
        return frames[frame % frames.length];
    }

    /**
     * Clear current line (for progress updates).
     */
    public static String clearLine() {
        return "\r\u001B[K";
    }
}
