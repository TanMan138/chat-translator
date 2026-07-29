package com.tanman.chattranslator.client.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protects spans wrapped in {@code {{...}}} so they are not translated.
 *
 * <p>Example: {@code hi {{Steve}} welcome} → model sees
 * {@code hi __CTPROT_0__ welcome}, then restore puts {@code Steve} back
 * (braces removed in the final text).
 */
public final class ProtectedSpans {

    /**
     * Non-nested double-brace markers. Inner text may include spaces and most
     * punctuation, but not {@code {} } themselves.
     */
    public static final Pattern MARKER = Pattern.compile("\\{\\{([^{}]+)\\}\\}");

    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("__CTPROT_(\\d+)__");

    private ProtectedSpans() {
    }

    /**
     * Masked text ready for the translator, plus the original protected strings
     * (without braces) for {@link Masked#restore}.
     */
    public record Masked(String text, List<String> originals) {

        /**
         * Puts protected strings back into model output. Tolerates light placeholder
         * mangling (extra spaces / case changes). Leftover braces are stripped.
         */
        public String restore(String translated) {
            if (translated == null) {
                return "";
            }
            if (originals.isEmpty()) {
                return unwrap(translated);
            }
            String out = translated;
            for (int i = 0; i < originals.size(); i++) {
                String ph = placeholder(i);
                String original = originals.get(i);
                if (out.contains(ph)) {
                    out = out.replace(ph, original);
                    continue;
                }
                // Model sometimes inserts spaces: __ CTPROT _ 0 __
                Pattern loose = Pattern.compile(
                        "(?i)__\\s*CTPROT\\s*_\\s*" + i + "\\s*__");
                Matcher m = loose.matcher(out);
                if (m.find()) {
                    out = m.replaceAll(Matcher.quoteReplacement(original));
                }
            }
            // Drop any braces the model echoed, and any unused placeholders.
            out = unwrap(out);
            out = PLACEHOLDER_TOKEN.matcher(out).replaceAll("");
            return out.replaceAll(" {2,}", " ").trim();
        }
    }

    /** True if {@code input} contains at least one {@code {{...}}} span. */
    public static boolean hasMarkers(String input) {
        return input != null && MARKER.matcher(input).find();
    }

    /** Removes markers, leaving inner text: {@code {{Steve}} → Steve}. */
    public static String unwrap(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        return MARKER.matcher(input).replaceAll("$1");
    }

    /**
     * Replaces each {@code {{span}}} with a stable placeholder the model is unlikely
     * to rewrite.
     */
    public static Masked mask(String input) {
        if (input == null || input.isEmpty()) {
            return new Masked(input == null ? "" : input, List.of());
        }
        input = GamingSlangGlossary.autoWrap(input);
        List<String> originals = new ArrayList<>();
        Matcher matcher = MARKER.matcher(input);
        StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            String inner = matcher.group(1).trim();
            if (inner.isEmpty()) {
                matcher.appendReplacement(masked, "");
                continue;
            }
            int index = originals.size();
            originals.add(inner);
            matcher.appendReplacement(masked, Matcher.quoteReplacement(placeholder(index)));
        }
        matcher.appendTail(masked);
        return new Masked(masked.toString(), List.copyOf(originals));
    }

    static String placeholder(int index) {
        return "__CTPROT_" + index + "__";
    }
}
